param(
    [string]$MysqlExe = "",
    [string]$DockerContainer = "",
    [string]$DbHost = "",
    [int]$Port = 0,
    [string]$Username = "",
    [string]$Password = ""
)

$root = Split-Path -Parent $PSScriptRoot
$sqlFile = Join-Path $root "db\microservices\init-microservice-schemas.sql"
$composeFile = Join-Path $root "docker-compose.yml"

if (-not $DbHost) { $DbHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "127.0.0.1" } }
if (-not $Port) { $Port = if ($env:MYSQL_PORT) { [int]$env:MYSQL_PORT } else { 3306 } }
if (-not $Username) { $Username = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "root" } }
if (-not $Password) { $Password = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } elseif ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "123456" } }
if (-not $DockerContainer) { $DockerContainer = if ($env:MYSQL_DOCKER_CONTAINER) { $env:MYSQL_DOCKER_CONTAINER } else { "life-assistant-mysql" } }

if (-not (Test-Path $sqlFile)) {
    throw "Microservice schema SQL not found: $sqlFile"
}

if ($MysqlExe -and (Test-Path $MysqlExe)) {
    Get-Content $sqlFile -Encoding UTF8 | & $MysqlExe --protocol=TCP --host=$DbHost --port=$Port --user=$Username "--password=$Password"
    return
}

if (Get-Command mysql -ErrorAction SilentlyContinue) {
    Get-Content $sqlFile -Encoding UTF8 | mysql --protocol=TCP --host=$DbHost --port=$Port --user=$Username "--password=$Password"
    return
}

if (Get-Command docker -ErrorAction SilentlyContinue) {
    $isRunning = docker inspect -f "{{.State.Running}}" $DockerContainer 2>$null
    if ($isRunning -eq "true") {
        Get-Content $sqlFile -Encoding UTF8 | docker exec -i $DockerContainer mysql --default-character-set=utf8mb4 --user=$Username "--password=$Password"
        return
    }

    if (Test-Path $composeFile) {
        Get-Content $sqlFile -Encoding UTF8 | docker compose -f $composeFile exec -T mysql mysql --default-character-set=utf8mb4 --user=$Username "--password=$Password"
        return
    }
}

throw "No MySQL client or running Docker MySQL container found. Start docker compose, set MYSQL_DOCKER_CONTAINER, or provide -MysqlExe."
