$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = Join-Path $repoRoot ".tools\android-sdk"
$jdkHome = Join-Path $repoRoot ".tools\jdk\jdk-17.0.19+10"
$avdHome = Join-Path $repoRoot ".tmp\android-avd"
$emulatorExe = Join-Path $sdkRoot "emulator\emulator.exe"
$adbExe = Join-Path $sdkRoot "platform-tools\adb.exe"
$avdName = "codex-mobile-api35"

if (-not (Test-Path $emulatorExe)) {
    throw "未找到 emulator.exe：$emulatorExe"
}

if (-not (Test-Path $adbExe)) {
    throw "未找到 adb.exe：$adbExe"
}

$env:JAVA_HOME = $jdkHome
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_AVD_HOME = $avdHome

New-Item -ItemType Directory -Force -Path $avdHome | Out-Null

$existingDevice = & $adbExe devices | Select-String -Pattern "emulator-\d+\s+device"
if ($existingDevice) {
    Write-Host "Android emulator is already running."
    exit 0
}

Write-Host "Starting Android emulator $avdName ..."
Start-Process -FilePath $emulatorExe `
    -ArgumentList @(
        "-avd", $avdName,
        "-no-snapshot",
        "-no-boot-anim",
        "-gpu", "swiftshader_indirect"
    ) `
    -WorkingDirectory (Split-Path $emulatorExe -Parent) `
    -WindowStyle Hidden

Write-Host "Waiting for emulator to appear in adb..."
& $adbExe wait-for-device

for ($i = 0; $i -lt 90; $i++) {
    $boot = (& $adbExe shell getprop sys.boot_completed 2>$null).Trim()
    if ($boot -eq "1") {
        Write-Host "Android emulator boot completed."
        exit 0
    }
    Start-Sleep -Seconds 2
}

throw "Android emulator did not finish booting within the expected time."
