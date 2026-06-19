[CmdletBinding()]
param(
    [string]$HostAddress = "0.0.0.0",
    [int]$Port = 8787,
    [string]$Runner = "app-server",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$bridgeDir = Join-Path $repoRoot "bridge"
$runtimeDir = Join-Path $repoRoot ".tmp\bridge"
$logsDir = Join-Path $repoRoot ".logs\bridge"
$statePath = Join-Path $runtimeDir "bridge-process.json"
$stdoutLog = Join-Path $logsDir "bridge-stdout.log"
$stderrLog = Join-Path $logsDir "bridge-stderr.log"
$controlLog = Join-Path $logsDir "bridge-control.log"
$restartLockPath = Join-Path $runtimeDir "restart-bridge.lock"
$stopScript = Join-Path $PSScriptRoot "stop-bridge-background.ps1"
$restartLockStream = $null

function Resolve-CodexExecutable {
    if ($env:CODEX_EXECUTABLE -and (Test-Path $env:CODEX_EXECUTABLE)) {
        return $env:CODEX_EXECUTABLE
    }

    $appData = $env:APPDATA
    if ($appData) {
        $npmCliEntrypoint = Join-Path $appData "npm\node_modules\@openai\codex\bin\codex.js"
        if (Test-Path $npmCliEntrypoint) {
            return $npmCliEntrypoint
        }

        $vendorExecutable = Join-Path $appData "npm\node_modules\@openai\codex\node_modules\@openai\codex-win32-x64\vendor\x86_64-pc-windows-msvc\codex\codex.exe"
        if (Test-Path $vendorExecutable) {
            return $vendorExecutable
        }
    }

    try {
        $resolvedCodexCmd = (Get-Command codex.cmd -ErrorAction Stop).Source
        if ($resolvedCodexCmd -and (Test-Path $resolvedCodexCmd)) {
            return $resolvedCodexCmd
        }
    } catch {
    }

    try {
        $resolvedCodex = (Get-Command codex.exe -ErrorAction Stop).Source
        if ($resolvedCodex -and (Test-Path $resolvedCodex)) {
            return $resolvedCodex
        }
    } catch {
    }

    return $null
}

function Write-ControlLog {
    param(
        [string]$Action,
        [string]$Message
    )

    $timestamp = (Get-Date).ToString("o")
    $line = "$timestamp [$Action] $Message"
    Add-Content -Path $controlLog -Value $line -Encoding UTF8
}

New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

try {
    try {
        $restartLockStream = [System.IO.File]::Open($restartLockPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
        $lockText = "pid=$PID startedAt=$((Get-Date).ToString("o"))"
        $lockBytes = [System.Text.Encoding]::UTF8.GetBytes($lockText)
        $restartLockStream.SetLength(0)
        $restartLockStream.Write($lockBytes, 0, $lockBytes.Length)
        $restartLockStream.Flush()
    } catch [System.IO.IOException] {
        Write-ControlLog "failure" "restart skipped because another restart instance holds the lock: $restartLockPath"
        Write-Warning "Another bridge restart is already running; skipping."
        exit 0
    }

    if (Test-Path $statePath) {
        try {
            $powerShellExe = Join-Path $PSHOME "powershell.exe"
            if (-not (Test-Path $powerShellExe)) {
                $powerShellExe = "powershell.exe"
            }
            & $powerShellExe -NoProfile -ExecutionPolicy Bypass -File $stopScript
            if ($LASTEXITCODE -ne 0) {
                Write-ControlLog "failure" "stop script failed before restart start; existing state was kept"
                exit $LASTEXITCODE
            }
        } catch {
            Write-ControlLog "failure" "stop script threw before restart start: $($_.Exception.Message)"
            throw
        }

        Write-ControlLog "drain" "skipped drain because restart stops the old process before starting a new one"
    }

    Push-Location $bridgeDir
    try {
        if (-not $SkipBuild) {
            Write-Host "Building bridge production bundle..."
            npm run build
        }

        $nodeCommand = Get-Command node -ErrorAction Stop
        $nodePath = $nodeCommand.Source

        $env:CODEX_MOBILE_RUNNER = $Runner
        $env:HOST = $HostAddress
        $env:PORT = [string]$Port
        $resolvedCodexExecutable = Resolve-CodexExecutable
        if ($resolvedCodexExecutable) {
            $env:CODEX_EXECUTABLE = $resolvedCodexExecutable
            Write-Host "Resolved codex executable: $resolvedCodexExecutable"
        } else {
            Write-Warning "Unable to resolve codex executable explicitly; bridge will fall back to its internal lookup."
        }

        if (Test-Path $stdoutLog) {
            Remove-Item -LiteralPath $stdoutLog -Force
        }
        if (Test-Path $stderrLog) {
            Remove-Item -LiteralPath $stderrLog -Force
        }

        Write-ControlLog "start" "starting bridge in background: runner=$Runner, host=$HostAddress, port=$Port"
        Write-Host "Starting bridge in background: runner=$Runner, host=$HostAddress, port=$Port"
        $process = Start-Process `
            -FilePath $nodePath `
            -ArgumentList "dist/index.js" `
            -WorkingDirectory $bridgeDir `
            -WindowStyle Hidden `
            -RedirectStandardOutput $stdoutLog `
            -RedirectStandardError $stderrLog `
            -PassThru

        $state = [ordered]@{
            ProcessId = $process.Id
            Runner = $Runner
            Host = $HostAddress
            Port = $Port
            StartedAt = (Get-Date).ToString("o")
            CodexExecutable = $resolvedCodexExecutable
            StdoutLog = $stdoutLog
            StderrLog = $stderrLog
        }
        $state | ConvertTo-Json | Set-Content -Path $statePath -Encoding UTF8

        $healthUrl = "http://127.0.0.1:$Port/health"
        $deadline = (Get-Date).AddSeconds(20)
        $started = $false
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Milliseconds 500

            $runningProcess = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
            if ($null -eq $runningProcess) {
                break
            }

            try {
                $response = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 2
                if ($response.ok -eq $true) {
                    $started = $true
                    break
                }
            } catch {
            }
        }

        if (-not $started) {
            $stderr = ""
            if (Test-Path $stderrLog) {
                $stderr = Get-Content -Path $stderrLog -Raw
            }
            Write-ControlLog "failure" "bridge background start failed; check log: $stderrLog"
            throw "Bridge background start failed. Check log: $stderrLog`n$stderr"
        }

        Write-ControlLog "start" "bridge is running in background: pid=$($process.Id), endpoint=http://${HostAddress}:$Port"
        Write-Host "Bridge is running in background."
        Write-Host "Endpoint: http://${HostAddress}:$Port"
        Write-Host "stdout log: $stdoutLog"
        Write-Host "stderr log: $stderrLog"
    } finally {
        Pop-Location
    }
} finally {
    if ($null -ne $restartLockStream) {
        $restartLockStream.Close()
        Remove-Item -LiteralPath $restartLockPath -Force -ErrorAction SilentlyContinue
    }
}
