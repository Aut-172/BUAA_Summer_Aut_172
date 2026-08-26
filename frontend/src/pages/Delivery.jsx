import React, { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { formatStatusText, getStatusTone } from '../utils/format'

export default function Delivery() {
    const { orderId } = useParams()
    const { api } = useSession()
    const [delivery, setDelivery] = useState(null)
    const [loading, setLoading] = useState(true)
    const [message, setMessage] = useState('')
    const canReview = delivery && formatStatusText(delivery.status) === '已完成'

    useEffect(() => {
        async function loadDelivery() {
            setLoading(true)
            try {
                const data = await api.orders.getDelivery(orderId)
                setDelivery(data)
                setMessage('')
            } catch (error) {
                setDelivery(null)
                setMessage(error.message || '暂时无法获取配送信息')
            } finally {
                setLoading(false)
            }
        }

        loadDelivery()
    }, [api, orderId])

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">配送详情</h1>
                    <p className="section-subtitle">数据来源：`GET /api/delivery/{id}`</p>
                </div>
                <div className="card-actions">
                    {canReview ? <Link className="btn primary small" to={`/reviews/${orderId}`}>评价订单</Link> : null}
                    <Link className="btn ghost small" to="/orders">返回订单</Link>
                </div>
            </div>

            {loading ? (
                <div className="panel empty-state">正在加载配送信息...</div>
            ) : !delivery ? (
                <div className="panel empty-state">{message || '当前暂无配送信息。'}</div>
            ) : (
                <div className="split-grid">
                    <section className="panel">
                        <div className="panel-head">
                            <div>
                                <h2 className="section-title">当前状态</h2>
                                <p className="section-subtitle">订单 #{delivery.orderId}</p>
                            </div>
                            <span className={`status-chip ${getStatusTone(delivery.status)}`}>{formatStatusText(delivery.status)}</span>
                        </div>

                        <div className="stack tight">
                            <div className="summary-line"><span>骑手</span><strong>{delivery.riderName || '暂未分配'}</strong></div>
                            <div className="summary-line"><span>联系电话</span><strong>{delivery.riderPhone || '暂无'}</strong></div>
                            <div className="summary-line"><span>预计送达</span><strong>{delivery.eta || '待更新'}</strong></div>
                        </div>
                    </section>

                    <section className="panel">
                        <div className="panel-head">
                            <div>
                                <h2 className="section-title">配送时间线</h2>
                                <p className="section-subtitle">只展示后端返回的真实节点。</p>
                            </div>
                        </div>

                        {(delivery.timeline || []).length === 0 ? (
                            <div className="empty-state">当前还没有配送时间线。</div>
                        ) : (
                            <div className="timeline">
                                {delivery.timeline.map((item, index) => (
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
