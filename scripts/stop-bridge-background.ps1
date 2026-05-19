[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $repoRoot ".tmp\bridge"
$statePath = Join-Path $runtimeDir "bridge-process.json"

if (-not (Test-Path $statePath)) {
    Write-Host "No recorded bridge background process found."
    exit 0
}

$state = Get-Content -Path $statePath -Raw | ConvertFrom-Json
$processId = [int]$state.ProcessId
$process = Get-Process -Id $processId -ErrorAction SilentlyContinue

if ($null -eq $process) {
    Write-Host "Bridge process record exists, but process $processId is no longer running."
    Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
    exit 0
}

Write-Host "Stopping bridge background process: PID=$processId"
Stop-Process -Id $processId -Force
Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
Write-Host "Bridge stopped."
