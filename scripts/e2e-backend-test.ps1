$ErrorActionPreference = 'Stop'

$base = 'http://localhost:8081/api'

function Show($title, $obj) {
    Write-Host "=== $title ==="
    Write-Host (ConvertTo-Json $obj -Depth 10)
}

function Login($path, $payload) {
    $resp = Invoke-RestMethod -Method Post -Uri ($base + $path) -Body ($payload | ConvertTo-Json) -ContentType 'application/json'
    if (-not $resp.data.accessToken) {
        throw "Login failed for path $path"
    }
    return $resp.data.accessToken
}

$consumerToken = Login '/auth/login' @{ username = 'demo'; password = '123456' }
$riderToken = Login '/auth/rider/login' @{ username = 'rider01'; password = '123456' }
$merchantToken = Login '/auth/merchant/login' @{ username = 'merchant1'; password = '123456' }
$adminToken = Login '/auth/admin/login' @{ username = 'gl1'; password = 'gl1gl1gl1' }

$consumerHeaders = @{ Authorization = "Bearer $consumerToken" }
$riderHeaders = @{ Authorization = "Bearer $riderToken" }
$merchantHeaders = @{ Authorization = "Bearer $merchantToken" }
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

Invoke-RestMethod -Method Delete -Uri "$base/user/cart" -Headers $consumerHeaders | Out-Null

$addCart = Invoke-RestMethod -Method Post -Uri "$base/user/cart" -Headers $consumerHeaders -Body (@{
    merchantId = 20001
    productId = 30001
    quantity = 1
    specLabel = 'Large'
} | ConvertTo-Json) -ContentType 'application/json'
Show 'Add Cart' $addCart

$cart = Invoke-RestMethod -Method Get -Uri "$base/user/cart" -Headers $consumerHeaders
Show 'Cart' $cart

$checkout = Invoke-RestMethod -Method Post -Uri "$base/checkout" -Headers $consumerHeaders -Body (@{
    merchantId = 20001
    address = 'Demo Dorm 1-101'
    items = @(
        @{
            productId = 30001
            quantity = 1
            specLabel = 'Large'
        }
    )
} | ConvertTo-Json -Depth 6) -ContentType 'application/json'
Show 'Checkout' $checkout

$orderId = $checkout.data.id
if (-not $orderId) {
    throw 'Checkout did not return order id'
}

$pay = Invoke-RestMethod -Method Post -Uri "$base/orders/$orderId/pay" -Headers $consumerHeaders -Body (@{
    payMethod = 'ALIPAY'
} | ConvertTo-Json) -ContentType 'application/json'
Show 'Pay Order' $pay

$merchantOrders = Invoke-RestMethod -Method Get -Uri "$base/merchant/orders" -Headers $merchantHeaders
Show 'Merchant Orders' $merchantOrders

# Use English status codes here so the script is stable regardless of console encoding.
$accept = Invoke-RestMethod -Method Put -Uri "$base/rider/tasks/$orderId" -Headers $riderHeaders -Body (@{
    status = 'pending_accept'
} | ConvertTo-Json) -ContentType 'application/json'
Show 'Rider Accept' $accept

$delivery = Invoke-RestMethod -Method Get -Uri "$base/delivery/$orderId" -Headers $consumerHeaders
Show 'Delivery Info' $delivery

$complete = Invoke-RestMethod -Method Post -Uri "$base/orders/$orderId/complete" -Headers $consumerHeaders
Show 'Complete Order' $complete

$payments = Invoke-RestMethod -Method Get -Uri "$base/orders/$orderId/payments" -Headers $consumerHeaders
Show 'Payments' $payments

$adminUsers = Invoke-RestMethod -Method Get -Uri "$base/admin/users?page=1&pageSize=5" -Headers $adminHeaders
Show 'Admin Users' $adminUsers

Write-Host 'E2E backend script finished successfully'
