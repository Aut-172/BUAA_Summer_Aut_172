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

$results = New-Object System.Collections.Generic.List[object]

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

function Add-Result {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Reason
    )

    $results.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Reason = $Reason
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
        [string]$Name,
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
        Add-Result $Name "PASSED" ""
        return $response
    } catch {
        Add-Result $Name "FAILED" $_.Exception.Message
        Write-E2EReport "FAILED"
        throw
    }
}

if ([string]::IsNullOrWhiteSpace($ConsumerToken)) {
    Add-Result "consumer profile" "SKIPPED" "ConsumerToken is empty"
    Add-Result "favorite merchant" "SKIPPED" "ConsumerToken is empty"
    Add-Result "add cart" "SKIPPED" "ConsumerToken is empty"
    Add-Result "conversation order" "SKIPPED" "ConsumerToken is empty"
    Add-Result "send message" "SKIPPED" "ConsumerToken is empty"
} else {
    Invoke-Scenario "consumer profile" { Invoke-BSideApi GET "$GatewayUrl/api/user/profile" $ConsumerToken $null } | Out-Host
    Invoke-Scenario "favorite merchant" { Invoke-BSideApi POST "$GatewayUrl/api/user/favorites/$MerchantId" $ConsumerToken $null } | Out-Host
    Invoke-Scenario "add cart" {
        Invoke-BSideApi POST "$GatewayUrl/api/user/cart" $ConsumerToken @{
            merchantId = $MerchantId
            productId = $ProductId
            quantity = 1
        }
    } | Out-Host

    if ($OrderId -gt 0) {
        Invoke-Scenario "conversation order" { Invoke-BSideApi GET "$GatewayUrl/api/messages/orders/$OrderId" $ConsumerToken $null } | Out-Host
        Invoke-Scenario "send message" {
            Invoke-BSideApi POST "$GatewayUrl/api/messages" $ConsumerToken @{
                receiverId = $MerchantId
                receiverType = "merchant"
                orderId = $OrderId
                content = "联调消息"
            }
        } | Out-Host
    } else {
        Add-Result "conversation order" "SKIPPED" "OrderId must be greater than 0"
        Add-Result "send message" "SKIPPED" "OrderId must be greater than 0"
    }
}

if ([string]::IsNullOrWhiteSpace($RiderToken)) {
    Add-Result "rider profile" "SKIPPED" "RiderToken is empty"
    Add-Result "rider dashboard" "SKIPPED" "RiderToken is empty"
    Add-Result "rider tasks" "SKIPPED" "RiderToken is empty"
} else {
    Invoke-Scenario "rider profile" { Invoke-BSideApi GET "$GatewayUrl/api/rider/profile" $RiderToken $null } | Out-Host
    Invoke-Scenario "rider dashboard" { Invoke-BSideApi GET "$GatewayUrl/api/rider/dashboard" $RiderToken $null } | Out-Host
    Invoke-Scenario "rider tasks" { Invoke-BSideApi GET "$GatewayUrl/api/rider/tasks" $RiderToken $null } | Out-Host
}

if ([string]::IsNullOrWhiteSpace($AdminToken)) {
    Add-Result "admin users" "SKIPPED" "AdminToken is empty"
    Add-Result "admin riders" "SKIPPED" "AdminToken is empty"
} else {
    Invoke-Scenario "admin users" { Invoke-BSideApi GET "$GatewayUrl/api/admin/users?page=1&pageSize=20" $AdminToken $null } | Out-Host
    Invoke-Scenario "admin riders" { Invoke-BSideApi GET "$GatewayUrl/api/admin/riders?page=1&pageSize=20" $AdminToken $null } | Out-Host
}

Write-E2EReport "COMPLETED"
Write-Host "B-side API smoke script completed."
