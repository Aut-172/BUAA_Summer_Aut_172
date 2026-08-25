$ErrorActionPreference = 'Stop'

$base = 'http://localhost:8081/api'

function Invoke-ApiJson {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $params = @{
        Method      = $Method
        Uri         = $Url
        Headers     = $Headers
        ContentType = 'application/json'
    }

    if ($null -ne $Body) {
        $params.Body = $Body | ConvertTo-Json -Depth 8
    }

    Invoke-RestMethod @params
}

function Login-Merchant {
    param(
        [string]$Username,
        [string]$Password
    )

    $resp = Invoke-ApiJson -Method 'Post' -Url "$base/auth/merchant/login" -Body @{
        username = $Username
        password = $Password
    }

    $resp.data.accessToken
}

function Ensure-Merchant {
    param(
        [hashtable]$Merchant
    )

    try {
        Invoke-ApiJson -Method 'Post' -Url "$base/auth/merchant/register" -Body @{
            username = $Merchant.username
            phone    = $Merchant.phone
            password = $Merchant.password
            nickname = $Merchant.name
        } | Out-Null
    } catch {
        $message = $_.Exception.Message
        if ($message -notmatch 'already|exist|registered|exists') {
            throw
        }
    }

    $token = Login-Merchant -Username $Merchant.username -Password $Merchant.password
    $headers = @{ Authorization = "Bearer $token" }

    Invoke-ApiJson -Method 'Put' -Url "$base/merchant/profile" -Headers $headers -Body @{
        name           = $Merchant.name
        phone          = $Merchant.phone
        address        = $Merchant.address
        businessHours  = $Merchant.businessHours
        category       = $Merchant.category
        description    = $Merchant.description
        avatar         = $Merchant.avatar
        tags           = $Merchant.tags
        minDeliveryFee = $Merchant.minDeliveryFee
        deliveryFee    = $Merchant.deliveryFee
        deliveryRadius = $Merchant.deliveryRadius
    } | Out-Null

    $existingProducts = Invoke-ApiJson -Method 'Get' -Url "$base/merchant/products" -Headers $headers
    $existingNames = @{}

    foreach ($product in @($existingProducts.data)) {
        if ($null -ne $product -and $product.name) {
            $existingNames[$product.name] = $true
        }
    }

    foreach ($product in $Merchant.products) {
        if ($existingNames.ContainsKey($product.name)) {
            continue
        }

        Invoke-ApiJson -Method 'Post' -Url "$base/merchant/products" -Headers $headers -Body @{
            categoryId  = $product.categoryId
            name        = $product.name
            image       = $product.image
            price       = $product.price
            description = $product.description
            stock       = $product.stock
            type        = 'delivery'
            status      = 'active'
            gallery     = '[]'
        } | Out-Null
    }
}

