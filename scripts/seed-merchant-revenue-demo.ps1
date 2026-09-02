param(
    [string]$DockerContainer = "life-assistant-mysql",
    [string]$Password = ""
)

$root = Split-Path -Parent $PSScriptRoot

if (-not $Password) {
    if ($env:MYSQL_ROOT_PASSWORD) {
        $Password = $env:MYSQL_ROOT_PASSWORD
    } else {
        $Password = "123456"
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required to seed demo revenue data."
}

$containerState = docker inspect -f "{{.State.Running}}" $DockerContainer 2>$null
if ($containerState -ne "true") {
    throw "MySQL container '$DockerContainer' is not running."
}

$culture = [System.Globalization.CultureInfo]::InvariantCulture

function Escape-Sql([string]$value) {
    if ($null -eq $value) {
        return ""
    }
    return $value.Replace("'", "''")
}

function Format-DateTime([datetime]$value) {
    return $value.ToString("yyyy-MM-dd HH:mm:ss", $culture)
}

function Format-Decimal([decimal]$value) {
    return $value.ToString("0.00", $culture)
}

$merchantSeeds = @(
    @{
        MerchantId = 20001
        ProductId = 30001
        ProductName = "Braised Pork Rice"
        ProductImage = "/oss/life-assistant/demo/products/braised-pork-rice.png"
        DeliveryFee = [decimal]5.00
        OrderIdStart = 81001
        ItemIdStart = 91001
        Amounts = @(27, 31, 24, 29, 33, 26, 38, 22, 30, 28, 34, 25, 36, 32)
    },
    @{
        MerchantId = 20002
        ProductId = 30003
        ProductName = "Bubble Milk Tea"
        ProductImage = "/oss/life-assistant/demo/products/bubble-milk-tea.png"
        DeliveryFee = [decimal]3.00
        OrderIdStart = 82001
        ItemIdStart = 92001
        Amounts = @(15, 18, 20, 22, 17, 24, 19, 25, 21, 23, 16, 27, 28, 30)
    }
)

$orders = New-Object System.Collections.Generic.List[string]
$items = New-Object System.Collections.Generic.List[string]
$now = Get-Date

foreach ($seed in $merchantSeeds) {
    for ($offset = 13; $offset -ge 0; $offset--) {
        $index = 13 - $offset
        $day = $now.Date.AddDays(-$offset)
        $completedAt = $day.AddHours(12).AddMinutes(15 + (($index % 3) * 10))
        $createAt = $completedAt.AddMinutes(-35)
        $orderId = [long]$seed.OrderIdStart + $index
        $itemId = [long]$seed.ItemIdStart + $index
        $amount = [decimal]$seed.Amounts[$index]
        $deliveryFee = [decimal]$seed.DeliveryFee
        $goodsAmount = $amount - $deliveryFee

        $orderNo = "SEED-M{0}-{1}-{2:00}" -f $seed.MerchantId, $day.ToString("yyyyMMdd"), ($index + 1)
        $addressDetail = "Seeded address for merchant {0}" -f $seed.MerchantId

        $orders.Add((
            "({0}, '{1}', 10001, {2}, NULL, 'delivery', {3}, {3}, {4}, 0.00, 'completed', 1, '{5}', NULL, NULL, '{6}', '{6}', 1, '{7}', '{6}')" -f
            $orderId,
            (Escape-Sql $orderNo),
            $seed.MerchantId,
            (Format-Decimal $amount),
            (Format-Decimal $deliveryFee),
            (Escape-Sql $addressDetail),
            (Format-DateTime $completedAt),
            (Format-DateTime $createAt)
        ))

        $items.Add((
            "({0}, {1}, {2}, '{3}', {4}, 1, '{5}', NULL, {4}, 0, '{6}', '{6}')" -f
            $itemId,
            $orderId,
            $seed.ProductId,
            (Escape-Sql $seed.ProductName),
            (Format-Decimal $goodsAmount),
            (Escape-Sql $seed.ProductImage),
            (Format-DateTime $completedAt)
        ))
    }
}

$sql = @"
START TRANSACTION;
DELETE oi
FROM order_item oi
INNER JOIN orders o ON o.id = oi.order_id
WHERE o.order_no LIKE 'SEED-M%';
DELETE FROM orders WHERE order_no LIKE 'SEED-M%';

INSERT INTO orders (
    id, order_no, user_id, merchant_id, rider_id, type, total_amount, actual_amount,
    delivery_fee, discount, status, address_id, address_detail, buyer_remark, coupon_id,
    paid_at, completed_at, stock_reserved, create_time, update_time
) VALUES
$(($orders -join ",`n"))
;

INSERT INTO order_item (
    id, order_id, product_id, name, price, quantity, image, spec_label, subtotal, reviewed,
    create_time, update_time
) VALUES
$(($items -join ",`n"))
;
COMMIT;
"@

Write-Host "Seeding demo revenue data into $DockerContainer..."
$sql | docker exec -i $DockerContainer mysql --default-character-set=utf8mb4 --user=root "--password=$Password" order_db
if ($LASTEXITCODE -ne 0) {
    throw "Seeding demo revenue data failed."
}

Write-Host "Demo revenue data seeded successfully for merchants 20001 and 20002."
