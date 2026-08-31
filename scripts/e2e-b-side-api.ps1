param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$ConsumerToken,
    [string]$RiderToken,
    [string]$AdminToken,
    [long]$MerchantId = 20001,
    [long]$ProductId = 30001,
    [long]$OrderId = 0,
    [string]$ReportPath
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $repoRoot "reports/testing/b-side-e2e-report.md"
}

$LogPath = Join-Path $repoRoot "reports/testing/b-side-e2e-log.txt"

$results = New-Object System.Collections.Generic.List[object]
$logLines = New-Object System.Collections.Generic.List[string]

function Get-CommandOutputLine {
    param([scriptblock]$Command)

    try {
        $output = & $Command 2>&1
        $line = $output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1
        return $line -as [string]
    } catch {
        return "unavailable: $($_.Exception.Message)"
    }
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

function Format-JsonValue {
    param([object]$Value)

    if ($null -eq $Value) {
        return "-"
    }

    if ($Value -is [string]) {
        return $Value
    }

    return ($Value | ConvertTo-Json -Depth 8 -Compress)
}

function Format-ResponseSummary {
    param([object]$Response)

    if ($null -eq $Response) {
        return "Empty response"
    }

    $summary = @()
    if ($Response.PSObject.Properties.Name -contains 'code') {
        $summary += "code=$($Response.code)"
    }
    if ($Response.PSObject.Properties.Name -contains 'message' -and -not [string]::IsNullOrWhiteSpace($Response.message)) {
        $summary += "message=$($Response.message)"
    }
    if ($Response.PSObject.Properties.Name -contains 'data' -and $null -ne $Response.data) {
        if ($Response.data -is [System.Collections.IEnumerable] -and -not ($Response.data -is [string])) {
            $summary += "dataCount=$(@($Response.data).Count)"
        } elseif ($Response.data -is [psobject]) {
            $summary += "dataKeys=$((@($Response.data.PSObject.Properties.Name) -join ','))"
        } else {
            $summary += "data=$($Response.data)"
        }
    }

    if ($summary.Count -eq 0) {
        return ($Response | ConvertTo-Json -Depth 6 -Compress)
    }

    return ($summary -join ', ')
}

function Add-Result {
    param(
        [string]$Id,
        [string]$InterfaceName,
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [string]$Request,
        [string]$Expected,
        [string]$Actual,
        [string]$Assertions,
        [string]$Status,
        [string]$Reason,
        [string]$Evidence
    )

    $results.Add([pscustomobject]@{
        Id = $Id
        InterfaceName = $InterfaceName
        Name = $Name
        Method = $Method
        Url = $Url
        Request = $Request
        Expected = $Expected
        Actual = $Actual
        Assertions = $Assertions
        Status = $Status
        Reason = $Reason
        Evidence = $Evidence
    }) | Out-Null
}

function Write-E2EReport {
    param([string]$Status)

    $total = $results.Count
    $passed = @($results | Where-Object { $_.Status -eq "PASSED" }).Count
    $failed = @($results | Where-Object { $_.Status -eq "FAILED" }).Count
    $skipped = @($results | Where-Object { $_.Status -eq "SKIPPED" }).Count
    $reportDir = Split-Path -Parent $ReportPath
    New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

    $branch = Get-CommandOutputLine { git branch --show-current }
    $commit = Get-CommandOutputLine { git rev-parse --short HEAD }
    $os = [System.Environment]::OSVersion.VersionString
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"

    $lines = @()
    $lines += "# B-side E2E API Report"
    $lines += ""
    $lines += "## Summary"
    $lines += ""
    $lines += "| Status | Total | Passed | Failed | Skipped |"
    $lines += "| --- | ---: | ---: | ---: | ---: |"
    $lines += "| $Status | $total | $passed | $failed | $skipped |"
    $lines += ""
    $lines += "## Scenario Results"
    $lines += ""
    $lines += "| Scenario | Status | Reason |"
    $lines += "| --- | --- | --- |"
    foreach ($result in $results) {
        $reason = if ([string]::IsNullOrWhiteSpace($result.Reason)) { "-" } else { $result.Reason.Replace("|", "\\|") }
        $lines += "| $($result.Name) | $($result.Status) | $reason |"
    }
    $lines += ""
    $lines += "## Failure Reasons"
    $lines += ""
    $failures = @($results | Where-Object { $_.Status -eq "FAILED" })
    if ($failures.Count -eq 0) {
        $lines += "- None"
    } else {
        foreach ($failure in $failures) {
            $lines += "- $($failure.Name): $($failure.Reason)"
        }
    }
    $lines += ""
    $lines += "## Runtime Environment"
    $lines += ""
    $lines += "| Item | Value |"
    $lines += "| --- | --- |"
    $lines += "| Generated At | $timestamp |"
    $lines += "| OS | $os |"
    $lines += "| Gateway URL | $GatewayUrl |"
    $lines += "| Branch | $branch |"
    $lines += "| Commit | $commit |"

    $lines += ""
    $lines += "## Case Details"
    $lines += ""
    $lines += "| Case ID | Interface Name | Scenario | Method | URL | Request | Expected Result | Actual Result | Actual Response / Key Assertions | Test Conclusion | Failure Reason | Logs/Screenshots |"
    $lines += "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"
    foreach ($result in $results) {
        $failureReason = if ([string]::IsNullOrWhiteSpace($result.Reason)) { "-" } else { $result.Reason }
        $lines += "| $(Escape-MarkdownCell $result.Id) | $(Escape-MarkdownCell $result.InterfaceName) | $(Escape-MarkdownCell $result.Name) | $(Escape-MarkdownCell $result.Method) | $(Escape-MarkdownCell $result.Url) | $(Escape-MarkdownCell $result.Request) | $(Escape-MarkdownCell $result.Expected) | $(Escape-MarkdownCell $result.Actual) | $(Escape-MarkdownCell $result.Assertions) | $(Escape-MarkdownCell $result.Status) | $(Escape-MarkdownCell $failureReason) | $(Escape-MarkdownCell $result.Evidence) |"
    }

    $lines += ""
    $lines += "## Log File"
    $lines += ""
    $lines += "[$LogPath]($LogPath)"

    Set-Content -LiteralPath $ReportPath -Value $lines -Encoding UTF8
    Write-Host "E2E report written to $ReportPath"
}

function Invoke-BSideApi {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Token,
        [object]$Body
    )

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $params = @{
        Method = $Method
        Uri = $Url
        Headers = $headers
    }
    if ($null -ne $Body) {
        $params["ContentType"] = "application/json"
        $params["Body"] = ($Body | ConvertTo-Json -Depth 8)
    }

    Invoke-RestMethod @params
}

