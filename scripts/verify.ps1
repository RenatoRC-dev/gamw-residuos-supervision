param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Invoke-Checked {
    param([scriptblock]$Command)
    # Windows PowerShell 5 treats native stderr warnings as ErrorRecords.
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Command 2>&1 | ForEach-Object { Write-Output ([string]$_) }
        $commandExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
    if ($commandExit -ne 0) { throw "Verification failed with exit code $commandExit" }
}

Push-Location $projectRoot
try {
    Push-Location backend
    try {
        Invoke-Checked { .\mvnw.cmd --batch-mode --no-transfer-progress test }
        Invoke-Checked { .\mvnw.cmd --batch-mode --no-transfer-progress package }
    } finally { Pop-Location }

    Push-Location apps/web
    try {
        Invoke-Checked { npm.cmd ci }
        Invoke-Checked { npm.cmd test }
        Invoke-Checked { npm.cmd run build }
    } finally { Pop-Location }

    Push-Location apps/mobile
    try {
        Invoke-Checked { flutter.bat pub get }
        Invoke-Checked { flutter.bat analyze }
        Invoke-Checked { flutter.bat test }
    } finally { Pop-Location }
} finally { Pop-Location }
