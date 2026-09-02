const orderStatusMap = {
    pending_payment: '待支付',
    pending_accept: '待接单',
    delivering: '配送中',
    completed: '已完成',
    cancelled: '已取消',
    pending_use: '待使用'
}

const commonStatusMap = {
    active: '正常',
    pending: '待审核',
    frozen: '已冻结',
    released: '可领取',
    unused: '未使用',
    used: '已使用',
    expired: '已过期',
    locked: '已锁定',
    SUCCESS: '成功',
    PENDING: '处理中',
    FAIL: '失败'
}

const roleMap = {
    consumer: '消费者',
    merchant: '商家',
    rider: '骑手',
    admin: '管理员'
}

function normalizeStatusCode(status) {
    if (!status) {
        return ''
    }

    const text = String(status).trim()
    const matchedOrderStatus = Object.entries(orderStatusMap).find(([, label]) => label === text)
    if (matchedOrderStatus) {
        return matchedOrderStatus[0]
    }

    const matchedCommonStatus = Object.entries(commonStatusMap).find(([, label]) => label === text)
    if (matchedCommonStatus) {
        return matchedCommonStatus[0]
    }

    return text
}

export function formatMoney(value) {
    const amount = Number(value || 0)
    return `￥${amount.toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    })}`
}

export function formatDateTime(value) {
    if (!value) {
        return '未记录'
    }

    const text = String(value)
    const normalized = text.includes('T') ? text : text.replace(' ', 'T')
    const date = new Date(normalized)

    if (Number.isNaN(date.getTime())) {
        return text.replace('T', ' ')
    }

    return new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    }).format(date).replaceAll('/', '-')
}

export function formatStatusText(status) {
    const normalized = normalizeStatusCode(status)
    return orderStatusMap[normalized] || commonStatusMap[normalized] || normalized || '未知状态'
}

export function getStatusTone(status) {
    const normalized = normalizeStatusCode(status)

    if (normalized === 'completed' || normalized === 'active' || normalized === 'SUCCESS' || normalized === 'unused') {
        return 'success'
    }

    if (normalized === 'pending_payment' || normalized === 'pending_accept' || normalized === 'pending' || normalized === 'PENDING' || normalized === 'released') {
        return 'warning'
    }

    if (normalized === 'delivering') {
        return 'info'
    }

    if (normalized === 'cancelled' || normalized === 'frozen' || normalized === 'FAIL' || normalized === 'expired') {
        return 'danger'
    }

    return 'muted'
}

export function formatRole(role) {
    return roleMap[role] || role || '访客'
}

export function normalizeTags(tags) {
    if (Array.isArray(tags)) {
        return tags.filter(Boolean)
    }

    if (typeof tags === 'string') {
        return tags.split(',').map((item) => item.trim()).filter(Boolean)
    }

    return []
}

export function groupCartItems(items = []) {
    const groups = new Map()

    items.forEach((item) => {
        const key = String(item.merchantId || 'unknown')
        const subtotal = Number(item.subtotal || Number(item.price || 0) * Number(item.quantity || 0))

        if (!groups.has(key)) {
            groups.set(key, {
                merchantId: item.merchantId,
                merchantName: item.merchantName || `商家 ${item.merchantId}`,
                items: [],
                subtotal: 0
            })
        }

        const group = groups.get(key)
        group.items.push(item)
        group.subtotal += subtotal
    })

    return Array.from(groups.values())
}

export function buildAddressLabel(address) {
    if (!address) {
        return ''
    }

    return `${address.name || '收货人'} ${address.phone || ''} ${address.detail || ''}`.trim()
}

export function isConsumerOrderPayable(status) {
    return normalizeStatusCode(status) === 'pending_payment'
}

export function isConsumerOrderCancelable(status) {
    const normalized = normalizeStatusCode(status)
    return normalized === 'pending_payment' || normalized === 'pending_accept'
}

export function isConsumerOrderCompletable(status) {
    return normalizeStatusCode(status) === 'delivering'
}
