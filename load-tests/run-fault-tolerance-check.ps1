param(
    [string]$GatewayUrl = "http://47.120.37.61:30081",
    [ValidateSet("baseline", "fault", "recovery", "custom")]
    [string]$Phase = "baseline",
    [string]$MerchantUsername = "merchant1",
    [string]$MerchantPassword = "123456",
    [string]$MerchantToken,
    [string]$CaptchaKey,
    [string]$CaptchaCode,
    [string]$Keyword = "Braised",
    [int]$Iterations = 10,
    [int]$IntervalSeconds = 3,
    [int]$TimeoutSeconds = 5,
    [string]$OutputDir
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Join-Url {
    param([string]$Base, [string]$Path)
    return $Base.TrimEnd('/') + '/' + $Path.TrimStart('/')
}

function ConvertTo-SafeJson {
    param([object]$Value)
    if ($null -eq $Value) {
        return ""
    }
    return ($Value | ConvertTo-Json -Depth 10 -Compress)
}

function Escape-CsvCell {
    param([object]$Value)
    if ($null -eq $Value) {
        return '""'
    }
    $text = [string]$Value
    $text = $text -replace '"', '""'
    return '"' + $text + '"'
}

function Escape-MarkdownCell {
    param([object]$Value)
    if ($null -eq $Value) {
        return "-"
    }
    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return "-"
    }
    $text = $text -replace '\r?\n', '<br>'
    $text = $text -replace '\|', '\|'
    return $text
}

function Invoke-ProbeRequest {
    param(
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
        try {
            $json = $rawBody | ConvertFrom-Json
        } catch {
            $json = $null
        }
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

    $ok = $false
    $assertion = ""
    if ($Name -eq "merchant-dashboard") {
        if ($Phase -eq "fault") {
            $ok = ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $businessCode -eq 200 -and $degraded -eq $true)
            $assertion = "HTTP 2xx, code=200, degraded=true"
        } elseif ($Phase -eq "custom") {
            $ok = ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $businessCode -eq 200)
            $assertion = "HTTP 2xx, code=200"
        } else {
            $ok = ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $businessCode -eq 200 -and ($degraded -eq $false -or $degraded -eq ""))
            $assertion = "HTTP 2xx, code=200, degraded=false"
        }
    } else {
        $ok = ($httpStatus -ge 200 -and $httpStatus -lt 300 -and ($businessCode -eq 200 -or $businessCode -eq ""))
        $assertion = "HTTP 2xx and service remains responsive"
    }

    return [pscustomobject]@{
        Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
        Phase = $Phase
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
        Assertion = $assertion
        Passed = $ok
        Error = $error
        Body = $rawBody
    }
}

if ($GatewayUrl -match "你的|公网|域名|ECS|\s") {
    throw "GatewayUrl looks like a placeholder: $GatewayUrl"
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDir = Join-Path $repoRoot "reports\fault\fault-check-$timestamp-$Phase"
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$csvPath = Join-Path $OutputDir "probe-results.csv"
$jsonPath = Join-Path $OutputDir "probe-results.json"
$mdPath = Join-Path $OutputDir "probe-summary.md"

$token = $MerchantToken
if ([string]::IsNullOrWhiteSpace($token)) {
    $loginUrl = Join-Url $GatewayUrl "/api/auth/merchant/login"
    $loginBody = @{
        username = $MerchantUsername
        password = $MerchantPassword
    }
    if (-not [string]::IsNullOrWhiteSpace($CaptchaKey)) {
        $loginBody.captchaKey = $CaptchaKey
    }
    if (-not [string]::IsNullOrWhiteSpace($CaptchaCode)) {
        $loginBody.captchaCode = $CaptchaCode
    }

    $login = Invoke-ProbeRequest "merchant-login" "POST" $loginUrl @{} $loginBody
    if (-not $login.Passed) {
        throw "Merchant login failed: HTTP $($login.HttpStatus), code=$($login.BusinessCode), error=$($login.Error), body=$($login.Body). If captcha is enabled, pass -CaptchaKey and -CaptchaCode, or pass -MerchantToken to skip login."
    }

    $loginJson = $login.Body | ConvertFrom-Json
    $token = $loginJson.data.accessToken
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Merchant login response did not contain data.accessToken."
    }
    $allResults = New-Object System.Collections.Generic.List[object]
    $allResults.Add($login) | Out-Null
} else {
    $allResults = New-Object System.Collections.Generic.List[object]
    $allResults.Add([pscustomobject]@{
        Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff zzz"
        Phase = $Phase
        Name = "merchant-login"
        Method = "SKIP"
        Url = "token-provided"
        HttpStatus = ""
        BusinessCode = ""
        ElapsedMs = 0
        Degraded = ""
        Dependency = ""
        Message = "MerchantToken provided; login skipped"
        FallbackReason = ""
        Assertion = "Token is provided by caller"
        Passed = $true
        Error = ""
        Body = ""
    }) | Out-Null
}

