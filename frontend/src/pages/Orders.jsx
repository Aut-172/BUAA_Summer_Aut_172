import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import {
    formatDateTime,
    formatMoney,
    formatStatusText,
    getStatusTone,
    isConsumerOrderCancelable,
    isConsumerOrderCompletable,
    isConsumerOrderPayable
} from '../utils/format'

const FILTERS = ['全部', '待支付', '待接单', '配送中', '已完成', '已取消']

export default function Orders() {
    const { api, notify } = useSession()
    const [orders, setOrders] = useState([])
    const [filter, setFilter] = useState('全部')
    const [paymentsMap, setPaymentsMap] = useState({})
    const [loading, setLoading] = useState(true)
    const [busyId, setBusyId] = useState(null)

    async function loadOrders() {
        setLoading(true)
        try {
            const data = await api.orders.list()
            setOrders(Array.isArray(data) ? data : [])
        } catch (error) {
            notify(error.message || '加载订单失败', 'danger')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadOrders()
    }, [])

    async function handlePay(orderId) {
        const orderKey = String(orderId)
        setBusyId(orderKey)
        try {
            await api.orders.pay(orderId, { payMethod: 'ALIPAY' })
            notify('订单支付成功', 'success')
            await loadOrders()
        } catch (error) {
            notify(error.message || '支付失败', 'danger')
        } finally {
            setBusyId(null)
        }
    }

    async function handleCancel(orderId) {
        const orderKey = String(orderId)
        setBusyId(orderKey)
        try {
            await api.orders.cancel(orderId)
            notify('订单已取消', 'success')
            await loadOrders()
        } catch (error) {
            notify(error.message || '取消失败', 'danger')
        } finally {
            setBusyId(null)
        }
    }

    async function handleComplete(orderId) {
        const orderKey = String(orderId)
        setBusyId(orderKey)
        try {
            await api.orders.complete(orderId)
            notify('订单已确认完成', 'success')
            await loadOrders()
        } catch (error) {
            notify(error.message || '确认收货失败', 'danger')
        } finally {
            setBusyId(null)
        }
    }

    async function togglePayments(orderId) {
        const orderKey = String(orderId)

        if (paymentsMap[orderKey]) {
            setPaymentsMap((current) => {
                const next = { ...current }
                delete next[orderKey]
                return next
            })
            return
        }

        try {
            const data = await api.orders.getPayments(orderId)
            setPaymentsMap((current) => ({
                ...current,
                [orderKey]: Array.isArray(data) ? data : []
            }))
        } catch (error) {
            notify(error.message || '获取支付记录失败', 'danger')
        }
    }

    const visibleOrders = filter === '全部'
        ? orders
        : orders.filter((item) => formatStatusText(item.status) === filter)

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">我的订单</h1>
                    <p className="section-subtitle">这里会直接调用订单支付、取消、完成、配送详情和支付记录接口。</p>
                </div>
            </div>

            <div className="tabs">
                {FILTERS.map((item) => (
                    <button
                        key={item}
                        className={`tab ${filter === item ? 'active' : ''}`}
                        type="button"
                        onClick={() => setFilter(item)}
                    >
                        {item}
                    </button>
                ))}
            </div>

            {loading ? (
                <div className="panel empty-state">正在加载订单...</div>
            ) : visibleOrders.length === 0 ? (
                <div className="panel empty-state">
                    当前筛选下没有订单，<Link className="text-link" to="/">去首页下单</Link>
                </div>
            ) : (
                <div className="stack">
                    {visibleOrders.map((order) => {
                        const orderKey = String(order.id)
                        const payments = paymentsMap[orderKey]

                        return (
                            <article className="panel order-card" key={orderKey}>
                                <div className="panel-head">
                                    <div>
                                        <h2 className="section-title">{order.merchant || '未知商家'}</h2>
                                        <p className="section-subtitle">订单号 {order.orderNo}</p>
                                    </div>
                                    <span className={`status-chip ${getStatusTone(order.status)}`}>{formatStatusText(order.status)}</span>
                                </div>

                                <div className="stack tight">
                                    <div className="summary-line"><span>下单时间</span><strong>{formatDateTime(order.createdAt)}</strong></div>
                                    <div className="summary-line"><span>收货地址</span><strong>{order.address || '未填写'}</strong></div>
                                    <div className="summary-line"><span>配送骑手</span><strong>{order.riderName || '暂未分配'}</strong></div>
                                    <div className="summary-total"><span>实付金额</span><strong>{formatMoney(order.total)}</strong></div>
                                </div>

                                <div className="stack tight">
                                    {(order.items || []).map((item) => (
                                        <div className="line-item" key={`${orderKey}-${item.productId}-${item.specLabel || 'default'}`}>
                                            <span>{item.name}{item.specLabel ? ` · ${item.specLabel}` : ''}</span>
                                            <strong>x{item.quantity}</strong>
                                        </div>
                                    ))}
                                </div>

                                {(order.timeline || []).length > 0 ? (
                                    <div className="timeline">
                                        {(order.timeline || []).map((item, index) => (
                                            <div className="timeline-item" key={`${orderKey}-${index}`}>
                                                <strong>{item.label}</strong>
                                                <span>{item.time}</span>
                                            </div>
                                        ))}
                                    </div>
                                ) : null}

                                <div className="card-actions wrap">
                                    {isConsumerOrderPayable(order.status) ? (
                                        <button
                                            className="btn primary small"
                                            type="button"
                                            disabled={busyId === orderKey}
                                            onClick={() => handlePay(order.id)}
                                        >
                                            立即支付
                                        </button>
                                    ) : null}

                                    {isConsumerOrderCancelable(order.status) ? (
                                        <button
                                            className="btn danger small"
                                            type="button"
                                            disabled={busyId === orderKey}
                                            onClick={() => handleCancel(order.id)}
                                        >
                                            取消订单
                                        </button>
                                    ) : null}

                                    {isConsumerOrderCompletable(order.status) ? (
                                        <button
                                            className="btn primary small"
                                            type="button"
                                            disabled={busyId === orderKey}
                                            onClick={() => handleComplete(order.id)}
                                        >
                                            确认收货
                                        </button>
                                    ) : null}

                                    <Link className="btn ghost small" to={`/delivery/${orderKey}`}>查看配送</Link>
                                    <button className="btn ghost small" type="button" onClick={() => togglePayments(order.id)}>
                                        {payments ? '收起支付记录' : '查看支付记录'}
                                    </button>
                                </div>

                                {payments ? (
                                    <div className="payment-list">
                                        {payments.length === 0 ? (
                                            <p className="helper">该订单还没有支付记录。</p>
                                        ) : payments.map((payment) => (
                                            <div className="line-item" key={String(payment.id)}>
                                                <span>{payment.payMethod} · {formatStatusText(payment.status)}</span>
                                                <strong>{formatMoney(payment.amount)} · {formatDateTime(payment.payTime)}</strong>
                                            </div>
                                        ))}
                                    </div>
                                ) : null}
                            </article>
                        )
                    })}
                </div>
            )}
        </section>
    )
}
