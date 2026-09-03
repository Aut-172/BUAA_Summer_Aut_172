param(
    [string]$DockerContainer = "life-assistant-mysql",
    [string]$RedisContainer = "life-assistant-redis",
    [string]$Password = "",
    [int]$Days = 30,
    [int]$OrdersPerDay = 6,
    [int]$ReviewPercent = 70,
    [datetime]$EndDate = (Get-Date).Date,
    [long]$OrderIdBase = 9000000,
    [long]$OrderItemIdBase = 9100000,
    [long]$PaymentIdBase = 9200000,
    [long]$ReviewIdBase = 9300000,
    [long]$AddressId = 9400000,
    [string]$OutputSql = "",
    [string]$Kubectl = "kubectl",
    [string]$Namespace = "default",
    [string]$MysqlDeployment = "life-assistant-mysql",
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = "",
    [string]$RedisDeployment = "life-assistant-redis",
    [string]$SshHost = "",
    [string]$SshUser = "root",
    [int]$SshPort = 22,
    [string]$SshKey = "",
    [string]$RemoteSqlPath = "/tmp/life-assistant-order-review-demo.sql",
    [switch]$ApplyToKubernetes,
    [switch]$ApplyViaSsh,
    [switch]$ApplyToDocker,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not $OutputSql) {
    $OutputSql = Join-Path $repoRoot "reports\demo\order-review-demo-data.sql"
}

if (-not $Password) {
    if ($env:MYSQL_ROOT_PASSWORD) {
        $Password = $env:MYSQL_ROOT_PASSWORD
    } else {
        $Password = "123456"
    }
}
if (-not $MysqlPassword) {
    $MysqlPassword = $Password
}

if ($Days -lt 1) {
    throw "Days must be at least 1."
}
if ($OrdersPerDay -lt 1) {
    throw "OrdersPerDay must be at least 1."
}
if ($ReviewPercent -lt 0 -or $ReviewPercent -gt 100) {
    throw "ReviewPercent must be between 0 and 100."
}

$culture = [System.Globalization.CultureInfo]::InvariantCulture

function Escape-Sql([string]$value) {
    if ($null -eq $value) {
        return ""
    }
    return $value.Replace("'", "''")
}

function Sql-String([string]$value) {
    if ($null -eq $value) {
        return "NULL"
    }
    return "'$(Escape-Sql $value)'"
}

function Sql-DateTime([object]$value) {
    if ($null -eq $value) {
        return "NULL"
    }
    $date = [datetime]$value
    return "'$($date.ToString("yyyy-MM-dd HH:mm:ss", $culture))'"
}

function Format-Decimal([decimal]$value) {
    return $value.ToString("0.00", $culture)
}

function Quote-Bash([string]$value) {
    if ($null -eq $value) {
        return "''"
    }
    return "'" + $value.Replace("'", "'\''") + "'"
}

function Pick-Status([int]$dayOffset, [int]$index, [int]$serial) {
    if ($dayOffset -eq 0) {
        $todayStatuses = @("pending_payment", "pending_accept", "delivering", "completed", "cancelled", "pending_accept")
        return $todayStatuses[$index % $todayStatuses.Count]
    }
    if ($dayOffset -eq 1) {
        $yesterdayStatuses = @("delivering", "completed", "completed", "pending_accept", "completed", "cancelled")
        return $yesterdayStatuses[$index % $yesterdayStatuses.Count]
    }
    if (($serial % 9) -eq 0) {
        return "cancelled"
    }
    return "completed"
}

$merchantSeeds = @(
    @{
        MerchantId = 20001
        MerchantName = "Campus Kitchen"
        DeliveryFee = [decimal]5.00
        Products = @(
            @{ ProductId = 30001; Name = "Braised Pork Rice"; Price = [decimal]22.00; Image = "/oss/life-assistant/demo/products/braised-pork-rice.png"; Spec = "Large" },
            @{ ProductId = 30002; Name = "Kung Pao Chicken Rice"; Price = [decimal]24.00; Image = "/oss/life-assistant/demo/products/kung-pao-chicken-rice.png"; Spec = "Standard" }
        )
    },
    @{
        MerchantId = 20002
        MerchantName = "Tea Corner"
        DeliveryFee = [decimal]3.00
        Products = @(
            @{ ProductId = 30003; Name = "Bubble Milk Tea"; Price = [decimal]12.00; Image = "/oss/life-assistant/demo/products/bubble-milk-tea.png"; Spec = "70% Sugar, Cold" },
            @{ ProductId = 30004; Name = "Tiramisu"; Price = [decimal]28.00; Image = "/oss/life-assistant/demo/products/tiramisu.png"; Spec = "Standard" }
        )
    }
)

