[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$sourceScriptsDir = Join-Path $repoRoot "scripts"
$restartScriptSource = Join-Path $sourceScriptsDir "restart-bridge-background.ps1"
$stopScriptSource = Join-Path $sourceScriptsDir "stop-bridge-background.ps1"
$powerShell = (Get-Command pwsh -ErrorAction SilentlyContinue).Source
if (-not $powerShell) {
    $powerShell = (Get-Command powershell.exe -ErrorAction Stop).Source
}

function New-BridgeScriptWorkspace {
    $workspace = Join-Path ([System.IO.Path]::GetTempPath()) ("codex-mobile-bridge-script-tests-" + [System.Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path (Join-Path $workspace "scripts") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $workspace "bridge\dist") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $workspace ".tmp\bridge") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $workspace ".logs\bridge") -Force | Out-Null
    Copy-Item -LiteralPath $restartScriptSource -Destination (Join-Path $workspace "scripts\restart-bridge-background.ps1") -Force
    Copy-Item -LiteralPath $stopScriptSource -Destination (Join-Path $workspace "scripts\stop-bridge-background.ps1") -Force
    return $workspace
}

function Invoke-BridgeScriptCase {
    param(
        [Parameter(Mandatory)]
        [string]$Name,
        [Parameter(Mandatory)]
        [string]$Script,
        [int]$ExpectedExitCode = 0
    )

    $casePath = Join-Path ([System.IO.Path]::GetTempPath()) ("codex-mobile-bridge-script-case-" + [System.Guid]::NewGuid().ToString("N") + ".ps1")
    Set-Content -Path $casePath -Value $Script -Encoding UTF8

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $powerShell -NoProfile -ExecutionPolicy Bypass -File $casePath 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    Remove-Item -LiteralPath $casePath -Force -ErrorAction SilentlyContinue

    if ($exitCode -ne $ExpectedExitCode) {
        throw "[$Name] expected exit code $ExpectedExitCode, got $exitCode.`n$output"
    }

    return [pscustomobject]@{
        Name = $Name
        ExitCode = $exitCode
        Output = ($output -join [Environment]::NewLine)
    }
}

