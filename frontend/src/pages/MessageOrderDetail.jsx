import React, { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { formatDateTime, formatMoney, formatStatusText, getStatusTone } from '../utils/format'

export default function MessageOrderDetail() {
    const { orderId } = useParams()
    const [searchParams] = useSearchParams()
    const { api, notify } = useSession()
    const [order, setOrder] = useState(null)
    const [loading, setLoading] = useState(true)
    const [message, setMessage] = useState('')
    const backTo = searchParams.toString() ? `/messages?${searchParams.toString()}` : '/messages'

    useEffect(() => {
        async function loadOrder() {
            setLoading(true)
            try {
                const data = await api.messages.getOrder(orderId)
                setOrder(data || null)
                setMessage('')
            } catch (error) {
                setOrder(null)
                setMessage(error.message || '订单详情加载失败')
                notify(error.message || '订单详情加载失败', 'danger')
            } finally {
                setLoading(false)
            }
        }

        loadOrder()
    }, [api, orderId])

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">订单详情</h1>
                    <p className="section-subtitle">来自当前会话关联订单。</p>
                </div>
                <Link className="btn ghost small" to={backTo}>返回会话</Link>
            </div>

            {loading ? (
                <div className="panel empty-state">正在加载订单详情...</div>
            ) : !order ? (
                <div className="panel empty-state">{message || '订单不存在。'}</div>
            ) : (
                <div className="split-grid">
                    <section className="panel">
                        <div className="panel-head">
                            <div>
                                <h2 className="section-title">{order.orderNo}</h2>
                                <p className="section-subtitle">下单时间 {formatDateTime(order.createdAt)}</p>
                            </div>
                            <span className={`status-chip ${getStatusTone(order.status)}`}>{formatStatusText(order.status)}</span>
                        </div>

                        <div className="stack tight">
                            <div className="summary-line"><span>商家</span><strong>{order.merchant || '未知商家'}</strong></div>
                            <div className="summary-line"><span>收货地址</span><strong>{order.address || '未填写'}</strong></div>
                            <div className="summary-line"><span>配送骑手</span><strong>{order.riderName || '暂未分配'}</strong></div>
                            <div className="summary-line"><span>配送费</span><strong>{formatMoney(order.deliveryFee)}</strong></div>
                            <div className="summary-line"><span>优惠</span><strong>{formatMoney(order.discount)}</strong></div>
                            <div className="summary-total"><span>实付金额</span><strong>{formatMoney(order.total)}</strong></div>
                        </div>
                    </section>

                    <section className="panel">
                        <div className="panel-head">
                            <div>
                                <h2 className="section-title">商品明细</h2>
                                <p className="section-subtitle">共 {(order.items || []).length} 项</p>
                            </div>
                        </div>

                        <div className="stack tight">
                            {(order.items || []).length === 0 ? (
                                <div className="empty-state">暂无商品明细。</div>
                            ) : (order.items || []).map((item) => (
                                <div className="line-item" key={`${item.productId}-${item.specLabel || 'default'}`}>
                                    <span>{item.name}{item.specLabel ? ` · ${item.specLabel}` : ''}</span>
                                    <strong>{formatMoney(item.price)} x{item.quantity}</strong>
                                </div>
                            ))}
                        </div>
                    </section>

                    <section className="panel">
                        <div className="panel-head">
                            <div>
                                <h2 className="section-title">订单时间线</h2>
                                <p className="section-subtitle">展示订单关键状态。</p>
                            </div>
                        </div>

                        {(order.timeline || []).length === 0 ? (
                            <div className="empty-state">当前还没有时间线。</div>
                        ) : (
                            <div className="timeline">
                                {(order.timeline || []).map((item, index) => (
                                    <div className="timeline-item" key={`${index}-${item.label}`}>
                                        <strong>{item.label}</strong>
                                        <span>{item.time}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </section>
                </div>
            )}
        </section>
    )
}
