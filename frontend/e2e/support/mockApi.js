const image = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw=='

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

function ok(data, extra = {}) {
    return {
        code: 200,
        message: 'success',
        data,
        ...extra
    }
}

function pageResult(items) {
    return ok(items, {
        total: items.length,
        page: 1,
        pageSize: 20
    })
}

async function fulfillJson(route, payload, status = 200) {
    await route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(payload)
    })
}

function parseJson(request) {
    try {
        return request.postDataJSON()
    } catch {
        return {}
    }
}

function buildCartItem(state, payload) {
    const product = state.products.find((item) => Number(item.id) === Number(payload.productId))
    const merchant = state.merchant
    const quantity = Number(payload.quantity || 1)

    return {
        id: state.nextCartId++,
        merchantId: merchant.id,
        merchantName: merchant.name,
        productId: product.id,
        name: product.name,
        price: product.price,
        quantity,
        subtotal: product.price * quantity,
        specLabel: payload.specLabel || '标准',
        image: product.image
    }
}

function toOrderItem(cartItem) {
    return {
        productId: cartItem.productId,
        name: cartItem.name,
        price: cartItem.price,
        quantity: cartItem.quantity,
        specLabel: cartItem.specLabel,
        image: cartItem.image,
        reviewed: false
    }
}

function nowText() {
    return '2026-08-26 12:00:00'
}

function makeOrder(overrides = {}) {
    return {
        id: overrides.id,
        orderNo: overrides.orderNo,
        userId: 1,
        merchantId: 10,
        merchant: '桂香米粉',
        status: overrides.status || 'pending_payment',
        address: overrides.address || '宿舍 3 号楼 302 室',
        deliveryFee: 2,
        discount: overrides.discount || 0,
        total: overrides.total || 20,
        totalAmount: overrides.total || 20,
        actualAmount: overrides.total || 20,
        createdAt: overrides.createdAt || nowText(),
        createTime: overrides.createdAt || nowText(),
        paidAt: overrides.paidAt || null,
        completedAt: overrides.completedAt || null,
        riderId: overrides.riderId || null,
        riderName: overrides.riderName || '',
        riderPhone: overrides.riderPhone || '',
        couponId: overrides.couponId || null,
        items: overrides.items || [
            { productId: 101, name: '牛肉米粉', price: 18, quantity: 1, specLabel: '标准', reviewed: false }
        ],
        timeline: overrides.timeline || [{ label: '已下单', time: nowText() }]
    }
}

