param(
    [string]$GatewayUrl = "http://47.120.37.61:30081",
    [string]$NacosUrl = "http://127.0.0.1:8848",
    [string]$Group = "DEFAULT_GROUP",
    [string]$Namespace = "",
    [ValidateSet("baseline", "gateway-flow", "service-flow", "dependency-fallback", "all")]
    [string]$Mode = "baseline",
    [switch]$ApplyTemporaryRules,
    [bool]$RestoreOriginalRules = $true,
    [string]$MerchantUsername = "merchant1",
    [string]$MerchantPassword = "123456",
    [string]$MerchantToken,
    [string]$CaptchaKey,
    [string]$CaptchaCode,
    [string]$Keyword = "Braised",
    [int]$Iterations = 12,
    [int]$IntervalMilliseconds = 80,
    [int]$RuleWarmupSeconds = 8,
    [int]$TimeoutSeconds = 6,
    [string]$OutputDir
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Join-Url {
    param([string]$Base, [string]$Path)
    return $Base.TrimEnd('/') + '/' + $Path.TrimStart('/')
}

function Escape-CsvCell {
    param([object]$Value)
    if ($null -eq $Value) { return '""' }
    $text = [string]$Value
    $text = $text -replace '"', '""'
    return '"' + $text + '"'
}

function Escape-MarkdownCell {
    param([object]$Value)
    if ($null -eq $Value) { return "-" }
    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) { return "-" }
    $text = $text -replace '\r?\n', '<br>'
    $text = $text -replace '\|', '\|'
    return $text
}

function ConvertTo-SafeJson {
    param([object]$Value)
    if ($null -eq $Value) { return "" }
    return ($Value | ConvertTo-Json -Depth 20 -Compress)
}

function Get-NacosQueryString {
    param([string]$DataId)
    $query = "dataId=$([uri]::EscapeDataString($DataId))&group=$([uri]::EscapeDataString($Group))"
    if (-not [string]::IsNullOrWhiteSpace($Namespace)) {
        $query += "&tenant=$([uri]::EscapeDataString($Namespace))"
    }
    return $query
}

function Get-NacosConfigContent {
    param([string]$DataId)
    $uri = Join-Url $NacosUrl ("/nacos/v1/cs/configs?" + (Get-NacosQueryString $DataId))
    $response = Invoke-WebRequest -Method Get -Uri $uri -TimeoutSec $TimeoutSeconds -UseBasicParsing
    return [string]$response.Content
}

function Publish-NacosConfigContent {
    param(
        [string]$DataId,
        [string]$Content,
        [string]$Type = "json"
    )

    $body = @{
        dataId = $DataId
        group = $Group
        type = $Type
        content = $Content
    }
    if (-not [string]::IsNullOrWhiteSpace($Namespace)) {
        $body.tenant = $Namespace
    }

    $uri = Join-Url $NacosUrl "/nacos/v1/cs/configs"
    $result = Invoke-RestMethod -Method Post -Uri $uri -ContentType "application/x-www-form-urlencoded;charset=utf-8" -Body $body -TimeoutSec $TimeoutSeconds
    if ($result -ne $true -and $result -ne "true") {
        throw "Failed to publish $DataId to Nacos. Response: $result"
    }
}

function Backup-NacosConfig {
    param([string]$DataId)
    if ($script:Backups.ContainsKey($DataId)) { return }
    $content = Get-NacosConfigContent $DataId
    $script:Backups[$DataId] = $content
    Set-Content -LiteralPath (Join-Path $script:BackupDir $DataId) -Value $content -Encoding UTF8
}

function Set-JsonRuleCount {
    param(
        [string]$DataId,
        [string]$Resource,
        [double]$Count,
        [Nullable[int]]$Burst = $null
    )

    Backup-NacosConfig $DataId
    $rules = @(($script:Backups[$DataId] | ConvertFrom-Json))
    $matched = $false
    foreach ($rule in $rules) {
        if ($rule.resource -eq $Resource) {
            $rule.count = $Count
            if ($null -ne $Burst -and ($rule.PSObject.Properties.Name -contains "burst")) {
                $rule.burst = [int]$Burst
            }
            $matched = $true
        }
    }
    if (-not $matched) {
        throw "Rule resource '$Resource' was not found in $DataId. Keep Sentinel rule files aligned before running this scenario."
    }
    Publish-NacosConfigContent $DataId (ConvertTo-SafeJson $rules) "json"
}

