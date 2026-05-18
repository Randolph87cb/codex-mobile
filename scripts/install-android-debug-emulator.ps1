$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = Join-Path $repoRoot ".tools\android-sdk"
$adbExe = Join-Path $sdkRoot "platform-tools\adb.exe"
$apkPath = Join-Path $repoRoot "android\app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $adbExe)) {
    throw "未找到 adb.exe：$adbExe"
}

if (-not (Test-Path $apkPath)) {
    throw "未找到调试安装包：$apkPath"
}

Write-Host "Waiting for emulator device ..."
& $adbExe wait-for-device

Write-Host "Installing debug APK to emulator ..."
& $adbExe install -r $apkPath

Write-Host "Launching app ..."
& $adbExe shell am start -n "com.openai.codexmobile/.MainActivity" | Out-Null

Write-Host "Android debug app installed and launched."