function Invoke-Scenario {
    param(
        [string]$Id,
        [string]$InterfaceName,
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [string]$Request,
        [string]$Expected,
        [string]$Assertions,
        [scriptblock]$Action
    )

    try {
        $response = & $Action
        if ($null -eq $response) {
            throw "Empty response"
        }
        if ($response.code -ne 200) {
            throw "Expected code 200 but got $($response.code): $($response.message)"
        }
        $actual = Format-ResponseSummary $response
        $logLines.Add("[$Id] $Name") | Out-Null
        $logLines.Add("  Interface: $InterfaceName") | Out-Null
        $logLines.Add("  Method: $Method") | Out-Null
        $logLines.Add("  URL: $Url") | Out-Null
        $logLines.Add("  Request: $Request") | Out-Null
        $logLines.Add("  Expected: $Expected") | Out-Null
        $logLines.Add("  Actual: $actual") | Out-Null
        $logLines.Add("  Assertions: $Assertions") | Out-Null
        $logLines.Add("  Status: PASSED") | Out-Null
        $logLines.Add("") | Out-Null
        Add-Result $Id $InterfaceName $Name $Method $Url $Request $Expected $actual $Assertions "PASSED" "" $LogPath
        return $response
    } catch {
        $actual = if ($null -ne $response) { Format-ResponseSummary $response } else { $_.Exception.Message }
        $logLines.Add("[$Id] $Name") | Out-Null
        $logLines.Add("  Interface: $InterfaceName") | Out-Null
        $logLines.Add("  Method: $Method") | Out-Null
        $logLines.Add("  URL: $Url") | Out-Null
        $logLines.Add("  Request: $Request") | Out-Null
        $logLines.Add("  Expected: $Expected") | Out-Null
        $logLines.Add("  Actual: $actual") | Out-Null
        $logLines.Add("  Assertions: $Assertions") | Out-Null
        $logLines.Add("  Status: FAILED") | Out-Null
        $logLines.Add("  Reason: $($_.Exception.Message)") | Out-Null
        $logLines.Add("") | Out-Null
        Add-Result $Id $InterfaceName $Name $Method $Url $Request $Expected $actual $Assertions "FAILED" $_.Exception.Message $LogPath
        Set-Content -LiteralPath $LogPath -Value $logLines -Encoding UTF8
        Write-E2EReport "FAILED"
        throw
    }
}

