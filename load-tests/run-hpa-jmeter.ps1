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
    [string]$JMeterJar = ""
)

$ErrorActionPreference = "Stop"

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
    "-Jkeyword=$Keyword"
)

if ($JMeterJar) {
    & java -jar $JMeterJar @jmeterArgs
}
else {
    & $JMeterBin @jmeterArgs
}

Write-Host "JTL: $jtl"
Write-Host "HTML report: $html\index.html"