$reviewContents = @(
    "Delivery was quick and the package was complete.",
    "The taste is stable and the lunch rush flow looks smooth.",
    "Good portion size, useful for the order and review demo.",
    "Nice service experience with clear product and review display.",
    "Reasonable price and complete delivery information.",
    "Overall satisfied; this is a useful historical demo review."
)

$orders = New-Object System.Collections.Generic.List[string]
$items = New-Object System.Collections.Generic.List[string]
$payments = New-Object System.Collections.Generic.List[string]
$reviews = New-Object System.Collections.Generic.List[string]

$serial = 0
$reviewSerial = 0

for ($dayOffset = $Days - 1; $dayOffset -ge 0; $dayOffset--) {
    $day = $EndDate.Date.AddDays(-$dayOffset)

    for ($index = 0; $index -lt $OrdersPerDay; $index++) {
        $serial++
        $merchant = $merchantSeeds[$serial % $merchantSeeds.Count]
        $product = $merchant.Products[($serial + $dayOffset + $index) % $merchant.Products.Count]
        $status = Pick-Status $dayOffset $index $serial
        $quantity = 1 + (($serial + $index) % 2)
        $discount = if (($serial % 5) -eq 0 -and $status -ne "pending_payment") { [decimal]5.00 } else { [decimal]0.00 }
        $goodsTotal = [decimal]$product.Price * [decimal]$quantity
        $totalAmount = $goodsTotal + [decimal]$merchant.DeliveryFee
        $actualAmount = $totalAmount - $discount
        if ($actualAmount -lt [decimal]0.01) {
            $actualAmount = [decimal]0.01
        }
        $createAt = $day.AddHours(10 + (($index * 2) % 11)).AddMinutes(($serial * 7) % 50)
        $paidAt = $null
        $completedAt = $null

        if ($status -ne "pending_payment" -and $status -ne "cancelled") {
            $paidAt = $createAt.AddMinutes(2 + ($serial % 4))
        }
        if ($status -eq "completed") {
            $completedAt = $createAt.AddMinutes(28 + ($serial % 25))
        }

        $riderId = if ($status -eq "delivering" -or $status -eq "completed") { 40001 } else { "NULL" }
        $orderId = $OrderIdBase + $serial
        $itemId = $OrderItemIdBase + $serial
        $paymentId = $PaymentIdBase + $serial
        $orderNo = "DEMO-{0}-{1:0000}" -f $day.ToString("yyyyMMdd"), $serial
        $address = "Demo address: Dormitory 3 Room 302"
        $remark = if (($serial % 3) -eq 0) { "Less spicy, add tableware" } else { "Demo order" }
        $reviewCreated = $false

        if ($status -eq "completed" -and $completedAt -ne $null -and (($serial * 37) % 100) -lt $ReviewPercent) {
            $reviewCreated = $true
            $reviewSerial++
            $reviewId = $ReviewIdBase + $reviewSerial
            $rating = 3 + (($serial + $index) % 3)
            $reviewContent = $reviewContents[($serial + $index) % $reviewContents.Count]
            $reviewTime = $completedAt.AddMinutes(15 + ($serial % 90))
            $images = if (($serial % 4) -eq 0) { '["/oss/life-assistant/demo/products/braised-pork-rice.png"]' } else { "[]" }

            $reviews.Add((
                "({0}, {1}, 10001, {2}, {3}, {4}, {5}, {6}, {7}, {7})" -f
                $reviewId,
                $orderId,
                $merchant.MerchantId,
                $product.ProductId,
                $rating,
                (Sql-String $reviewContent),
                (Sql-String $images),
                (Sql-DateTime ([Nullable[datetime]]$reviewTime))
            ))
        }

        $orders.Add((
            "({0}, {1}, 10001, {2}, {3}, 'delivery', {4}, {5}, {6}, {7}, {8}, {9}, {10}, {11}, NULL, {12}, {13}, 0, {14}, {14})" -f
            $orderId,
            (Sql-String $orderNo),
            $merchant.MerchantId,
            $riderId,
            (Format-Decimal $totalAmount),
            (Format-Decimal $actualAmount),
            (Format-Decimal $merchant.DeliveryFee),
            (Format-Decimal $discount),
            (Sql-String $status),
            $AddressId,
            (Sql-String $address),
            (Sql-String $remark),
            (Sql-DateTime ([Nullable[datetime]]$paidAt)),
            (Sql-DateTime ([Nullable[datetime]]$completedAt)),
            (Sql-DateTime ([Nullable[datetime]]$createAt))
        ))

        $items.Add((
            "({0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}, {8}, {9}, {10}, {10})" -f
            $itemId,
            $orderId,
            $product.ProductId,
            (Sql-String $product.Name),
            (Format-Decimal $product.Price),
            $quantity,
            (Sql-String $product.Image),
            (Sql-String $product.Spec),
            (Format-Decimal $goodsTotal),
            ($(if ($reviewCreated) { 1 } else { 0 })),
            (Sql-DateTime ([Nullable[datetime]]$createAt))
        ))

        if ($paidAt -ne $null) {
            $payMethod = if (($serial % 2) -eq 0) { "ALIPAY" } else { "WECHAT" }
            $payments.Add((
                "({0}, {1}, {2}, {3}, {4}, 'SUCCESS', {5}, {6}, {6})" -f
                $paymentId,
                $orderId,
                (Format-Decimal $actualAmount),
                (Sql-String $payMethod),
                (Sql-String ("PAY-DEMO-{0}" -f $orderNo)),
                (Sql-DateTime ([Nullable[datetime]]$paidAt)),
                (Sql-DateTime ([Nullable[datetime]]$paidAt))
            ))
        }
    }
}

