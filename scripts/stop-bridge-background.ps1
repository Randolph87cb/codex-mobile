[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $repoRoot ".tmp\bridge"
$logsDir = Join-Path $repoRoot ".logs\bridge"
$statePath = Join-Path $runtimeDir "bridge-process.json"
$controlLog = Join-Path $logsDir "bridge-control.log"

function Write-ControlLog {
    param(
        [string]$Action,
        [string]$Message
    )

    $timestamp = (Get-Date).ToString("o")
    $line = "$timestamp [$Action] $Message"
    Add-Content -Path $controlLog -Value $line -Encoding UTF8
}

function Test-ProcessRunning {
    param(
        [int]$ProcessId
    )

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    return ($null -ne $process)
}

New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

if (-not (Test-Path $statePath)) {
    Write-ControlLog "stop" "no recorded bridge background process found"
    Write-Host "No recorded bridge background process found."
    exit 0
}

try {
    $state = Get-Content -Path $statePath -Raw | ConvertFrom-Json
} catch {
    Write-ControlLog "failure" "failed to read bridge state; keeping state file: $($_.Exception.Message)"
    Write-Error "Failed to read bridge state file: $($_.Exception.Message)"
    exit 1
}

$processId = [int]$state.ProcessId
$process = Get-Process -Id $processId -ErrorAction SilentlyContinue

if ($null -eq $process) {
    Write-ControlLog "stop" "bridge process record exists, but process $processId is no longer running; removing stale state"
    Write-Host "Bridge process record exists, but process $processId is no longer running."
    Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
    exit 0
}

Write-ControlLog "stop" "stopping bridge background process: pid=$processId"
Write-Host "Stopping bridge background process: PID=$processId"

$stopped = $false
try {
    Stop-Process -Id $processId -Force -ErrorAction Stop
    Start-Sleep -Milliseconds 500
    $stopped = -not (Test-ProcessRunning -ProcessId $processId)
} catch {
    Write-ControlLog "failure" "Stop-Process failed for pid=${processId}: $($_.Exception.Message)"
    Write-Warning "Stop-Process failed for PID=${processId}: $($_.Exception.Message)"
}

if (-not $stopped) {
    Write-ControlLog "fallback" "attempting taskkill fallback for pid=$processId"
    Write-Host "Trying taskkill fallback for bridge process: PID=$processId"

    try {
        $taskkillOutput = & taskkill.exe /PID $processId /T /F 2>&1
        $taskkillExitCode = $LASTEXITCODE
        if ($taskkillOutput) {
            Write-ControlLog "fallback" "taskkill output for pid=${processId}: $($taskkillOutput -join ' ')"
        }
        Write-ControlLog "fallback" "taskkill exit code for pid=${processId}: $taskkillExitCode"
    } catch {
        Write-ControlLog "failure" "taskkill fallback threw for pid=${processId}: $($_.Exception.Message)"
        Write-Warning "taskkill fallback failed for PID=${processId}: $($_.Exception.Message)"
    }

    Start-Sleep -Milliseconds 500
    $stopped = -not (Test-ProcessRunning -ProcessId $processId)
}

if (-not $stopped) {
    Write-ControlLog "failure" "failed to stop bridge process pid=$processId; keeping state file: $statePath"
    Write-Error "Failed to stop bridge background process PID=$processId. State file was kept: $statePath"
    exit 1
}

Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
Write-ControlLog "stop" "bridge stopped and state file removed: pid=$processId"
Write-Host "Bridge stopped."