function Restore-NacosBackups {
    foreach ($dataId in $script:Backups.Keys) {
        Publish-NacosConfigContent $dataId $script:Backups[$dataId] "json"
        Write-Host "Restored $dataId"
    }
}

function Invoke-ProbeRequest {
    param(
        [string]$Scenario,
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [object]$Body
    )

    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $httpStatus = 0
    $rawBody = ""
    $json = $null
    $error = ""

    try {
        $parameters = @{
            Method = $Method
            Uri = $Url
            Headers = $Headers
            TimeoutSec = $TimeoutSeconds
            UseBasicParsing = $true
        }
        if ($null -ne $Body) {
            $parameters.ContentType = "application/json; charset=utf-8"
            $parameters.Body = ConvertTo-SafeJson $Body
        }

        $response = Invoke-WebRequest @parameters
        $httpStatus = [int]$response.StatusCode
        $rawBody = [string]$response.Content
    } catch {
        $exception = $_.Exception
        if ($exception.Response -and $exception.Response.StatusCode) {
            $httpStatus = [int]$exception.Response.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($exception.Response.GetResponseStream(), [System.Text.Encoding]::UTF8)
                $rawBody = $reader.ReadToEnd()
            } catch {
                $rawBody = ""
            }
        }
        $error = $exception.Message
    } finally {
        $watch.Stop()
    }

    if (-not [string]::IsNullOrWhiteSpace($rawBody)) {
        try { $json = $rawBody | ConvertFrom-Json } catch { $json = $null }
    }

    $businessCode = if ($json -and ($json.PSObject.Properties.Name -contains "code")) { $json.code } else { "" }
    $message = if ($json -and ($json.PSObject.Properties.Name -contains "message")) { $json.message } else { "" }
    $degraded = ""
    $dependency = ""
    $fallbackReason = ""
    if ($json -and $json.data) {
        if ($json.data.PSObject.Properties.Name -contains "degraded") { $degraded = $json.data.degraded }
        if ($json.data.PSObject.Properties.Name -contains "degradedDependency") { $dependency = $json.data.degradedDependency }
        if ($json.data.PSObject.Properties.Name -contains "degradationMessage") { $message = $json.data.degradationMessage }
        if ($json.data.PSObject.Properties.Name -contains "fallbackReason") { $fallbackReason = $json.data.fallbackReason }
    }

    return [pscustomobject]@{
        Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
        Scenario = $Scenario
        Name = $Name
        Method = $Method
        Url = $Url
        HttpStatus = $httpStatus
        BusinessCode = $businessCode
        ElapsedMs = [math]::Round($watch.Elapsed.TotalMilliseconds, 2)
        Degraded = $degraded
        Dependency = $dependency
        Message = $message
        FallbackReason = $fallbackReason
        Error = $error
        Body = $rawBody
    }
}

function Get-MerchantToken {
    if (-not [string]::IsNullOrWhiteSpace($MerchantToken)) {
        return $MerchantToken
    }

    $loginBody = @{
        username = $MerchantUsername
        password = $MerchantPassword
    }
    if (-not [string]::IsNullOrWhiteSpace($CaptchaKey)) { $loginBody.captchaKey = $CaptchaKey }
    if (-not [string]::IsNullOrWhiteSpace($CaptchaCode)) { $loginBody.captchaCode = $CaptchaCode }

    $login = Invoke-ProbeRequest "dependency-fallback" "merchant-login" "POST" (Join-Url $GatewayUrl "/api/auth/merchant/login") @{} $loginBody
    $script:Results.Add($login) | Out-Null
    if ($login.HttpStatus -lt 200 -or $login.HttpStatus -ge 300 -or $login.BusinessCode -ne 200) {
        throw "Merchant login failed. If captcha is enabled, pass -CaptchaKey/-CaptchaCode or pass -MerchantToken. Body: $($login.Body)"
    }
    $loginJson = $login.Body | ConvertFrom-Json
    return $loginJson.data.accessToken
}

