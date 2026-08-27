param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$ConsumerToken,
    [string]$RiderToken,
    [long]$MerchantId = 20001,
    [long]$ProductId = 30001,
    [long]$OrderId = 0
)

$ErrorActionPreference = "Stop"

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

if ([string]::IsNullOrWhiteSpace($ConsumerToken)) {
    Write-Warning "ConsumerToken is empty. Authenticated user-service and engagement-service checks will be skipped."
} else {
    Invoke-BSideApi GET "$GatewayUrl/api/user/profile" $ConsumerToken $null | Out-Host
    Invoke-BSideApi POST "$GatewayUrl/api/user/favorites/$MerchantId" $ConsumerToken $null | Out-Host
    Invoke-BSideApi POST "$GatewayUrl/api/user/cart" $ConsumerToken @{
        merchantId = $MerchantId
        productId = $ProductId
        quantity = 1
    } | Out-Host

    if ($OrderId -gt 0) {
        Invoke-BSideApi GET "$GatewayUrl/api/messages/orders/$OrderId" $ConsumerToken $null | Out-Host
        Invoke-BSideApi POST "$GatewayUrl/api/messages" $ConsumerToken @{
            receiverId = $MerchantId
            receiverType = "merchant"
            orderId = $OrderId
            content = "联调消息"
        } | Out-Host
    }
}

if ([string]::IsNullOrWhiteSpace($RiderToken)) {
    Write-Warning "RiderToken is empty. Authenticated fulfillment-service checks will be skipped."
} else {
    Invoke-BSideApi GET "$GatewayUrl/api/rider/profile" $RiderToken $null | Out-Host
    Invoke-BSideApi GET "$GatewayUrl/api/rider/tasks" $RiderToken $null | Out-Host
}

Write-Host "B-side API smoke script completed."