function createBusinessState() {
    const products = [
        {
            id: 101,
            name: '牛肉米粉',
            categoryId: 1,
            price: 18,
            stock: 30,
            image,
            description: '热汤鲜香，适合快速下单。',
            status: 'active',
            monthlySales: 56,
            specGroups: [
                {
                    id: 1,
                    name: '份量',
                    specs: [
                        { id: 1001, name: '标准', extraPrice: 0 },
                        { id: 1002, name: '加粉', extraPrice: 3 }
                    ]
                }
            ]
        }
    ]

    const state = {
        nextCartId: 1000,
        nextOrderId: 950,
        nextProductId: 200,
        nextAddressId: 600,
        nextMessageId: 700,
        nextReviewId: 800,
        products,
        merchant: {
            id: 10,
            name: '桂香米粉',
            phone: '13800000011',
            address: '校园东区食堂一层',
            businessHours: '09:00-22:00',
            category: '快餐',
            description: '热汤米粉和小吃',
            avatar: image,
            tags: '米粉,夜宵',
            minDeliveryFee: 15,
            deliveryFee: 2,
            deliveryRadius: 3,
            monthlySales: 88,
            rating: 4.8,
            status: 'active'
        },
        consumerProfile: {
            id: 1,
            username: 'demo',
            nickname: '演示用户',
            phone: '13800000001',
            avatar: image,
            status: 'active',
            role: 'consumer'
        },
        riderProfile: {
            id: 20,
            name: '一号骑手',
            phone: '13800000021',
            serviceArea: '校园东区',
            status: 'active'
        },
        addresses: [
            { id: 501, name: '演示用户', phone: '13800000001', detail: '宿舍 3 号楼 302 室', isDefault: true }
        ],
        favoriteMerchants: [],
        cart: [],
        availableCoupons: [
            {
                id: 301,
                title: '新人满减券',
                description: '满 20 减 5',
                threshold: 20,
                discount: 5,
                status: 'released',
                expireAt: '2026-12-31 23:59:59'
            }
        ],
        mineCoupons: [],
        payments: {
            900: [{ id: 401, payMethod: 'ALIPAY', status: 'SUCCESS', amount: 20, payTime: nowText() }]
        },
        reviews: [
            { id: 801, orderId: 899, productId: 101, userName: '同学 A', productName: '牛肉米粉', rating: 5, content: '汤底很香。', images: [], createTime: nowText() }
        ],
        messages: [
            { id: 701, senderId: 10, senderType: 'merchant', content: '订单马上出餐', createTime: nowText(), orderId: 900 }
        ],
        adminUsers: [
            { id: 1, username: 'demo', nickname: '演示用户', phone: '13800000001', status: 'active' }
        ],
        adminMerchants: [
            { id: 10, name: '桂香米粉', category: '快餐', status: 'pending' }
        ],
        adminRiders: [
            { id: 20, name: '一号骑手', phone: '13800000021', status: 'pending' }
        ]
    }

    state.orders = [
        makeOrder({ id: 900, orderNo: 'NO20260826001', status: 'pending_payment', total: 20 }),
        makeOrder({ id: 901, orderNo: 'NO20260826002', status: 'pending_accept', total: 20 }),
        makeOrder({ id: 902, orderNo: 'NO20260826003', status: 'delivering', total: 20, riderId: 20, riderName: '一号骑手', riderPhone: '13800000021' }),
        makeOrder({ id: 903, orderNo: 'NO20260826004', status: 'completed', total: 20, riderId: 20, riderName: '一号骑手', riderPhone: '13800000021', completedAt: nowText() })
    ]
    state.merchantOrders = [
        makeOrder({ id: 910, orderNo: 'NO20260826010', status: '待取餐', riderId: 20, riderName: '一号骑手' })
    ]

    return state
}

function merchantDetail(state) {
    return {
        ...state.merchant,
        products: state.products,
        categoryList: categories.map((category) => ({
            ...category,
            products: state.products.filter((product) => Number(product.categoryId) === Number(category.id))
        }))
    }
}

function merchantCards(state) {
    return [
        {
            ...state.merchant,
            products: state.products.slice(0, 3)
        }
    ]
}

function normalizeMerchantOrderStatus(status) {
    if (status === '配送中') {
        return 'delivering'
    }
    if (status === '已完成') {
        return 'completed'
    }
    return status
}

function orderToTask(order) {
    return {
        id: order.id,
        orderNo: order.orderNo,
        merchantId: order.merchantId,
        merchant: order.merchant,
        userId: order.userId,
        items: order.items.map((item) => `${item.name} x${item.quantity}`).join('，'),
        pickup: '校园东区食堂一层',
        destination: order.address,
        status: order.status,
        total: order.total
    }
}

function threadList(state) {
    return [
        {
            targetId: 10,
            targetType: 'merchant',
            targetName: '桂香米粉',
            orderId: 900,
            orderNo: 'NO20260826001',
            unreadCount: 1,
            lastMessage: state.messages.at(-1)?.content || '暂无内容'
        }
    ]
}

