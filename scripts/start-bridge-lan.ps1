$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $PSScriptRoot "restart-bridge-background.ps1"

& $scriptPath -HostAddress "0.0.0.0" -Port 8787 -Runner "app-server"
