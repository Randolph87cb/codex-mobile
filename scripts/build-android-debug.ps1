$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$androidDir = Join-Path $repoRoot "android"

$env:JAVA_HOME = Join-Path $repoRoot ".tools\jdk\jdk-17.0.19+10"
$env:ANDROID_SDK_ROOT = Join-Path $repoRoot ".tools\android-sdk"

Write-Host "Building debug APK with JAVA_HOME=$env:JAVA_HOME"
Set-Location $androidDir
& ".\gradlew.bat" assembleDebug

