param(
    [string]$Protocol = "http",
    [string]$HostName = "localhost",
    [int]$Port = 8080,
    [int]$Users = 100,
    [int]$Ramp = 120,
    [int]$Duration = 600,
    [int]$Think = 50,
    [string]$Keyword = "food",
    [string]$JMeterBin = "jmeter",
    [string]$JMeterJar = "",
    [switch]$SkipPreflight
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($HostName)) {
    throw "HostName is required. Use the ECS public IP or a resolvable domain name."
}
if ($HostName -match "你的|公网|域名|ECS|\s|://|/") {
    throw "HostName looks like a placeholder or a full URL: '$HostName'. Use only the real host, for example 47.120.37.61."
}
if ($JMeterJar -and -not (Test-Path -LiteralPath $JMeterJar)) {
    throw "JMeterJar does not exist: $JMeterJar"
}

if (-not $SkipPreflight) {
    Write-Host "Preflight: checking $HostName`:$Port ..."
    $reachable = Test-NetConnection -ComputerName $HostName -Port $Port -InformationLevel Quiet
    if (-not $reachable) {
        throw "Cannot connect to $HostName`:$Port from this machine. Check the ECS security group, Service NodePort, firewall, and host/port values."
    }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$resultDir = Join-Path $root "reports\perf\hpa-$timestamp"
$jmx = Join-Path $PSScriptRoot "life-assistant-hpa.jmx"
$jtl = Join-Path $resultDir "samples.jtl"
$html = Join-Path $resultDir "html"

New-Item -ItemType Directory -Force $resultDir | Out-Null

$jmeterArgs = @(
    "-n",
    "-t", $jmx,
    "-l", $jtl,
    "-e",
    "-o", $html,
    "-Jprotocol=$Protocol",
    "-Jhost=$HostName",
    "-Jport=$Port",
    "-Jusers=$Users",
    "-Jramp=$Ramp",
    "-Jduration=$Duration",
    "-Jthink=$Think",
    "-Jkeyword=$Keyword",
    "-Jsampleresult.default.encoding=UTF-8"
)

if ($JMeterJar) {
    & java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Duser.language=en -Duser.country=US -jar $JMeterJar @jmeterArgs
}
else {
    $previousJvmArgs = $env:JVM_ARGS
    $env:JVM_ARGS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Duser.language=en -Duser.country=US $previousJvmArgs"
    try {
        & $JMeterBin @jmeterArgs
    }
    finally {
        $env:JVM_ARGS = $previousJvmArgs
    }
}

Write-Host "JTL: $jtl"
Write-Host "HTML report: $html\index.html"
