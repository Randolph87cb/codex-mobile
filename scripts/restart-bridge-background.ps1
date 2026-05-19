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

New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

if (Test-Path $statePath) {
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
