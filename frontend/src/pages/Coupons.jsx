import React, { useEffect, useState } from 'react'
import { useSession } from '../utils/ApiProvider'
import { formatDateTime, formatMoney, formatStatusText, getStatusTone } from '../utils/format'

export default function Coupons() {
    const { api, notify } = useSession()
    const [mine, setMine] = useState([])
    const [available, setAvailable] = useState([])
    const [claimingId, setClaimingId] = useState(null)
    const [loading, setLoading] = useState(true)

    async function loadCoupons() {
        setLoading(true)
        try {
            const [mineData, availableData] = await Promise.all([
                api.coupons.getMine(),
                api.coupons.getAvailable()
            ])

            setMine(mineData || [])
            setAvailable(availableData || [])
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadCoupons()
    }, [])

    async function handleClaim(couponId) {
        setClaimingId(couponId)
        try {
            await api.coupons.claim(couponId)
            notify('优惠券领取成功', 'success')
            await loadCoupons()
        } catch (error) {
            notify(error.message || '领取失败', 'danger')
        } finally {
            setClaimingId(null)
        }
    }

    const claimedIds = new Set((mine || []).map((item) => String(item.id)))

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">优惠券中心</h1>
                    <p className="section-subtitle">领取接口和我的优惠券列表都已经接通后端，不再保留前端假券包。</p>
                </div>
            </div>

            {loading ? (
                <div className="panel empty-state">正在加载优惠券...</div>
            ) : (
                <div className="split-grid">
                    <section className="panel">
                        <div className="panel-head">
                            <div>
                                <h2 className="section-title">可领取</h2>
                                <p className="section-subtitle">来源：`GET /api/coupons/available`</p>
                            </div>
                        </div>

                        <div className="stack">
                            {available.length === 0 ? (
                                <div className="empty-state">当前没有可领取优惠券。</div>
                            ) : available.map((coupon) => (
                                <article className="coupon-card panel" key={coupon.id}>
                                    <div className="card-line">
                                        <strong>{coupon.title}</strong>
                                        <span className={`status-chip ${getStatusTone(coupon.status)}`}>{formatStatusText(coupon.status)}</span>
                                    </div>
                                    <p className="muted-text">{coupon.description}</p>
                                    <div className="summary-line"><span>门槛</span><strong>{formatMoney(coupon.threshold)}</strong></div>
                                    <div className="summary-line"><span>面额</span><strong>{formatMoney(coupon.discount)}</strong></div>
                                    <div className="summary-line"><span>有效期</span><strong>{formatDateTime(coupon.expireAt)}</strong></div>
                                    <button
                                        className="btn primary small"
                                        type="button"
                                        disabled={claimingId === coupon.id || claimedIds.has(String(coupon.id))}
                                        onClick={() => handleClaim(coupon.id)}
                                    >
                                        {claimedIds.has(String(coupon.id)) ? '已领取' : claimingId === coupon.id ? '领取中...' : '立即领取'}
                                    </button>
                                </article>
                            ))}
                        </div>
                    </section>

                    <section className="panel">
                        <div className="panel-head">
                            <div>
                                <h2 className="section-title">我的优惠券</h2>
                                <p className="section-subtitle">来源：`GET /api/coupons`</p>
                            </div>
                        </div>

                        <div className="stack">
                            {mine.length === 0 ? (
                                <div className="empty-state">你还没有领取优惠券。</div>
                            ) : mine.map((coupon) => (
                                <article className="coupon-card panel" key={coupon.id}>
                                    <div className="card-line">
                                        <strong>{coupon.title}</strong>
                                        <span className={`status-chip ${getStatusTone(coupon.status)}`}>{formatStatusText(coupon.status)}</span>
                                    </div>
                                    <p className="muted-text">{coupon.description}</p>
                                    <div className="summary-line"><span>门槛</span><strong>{formatMoney(coupon.threshold)}</strong></div>
                                    <div className="summary-line"><span>面额</span><strong>{formatMoney(coupon.discount)}</strong></div>
                                    <div className="summary-line"><span>有效期</span><strong>{formatDateTime(coupon.expireAt)}</strong></div>
                                </article>
                            ))}
                        </div>
                    </section>
                </div>
            )}
        </section>
    )
}