function Assert-Condition {
    param(
        [Parameter(Mandatory)]
        [bool]$Condition,
        [Parameter(Mandatory)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Assert-FileContains {
    param(
        [Parameter(Mandatory)]
        [string]$Path,
        [Parameter(Mandatory)]
        [string]$Pattern,
        [Parameter(Mandatory)]
        [string]$Message
    )

    Assert-Condition (Test-Path $Path) "Expected file to exist: $Path"
    $content = Get-Content -Path $Path -Raw
    Assert-Condition ($content -match $Pattern) $Message
}

$passed = 0
$workspaces = @()

try {
    $workspace = New-BridgeScriptWorkspace
    $workspaces += $workspace
    $statePath = Join-Path $workspace ".tmp\bridge\bridge-process.json"
    $tracePath = Join-Path $workspace ".logs\bridge\case-trace.log"
    $controlLog = Join-Path $workspace ".logs\bridge\bridge-control.log"
    $lockPath = Join-Path $workspace ".tmp\bridge\restart-bridge.lock"
    @{ ProcessId = 3131; Runner = "app-server"; Host = "0.0.0.0"; Port = 19087 } |
        ConvertTo-Json |
        Set-Content -Path $statePath -Encoding UTF8

    @"
[CmdletBinding()]
param()

`$ErrorActionPreference = "Stop"
Add-Content -Path "$($tracePath.Replace('\', '\\'))" -Value "stop-invoked" -Encoding UTF8
exit 99
"@ | Set-Content -Path (Join-Path $workspace "scripts\stop-bridge-background.ps1") -Encoding UTF8

    $lockStream = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    try {
        $case = @"
`$ErrorActionPreference = "Stop"
`$workspace = "$($workspace.Replace('\', '\\'))"
`$tracePath = "$($tracePath.Replace('\', '\\'))"
`$restartScript = Join-Path `$workspace "scripts\restart-bridge-background.ps1"

function Start-Process {
    [CmdletBinding()]
    param(
        [string]`$FilePath,
        [string]`$ArgumentList,
        [string]`$WorkingDirectory,
        [System.Diagnostics.ProcessWindowStyle]`$WindowStyle,
        [string]`$RedirectStandardOutput,
        [string]`$RedirectStandardError,
        [switch]`$PassThru
    )
    Add-Content -Path `$tracePath -Value "start-process `$FilePath `$ArgumentList" -Encoding UTF8
    return [pscustomobject]@{ Id = 3132 }
}

function Invoke-RestMethod {
    [CmdletBinding()]
    param(
        [string]`$Uri,
        [string]`$Method,
        [string]`$ContentType,
        [string]`$Body,
        [int]`$TimeoutSec
    )
    Add-Content -Path `$tracePath -Value "invoke-rest `$Uri" -Encoding UTF8
    return [pscustomobject]@{ ok = `$true }
}

& `$restartScript -SkipBuild -Port 19087
"@

        Invoke-BridgeScriptCase -Name "held restart lock skips restart work" -Script $case | Out-Null
    } finally {
        $lockStream.Close()
    }

    $trace = if (Test-Path $tracePath) { Get-Content -Path $tracePath -Raw } else { "" }
    Assert-Condition ($trace -notmatch "stop-invoked") "Restart lock contention should not call stop script."
    Assert-Condition ($trace -notmatch "invoke-rest") "Restart lock contention should not call drain or health HTTP requests."
    Assert-Condition ($trace -notmatch "start-process") "Restart lock contention should not start a new bridge process."
    Assert-FileContains -Path $controlLog -Pattern "restart skipped because another restart instance holds the lock" -Message "Control log should record restart lock contention."
    $passed++
    Write-Host "[pass] held restart lock skips restart work"

    $workspace = New-BridgeScriptWorkspace
    $workspaces += $workspace
    $statePath = Join-Path $workspace ".tmp\bridge\bridge-process.json"
    $tracePath = Join-Path $workspace ".logs\bridge\case-trace.log"
    $controlLog = Join-Path $workspace ".logs\bridge\bridge-control.log"
    @{ ProcessId = 4242; Runner = "app-server"; Host = "0.0.0.0"; Port = 19087 } |
        ConvertTo-Json |
        Set-Content -Path $statePath -Encoding UTF8

    @'
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$statePath = Join-Path $repoRoot ".tmp\bridge\bridge-process.json"
$controlLog = Join-Path $repoRoot ".logs\bridge\bridge-control.log"
Add-Content -Path $controlLog -Value "stub stop invoked" -Encoding UTF8
Remove-Item -LiteralPath $statePath -Force -ErrorAction SilentlyContinue
exit 0
'@ | Set-Content -Path (Join-Path $workspace "scripts\stop-bridge-background.ps1") -Encoding UTF8

    $case = @"
`$ErrorActionPreference = "Stop"
`$workspace = "$($workspace.Replace('\', '\\'))"
`$tracePath = "$($tracePath.Replace('\', '\\'))"
`$restartScript = Join-Path `$workspace "scripts\restart-bridge-background.ps1"

function Get-Command {
    [CmdletBinding()]
    param([string]`$Name)
    if (`$Name -eq "node") {
        return [pscustomobject]@{ Source = "`$env:SystemRoot\System32\cmd.exe" }
    }
    throw "Command not found in test case: `$Name"
}

function Start-Process {
    [CmdletBinding()]
    param(
        [string]`$FilePath,
        [string]`$ArgumentList,
        [string]`$WorkingDirectory,
        [System.Diagnostics.ProcessWindowStyle]`$WindowStyle,
        [string]`$RedirectStandardOutput,
        [string]`$RedirectStandardError,
        [switch]`$PassThru
    )
    Add-Content -Path `$tracePath -Value "start-process `$FilePath `$ArgumentList" -Encoding UTF8
    return [pscustomobject]@{ Id = 4321 }
}

function Get-Process {
    [CmdletBinding()]
    param([int]`$Id)
    if (`$Id -eq 4321) {
        return [pscustomobject]@{ Id = 4321 }
    }
    return `$null
}

function Invoke-RestMethod {
    [CmdletBinding()]
    param(
        [string]`$Uri,
        [string]`$Method,
        [string]`$ContentType,
        [string]`$Body,
        [int]`$TimeoutSec
    )
    if (`$Uri -like "*/internal/lifecycle/drain") {
        Add-Content -Path `$tracePath -Value "drain-invoked `$Uri" -Encoding UTF8
        throw "drain endpoint must not be invoked by repeated background start"
    }
    if (`$Uri -like "*/health") {
        return [pscustomobject]@{ ok = `$true }
    }
    throw "Unexpected Invoke-RestMethod URI: `$Uri"
}

function Start-Sleep {
    [CmdletBinding()]
    param([int]`$Milliseconds)
}

& `$restartScript -SkipBuild -Port 19087
"@

    Invoke-BridgeScriptCase -Name "repeated start does not invoke drain endpoint" -Script $case | Out-Null
    $trace = if (Test-Path $tracePath) { Get-Content -Path $tracePath -Raw } else { "" }
    Assert-Condition ($trace -notmatch "drain-invoked") "Repeated start invoked /internal/lifecycle/drain."
    Assert-FileContains -Path $controlLog -Pattern "skipped drain|no drain" -Message "Control log should record that drain was skipped."
    $passed++
    Write-Host "[pass] repeated start does not invoke drain endpoint"

    $workspace = New-BridgeScriptWorkspace
    $workspaces += $workspace
    $statePath = Join-Path $workspace ".tmp\bridge\bridge-process.json"
    $markerPath = Join-Path $workspace ".tmp\bridge\taskkill-called.txt"
    $controlLog = Join-Path $workspace ".logs\bridge\bridge-control.log"
    @{ ProcessId = 5151; Runner = "app-server"; Host = "0.0.0.0"; Port = 8787 } |
        ConvertTo-Json |
        Set-Content -Path $statePath -Encoding UTF8

    $case = @"
`$ErrorActionPreference = "Stop"
`$workspace = "$($workspace.Replace('\', '\\'))"
`$markerPath = "$($markerPath.Replace('\', '\\'))"
`$stopScript = Join-Path `$workspace "scripts\stop-bridge-background.ps1"

function Get-Process {
    [CmdletBinding()]
    param([int]`$Id)
    if (Test-Path `$markerPath) {
        return `$null
    }
    return [pscustomobject]@{ Id = `$Id }
}

function Stop-Process {
    [CmdletBinding()]
    param([int]`$Id, [switch]`$Force)
    throw "access denied from test"
}

function taskkill.exe {
    param([Parameter(ValueFromRemainingArguments = `$true)]`$Args)
    Set-Content -Path `$markerPath -Value (`$Args -join " ") -Encoding UTF8
    `$global:LASTEXITCODE = 0
    "SUCCESS: test taskkill fallback"
}

function Start-Sleep {
    [CmdletBinding()]
    param([int]`$Milliseconds)
}

& `$stopScript
"@

    Invoke-BridgeScriptCase -Name "stop uses taskkill fallback when Stop-Process fails" -Script $case | Out-Null
    Assert-Condition (Test-Path $markerPath) "taskkill fallback was not attempted after Stop-Process failed."
    Assert-Condition (-not (Test-Path $statePath)) "State file should be removed after fallback stops the process."
    Assert-FileContains -Path $controlLog -Pattern "Stop-Process failed" -Message "Control log should record Stop-Process failure."
    Assert-FileContains -Path $controlLog -Pattern "attempting taskkill fallback" -Message "Control log should record taskkill fallback attempt."
    Assert-FileContains -Path $controlLog -Pattern "bridge stopped and state file removed" -Message "Control log should record successful stop."
    $passed++
    Write-Host "[pass] stop uses taskkill fallback when Stop-Process fails"

    $workspace = New-BridgeScriptWorkspace
    $workspaces += $workspace
    $statePath = Join-Path $workspace ".tmp\bridge\bridge-process.json"
    $markerPath = Join-Path $workspace ".tmp\bridge\taskkill-called.txt"
    $controlLog = Join-Path $workspace ".logs\bridge\bridge-control.log"
    @{ ProcessId = 6262; Runner = "app-server"; Host = "0.0.0.0"; Port = 8787 } |
        ConvertTo-Json |
        Set-Content -Path $statePath -Encoding UTF8

    $case = @"
`$ErrorActionPreference = "Stop"
`$workspace = "$($workspace.Replace('\', '\\'))"
`$markerPath = "$($markerPath.Replace('\', '\\'))"
`$stopScript = Join-Path `$workspace "scripts\stop-bridge-background.ps1"

function Get-Process {
    [CmdletBinding()]
    param([int]`$Id)
    return [pscustomobject]@{ Id = `$Id }
}

function Stop-Process {
    [CmdletBinding()]
    param([int]`$Id, [switch]`$Force)
    throw "access denied from test"
}

function taskkill.exe {
    param([Parameter(ValueFromRemainingArguments = `$true)]`$Args)
    Set-Content -Path `$markerPath -Value (`$Args -join " ") -Encoding UTF8
    `$global:LASTEXITCODE = 5
    "ERROR: test taskkill fallback failed"
}

function Start-Sleep {
    [CmdletBinding()]
    param([int]`$Milliseconds)
}

& `$stopScript
"@

    Invoke-BridgeScriptCase -Name "failed fallback keeps state and exits nonzero" -Script $case -ExpectedExitCode 1 | Out-Null
    Assert-Condition (Test-Path $markerPath) "taskkill fallback was not attempted in failure case."
    Assert-Condition (Test-Path $statePath) "State file should be kept when fallback cannot stop the process."
    Assert-FileContains -Path $controlLog -Pattern "Stop-Process failed" -Message "Control log should record Stop-Process failure in failure case."
    Assert-FileContains -Path $controlLog -Pattern "attempting taskkill fallback" -Message "Control log should record fallback attempt in failure case."
    Assert-FileContains -Path $controlLog -Pattern "failed to stop bridge process.*keeping state file" -Message "Control log should record kept state after failed fallback."
    $passed++
    Write-Host "[pass] failed fallback keeps state and exits nonzero"

    Write-Host "Bridge background script acceptance tests passed: $passed"
} finally {
    foreach ($workspace in $workspaces) {
        Remove-Item -LiteralPath $workspace -Recurse -Force -ErrorAction SilentlyContinue
    }
}
