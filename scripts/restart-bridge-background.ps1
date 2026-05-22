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
$stopScript = Join-Path $PSScriptRoot "stop-bridge-background.ps1"
$drainGraceMs = 2000

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

New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

if (Test-Path $statePath) {
    try {
        $drainUri = "http://127.0.0.1:$Port/internal/lifecycle/drain"
        $drainBody = @{
            reason = "bridge restart requested"
            graceMs = $drainGraceMs
        } | ConvertTo-Json
        Write-Host "Requesting bridge drain: $drainUri"
        Invoke-RestMethod `
            -Uri $drainUri `
            -Method Post `
            -ContentType "application/json" `
            -Body $drainBody `
            -TimeoutSec 2 | Out-Null
        Start-Sleep -Milliseconds $drainGraceMs
    } catch {
        Write-Warning "Bridge drain request failed, continuing with stop: $($_.Exception.Message)"
    }
    & $stopScript
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
        throw "Bridge background start failed. Check log: $stderrLog`n$stderr"
    }

    Write-Host "Bridge is running in background."
    Write-Host "Endpoint: http://${HostAddress}:$Port"
    Write-Host "stdout log: $stdoutLog"
    Write-Host "stderr log: $stderrLog"
} finally {
    Pop-Location
}
