param(
    [string]$PostgresBin = (Split-Path -Parent (Get-Command initdb.exe -ErrorAction Stop).Source)
)

# Real PostgreSQL verification isolated from the user's running service.
# Creates only a new cluster below .local/ and stops it in finally; no DB is dropped.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path $projectRoot ('.local/postgres-phase0/' + [guid]::NewGuid().ToString('N'))
$clusterData = Join-Path $testRoot 'data'
$passwordFile = Join-Path $testRoot 'initdb-password'
$serverLog = Join-Path $testRoot 'postgres.log'
$initdb = Join-Path $PostgresBin 'initdb.exe'
$pgctl = Join-Path $PostgresBin 'pg_ctl.exe'
$started = $false
$savedEnvironment = @{}
foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

New-Item -ItemType Directory -Path $testRoot | Out-Null
$randomBytes = New-Object byte[] 32
$generator = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
$testPassword = [Convert]::ToBase64String($randomBytes)
[IO.File]::WriteAllText($passwordFile, $testPassword, [Text.UTF8Encoding]::new($false))

try {
    & $initdb -D $clusterData -U gamw_phase0_test --pwfile=$passwordFile --auth=scram-sha-256 -E UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'Isolated PostgreSQL initialization failed.' }

    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { $testPort = $listener.LocalEndpoint.Port } finally { $listener.Stop() }

    $startArguments = @('-D', ('"' + $clusterData + '"'), '-l', ('"' + $serverLog + '"'),
        '-o', ('"-h 127.0.0.1 -p ' + $testPort + '"'), '-w', '-t', '30', 'start')
    # Wait only for pg_ctl, not its server descendants (Start-Process -Wait would hang).
    $starter = Start-Process -FilePath $pgctl -ArgumentList $startArguments -WindowStyle Hidden -PassThru
    if (-not $starter.WaitForExit(35000)) { throw "pg_ctl timed out; inspect $serverLog" }
    if ($starter.ExitCode -ne 0) { throw "Isolated PostgreSQL startup failed; see $serverLog" }
    $started = $true

    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$testPort/postgres"
    $env:DB_USERNAME = 'gamw_phase0_test'
    $env:DB_PASSWORD = $testPassword
    Push-Location (Join-Path $projectRoot 'backend')
    try {
        # Native stderr warnings must not terminate PowerShell 5 before exit-code inspection.
        $savedPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & .\mvnw.cmd --batch-mode --no-transfer-progress -Ppostgres-integration verify 2>&1 |
                ForEach-Object { Write-Output ([string]$_) }
            $mavenExit = $LASTEXITCODE
        } finally { $ErrorActionPreference = $savedPreference }
        if ($mavenExit -ne 0) { throw 'PostgreSQL/Flyway integration verification failed.' }
    } finally { Pop-Location }
} finally {
    try {
        if ($started) {
            $savedPreference = $ErrorActionPreference
            try {
                $ErrorActionPreference = 'Continue'
                & $pgctl -D $clusterData -w -t 30 -m fast stop 2>&1 |
                    ForEach-Object { Write-Output ([string]$_) }
                if ($LASTEXITCODE -ne 0) { Write-Warning "Could not stop isolated cluster at $clusterData" }
            } finally { $ErrorActionPreference = $savedPreference }
        }
    } finally {
        foreach ($name in $savedEnvironment.Keys) {
            [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
        }
        # Only this run's generated password file is removed; cluster files remain ignored.
        if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    }
}