$seedMerchants = @(
    @{
        username = 'merchant_showcase_01'
        password = '123456'
        phone = '13910010001'
        name = 'Golden Wok'
        address = 'East Dining Hall, Booth 12'
        businessHours = '10:00-22:30'
        category = 'Food'
        description = 'Rice bowls, wok noodles and savory snacks with quick delivery.'
        avatar = 'https://picsum.photos/seed/showcase-merchant-01/480/360'
        tags = 'rice bowl,late night,fast'
        minDeliveryFee = 18
        deliveryFee = 3
        deliveryRadius = 4
        products = @(
            @{ categoryId = 1; name = 'Black Pepper Beef Bowl'; price = 26; stock = 120; image = 'https://picsum.photos/seed/showcase-food-011/640/480'; description = 'Peppery beef over rice with egg and greens.' }
            @{ categoryId = 1; name = 'Spicy Chicken Leg Bowl'; price = 24; stock = 120; image = 'https://picsum.photos/seed/showcase-food-012/640/480'; description = 'Roasted chicken leg with spicy sauce and steamed rice.' }
            @{ categoryId = 1; name = 'Golden Fried Noodles'; price = 21; stock = 100; image = 'https://picsum.photos/seed/showcase-food-013/640/480'; description = 'Wok-fried noodles with strong smoky flavor.' }
            @{ categoryId = 1; name = 'Garlic Popcorn Chicken'; price = 16; stock = 90; image = 'https://picsum.photos/seed/showcase-food-014/640/480'; description = 'Crispy bite-sized chicken with garlic seasoning.' }
        )
    }
    @{
        username = 'merchant_showcase_02'
        password = '123456'
        phone = '13910010002'
        name = 'Seoul Rice House'
        address = 'Library South Street No. 5'
        businessHours = '09:30-21:30'
        category = 'Food'
        description = 'Korean bowls, hot plates and fried chicken with bold flavors.'
        avatar = 'https://picsum.photos/seed/showcase-merchant-02/480/360'
        tags = 'korean,hot plate,popular'
        minDeliveryFee = 20
        deliveryFee = 4
        deliveryRadius = 5
        products = @(
            @{ categoryId = 1; name = 'Beef Stone Pot Bowl'; price = 29; stock = 110; image = 'https://picsum.photos/seed/showcase-food-021/640/480'; description = 'Sliced beef, kimchi and egg in a hot stone bowl.' }
            @{ categoryId = 1; name = 'Cheese Fire Noodles'; price = 23; stock = 90; image = 'https://picsum.photos/seed/showcase-food-022/640/480'; description = 'Spicy noodles balanced with mellow melted cheese.' }
            @{ categoryId = 1; name = 'Korean Fried Chicken Mix'; price = 32; stock = 80; image = 'https://picsum.photos/seed/showcase-food-023/640/480'; description = 'Sweet chili and garlic double-flavor fried chicken.' }
            @{ categoryId = 1; name = 'Kimchi Fish Cake Soup'; price = 18; stock = 70; image = 'https://picsum.photos/seed/showcase-food-024/640/480'; description = 'Light soup that pairs well with any main dish.' }
        )
    }
    @{
        username = 'merchant_showcase_03'
        password = '123456'
        phone = '13910010003'
        name = 'Pasta Planet'
        address = 'Innovation Center West Lobby'
        businessHours = '11:00-22:00'
        category = 'Food'
        description = 'Creamy pasta, baked rice and snacks for a more relaxed meal.'
        avatar = 'https://picsum.photos/seed/showcase-merchant-03/480/360'
        tags = 'pasta,baked rice,western'
        minDeliveryFee = 25
        deliveryFee = 4
        deliveryRadius = 4
        products = @(
            @{ categoryId = 1; name = 'Cream Bacon Pasta'; price = 30; stock = 80; image = 'https://picsum.photos/seed/showcase-food-031/640/480'; description = 'Silky cream pasta with smoky bacon pieces.' }
            @{ categoryId = 1; name = 'Tomato Meat Sauce Pasta'; price = 28; stock = 85; image = 'https://picsum.photos/seed/showcase-food-032/640/480'; description = 'Classic tomato sauce with a balanced sweet and sour taste.' }
            @{ categoryId = 1; name = 'Cheese Chicken Baked Rice'; price = 31; stock = 75; image = 'https://picsum.photos/seed/showcase-food-033/640/480'; description = 'Baked rice topped with stretchy cheese and chicken.' }
            @{ categoryId = 1; name = 'Herb Potato Wedges'; price = 14; stock = 120; image = 'https://picsum.photos/seed/showcase-food-034/640/480'; description = 'Crispy potato wedges dusted with herbs.' }
        )
    }
    @{
        username = 'merchant_showcase_04'
        password = '123456'
        phone = '13910010004'
        name = 'Midnight Grill'
        address = 'North Dorm Snack Street'
        businessHours = '17:00-01:00'
        category = 'Food'
        description = 'Late-night skewers, grilled dishes and rich-flavor comfort food.'
        avatar = 'https://picsum.photos/seed/showcase-merchant-04/480/360'
        tags = 'bbq,late night,bold'
        minDeliveryFee = 30
        deliveryFee = 5
        deliveryRadius = 5
        products = @(
            @{ categoryId = 1; name = 'Lamb Skewer Set'; price = 22; stock = 160; image = 'https://picsum.photos/seed/showcase-food-041/640/480'; description = 'Five cumin-scented lamb skewers fresh off the grill.' }
            @{ categoryId = 1; name = 'Garlic Crayfish Rice'; price = 38; stock = 70; image = 'https://picsum.photos/seed/showcase-food-042/640/480'; description = 'Rice meal with rich garlic crayfish flavor.' }
            @{ categoryId = 1; name = 'Iron Plate Squid Noodles'; price = 25; stock = 95; image = 'https://picsum.photos/seed/showcase-food-043/640/480'; description = 'Chewy squid tossed with savory stir-fried noodles.' }
            @{ categoryId = 1; name = 'Chilled Plum Drink'; price = 8; stock = 150; image = 'https://picsum.photos/seed/showcase-food-044/640/480'; description = 'Refreshing drink that cuts through heavier flavors.' }
        )
    }
    @{
        username = 'merchant_showcase_05'
        password = '123456'
        phone = '13910010005'
        name = 'Fit Fuel Lab'
        address = 'Gym East Gate No. 2'
        businessHours = '08:00-20:30'
        category = 'Food'
        description = 'Salads, sandwiches and lighter bowls for a cleaner daily meal.'
        avatar = 'https://picsum.photos/seed/showcase-merchant-05/480/360'
        tags = 'salad,fitness,light'
        minDeliveryFee = 18
        deliveryFee = 3
        deliveryRadius = 4
        products = @(
            @{ categoryId = 1; name = 'Avocado Chicken Power Bowl'; price = 27; stock = 60; image = 'https://picsum.photos/seed/showcase-food-051/640/480'; description = 'Chicken breast, grains and avocado with light dressing.' }
            @{ categoryId = 1; name = 'Caesar Chicken Salad'; price = 24; stock = 75; image = 'https://picsum.photos/seed/showcase-food-052/640/480'; description = 'Crisp romaine, grilled chicken and Caesar dressing.' }
            @{ categoryId = 1; name = 'Tuna Whole Wheat Sandwich'; price = 19; stock = 90; image = 'https://picsum.photos/seed/showcase-food-053/640/480'; description = 'Whole wheat sandwich with creamy tuna filling.' }
            @{ categoryId = 1; name = 'Orange Yogurt Cup'; price = 13; stock = 100; image = 'https://picsum.photos/seed/showcase-food-054/640/480'; description = 'Bright citrus yogurt cup with fruit pieces.' }
        )
    }
    @{
        username = 'merchant_showcase_06'
        password = '123456'
        phone = '13910010006'
        name = 'Soymilk Bakery Lab'
        address = 'Art Plaza Corner'
        businessHours = '08:30-21:00'
        category = 'Cafe'
        description = 'Soft cakes, bread and coffee for a cozy afternoon break.'
        avatar = 'https://picsum.photos/seed/showcase-merchant-06/480/360'
        tags = 'bakery,coffee,dessert'
        minDeliveryFee = 12
        deliveryFee = 2
        deliveryRadius = 3
        products = @(
            @{ categoryId = 2; name = 'Soymilk Cream Cake'; price = 22; stock = 70; image = 'https://picsum.photos/seed/showcase-cafe-061/640/480'; description = 'Smooth cream cake with a mellow soy aroma.' }
            @{ categoryId = 2; name = 'Sea Salt Latte'; price = 18; stock = 120; image = 'https://picsum.photos/seed/showcase-cafe-062/640/480'; description = 'Creamy latte with a lightly salty finish.' }
            @{ categoryId = 2; name = 'Butter Croissant'; price = 12; stock = 90; image = 'https://picsum.photos/seed/showcase-cafe-063/640/480'; description = 'Flaky croissant that pairs perfectly with coffee.' }
            @{ categoryId = 2; name = 'Matcha Cream Puff'; price = 14; stock = 60; image = 'https://picsum.photos/seed/showcase-cafe-064/640/480'; description = 'Light matcha filling tucked into a crisp shell.' }
        )
    }
    @{
        username = 'merchant_showcase_07'
        password = '123456'
        phone = '13910010007'
        name = 'Berry Yogurt Club'
        address = 'Central Living Plaza'
        businessHours = '10:00-22:00'
        category = 'Cafe'
        description = 'Fruit yogurt, tea and light snacks for a bright and fresh menu.'
        avatar = 'https://picsum.photos/seed/showcase-merchant-07/480/360'
        tags = 'yogurt,fruit tea,fresh'
        minDeliveryFee = 15
        deliveryFee = 2
        deliveryRadius = 3
        products = @(
            @{ categoryId = 2; name = 'Berry Yogurt Cup'; price = 20; stock = 90; image = 'https://picsum.photos/seed/showcase-cafe-071/640/480'; description = 'Strawberries and blueberries layered with yogurt.' }
            @{ categoryId = 2; name = 'Mango Pomelo Yogurt'; price = 19; stock = 85; image = 'https://picsum.photos/seed/showcase-cafe-072/640/480'; description = 'A fresh mango and pomelo flavor combination.' }
            @{ categoryId = 2; name = 'Grape Iced Tea'; price = 16; stock = 110; image = 'https://picsum.photos/seed/showcase-cafe-073/640/480'; description = 'Cold fruit tea with a strong grape note.' }
            @{ categoryId = 2; name = 'Peach Bagel'; price = 13; stock = 70; image = 'https://picsum.photos/seed/showcase-cafe-074/640/480'; description = 'Soft bagel with a sweet peach-inspired filling.' }
        )
    }
    @{
        username = 'merchant_showcase_08'
        password = '123456'
        phone = '13910010008'
        name = 'Cloud Pastry House'
        address = 'West Gate Shared Hall'
        businessHours = '09:00-21:30'
        category = 'Cafe'
        description = 'Pretty cakes, tarts and coffee designed to look great on the page.'
        avatar = 'https://picsum.photos/seed/showcase-merchant-08/480/360'
        tags = 'cake,pastry,handmade'
        minDeliveryFee = 20
        deliveryFee = 3
        deliveryRadius = 4
        products = @(
            @{ categoryId = 2; name = 'Strawberry Cloud Cake'; price = 26; stock = 55; image = 'https://picsum.photos/seed/showcase-cafe-081/640/480'; description = 'Layered strawberry cake with soft cream and bright color.' }
            @{ categoryId = 2; name = 'Earl Grey Roll'; price = 18; stock = 65; image = 'https://picsum.photos/seed/showcase-cafe-082/640/480'; description = 'Tea-flavored cake roll with a clean finish.' }
            @{ categoryId = 2; name = 'Hazelnut Chocolate Tart'; price = 21; stock = 60; image = 'https://picsum.photos/seed/showcase-cafe-083/640/480'; description = 'Rich chocolate tart with roasted hazelnut notes.' }
            @{ categoryId = 2; name = 'Caramel Iced Americano'; price = 15; stock = 100; image = 'https://picsum.photos/seed/showcase-cafe-084/640/480'; description = 'Bittersweet coffee with a touch of caramel.' }
        )
    }
)

foreach ($merchant in $seedMerchants) {
    Ensure-Merchant -Merchant $merchant
}

$publicMerchants = Invoke-RestMethod -Method Get -Uri "$base/merchants?page=1&size=50"

$summary = [ordered]@{
    addedMerchantTemplates = $seedMerchants.Count
    currentMerchantCount   = (@($publicMerchants.data.data)).Count
    merchantNames          = ($seedMerchants | ForEach-Object { $_.name })
    totalSeedProducts      = (($seedMerchants | ForEach-Object { $_.products.Count }) | Measure-Object -Sum).Sum
}

Write-Host (ConvertTo-Json $summary -Depth 6)