$merchantIds = ($merchantSeeds | ForEach-Object { $_.MerchantId }) -join ","
$reviewInsert = if ($reviews.Count -gt 0) {
@"

INSERT INTO engagement_db.review (
    id, order_id, user_id, merchant_id, product_id, rating, content, images,
    create_time, update_time
) VALUES
$($reviews -join ",`n")
;
"@
} else {
    ""
}

$paymentInsert = if ($payments.Count -gt 0) {
@"

INSERT INTO settlement_db.payment (
    id, order_id, amount, pay_method, transaction_id, status, pay_time,
    create_time, update_time
) VALUES
$($payments -join ",`n")
;
"@
} else {
    ""
}

$sql = @"
SET NAMES utf8mb4;
START TRANSACTION;

SET @demo_order_prefix := 'DEMO-%';

DELETE FROM engagement_db.review
WHERE order_id IN (SELECT id FROM order_db.orders WHERE order_no LIKE @demo_order_prefix);

DELETE FROM settlement_db.payment
WHERE order_id IN (SELECT id FROM order_db.orders WHERE order_no LIKE @demo_order_prefix);

DELETE oi
FROM order_db.order_item oi
INNER JOIN order_db.orders o ON o.id = oi.order_id
WHERE o.order_no LIKE @demo_order_prefix;

DELETE FROM order_db.orders
WHERE order_no LIKE @demo_order_prefix;

