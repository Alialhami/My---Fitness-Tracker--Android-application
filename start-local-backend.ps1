param([int]$DatabasePort = 3308, [int]$ApiPort = 8088)
$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskRuntime = Join-Path $taskRoot '.runtime'
New-Item -ItemType Directory -Path $taskRuntime -Force | Out-Null
$taskMysqlBase = 'C:\Program Files\MySQL\MySQL Server 8.0'
$taskMysqld = Join-Path $taskMysqlBase 'bin\mysqld.exe'
$taskMysql = Join-Path $taskMysqlBase 'bin\mysql.exe'
if (-not (Test-Path -LiteralPath $taskMysqld)) {
    throw 'Install MySQL Server 8.0 first, or use the manual backend instructions in START HERE.md.'
}
$taskPhpFolder = Join-Path $taskRuntime 'php'
$taskPhp = Join-Path $taskPhpFolder 'php.exe'
if (-not (Test-Path -LiteralPath $taskPhp)) {
    Write-Host 'Downloading the official PHP 8.4.27 runtime for this project...'
    $taskZip = Join-Path $taskRuntime 'php.zip'
    Invoke-WebRequest -Uri 'https://downloads.php.net/~windows/releases/php-8.4.27-nts-Win32-vs17-x64.zip' -OutFile $taskZip
    $taskHash = (Get-FileHash -LiteralPath $taskZip -Algorithm SHA256).Hash
    if ($taskHash -ne '93BD8A815A52156606FB906D0573437908C61F96E2CC52C03A51E36A41BF5236') { throw 'PHP download checksum mismatch.' }
    Expand-Archive -LiteralPath $taskZip -DestinationPath $taskPhpFolder
}
$taskIni = Join-Path $taskRuntime 'php.ini'
$taskExtensions = (Join-Path $taskPhpFolder 'ext').Replace('\','/')
@"
extension_dir="$taskExtensions"
extension=pdo_mysql
extension=openssl
display_errors=Off
log_errors=On
date.timezone=UTC
"@ | Set-Content -LiteralPath $taskIni -Encoding ASCII

# A separate database directory and nondefault port protect existing MySQL data.
$taskData = Join-Path $taskRuntime 'mysql-data'
$taskAdminFile = Join-Path $taskRuntime 'mysql-admin.cnf'
$taskAppConfig = Join-Path $taskRoot 'backend\config.local.php'
# Check ports before initializing any persistent state.
foreach ($taskPort in @($DatabasePort,$ApiPort)) {
    $taskProbe = New-Object System.Net.Sockets.TcpClient
    try { $taskProbe.Connect('127.0.0.1',$taskPort); throw "Port $taskPort is already in use. Close the earlier project server or choose another port." }
    catch [System.Net.Sockets.SocketException] { }
    finally { $taskProbe.Dispose() }
}
$taskFresh = -not (Test-Path -LiteralPath (Join-Path $taskData 'auto.cnf'))
if ($taskFresh) {
    New-Item -ItemType Directory -Path $taskData -Force | Out-Null
    & $taskMysqld --no-defaults --initialize-insecure "--basedir=$taskMysqlBase" "--datadir=$taskData" --console
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed.' }
} elseif (-not (Test-Path -LiteralPath $taskAdminFile)) {
    throw 'This local database has no saved admin configuration. Use the manual setup instructions; no existing data was changed.'
}
$taskDbProcess = $null
try {
    $taskDbProcess = Start-Process -FilePath $taskMysqld -ArgumentList @('--no-defaults',"--basedir=`"$taskMysqlBase`"", "--datadir=`"$taskData`"", "--port=$DatabasePort", '--bind-address=127.0.0.1','--mysqlx=OFF','--console') -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskRuntime 'mysql.log') -RedirectStandardError (Join-Path $taskRuntime 'mysql-error.log')
    $taskReady = $false
    for ($taskAttempt=0; $taskAttempt -lt 30; $taskAttempt++) {
        if ($taskDbProcess.HasExited) { throw 'MySQL stopped. See .runtime/mysql-error.log.' }
        $taskProbe = New-Object System.Net.Sockets.TcpClient
        try { $taskProbe.Connect('127.0.0.1',$DatabasePort); $taskReady=$true; break } catch { Start-Sleep -Milliseconds 500 } finally { $taskProbe.Dispose() }
    }
    if (-not $taskReady) { throw 'MySQL did not become ready.' }
    if ($taskFresh) {
        $taskBytes = New-Object byte[] 24
        $taskRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        $taskRng.GetBytes($taskBytes);$taskAdminPassword=([BitConverter]::ToString($taskBytes)).Replace('-','').ToLowerInvariant()
        $taskRng.GetBytes($taskBytes);$taskAppPassword=([BitConverter]::ToString($taskBytes)).Replace('-','').ToLowerInvariant();$taskRng.Dispose()
        $taskSql = Get-Content -LiteralPath (Join-Path $taskRoot 'backend\fitness_db.sql') -Raw
        $taskSql += "`nCREATE USER 'fitness_app'@'127.0.0.1' IDENTIFIED BY '$taskAppPassword';`nGRANT SELECT, INSERT, UPDATE, DELETE ON fitness_db.* TO 'fitness_app'@'127.0.0.1';`nALTER USER 'root'@'localhost' IDENTIFIED BY '$taskAdminPassword';`n"
        $taskSql | & $taskMysql --no-defaults --default-character-set=utf8mb4 --host=127.0.0.1 "--port=$DatabasePort" --user=root
        if ($LASTEXITCODE -ne 0) { throw 'Database setup failed. Inspect the local logs.' }
        @"
[client]
host=127.0.0.1
port=$DatabasePort
user=root
password=$taskAdminPassword
"@ | Set-Content -LiteralPath $taskAdminFile -Encoding ASCII
        "<?php return ['host'=>'127.0.0.1','port'=>$DatabasePort,'database'=>'fitness_db','user'=>'fitness_app','password'=>'$taskAppPassword'];" | Set-Content -LiteralPath $taskAppConfig -Encoding ASCII
    }
    Write-Host "Backend running at http://127.0.0.1:$ApiPort"
    Write-Host "Emulator app URL: http://10.0.2.2:$ApiPort"
    Write-Host 'Keep this terminal open. Press Ctrl+C when finished.'
    & $taskPhp -c $taskIni -S "127.0.0.1:$ApiPort" -t (Join-Path $taskRoot 'backend\public')
} finally {
    if ($taskDbProcess -and -not $taskDbProcess.HasExited) {
        if (Test-Path -LiteralPath $taskAdminFile) {
            & (Join-Path $taskMysqlBase 'bin\mysqladmin.exe') "--defaults-extra-file=$taskAdminFile" shutdown
        } else { Stop-Process -Id $taskDbProcess.Id -ErrorAction SilentlyContinue }
    }
}