$headers = @{ Authorization = "Bearer $token" }

$dashboardUrl = Join-Url $GatewayUrl "/api/merchant/dashboard"
$profileUrl = Join-Url $GatewayUrl "/api/merchant/profile"
$searchUrl = Join-Url $GatewayUrl ("/api/search?keyword=" + [uri]::EscapeDataString($Keyword))

for ($i = 1; $i -le $Iterations; $i++) {
    Write-Host "[$Phase] probe iteration $i/$Iterations"
    $allResults.Add((Invoke-ProbeRequest "merchant-dashboard" "GET" $dashboardUrl $headers $null)) | Out-Null
    $allResults.Add((Invoke-ProbeRequest "merchant-profile" "GET" $profileUrl $headers $null)) | Out-Null
    $allResults.Add((Invoke-ProbeRequest "merchant-search" "GET" $searchUrl @{} $null)) | Out-Null
    if ($i -lt $Iterations) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

$csvLines = @()
$csvLines += "timestamp,phase,name,method,url,http_status,business_code,elapsed_ms,degraded,dependency,message,fallback_reason,assertion,passed,error"
foreach ($result in $allResults) {
    $csvLines += (@(
        (Escape-CsvCell $result.Timestamp),
        (Escape-CsvCell $result.Phase),
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
        (Escape-CsvCell $result.Assertion),
        (Escape-CsvCell $result.Passed),
        (Escape-CsvCell $result.Error)
    ) -join ",")
}
Set-Content -LiteralPath $csvPath -Value $csvLines -Encoding UTF8
$allResults | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

$passed = @($allResults | Where-Object { $_.Passed }).Count
$failed = $allResults.Count - $passed
$dashboardRows = @($allResults | Where-Object { $_.Name -eq "merchant-dashboard" })
$avgDashboard = if ($dashboardRows.Count -gt 0) { [math]::Round((($dashboardRows | Measure-Object -Property ElapsedMs -Average).Average), 2) } else { 0 }
$degradedCount = @($dashboardRows | Where-Object { $_.Degraded -eq $true }).Count

$lines = @()
$lines += "# Fault Tolerance Probe Summary"
$lines += ""
$lines += "## Summary"
$lines += ""
$lines += "| Item | Value |"
$lines += "| --- | --- |"
$lines += "| Generated At | $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz') |"
$lines += "| Gateway URL | $GatewayUrl |"
$lines += "| Phase | $Phase |"
$lines += "| Iterations | $Iterations |"
$lines += "| Total Probes | $($allResults.Count) |"
$lines += "| Passed | $passed |"
$lines += "| Failed | $failed |"
$lines += "| Dashboard Avg Latency ms | $avgDashboard |"
$lines += "| Dashboard Degraded Count | $degradedCount |"
$lines += ""
$lines += "## Probe Results"
$lines += ""
$lines += "| Time | Name | HTTP | Code | Latency ms | Degraded | Dependency | Message | Passed |"
$lines += "| --- | --- | ---: | ---: | ---: | --- | --- | --- | --- |"
foreach ($result in $allResults) {
    $lines += "| $(Escape-MarkdownCell $result.Timestamp) | $(Escape-MarkdownCell $result.Name) | $(Escape-MarkdownCell $result.HttpStatus) | $(Escape-MarkdownCell $result.BusinessCode) | $(Escape-MarkdownCell $result.ElapsedMs) | $(Escape-MarkdownCell $result.Degraded) | $(Escape-MarkdownCell $result.Dependency) | $(Escape-MarkdownCell $result.Message) | $(Escape-MarkdownCell $result.Passed) |"
}
$lines += ""
$lines += "## Output Files"
$lines += ""
$lines += "- CSV: $csvPath"
$lines += "- JSON: $jsonPath"

Set-Content -LiteralPath $mdPath -Value $lines -Encoding UTF8

Write-Host "Probe CSV: $csvPath"
Write-Host "Probe JSON: $jsonPath"
Write-Host "Probe summary: $mdPath"
