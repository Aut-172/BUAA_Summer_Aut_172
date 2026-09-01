param(
    [int]$MerchantCount = 80,
    [int]$ProductsPerMerchant = 12,
    [string]$OutputSql = "",
    [string]$Bucket = $(if ($env:OSS_BUCKET) { $env:OSS_BUCKET } else { "buaa-summer-life-assistant" }),
    [string]$Prefix = $(if ($env:OSS_UPLOAD_PREFIX) { $env:OSS_UPLOAD_PREFIX } else { "life-assistant" }),
    [string]$OssUtil = $(if ($env:OSSUTIL_PATH) { $env:OSSUTIL_PATH } else { "ossutil" }),
    [string]$Kubectl = "kubectl",
    [string]$Namespace = "default",
    [string]$MysqlDeployment = "life-assistant-mysql",
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = $(if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "123456" }),
    [switch]$UploadImages,
    [switch]$SkipImageUpload,
    [switch]$ApplyToKubernetes,
    [switch]$ResetDemoRange
)

$ErrorActionPreference = "Stop"

if ($MerchantCount -lt 1) { throw "MerchantCount must be at least 1." }
if ($ProductsPerMerchant -lt 1) { throw "ProductsPerMerchant must be at least 1." }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
if (-not $OutputSql) {
    $OutputSql = Join-Path $repoRoot "reports\perf\hpa-demo-data.sql"
}
New-Item -ItemType Directory -Force -Path (Split-Path $OutputSql -Parent) | Out-Null

if ($UploadImages) {
    $uploadArgs = @(
        "-Bucket", $Bucket,
        "-Prefix", $Prefix,
        "-OssUtil", $OssUtil
    )
    if ($SkipImageUpload) {
        $uploadArgs += "-SkipUpload"
    }
    & (Join-Path $PSScriptRoot "upload-demo-oss-images.ps1") @uploadArgs
}

function Escape-SqlString {
    param([string]$Value)
    if ($null -eq $Value) { return "" }
    return $Value.Replace("'", "''")
}

function Add-InsertStatement {
    param(
        [System.Text.StringBuilder]$Builder,
        [string]$Table,
        [string[]]$Columns,
        [object[][]]$Rows,
        [string[]]$UpdateColumns
    )

    if ($Rows.Count -eq 0) { return }

    $quotedColumns = ($Columns | ForEach-Object { "``$_``" }) -join ", "
    [void]$Builder.AppendLine("INSERT INTO ``$Table`` ($quotedColumns) VALUES")
    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $values = foreach ($value in $Rows[$i]) {
            if ($null -eq $value) {
                "NULL"
            }
            elseif ($value -is [int] -or $value -is [long] -or $value -is [decimal] -or $value -is [double]) {
                ([string]$value).Replace(",", ".")
            }
            else {
                "'$(Escape-SqlString ([string]$value))'"
            }
        }
        $suffix = if ($i -eq $Rows.Count - 1) { "" } else { "," }
        [void]$Builder.AppendLine("(" + ($values -join ", ") + ")$suffix")
    }

    if ($UpdateColumns.Count -gt 0) {
        $assignments = $UpdateColumns | ForEach-Object { "``$_`` = VALUES(``$_``)" }
        [void]$Builder.AppendLine("ON DUPLICATE KEY UPDATE " + ($assignments -join ", ") + ";")
    }
    else {
        [void]$Builder.AppendLine(";")
    }
    [void]$Builder.AppendLine()
}

$normalizedPrefix = $Prefix.Trim("/")
$assetPrefix = if ($normalizedPrefix) { "/oss/$normalizedPrefix/demo" } else { "/oss/demo" }
$merchantImages = @(
    "$assetPrefix/merchants/campus-kitchen.png",
    "$assetPrefix/merchants/tea-corner.png"
)
$productImages = @(
    "$assetPrefix/products/braised-pork-rice.png",
    "$assetPrefix/products/kung-pao-chicken-rice.png",
    "$assetPrefix/products/bubble-milk-tea.png",
    "$assetPrefix/products/tiramisu.png"
)
$categories = @("Food", "Cafe", "Service")
$productNames = @(
    "Braised Pork Rice", "Kung Pao Chicken Rice", "Bubble Milk Tea", "Tiramisu",
    "Beef Noodle", "Chicken Sandwich", "Fruit Tea", "Cheese Cake",
    "Campus Bento", "Latte", "Spicy Hotpot", "Lemon Soda"
)