if ([string]::IsNullOrWhiteSpace($ConsumerToken)) {
    Add-Result "BAPI-001" "Consumer Profile API" "consumer profile" "GET" "$GatewayUrl/api/user/profile" "-" "HTTP 2xx, code=200" "-" "Current profile returned" "SKIPPED" "ConsumerToken is empty" $LogPath
    Add-Result "BAPI-002" "Favorite Merchant API" "favorite merchant" "POST" "$GatewayUrl/api/user/favorites/$MerchantId" "-" "HTTP 2xx, code=200" "-" "Favorite status updated" "SKIPPED" "ConsumerToken is empty" $LogPath
    Add-Result "BAPI-003" "Add Cart API" "add cart" "POST" "$GatewayUrl/api/user/cart" "-" "HTTP 2xx, code=200" "-" "Cart item created" "SKIPPED" "ConsumerToken is empty" $LogPath
    Add-Result "BAPI-004" "Conversation API" "conversation order" "GET" "$GatewayUrl/api/messages/orders/$OrderId" "-" "HTTP 2xx, code=200" "-" "Messages returned" "SKIPPED" "ConsumerToken is empty" $LogPath
    Add-Result "BAPI-005" "Send Message API" "send message" "POST" "$GatewayUrl/api/messages" "-" "HTTP 2xx, code=200" "-" "Message persisted" "SKIPPED" "ConsumerToken is empty" $LogPath
} else {
    Invoke-Scenario "BAPI-001" "Consumer Profile API" "consumer profile" "GET" "$GatewayUrl/api/user/profile" "-" "HTTP 2xx, code=200" "code==200; profile returned" {
        Invoke-BSideApi GET "$GatewayUrl/api/user/profile" $ConsumerToken $null
    } | Out-Host
    Invoke-Scenario "BAPI-002" "Favorite Merchant API" "favorite merchant" "POST" "$GatewayUrl/api/user/favorites/$MerchantId" "-" "HTTP 2xx, code=200" "code==200; merchant marked favorite" {
        Invoke-BSideApi POST "$GatewayUrl/api/user/favorites/$MerchantId" $ConsumerToken $null
    } | Out-Host
    Invoke-Scenario "BAPI-003" "Add Cart API" "add cart" "POST" "$GatewayUrl/api/user/cart" (Format-JsonValue @{
        merchantId = $MerchantId
        productId = $ProductId
        quantity = 1
    }) "HTTP 2xx, code=200" "code==200; cart item created" {
        Invoke-BSideApi POST "$GatewayUrl/api/user/cart" $ConsumerToken @{
            merchantId = $MerchantId
            productId = $ProductId
            quantity = 1
        }
    } | Out-Host

    if ($OrderId -gt 0) {
        Invoke-Scenario "BAPI-004" "Conversation API" "conversation order" "GET" "$GatewayUrl/api/messages/orders/$OrderId" "-" "HTTP 2xx, code=200" "code==200; messages returned" {
            Invoke-BSideApi GET "$GatewayUrl/api/messages/orders/$OrderId" $ConsumerToken $null
        } | Out-Host
        Invoke-Scenario "BAPI-005" "Send Message API" "send message" "POST" "$GatewayUrl/api/messages" (Format-JsonValue @{
            receiverId = $MerchantId
            receiverType = "merchant"
            orderId = $OrderId
            content = "联调消息"
        }) "HTTP 2xx, code=200" "code==200; message persisted" {
            Invoke-BSideApi POST "$GatewayUrl/api/messages" $ConsumerToken @{
                receiverId = $MerchantId
                receiverType = "merchant"
                orderId = $OrderId
                content = "联调消息"
            }
        } | Out-Host
    } else {
        Add-Result "BAPI-004" "Conversation API" "conversation order" "GET" "$GatewayUrl/api/messages/orders/$OrderId" "-" "HTTP 2xx, code=200" "-" "Messages returned" "SKIPPED" "OrderId must be greater than 0" $LogPath
        Add-Result "BAPI-005" "Send Message API" "send message" "POST" "$GatewayUrl/api/messages" "-" "HTTP 2xx, code=200" "-" "Message persisted" "SKIPPED" "OrderId must be greater than 0" $LogPath
    }
}

