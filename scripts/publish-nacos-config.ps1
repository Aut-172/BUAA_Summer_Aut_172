param(
    [string]$NacosUrl = "http://127.0.0.1:8848",
    [string]$Group = "DEFAULT_GROUP",
    [string]$Namespace = "",
    [string]$ConfigDir = (Join-Path $PSScriptRoot "..\configs\nacos"),
    [switch]$SkipHealthCheck
)

$ErrorActionPreference = "Stop"

$resolvedConfigDir = Resolve-Path $ConfigDir
$baseUrl = $NacosUrl.TrimEnd("/")

if (-not $SkipHealthCheck) {
    $healthUrl = "$baseUrl/nacos/actuator/health"
    try {
        Invoke-RestMethod -Method Get -Uri $healthUrl -TimeoutSec 5 | Out-Null
    } catch {
        throw "Nacos health check failed at $healthUrl. Start Nacos first or pass -SkipHealthCheck. $($_.Exception.Message)"
    }
}

$dataIds = @(
    "life-assistant-common.yml",
    "api-gateway.yml",
    "merchant-service.yml",
    "user-service.yml",
    "order-service.yml",
    "settlement-service.yml",
    "fulfillment-service.yml",
    "engagement-service.yml"
)

foreach ($dataId in $dataIds) {
    $path = Join-Path $resolvedConfigDir $dataId
    if (-not (Test-Path $path)) {
        throw "Missing Nacos config file: $path"
    }

    $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    $body = @{
        dataId = $dataId
        group = $Group
        type = "yaml"
        content = $content
    }

    if (-not [string]::IsNullOrWhiteSpace($Namespace)) {
        $body.tenant = $Namespace
    }

    $result = Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/nacos/v1/cs/configs" `
        -ContentType "application/x-www-form-urlencoded;charset=utf-8" `
        -Body $body `
        -TimeoutSec 15

    if ($result -ne $true -and $result -ne "true") {
        throw "Failed to publish $dataId to Nacos. Response: $result"
    }

    Write-Host "Published $dataId to group=$Group namespace=$Namespace"
}

Write-Host "Nacos config publish completed."