$merchantBaseId = 900000
$productBaseId = 910000
$specGroupBaseId = 920000
$productSpecBaseId = 930000
$phoneBase = 13910000000

$sql = [System.Text.StringBuilder]::new()
[void]$sql.AppendLine("SET NAMES utf8mb4;")
[void]$sql.AppendLine("USE ``merchant_db``;")
[void]$sql.AppendLine()

if ($ResetDemoRange) {
    $merchantMax = $merchantBaseId + $MerchantCount + 10000
    $productMax = $productBaseId + ($MerchantCount * $ProductsPerMerchant) + 10000
    $specGroupMax = $specGroupBaseId + ($MerchantCount * $ProductsPerMerchant) + 10000
    $productSpecMax = $productSpecBaseId + ($MerchantCount * $ProductsPerMerchant * 2) + 10000
    [void]$sql.AppendLine("DELETE FROM ``product_spec`` WHERE ``id`` BETWEEN $productSpecBaseId AND $productSpecMax;")
    [void]$sql.AppendLine("DELETE FROM ``spec_group`` WHERE ``id`` BETWEEN $specGroupBaseId AND $specGroupMax;")
    [void]$sql.AppendLine("DELETE FROM ``product`` WHERE ``id`` BETWEEN $productBaseId AND $productMax;")
    [void]$sql.AppendLine("DELETE FROM ``merchant`` WHERE ``id`` BETWEEN $merchantBaseId AND $merchantMax;")
    [void]$sql.AppendLine()
}