if ([string]::IsNullOrWhiteSpace($RiderToken)) {
    Add-Result "BAPI-006" "Rider Profile API" "rider profile" "GET" "$GatewayUrl/api/rider/profile" "-" "HTTP 2xx, code=200" "-" "Profile returned" "SKIPPED" "RiderToken is empty" $LogPath
    Add-Result "BAPI-007" "Rider Dashboard API" "rider dashboard" "GET" "$GatewayUrl/api/rider/dashboard" "-" "HTTP 2xx, code=200" "-" "Dashboard summary returned" "SKIPPED" "RiderToken is empty" $LogPath
    Add-Result "BAPI-008" "Rider Tasks API" "rider tasks" "GET" "$GatewayUrl/api/rider/tasks" "-" "HTTP 2xx, code=200" "-" "Task list returned" "SKIPPED" "RiderToken is empty" $LogPath
} else {
    Invoke-Scenario "BAPI-006" "Rider Profile API" "rider profile" "GET" "$GatewayUrl/api/rider/profile" "-" "HTTP 2xx, code=200" "code==200; profile returned" {
        Invoke-BSideApi GET "$GatewayUrl/api/rider/profile" $RiderToken $null
    } | Out-Host
    Invoke-Scenario "BAPI-007" "Rider Dashboard API" "rider dashboard" "GET" "$GatewayUrl/api/rider/dashboard" "-" "HTTP 2xx, code=200" "code==200; dashboard summary returned" {
        Invoke-BSideApi GET "$GatewayUrl/api/rider/dashboard" $RiderToken $null
    } | Out-Host
    Invoke-Scenario "BAPI-008" "Rider Tasks API" "rider tasks" "GET" "$GatewayUrl/api/rider/tasks" "-" "HTTP 2xx, code=200" "code==200; task list returned" {
        Invoke-BSideApi GET "$GatewayUrl/api/rider/tasks" $RiderToken $null
    } | Out-Host
}

if ([string]::IsNullOrWhiteSpace($AdminToken)) {
    Add-Result "BAPI-009" "Admin Users API" "admin users" "GET" "$GatewayUrl/api/admin/users?page=1&pageSize=20" "-" "HTTP 2xx, code=200" "-" "Paged admin users returned" "SKIPPED" "AdminToken is empty" $LogPath
    Add-Result "BAPI-010" "Admin Riders API" "admin riders" "GET" "$GatewayUrl/api/admin/riders?page=1&pageSize=20" "-" "HTTP 2xx, code=200" "-" "Paged admin riders returned" "SKIPPED" "AdminToken is empty" $LogPath
} else {
    Invoke-Scenario "BAPI-009" "Admin Users API" "admin users" "GET" "$GatewayUrl/api/admin/users?page=1&pageSize=20" "-" "HTTP 2xx, code=200" "code==200; paged admin users returned" {
        Invoke-BSideApi GET "$GatewayUrl/api/admin/users?page=1&pageSize=20" $AdminToken $null
    } | Out-Host
    Invoke-Scenario "BAPI-010" "Admin Riders API" "admin riders" "GET" "$GatewayUrl/api/admin/riders?page=1&pageSize=20" "-" "HTTP 2xx, code=200" "code==200; paged admin riders returned" {
        Invoke-BSideApi GET "$GatewayUrl/api/admin/riders?page=1&pageSize=20" $AdminToken $null
    } | Out-Host
}

Set-Content -LiteralPath $LogPath -Value $logLines -Encoding UTF8
Write-E2EReport "COMPLETED"
Write-Host "B-side API smoke script completed."
