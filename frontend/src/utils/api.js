import axios from 'axios'

let authToken = null
let unauthorizedHandler = null

export class ApiError extends Error {
    constructor(message, code = 500, details = null) {
        super(message)
        this.name = 'ApiError'
        this.code = code
        this.details = details
    }
}

const client = axios.create({
    baseURL: '/api',
    timeout: 12000
})

export function setAuthToken(token) {
    authToken = token || null
}

export function setUnauthorizedHandler(handler) {
    unauthorizedHandler = handler || null
}

function toPageResult(payload) {
    return {
        items: Array.isArray(payload?.data) ? payload.data : [],
        total: Number(payload?.total || 0),
        page: Number(payload?.page || 1),
        pageSize: Number(payload?.pageSize || 0)
    }
}

function unwrapPayload(payload) {
    if (!payload || typeof payload !== 'object') {
        return payload
    }

    if (Object.prototype.hasOwnProperty.call(payload, 'code')) {
        if (payload.code !== 200) {
            throw new ApiError(payload.message || '请求失败', payload.code, payload)
        }

        if (
            Object.prototype.hasOwnProperty.call(payload, 'total') ||
            Object.prototype.hasOwnProperty.call(payload, 'page') ||
            Object.prototype.hasOwnProperty.call(payload, 'pageSize')
        ) {
            return toPageResult(payload)
        }

        return unwrapPayload(payload.data)
    }

    return payload
}

function toApiError(error) {
    const payload = error?.response?.data

    if (payload && typeof payload === 'object' && Object.prototype.hasOwnProperty.call(payload, 'code')) {
        return new ApiError(payload.message || '请求失败', payload.code, payload)
    }

    if (error?.code === 'ECONNABORTED') {
        return new ApiError('请求超时，请稍后重试', 408, error)
    }

    return new ApiError(
        error?.message || '网络异常，请检查后端服务是否已启动',
        error?.response?.status || 500,
        error
    )
}

async function request(config) {
    try {
        const response = await client.request({
            ...config,
            headers: {
                ...(config?.headers || {}),
                ...(authToken ? { Authorization: `Bearer ${authToken}` } : {})
            }
        })

        return unwrapPayload(response.data)
    } catch (error) {
        const apiError = toApiError(error)

        if (apiError.code === 401 && typeof unauthorizedHandler === 'function') {
            unauthorizedHandler(apiError)
        }

        throw apiError
    }
}