Add-InsertStatement `
    -Builder $sql `
    -Table "category" `
    -Columns @("id", "name", "parent_id", "sort_order") `
    -Rows @(
        @([long]1, "Food", $null, 1),
        @([long]2, "Cafe", $null, 2),
        @([long]3, "Service", $null, 3)
    ) `
    -UpdateColumns @("name", "sort_order")

$merchantRows = New-Object System.Collections.Generic.List[object[]]
$productRows = New-Object System.Collections.Generic.List[object[]]
$specGroupRows = New-Object System.Collections.Generic.List[object[]]
$productSpecRows = New-Object System.Collections.Generic.List[object[]]
$passwordHash = '$2a$10$Eec47nxK3dPutEqDpCyCqOj3mJcOn31z3fCve3xGKSeI1rb4Je.dm'

for ($m = 1; $m -le $MerchantCount; $m++) {
    $merchantId = [long]($merchantBaseId + $m)
    $category = $categories[($m - 1) % $categories.Count]
    $longitude = [decimal](116.3000000 + (($m % 40) * 0.003))
    $latitude = [decimal](39.9000000 + (($m % 30) * 0.002))
    $rating = [decimal](4.1 + (($m % 9) / 10.0))
    $monthlySales = 500 + ($m * 17)
    $deliveryFee = [decimal](3 + ($m % 4))
    $deliveryRadius = 5 + ($m % 5)
    $merchantRows.Add([object[]]@(
        $merchantId,
        "hpa_merchant_$m",
        $passwordHash,
        "HPA Demo Merchant $m",
        [string]($phoneBase + $m),
        "HPA Demo Block $m, Cloud Native Test Street",
        $longitude,
        $latitude,
        "09:00-22:00",
        $category,
        "Seeded merchant for Kubernetes HPA load testing.",
        $merchantImages[($m - 1) % $merchantImages.Count],
        "food,hpa-demo,load-test,$($category.ToLower())",
        "active",
        $rating,
        $monthlySales,
        [decimal]15.00,
        $deliveryFee,
        $deliveryRadius
    ))

    for ($p = 1; $p -le $ProductsPerMerchant; $p++) {
        $seq = (($m - 1) * $ProductsPerMerchant) + $p
        $productId = [long]($productBaseId + $seq)
        $categoryId = (($m - 1) % $categories.Count) + 1
        $productName = $productNames[($p - 1) % $productNames.Count]
        $price = [decimal](10 + (($p * 3 + $m) % 30))
        $productMonthlySales = 100 + (($m * $p) % 900)
        $productRows.Add([object[]]@(
            $productId,
            $merchantId,
            $categoryId,
            "$productName $m-$p",
            $productImages[($p - 1) % $productImages.Count],
            $price,
            "Seeded product for HPA read-load experiments.",
            $productMonthlySales,
            500,
            "delivery",
            "active",
            "[]"
        ))

        $specGroupId = [long]($specGroupBaseId + $seq)
        $productSpecId1 = [long]($productSpecBaseId + ($seq * 2) - 1)
        $productSpecId2 = [long]($productSpecBaseId + ($seq * 2))
        $specGroupRows.Add([object[]]@($specGroupId, $productId, "Size", '["Standard","Large(+3)"]'))
        $productSpecRows.Add([object[]]@($productSpecId1, $productId, "Standard", [decimal]0.00, 500))
        $productSpecRows.Add([object[]]@($productSpecId2, $productId, "Large", [decimal]3.00, 500))
    }
}

Add-InsertStatement `
    -Builder $sql `
    -Table "merchant" `
    -Columns @("id", "username", "password", "name", "phone", "address", "longitude", "latitude", "business_hours", "category", "description", "avatar", "tags", "status", "rating", "monthly_sales", "min_delivery_fee", "delivery_fee", "delivery_radius") `
    -Rows $merchantRows.ToArray() `
    -UpdateColumns @("name", "phone", "address", "longitude", "latitude", "business_hours", "category", "description", "avatar", "tags", "status", "rating", "monthly_sales", "min_delivery_fee", "delivery_fee", "delivery_radius")

Add-InsertStatement `
    -Builder $sql `
    -Table "product" `
    -Columns @("id", "merchant_id", "category_id", "name", "image", "price", "description", "monthly_sales", "stock", "type", "status", "gallery") `
    -Rows $productRows.ToArray() `
    -UpdateColumns @("merchant_id", "category_id", "name", "image", "price", "description", "monthly_sales", "stock", "type", "status", "gallery")

Add-InsertStatement `
    -Builder $sql `
    -Table "spec_group" `
    -Columns @("id", "product_id", "name", "values") `
    -Rows $specGroupRows.ToArray() `
    -UpdateColumns @("product_id", "name", "values")

Add-InsertStatement `
    -Builder $sql `
    -Table "product_spec" `
    -Columns @("id", "product_id", "label", "price", "stock") `
    -Rows $productSpecRows.ToArray() `
    -UpdateColumns @("product_id", "label", "price", "stock")

[void]$sql.AppendLine("SELECT COUNT(*) AS hpa_demo_merchants FROM ``merchant`` WHERE ``id`` >= $merchantBaseId;")
[void]$sql.AppendLine("SELECT COUNT(*) AS hpa_demo_products FROM ``product`` WHERE ``id`` >= $productBaseId;")

Set-Content -Path $OutputSql -Value $sql.ToString() -Encoding UTF8
Write-Host "Generated SQL: $OutputSql"
Write-Host "Demo merchants: $MerchantCount; demo products: $($MerchantCount * $ProductsPerMerchant)"

if ($ApplyToKubernetes) {
    Get-Content $OutputSql -Raw | & $Kubectl exec -i deployment/$MysqlDeployment -n $Namespace -- mysql -u$MysqlUser -p$MysqlPassword
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl/mysql seed command failed."
    }
    Write-Host "Seed data applied to Kubernetes MySQL deployment/$MysqlDeployment in namespace $Namespace."
}