INSERT INTO user_db.address (
    id, user_id, name, phone, detail, longitude, latitude, is_default, create_time, update_time
) VALUES (
    $AddressId, 10001, 'Demo Student', '13800138001', 'Dormitory 3 Room 302', 116.4600000, 39.9100000, 1, NOW(), NOW()
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    phone = VALUES(phone),
    detail = VALUES(detail),
    longitude = VALUES(longitude),
    latitude = VALUES(latitude),
    is_default = VALUES(is_default),
    update_time = NOW();

INSERT INTO order_db.orders (
    id, order_no, user_id, merchant_id, rider_id, type, total_amount, actual_amount,
    delivery_fee, discount, status, address_id, address_detail, buyer_remark, coupon_id,
    paid_at, completed_at, stock_reserved, create_time, update_time
) VALUES
$($orders -join ",`n")
;

INSERT INTO order_db.order_item (
    id, order_id, product_id, name, price, quantity, image, spec_label, subtotal, reviewed,
    create_time, update_time
) VALUES
$($items -join ",`n")
;$paymentInsert$reviewInsert

UPDATE merchant_db.merchant m
LEFT JOIN (
    SELECT merchant_id, COUNT(*) AS completed_orders
    FROM order_db.orders
    WHERE order_no LIKE @demo_order_prefix AND status = 'completed'
    GROUP BY merchant_id
) s ON s.merchant_id = m.id
LEFT JOIN (
    SELECT merchant_id, ROUND(AVG(rating), 1) AS avg_rating
    FROM engagement_db.review
    WHERE order_id IN (SELECT id FROM order_db.orders WHERE order_no LIKE @demo_order_prefix)
    GROUP BY merchant_id
) r ON r.merchant_id = m.id
SET m.monthly_sales = GREATEST(COALESCE(m.monthly_sales, 0), COALESCE(s.completed_orders, 0)),
    m.rating = COALESCE(r.avg_rating, m.rating),
    m.update_time = NOW()
WHERE m.id IN ($merchantIds);

COMMIT;
"@

$outputDir = Split-Path -Parent $OutputSql
if ($outputDir -and -not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}
[System.IO.File]::WriteAllText($OutputSql, $sql, [System.Text.UTF8Encoding]::new($false))

Write-Host "Generated $($orders.Count) demo orders, $($payments.Count) payments, and $($reviews.Count) reviews."
Write-Host "SQL written to $OutputSql"

if ($DryRun) {
    Write-Host "DryRun enabled; database was not changed."
    exit 0
}

if ($ApplyViaSsh) {
    if (-not $SshHost) {
        throw "SshHost is required when using -ApplyViaSsh."
    }

    $remoteTarget = "${SshUser}@${SshHost}:$RemoteSqlPath"
    $scpArgs = @()
    $sshArgs = @()
    if ($SshKey) {
        $scpArgs += @("-i", $SshKey)
        $sshArgs += @("-i", $SshKey)
    }
    if ($SshPort -ne 22) {
        $scpArgs += @("-P", [string]$SshPort)
        $sshArgs += @("-p", [string]$SshPort)
    }
    $scpArgs += @($OutputSql, $remoteTarget)
    $sshArgs += @("${SshUser}@${SshHost}")

    Write-Host "Uploading SQL to ${SshUser}@${SshHost}:$RemoteSqlPath..."
    & scp @scpArgs
    if ($LASTEXITCODE -ne 0) {
        throw "SCP upload failed."
    }

    $quotedRemoteSqlPath = Quote-Bash $RemoteSqlPath
    $quotedMysqlPassword = Quote-Bash $MysqlPassword
    $remoteMysqlCommand = "cat $quotedRemoteSqlPath | kubectl exec -i deployment/$MysqlDeployment -n $Namespace -- mysql --default-character-set=utf8mb4 -u$MysqlUser -p$quotedMysqlPassword"
    Write-Host "Importing SQL into Kubernetes deployment/$MysqlDeployment in namespace $Namespace through SSH..."
    & ssh @sshArgs $remoteMysqlCommand
    if ($LASTEXITCODE -ne 0) {
        throw "Remote Kubernetes MySQL seed command failed."
    }

    $remoteRedisCommand = "kubectl exec deployment/$RedisDeployment -n $Namespace -- sh -lc " + (Quote-Bash "redis-cli --scan --pattern 'la:engagement:*' | xargs -r redis-cli del >/dev/null")
    & ssh @sshArgs $remoteRedisCommand
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Engagement Redis caches cleared through SSH."
    } else {
        Write-Warning "Demo data was seeded, but remote Redis cache cleanup failed."
    }

    Write-Host "Demo order/review data seeded successfully through SSH."
    exit 0
}

if ($ApplyToKubernetes) {
    Write-Host "Seeding demo order/review data into Kubernetes deployment/$MysqlDeployment in namespace $Namespace..."
    $sql | & $Kubectl exec -i "deployment/$MysqlDeployment" -n $Namespace -- mysql --default-character-set=utf8mb4 "-u$MysqlUser" "-p$MysqlPassword"
    if ($LASTEXITCODE -ne 0) {
        throw "Kubernetes MySQL seed command failed."
    }

    try {
        & $Kubectl exec "deployment/$RedisDeployment" -n $Namespace -- sh -lc "redis-cli --scan --pattern 'la:engagement:*' | xargs -r redis-cli del >/dev/null" | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Engagement Redis caches cleared in Kubernetes."
        }
    } catch {
        Write-Warning "Demo data was seeded, but Kubernetes Redis cache cleanup failed: $($_.Exception.Message)"
    }

    Write-Host "Demo order/review data seeded successfully in Kubernetes."
    exit 0
}

if (-not $ApplyToDocker) {
    Write-Host "No apply target selected; database was not changed."
    Write-Host "Use -ApplyToKubernetes for cloud K8s or -ApplyToDocker for local Docker."
    exit 0
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is required to seed demo data with -ApplyToDocker."
}

$containerState = docker inspect -f "{{.State.Running}}" $DockerContainer 2>$null
if ($containerState -ne "true") {
    throw "MySQL container '$DockerContainer' is not running. Use -DryRun to only generate SQL."
}

Write-Host "Seeding demo order/review data into $DockerContainer..."
$sql | docker exec -i $DockerContainer mysql --default-character-set=utf8mb4 --user=root "--password=$Password"
if ($LASTEXITCODE -ne 0) {
    throw "Seeding demo order/review data failed."
}

try {
    $redisState = docker inspect -f "{{.State.Running}}" $RedisContainer 2>$null
    if ($redisState -eq "true") {
        docker exec $RedisContainer sh -lc "redis-cli --scan --pattern 'la:engagement:*' | xargs -r redis-cli del >/dev/null" | Out-Null
        Write-Host "Engagement Redis caches cleared."
    }
} catch {
    Write-Warning "Demo data was seeded, but Redis cache cleanup failed: $($_.Exception.Message)"
}

Write-Host "Demo order/review data seeded successfully."
