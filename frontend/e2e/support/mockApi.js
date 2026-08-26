const categories = [
    { id: 1, name: '快餐' },
    { id: 2, name: '饮品' }
]

const merchants = [
    {
        id: 10,
        name: '桂香米粉',
        description: '热汤米粉和小吃',
        category: '快餐',
        tags: '米粉,夜宵',
        minDeliveryFee: 15,
        deliveryFee: 2,
        monthlySales: 88,
        rating: 4.8,
        products: [
            { id: 101, name: '牛肉米粉', price: 18 }
        ]
    }
]

function ok(data) {
    return {
        code: 200,
        message: 'success',
        data
    }
}

async function fulfillJson(route, payload) {
    await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(payload)
    })
}

export async function mockPublicApi(page) {
    await page.route('**/api/categories', (route) => fulfillJson(route, ok(categories)))
    await page.route('**/api/merchants?**', (route) => fulfillJson(route, {
        code: 200,
        message: 'success',
        data: merchants,
        total: merchants.length,
        page: 1,
        pageSize: 12
    }))
    await page.route('**/api/search?**', (route) => fulfillJson(route, ok(merchants)))
    await page.route('**/api/recommend**', (route) => fulfillJson(route, ok([])))
}

export async function mockLoginApi(page) {
    await page.route('**/api/captcha', (route) => fulfillJson(route, ok({
        key: 'captcha-key',
        image: 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=='
    })))
    await page.route('**/api/auth/login', (route) => fulfillJson(route, ok({
        accessToken: 'consumer-token',
        user: {
            id: 1,
            username: 'demo',
            nickname: '演示用户',
            phone: '13800000001',
            role: 'consumer'
        }
    })))
}
