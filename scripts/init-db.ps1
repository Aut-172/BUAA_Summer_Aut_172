param(
    [string]$MysqlExe = "E:\MySQL\Server\8.0\bin\mysql.exe",
    [string]$DbHost = "127.0.0.1",
    [int]$Port = 3306,
    [string]$Username = "root",
    [string]$Password = "your_password"
)

$root = Split-Path -Parent $PSScriptRoot
$sqlFile = Join-Path $root "backend\src\main\resources\db\init.sql"

if (-not (Test-Path $MysqlExe)) {
    throw "mysql.exe not found: $MysqlExe"
}

if (-not (Test-Path $sqlFile)) {
    throw "init.sql not found: $sqlFile"
}

Get-Content $sqlFile | & $MysqlExe --protocol=TCP --host=$DbHost --port=$Port --user=$Username "--password=$Password"