const api = {
    captcha: {
        get() {
            return request({ url: '/captcha' })
        }
    },

    auth: {
        login(role, payload) {
            const pathMap = {
                consumer: '/auth/login',
                merchant: '/auth/merchant/login',
                rider: '/auth/rider/login',
                admin: '/auth/admin/login'
            }

            return request({
                url: pathMap[role] || pathMap.consumer,
                method: 'post',
                data: payload
            })
        },

        register(role, payload) {
            const pathMap = {
                consumer: '/auth/register',
                merchant: '/auth/merchant/register',
                rider: '/auth/rider/register'
            }

            return request({
                url: pathMap[role] || pathMap.consumer,
                method: 'post',
                data: payload
            })
        }
    },

    public: {
        getHealth() {
            return request({ url: '/health' })
        },

        getCategories() {
            return request({ url: '/categories' })
        },

        getMerchants(params = {}) {
            return request({
                url: '/merchants',
                params
            })
        },

        getMerchant(id) {
            return request({
                url: `/merchants/${id}`
            })
        },

        search(params = {}) {
            return request({
                url: '/search',
                params
            })
        },

        recommend(params = {}) {
            return request({
                url: '/recommend',
                params
            })
        },

        getProduct(id) {
            return request({
                url: `/products/${id}`
            })
        }
    },

    dashboard: {
        getMine() {
            return request({ url: '/dashboard' })
        }
    },

    user: {
        getProfile() {
            return request({ url: '/user/profile' })
        },

        updateProfile(payload) {
            return request({
                url: '/user/profile',
                method: 'put',
                data: payload
            })
        },

        getAddresses() {
            return request({ url: '/user/addresses' })
        },

        addAddress(payload) {
            return request({
                url: '/user/addresses',
                method: 'post',
                data: payload
            })
        },

        updateAddress(id, payload) {
            return request({
                url: `/user/addresses/${id}`,
                method: 'put',
                data: payload
            })
        },

        deleteAddress(id) {
            return request({
                url: `/user/addresses/${id}`,
                method: 'delete'
            })
        },

        getCart() {
            return request({ url: '/user/cart' })
        },

        addCart(payload) {
            return request({
                url: '/user/cart',
                method: 'post',
                data: payload
            })
        },

        updateCartQuantity(id, quantity) {
            return request({
                url: `/user/cart/${id}`,
                method: 'put',
                params: { quantity }
            })
        },

        deleteCart(id) {
            return request({
                url: `/user/cart/${id}`,
                method: 'delete'
            })
        },

        clearCart() {
            return request({
                url: '/user/cart',
                method: 'delete'
            })
        }
    },

    coupons: {
        getMine() {
            return request({ url: '/coupons' })
        },

        getAvailable() {
            return request({ url: '/coupons/available' })
        },

        claim(id) {
            return request({
                url: `/coupons/${id}/claim`,
                method: 'post'
            })
        }
    },

    orders: {
        checkout(payload) {
            return request({
                url: '/checkout',
                method: 'post',
                data: payload
            })
        },

        list() {
            return request({ url: '/orders' })
        },

        getDetail(id) {
            return request({ url: `/orders/${id}` })
        },

        pay(id, payload = { payMethod: 'ALIPAY' }) {
            return request({
                url: `/orders/${id}/pay`,
                method: 'post',
                data: payload
            })
        },

        cancel(id) {
            return request({
                url: `/orders/${id}/cancel`,
                method: 'post'
            })
        },

        complete(id) {
            return request({
                url: `/orders/${id}/complete`,
                method: 'post'
            })
        },

        getPayments(id) {
            return request({
                url: `/orders/${id}/payments`
            })
        },

        getPayment(id) {
            return request({
                url: `/payments/${id}`
            })
        },

        getDelivery(id) {
            return request({
                url: `/delivery/${id}`
            })
        }
    },

    merchant: {
        getProfile() {
            return request({ url: '/merchant/profile' })
        },

        updateProfile(payload) {
            return request({
                url: '/merchant/profile',
                method: 'put',
                data: payload
            })
        },

        getProducts() {
            return request({ url: '/merchant/products' })
        },

        createProduct(payload) {
            return request({
                url: '/merchant/products',
                method: 'post',
                data: payload
            })
        },

        updateProduct(payload) {
            return request({
                url: '/merchant/products',
                method: 'put',
                data: payload
            })
        },

        deleteProduct(productId) {
            return request({
                url: `/merchant/products/${productId}`,
                method: 'delete'
            })
        },

        getOrders() {
            return request({ url: '/merchant/orders' })
        },

        updateOrder(id, payload) {
            return request({
                url: `/merchant/orders/${id}`,
                method: 'put',
                data: payload
            })
        }
    },

    rider: {
        getProfile() {
            return request({ url: '/rider/profile' })
        },

        getTasks() {
            return request({ url: '/rider/tasks' })
        },

        updateTask(id, payload) {
            return request({
                url: `/rider/tasks/${id}`,
                method: 'put',
                data: payload
            })
        },

        updateProfile(payload) {
            return request({
                url: '/rider/profile',
                method: 'put',
                data: payload
            })
        }
    },

    admin: {
        getUsers(params = {}) {
            return request({
                url: '/admin/users',
                params
            })
        },

        getMerchants(params = {}) {
            return request({
                url: '/admin/merchants',
                params
            })
        },

        getRiders(params = {}) {
            return request({
                url: '/admin/riders',
                params
            })
        },

        getOrders(params = {}) {
            return request({
                url: '/admin/orders',
                params
            })
        },

        auditMerchant(id, payload) {
            return request({
                url: `/admin/merchants/${id}/audit`,
                method: 'put',
                data: payload
            })
        },

        auditRider(id, payload) {
            return request({
                url: `/admin/riders/${id}/audit`,
                method: 'put',
                data: payload
            })
        },

        freezeUser(id) {
            return request({
                url: `/admin/users/${id}`,
                method: 'delete'
            })
        },

        unfreezeUser(id) {
            return request({
                url: `/admin/users/${id}/unfreeze`,
                method: 'put'
            })
        },

        freezeMerchant(id) {
            return request({
                url: `/admin/merchants/${id}`,
                method: 'delete'
            })
        },

        unfreezeMerchant(id) {
            return request({
                url: `/admin/merchants/${id}/unfreeze`,
                method: 'put'
            })
        },

        freezeRider(id) {
            return request({
                url: `/admin/riders/${id}`,
                method: 'delete'
            })
        },

        unfreezeRider(id) {
            return request({
                url: `/admin/riders/${id}/unfreeze`,
                method: 'put'
            })
        }
    }
}

export default api
