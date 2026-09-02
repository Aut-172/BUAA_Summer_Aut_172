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

$configFiles = Get-ChildItem -LiteralPath $resolvedConfigDir -File |
    Where-Object { $_.Extension -in @(".yml", ".yaml", ".json") } |
    Sort-Object Name

if ($configFiles.Count -eq 0) {
    throw "No .yml, .yaml or .json config files found in $resolvedConfigDir"
}

foreach ($file in $configFiles) {
    $dataId = $file.Name
    $content = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
    $type = switch ($file.Extension.ToLowerInvariant()) {
        ".json" { "json" }
        default { "yaml" }
    }
    $body = @{
        dataId = $dataId
        group = $Group
        type = $type
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

    Write-Host "Published $dataId type=$type to group=$Group namespace=$Namespace"
}

Write-Host "Nacos config publish completed."