function Invoke-Burst {
    param(
        [string]$Scenario,
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [object]$Body
    )

    for ($i = 1; $i -le $Iterations; $i++) {
        Write-Host "[$Scenario] probe $i/$Iterations $Method $Url"
        $script:Results.Add((Invoke-ProbeRequest $Scenario $Name $Method $Url $Headers $Body)) | Out-Null
        if ($i -lt $Iterations -and $IntervalMilliseconds -gt 0) {
            Start-Sleep -Milliseconds $IntervalMilliseconds
        }
    }
}

function Start-Scenario {
    param([string]$Scenario)

    if ($ApplyTemporaryRules) {
        switch ($Scenario) {
            "gateway-flow" {
                Set-JsonRuleCount "sentinel-api-gateway-gw-flow.json" "gateway-search-api" 1 0
            }
            "service-flow" {
                Set-JsonRuleCount "sentinel-api-gateway-gw-flow.json" "gateway-search-api" 1000 100
                Set-JsonRuleCount "sentinel-merchant-service-flow.json" "/api/search" 1
            }
            "dependency-fallback" {
                Set-JsonRuleCount "sentinel-api-gateway-gw-flow.json" "gateway-merchant-dashboard-api" 1000 100
                Set-JsonRuleCount "sentinel-merchant-service-flow.json" "/api/merchant/dashboard" 1000
                Set-JsonRuleCount "sentinel-order-service-flow.json" "/internal/orders/merchant-dashboard" 1
            }
        }
        Write-Host "Waiting ${RuleWarmupSeconds}s for Sentinel rule refresh..."
        Start-Sleep -Seconds $RuleWarmupSeconds
    }

    switch ($Scenario) {
        "baseline" {
            Invoke-Burst "baseline" "captcha" "GET" (Join-Url $GatewayUrl "/api/captcha") @{} $null
            Invoke-Burst "baseline" "search" "GET" (Join-Url $GatewayUrl ("/api/search?keyword=" + [uri]::EscapeDataString($Keyword))) @{} $null
        }
        "gateway-flow" {
            Invoke-Burst "gateway-flow" "gateway-search-api" "GET" (Join-Url $GatewayUrl ("/api/search?keyword=" + [uri]::EscapeDataString($Keyword))) @{} $null
        }
        "service-flow" {
            Invoke-Burst "service-flow" "merchant-service-search" "GET" (Join-Url $GatewayUrl ("/api/search?keyword=" + [uri]::EscapeDataString($Keyword))) @{} $null
        }
        "dependency-fallback" {
            $token = Get-MerchantToken
            $headers = @{ Authorization = "Bearer $token" }
            Invoke-Burst "dependency-fallback" "merchant-dashboard" "GET" (Join-Url $GatewayUrl "/api/merchant/dashboard") $headers $null
        }
    }
}