export async function installBusinessScenarioMock(page) {
    const state = createBusinessState()

    await page.route('**/api/**', async (route) => {
        const request = route.request()
        const url = new URL(request.url())
        const path = url.pathname.replace(/^\/api/, '')
        const method = request.method().toUpperCase()
        const payload = parseJson(request)

        if (path === '/captcha') {
            return fulfillJson(route, ok({ key: 'captcha-key', image }))
        }

        if (method === 'POST' && path === '/auth/register') {
            return fulfillJson(route, ok({ registered: true, role: 'consumer' }))
        }
        if (method === 'POST' && path === '/auth/merchant/register') {
            return fulfillJson(route, ok({ registered: true, role: 'merchant' }))
        }
        if (method === 'POST' && path === '/auth/rider/register') {
            return fulfillJson(route, ok({ registered: true, role: 'rider' }))
        }

        if (method === 'POST' && path === '/auth/login') {
            return fulfillJson(route, ok({ accessToken: 'consumer-token', user: state.consumerProfile }))
        }
        if (method === 'POST' && path === '/auth/merchant/login') {
            return fulfillJson(route, ok({ accessToken: 'merchant-token', user: { id: 10, username: 'merchant1', nickname: state.merchant.name, phone: state.merchant.phone, role: 'merchant', merchantId: 10 } }))
        }
        if (method === 'POST' && path === '/auth/rider/login') {
            return fulfillJson(route, ok({ accessToken: 'rider-token', user: { id: 20, username: 'rider01', nickname: state.riderProfile.name, phone: state.riderProfile.phone, role: 'rider', riderId: 20 } }))
        }
        if (method === 'POST' && path === '/auth/admin/login') {
            return fulfillJson(route, ok({ accessToken: 'admin-token', user: { id: 99, username: 'gl1', nickname: '管理员', phone: '13800000099', role: 'admin' } }))
        }

        if (path === '/categories') {
            return fulfillJson(route, ok(categories))
        }
        if (path === '/merchants') {
            return fulfillJson(route, pageResult(merchantCards(state)))
        }
        if (path === '/search') {
            const keyword = url.searchParams.get('keyword') || ''
            const results = keyword && !state.merchant.name.includes(keyword) && !state.merchant.tags.includes(keyword)
                ? []
                : merchantCards(state)
            return fulfillJson(route, ok(results))
        }
        if (path === '/recommend') {
            return fulfillJson(route, ok(merchantCards(state)))
        }
        if (path === '/merchants/10') {
            return fulfillJson(route, ok(merchantDetail(state)))
        }
        if (path === '/merchants/10/reviews') {
            return fulfillJson(route, ok(state.reviews))
        }
        if (path === '/merchants/10/rating') {
            return fulfillJson(route, ok({ rating: 4.9, count: state.reviews.length }))
        }
        if (path === '/products/101') {
            return fulfillJson(route, ok(state.products[0]))
        }
        if (path === '/products/101/reviews') {
            return fulfillJson(route, ok(state.reviews.filter((review) => review.productId === 101)))
        }

        if (path === '/dashboard') {
            return fulfillJson(route, ok({
                consumer: { orders: state.orders.length, coupons: state.mineCoupons.length },
                merchant: { todayOrders: state.orders.length, todayRevenue: 120, pendingOrders: 2 },
                rider: { todayDeliveries: 2, todayEarnings: 8, status: state.riderProfile.status }
            }))
        }

        if (path === '/user/profile') {
            if (method === 'PUT') {
                state.consumerProfile = { ...state.consumerProfile, ...payload }
            }
            return fulfillJson(route, ok(state.consumerProfile))
        }
        if (path === '/user/addresses') {
            if (method === 'POST') {
                if (payload.isDefault) {
                    state.addresses = state.addresses.map((item) => ({ ...item, isDefault: false }))
                }
                state.addresses.push({ id: state.nextAddressId++, ...payload })
                return fulfillJson(route, ok(true))
            }
            return fulfillJson(route, ok(state.addresses))
        }
        if (path.startsWith('/user/addresses/')) {
            const id = Number(path.split('/').at(-1))
            if (method === 'PUT') {
                if (payload.isDefault) {
                    state.addresses = state.addresses.map((item) => ({ ...item, isDefault: false }))
                }
                state.addresses = state.addresses.map((item) => item.id === id ? { ...item, ...payload, id } : item)
                return fulfillJson(route, ok(true))
            }
            if (method === 'DELETE') {
                state.addresses = state.addresses.filter((item) => item.id !== id)
                return fulfillJson(route, ok(true))
            }
        }
        if (path === '/user/favorites') {
            return fulfillJson(route, ok(state.favoriteMerchants))
        }
        if (path === '/user/favorites/10') {
            if (method === 'POST') {
                state.favoriteMerchants = [{ favoriteId: 1, merchantId: 10, ...state.merchant }]
                return fulfillJson(route, ok(true))
            }
            if (method === 'DELETE') {
                state.favoriteMerchants = []
                return fulfillJson(route, ok(true))
            }
            return fulfillJson(route, ok(state.favoriteMerchants.length > 0))
        }
        if (path === '/user/cart') {
            if (method === 'POST') {
                state.cart.push(buildCartItem(state, payload))
                return fulfillJson(route, ok(true))
            }
            if (method === 'DELETE') {
                state.cart = []
                return fulfillJson(route, ok(true))
            }
            return fulfillJson(route, ok(state.cart))
        }
        if (path.startsWith('/user/cart/')) {
            const id = Number(path.split('/').at(-1))
            if (method === 'PUT') {
                const quantity = Number(url.searchParams.get('quantity') || 0)
                state.cart = quantity <= 0
                    ? state.cart.filter((item) => item.id !== id)
                    : state.cart.map((item) => item.id === id ? { ...item, quantity, subtotal: item.price * quantity } : item)
                return fulfillJson(route, ok(true))
            }
            if (method === 'DELETE') {
                state.cart = state.cart.filter((item) => item.id !== id)
                return fulfillJson(route, ok(true))
            }
        }

        if (path === '/coupons') {
            return fulfillJson(route, ok(state.mineCoupons))
        }
        if (path === '/coupons/available') {
            return fulfillJson(route, ok(state.availableCoupons))
        }
        if (path === '/coupons/301/claim' && method === 'POST') {
            if (!state.mineCoupons.some((item) => item.id === 301)) {
                state.mineCoupons.push({ ...state.availableCoupons[0], status: 'unused' })
            }
            return fulfillJson(route, ok(true))
        }

        if (path === '/checkout' && method === 'POST') {
            const selectedItems = state.cart.filter((item) => Number(item.merchantId) === Number(payload.merchantId))
            const goodsTotal = selectedItems.reduce((sum, item) => sum + item.subtotal, 0)
            const coupon = state.mineCoupons.find((item) => Number(item.id) === Number(payload.couponId))
            if (coupon) {
                coupon.status = 'locked'
            }
            const order = makeOrder({
                id: state.nextOrderId++,
                orderNo: `NO20260826${state.nextOrderId}`,
                status: 'pending_payment',
                address: payload.address,
                couponId: coupon?.id || null,
                discount: coupon?.discount || 0,
                total: goodsTotal + state.merchant.deliveryFee - (coupon?.discount || 0),
                items: selectedItems.map(toOrderItem)
            })
            state.orders.unshift(order)
            state.cart = state.cart.filter((item) => Number(item.merchantId) !== Number(payload.merchantId))
            return fulfillJson(route, ok(order))
        }
        if (path === '/orders') {
            return fulfillJson(route, ok(state.orders))
        }
        if (/^\/orders\/\d+$/.test(path)) {
            const id = Number(path.split('/').at(-1))
            return fulfillJson(route, ok(state.orders.find((order) => order.id === id) || null))
        }
        if (/^\/orders\/\d+\/pay$/.test(path) && method === 'POST') {
            const id = Number(path.split('/')[2])
            const order = state.orders.find((item) => item.id === id)
            if (order) {
                order.status = 'pending_accept'
                order.paidAt = nowText()
                state.payments[id] = [{ id: 500 + id, payMethod: payload.payMethod || 'ALIPAY', status: 'SUCCESS', amount: order.total, payTime: nowText() }]
            }
            return fulfillJson(route, ok(true))
        }
        if (/^\/orders\/\d+\/cancel$/.test(path) && method === 'POST') {
            const id = Number(path.split('/')[2])
            const order = state.orders.find((item) => item.id === id)
            if (order) {
                order.status = 'cancelled'
                const coupon = state.mineCoupons.find((item) => item.id === order.couponId)
                if (coupon) {
                    coupon.status = 'unused'
                }
            }
            return fulfillJson(route, ok(true))
        }
        if (/^\/orders\/\d+\/complete$/.test(path) && method === 'POST') {
            const id = Number(path.split('/')[2])
            const order = state.orders.find((item) => item.id === id)
            if (order) {
                order.status = 'completed'
                order.completedAt = nowText()
                const coupon = state.mineCoupons.find((item) => item.id === order.couponId)
                if (coupon) {
                    coupon.status = 'used'
                }
            }
            return fulfillJson(route, ok(true))
        }
        if (/^\/orders\/\d+\/payments$/.test(path)) {
            const id = Number(path.split('/')[2])
            return fulfillJson(route, ok(state.payments[id] || []))
        }
        if (/^\/delivery\/\d+$/.test(path)) {
            const id = Number(path.split('/').at(-1))
            const order = state.orders.find((item) => item.id === id)
            return fulfillJson(route, ok(order ? {
                orderId: id,
                status: order.status,
                riderName: order.riderName,
                riderPhone: order.riderPhone,
                eta: order.status === 'completed' ? '已送达' : '预计 20 分钟送达',
                timeline: [
                    { label: '已下单', time: nowText() },
                    { label: order.status === 'completed' ? '已完成' : '配送中', time: nowText() }
                ]
            } : null))
        }

        if (path === '/reviews' && method === 'POST') {
            const order = state.orders.find((item) => Number(item.id) === Number(payload.orderId))
            ;(payload.items || []).forEach((item) => {
                state.reviews.push({
                    id: state.nextReviewId++,
                    orderId: Number(payload.orderId),
                    productId: item.productId,
                    productName: order?.items?.find((orderItem) => orderItem.productId === item.productId)?.name || '订单商品',
                    userName: state.consumerProfile.nickname,
                    rating: item.rating,
                    content: item.content,
                    images: item.images || [],
                    createTime: nowText()
                })
            })
            if (order) {
                order.items = order.items.map((item) => ({ ...item, reviewed: true }))
            }
            return fulfillJson(route, ok(true))
        }
        if (path === '/uploads/images' && method === 'POST') {
            return fulfillJson(route, ok([image]))
        }

        if (path === '/messages/threads') {
            return fulfillJson(route, ok(threadList(state)))
        }
        if (path === '/messages/unread-count') {
            return fulfillJson(route, ok({ count: 1 }))
        }
        if (/^\/messages\/orders\/\d+$/.test(path)) {
            const id = Number(path.split('/').at(-1))
            return fulfillJson(route, ok(state.orders.find((order) => order.id === id) || null))
        }
        if (path === '/messages') {
            if (method === 'POST') {
                state.messages.push({ id: state.nextMessageId++, senderId: 1, senderType: 'user', content: payload.content, createTime: nowText(), orderId: Number(payload.orderId) })
                return fulfillJson(route, ok(true))
            }
            return fulfillJson(route, ok(state.messages.filter((message) => String(message.orderId) === String(url.searchParams.get('orderId')))))
        }

        if (path === '/merchant/profile') {
            if (method === 'PUT') {
                state.merchant = { ...state.merchant, ...payload }
            }
            return fulfillJson(route, ok(state.merchant))
        }
        if (path === '/merchant/products') {
            if (method === 'POST') {
                state.products.push({ ...payload, id: state.nextProductId++, image: payload.image || image, monthlySales: 0, specGroups: [] })
                return fulfillJson(route, ok(true))
            }
            if (method === 'PUT') {
                state.products = state.products.map((item) => item.id === payload.id ? { ...item, ...payload } : item)
                return fulfillJson(route, ok(true))
            }
            return fulfillJson(route, ok(state.products))
        }
        if (path.startsWith('/merchant/products/') && method === 'DELETE') {
            const id = Number(path.split('/').at(-1))
            state.products = state.products.filter((item) => item.id !== id)
            return fulfillJson(route, ok(true))
        }
        if (path === '/merchant/orders') {
            return fulfillJson(route, ok([...state.merchantOrders, ...state.orders]))
        }
        if (/^\/merchant\/orders\/\d+$/.test(path) && method === 'PUT') {
            const id = Number(path.split('/').at(-1))
            const order = [...state.merchantOrders, ...state.orders].find((item) => item.id === id)
            if (order) {
                order.status = normalizeMerchantOrderStatus(payload.status)
            }
            return fulfillJson(route, ok(true))
        }

        if (path === '/rider/profile') {
            if (method === 'PUT') {
                state.riderProfile = { ...state.riderProfile, ...payload, name: payload.nickname || payload.name || state.riderProfile.name }
            }
            return fulfillJson(route, ok(state.riderProfile))
        }
        if (path === '/rider/tasks') {
            const available = state.orders.filter((order) => order.status === 'pending_accept' && !order.riderId).map(orderToTask)
            const assigned = state.orders.filter((order) => order.riderId === 20 && order.status === 'delivering').map(orderToTask)
            const completed = state.orders.filter((order) => order.riderId === 20 && order.status === 'completed').map(orderToTask)
            return fulfillJson(route, ok({ available, assigned, completed }))
        }
        if (/^\/rider\/tasks\/\d+$/.test(path) && method === 'PUT') {
            const id = Number(path.split('/').at(-1))
            const order = state.orders.find((item) => item.id === id)
            if (order) {
                if (payload.status === '待取餐') {
                    order.status = 'delivering'
                    order.riderId = 20
                    order.riderName = state.riderProfile.name
                    order.riderPhone = state.riderProfile.phone
                } else if (payload.status === '已完成') {
                    order.status = 'completed'
                    order.completedAt = nowText()
                }
            }
            return fulfillJson(route, ok(true))
        }

        if (path === '/admin/users') {
            return fulfillJson(route, pageResult(state.adminUsers))
        }
        if (path === '/admin/merchants') {
            return fulfillJson(route, pageResult(state.adminMerchants))
        }
        if (path === '/admin/riders') {
            return fulfillJson(route, pageResult(state.adminRiders))
        }
        if (path === '/admin/orders') {
            return fulfillJson(route, pageResult(state.orders.map((order) => ({ ...order, createTime: order.createdAt }))))
        }
        if (/^\/admin\/users\/\d+$/.test(path) && method === 'DELETE') {
            state.adminUsers = state.adminUsers.map((item) => ({ ...item, status: 'frozen' }))
            return fulfillJson(route, ok(true))
        }
        if (/^\/admin\/users\/\d+\/unfreeze$/.test(path) && method === 'PUT') {
            state.adminUsers = state.adminUsers.map((item) => ({ ...item, status: 'active' }))
            return fulfillJson(route, ok(true))
        }
        if (/^\/admin\/merchants\/\d+\/audit$/.test(path) && method === 'PUT') {
            state.adminMerchants = state.adminMerchants.map((item) => ({ ...item, status: payload.status || 'active' }))
            return fulfillJson(route, ok(true))
        }
        if (/^\/admin\/merchants\/\d+$/.test(path) && method === 'DELETE') {
            state.adminMerchants = state.adminMerchants.map((item) => ({ ...item, status: 'frozen' }))
            return fulfillJson(route, ok(true))
        }
        if (/^\/admin\/merchants\/\d+\/unfreeze$/.test(path) && method === 'PUT') {
            state.adminMerchants = state.adminMerchants.map((item) => ({ ...item, status: 'active' }))
            return fulfillJson(route, ok(true))
        }
        if (/^\/admin\/riders\/\d+\/audit$/.test(path) && method === 'PUT') {
            state.adminRiders = state.adminRiders.map((item) => ({ ...item, status: payload.status || 'active' }))
            return fulfillJson(route, ok(true))
        }
        if (/^\/admin\/riders\/\d+$/.test(path) && method === 'DELETE') {
            state.adminRiders = state.adminRiders.map((item) => ({ ...item, status: 'frozen' }))
            return fulfillJson(route, ok(true))
        }
        if (/^\/admin\/riders\/\d+\/unfreeze$/.test(path) && method === 'PUT') {
            state.adminRiders = state.adminRiders.map((item) => ({ ...item, status: 'active' }))
            return fulfillJson(route, ok(true))
        }

        return fulfillJson(route, { code: 404, message: `No mock for ${method} ${path}` }, 404)
    })

    return state
}

export async function loginAs(page, role = 'consumer') {
    await page.goto('/login')
    await page.getByRole('combobox').selectOption(role)
    await page.getByPlaceholder('输入图形验证码').fill('abcd')
    await page.getByRole('button', { name: '进入系统' }).click()
}

export async function fetchApi(page, path, init = {}) {
    return page.evaluate(async ({ path: apiPath, init: requestInit }) => {
        const response = await fetch(apiPath, requestInit)
        return response.json()
    }, { path, init })
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
        image
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
