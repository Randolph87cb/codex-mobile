$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$bridgeDir = Join-Path $repoRoot "bridge"
$buildScript = Join-Path $PSScriptRoot "build-android-debug.ps1"
$startEmulatorScript = Join-Path $PSScriptRoot "start-android-emulator.ps1"
$installScript = Join-Path $PSScriptRoot "install-android-debug-emulator.ps1"

$bridgeUrl = "http://127.0.0.1:8787"

Write-Host "Starting local bridge on $bridgeUrl ..."
Start-Process -FilePath "powershell.exe" `
    -ArgumentList @(
        "-ExecutionPolicy", "Bypass",
        "-Command",
        "`$env:CODEX_MOBILE_RUNNER='app-server'; `$env:HOST='127.0.0.1'; `$env:PORT='8787'; Set-Location '$bridgeDir'; npm run dev"
    ) `
    -WorkingDirectory $bridgeDir `
    -WindowStyle Hidden

Write-Host "Building Android debug APK ..."
& $buildScript

Write-Host "Starting Android emulator ..."
& $startEmulatorScript

Write-Host "Installing APK into emulator ..."
& $installScript

Write-Host ""
Write-Host "Local Android test environment is ready."
Write-Host "Bridge URL for emulator: http://10.0.2.2:8787"
Write-Host "If bridge token auth is enabled, fill the token in the app settings page before connecting."
