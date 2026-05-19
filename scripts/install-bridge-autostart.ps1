[CmdletBinding()]
param(
    [string]$TaskName = "CodexMobileBridge",
    [string]$HostAddress = "0.0.0.0",
    [int]$Port = 8787,
    [string]$Runner = "app-server",
    [switch]$AtStartup
)

$ErrorActionPreference = "Stop"

$restartScript = Join-Path $PSScriptRoot "restart-bridge-background.ps1"
$powerShellPath = (Get-Command powershell.exe -ErrorAction Stop).Source
$currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$argumentList = @(
    "-NoProfile"
    "-ExecutionPolicy", "Bypass"
    "-File", "`"$restartScript`""
    "-HostAddress", "`"$HostAddress`""
    "-Port", "$Port"
    "-Runner", "`"$Runner`""
) -join " "

$action = New-ScheduledTaskAction -Execute $powerShellPath -Argument $argumentList
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

if ($AtStartup) {
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
    Write-Host "Registering startup scheduled task: $TaskName (SYSTEM)"
} else {
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $currentUser
    $principal = New-ScheduledTaskPrincipal -UserId $currentUser -LogonType InteractiveToken -RunLevel Highest
    Write-Host "Registering logon scheduled task: $TaskName ($currentUser)"
}

$task = New-ScheduledTask -Action $action -Principal $principal -Trigger $trigger -Settings $settings
Register-ScheduledTask -TaskName $TaskName -InputObject $task -Force | Out-Null

Write-Host "Scheduled task registered."
Write-Host "Manual start: Start-ScheduledTask -TaskName `"$TaskName`""