if ($GatewayUrl -match "你的|公网|域名|ECS|\s") {
    throw "GatewayUrl looks like a placeholder: $GatewayUrl"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $repoRoot "reports\sentinel\sentinel-check-$timestamp-$Mode"
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$script:BackupDir = Join-Path $OutputDir "original-nacos-configs"
New-Item -ItemType Directory -Force -Path $script:BackupDir | Out-Null
$script:Backups = @{}
$script:Results = New-Object System.Collections.Generic.List[object]

$scenarios = switch ($Mode) {
    "all" { @("baseline", "gateway-flow", "service-flow", "dependency-fallback") }
    default { @($Mode) }
}

try {
    foreach ($scenario in $scenarios) {
        Start-Scenario $scenario
    }
} finally {
    if ($ApplyTemporaryRules -and $RestoreOriginalRules -and $script:Backups.Count -gt 0) {
        Restore-NacosBackups
        if ($RuleWarmupSeconds -gt 0) {
            Write-Host "Waiting ${RuleWarmupSeconds}s after rule restore..."
            Start-Sleep -Seconds $RuleWarmupSeconds
        }
    }
}

$csvPath = Join-Path $OutputDir "sentinel-probe-results.csv"
$jsonPath = Join-Path $OutputDir "sentinel-probe-results.json"
$mdPath = Join-Path $OutputDir "sentinel-probe-summary.md"

$csvLines = @()
$csvLines += "timestamp,scenario,name,method,url,http_status,business_code,elapsed_ms,degraded,dependency,message,fallback_reason,error"
foreach ($result in $script:Results) {
    $csvLines += (@(
        (Escape-CsvCell $result.Timestamp),
        (Escape-CsvCell $result.Scenario),
        (Escape-CsvCell $result.Name),
        (Escape-CsvCell $result.Method),
        (Escape-CsvCell $result.Url),
        (Escape-CsvCell $result.HttpStatus),
        (Escape-CsvCell $result.BusinessCode),
        (Escape-CsvCell $result.ElapsedMs),
        (Escape-CsvCell $result.Degraded),
        (Escape-CsvCell $result.Dependency),
        (Escape-CsvCell $result.Message),
        (Escape-CsvCell $result.FallbackReason),
        (Escape-CsvCell $result.Error)
    ) -join ",")
}
Set-Content -LiteralPath $csvPath -Value $csvLines -Encoding UTF8
$script:Results | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$lines = @()
$lines += "# Sentinel Governance Probe Summary"
$lines += ""
$lines += "| Item | Value |"
$lines += "| --- | --- |"
$lines += "| Generated At | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz') |"
$lines += "| Gateway URL | $GatewayUrl |"
$lines += "| Nacos URL | $NacosUrl |"
$lines += "| Group | $Group |"
$lines += "| Namespace | $(if ([string]::IsNullOrWhiteSpace($Namespace)) { 'public' } else { $Namespace }) |"
$lines += "| Mode | $Mode |"
$lines += "| Temporary Rules Applied | $ApplyTemporaryRules |"
$lines += "| Original Rules Restored | $RestoreOriginalRules |"
$lines += "| Total Probes | $($script:Results.Count) |"
$lines += ""
$lines += "## Scenario Metrics"
$lines += ""
$lines += "| Scenario | Count | HTTP 429 | Business 429 | HTTP 503 | Business 503 | Degraded | Average Latency ms |"
$lines += "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"
foreach ($group in ($script:Results | Group-Object Scenario | Sort-Object Name)) {
    $rows = @($group.Group)
    $avg = if ($rows.Count -gt 0) { [math]::Round((($rows | Measure-Object -Property ElapsedMs -Average).Average), 2) } else { 0 }
    $http429 = @($rows | Where-Object { $_.HttpStatus -eq 429 }).Count
    $biz429 = @($rows | Where-Object { $_.BusinessCode -eq 429 }).Count
    $http503 = @($rows | Where-Object { $_.HttpStatus -eq 503 }).Count
    $biz503 = @($rows | Where-Object { $_.BusinessCode -eq 503 }).Count
    $degraded = @($rows | Where-Object { $_.Degraded -eq $true -or $_.Degraded -eq "True" -or $_.Degraded -eq "true" }).Count
    $lines += "| $(Escape-MarkdownCell $group.Name) | $($rows.Count) | $http429 | $biz429 | $http503 | $biz503 | $degraded | $avg |"
}
$lines += ""
$lines += "## Probe Results"
$lines += ""
$lines += "| Time | Scenario | Name | HTTP | Code | Latency ms | Degraded | Message | Error |"
$lines += "| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- |"
foreach ($result in $script:Results) {
    $lines += "| $(Escape-MarkdownCell $result.Timestamp) | $(Escape-MarkdownCell $result.Scenario) | $(Escape-MarkdownCell $result.Name) | $(Escape-MarkdownCell $result.HttpStatus) | $(Escape-MarkdownCell $result.BusinessCode) | $(Escape-MarkdownCell $result.ElapsedMs) | $(Escape-MarkdownCell $result.Degraded) | $(Escape-MarkdownCell $result.Message) | $(Escape-MarkdownCell $result.Error) |"
}
$lines += ""
$lines += "## Expected Signals"
$lines += ""
$lines += "- baseline: HTTP 2xx and business code 200."
$lines += "- gateway-flow: at least one HTTP 429 / business 429 with gateway block message."
$lines += "- service-flow: at least one HTTP 429 / business 429 with business service block message."
$lines += "- dependency-fallback: at least one dashboard response with code 200 and data.degraded=true."
$lines += ""
$lines += "## Output Files"
$lines += ""
$lines += "- CSV: $csvPath"
$lines += "- JSON: $jsonPath"
$lines += "- Original Nacos configs: $script:BackupDir"

Set-Content -LiteralPath $mdPath -Value $lines -Encoding UTF8

Write-Host "Sentinel probe CSV: $csvPath"
Write-Host "Sentinel probe JSON: $jsonPath"
Write-Host "Sentinel probe summary: $mdPath"
