$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$bridgeDir = Join-Path $repoRoot "bridge"

$env:CODEX_MOBILE_RUNNER = "app-server"
$env:HOST = "0.0.0.0"
$env:PORT = "8787"

Write-Host "Starting codex-mobile bridge on http://0.0.0.0:8787 using app-server mode..."
Set-Location $bridgeDir
npm run dev

