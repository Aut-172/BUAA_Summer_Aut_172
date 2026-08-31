import React, { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { FALLBACK_PRODUCT_IMAGE } from '../utils/demoImages'
import { buildAddressLabel, formatMoney, groupCartItems } from '../utils/format'

export default function Checkout() {
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const { api, notify } = useSession()
    const [cartItems, setCartItems] = useState([])
    const [addresses, setAddresses] = useState([])
    const [coupons, setCoupons] = useState([])
    const [merchant, setMerchant] = useState(null)
    const [selectedAddressId, setSelectedAddressId] = useState('')
    const [manualAddress, setManualAddress] = useState('')
    const [selectedCouponId, setSelectedCouponId] = useState('')
    const [payMethod, setPayMethod] = useState('ALIPAY')
    const [submitting, setSubmitting] = useState(false)
    const [loading, setLoading] = useState(true)

    const merchantIdParam = searchParams.get('merchantId')
    const groupedItems = groupCartItems(cartItems)
    const currentGroup = useMemo(() => {
        if (groupedItems.length === 0) {
            return null
        }

        if (!merchantIdParam) {
            return groupedItems[0]
        }

        return groupedItems.find((item) => String(item.merchantId) === String(merchantIdParam)) || groupedItems[0]
    }, [groupedItems, merchantIdParam])

    async function loadBaseData() {
        setLoading(true)
        try {
            const [cart, addressList, mineCoupons] = await Promise.all([
                api.user.getCart(),
                api.user.getAddresses(),
                api.coupons.getMine()
            ])

            setCartItems(cart || [])
            setAddresses(addressList || [])
            setCoupons(mineCoupons || [])

            const defaultAddress = (addressList || []).find((item) => item.isDefault) || addressList?.[0]
            if (defaultAddress) {
                setSelectedAddressId(String(defaultAddress.id))
            }
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadBaseData()
    }, [])

    useEffect(() => {
        async function loadMerchant() {
            if (!currentGroup?.merchantId) {
                setMerchant(null)
                return
            }

            const data = await api.public.getMerchant(currentGroup.merchantId)
            setMerchant(data)
        }

        loadMerchant()
    }, [api.public, currentGroup?.merchantId])

    const selectedAddress = addresses.find((item) => String(item.id) === selectedAddressId)
    const goodsTotal = currentGroup?.subtotal || 0
    const deliveryFee = Number(merchant?.deliveryFee || 0)
    const applicableCoupons = (coupons || []).filter((item) => item.status === 'unused' && Number(item.threshold || 0) <= goodsTotal + deliveryFee)
    const selectedCoupon = applicableCoupons.find((item) => String(item.id) === selectedCouponId) || null
    const estimatedDiscount = Number(selectedCoupon?.discount || 0)
    const estimatedTotal = Math.max(goodsTotal + deliveryFee - estimatedDiscount, 0)

    async function handleSubmit(event) {
        event.preventDefault()

        if (!currentGroup) {
            notify('当前没有可结算的商品', 'warning')
            return
        }

        const finalAddress = manualAddress.trim() || selectedAddress?.detail || ''
        if (!finalAddress) {
            notify('请先选择一个地址，或者手动填写收货地址', 'warning')
            return
        }

        const selectedSavedAddress = !manualAddress.trim() ? selectedAddress : null

        setSubmitting(true)
        try {
            const order = await api.orders.checkout({
                merchantId: currentGroup.merchantId,
                addressId: selectedSavedAddress ? Number(selectedSavedAddress.id) : null,
                address: finalAddress,
                couponId: selectedCoupon ? selectedCoupon.id : null,
                items: currentGroup.items.map((item) => ({
                    productId: item.productId,
                    quantity: item.quantity,
                    specLabel: item.specLabel || null
                }))
            })

            await api.orders.pay(order.id, { payMethod })
            notify(`订单 ${order.orderNo} 已支付`, 'success')
            navigate('/orders')
        } catch (error) {
            notify(error.message || '提交订单失败', 'danger')
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">结算</h1>
                    <p className="section-subtitle">确认商品、收货地址、优惠券和支付方式。</p>
                </div>
                <Link className="btn ghost small" to="/cart">返回购物车</Link>
            </div>

            {loading ? (
                <div className="panel empty-state">正在加载结算信息...</div>
            ) : !currentGroup ? (
                <div className="panel empty-state">当前没有可结算的商品，请先去购物车选择一个商家。</div>
            ) : (
                <form className="split-grid checkout-layout" onSubmit={handleSubmit}>
                    <div className="stack">
                        <section className="panel">
                            <div className="panel-head">
                                <div>
                                    <h2 className="section-title">订单商品</h2>
                                    <p className="section-subtitle">{currentGroup.merchantName}</p>
                                </div>
                            </div>

                            <div className="stack">
                                {currentGroup.items.map((item) => (
                                    <div className="cart-item-card" key={item.id}>
                                        <img
                                            className="cart-thumb"
                                            src={item.image || FALLBACK_PRODUCT_IMAGE}
                                            alt={item.name}
                                        />
                                        <div className="stack tight grow">
                                            <div className="card-line">
                                                <strong>{item.name}</strong>
                                                <span className="price-text">{formatMoney(item.subtotal)}</span>
                                            </div>
                                            <p className="muted-text">
                                                数量 {item.quantity}
                                                {item.specLabel ? ` · 规格 ${item.specLabel}` : ''}
                                            </p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </section>

                        <section className="panel">
                            <div className="panel-head">
                                <div>
                                    <h2 className="section-title">收货地址</h2>
                                    <p className="section-subtitle">既支持已保存地址，也支持直接手填。</p>
                                </div>
                                <Link className="btn ghost small" to="/profile">管理地址</Link>
                            </div>

                            <div className="stack">
                                {addresses.length === 0 ? (
                                    <p className="helper">还没有保存的地址，可以直接在下面输入收货地址。</p>
                                ) : (
                                    <div className="stack">
                                        {addresses.map((address) => (
                                            <label className={`select-card ${String(address.id) === selectedAddressId ? 'active' : ''}`} key={address.id}>
                                                <input
                                                    type="radio"
                                                    name="address"
                                                    checked={String(address.id) === selectedAddressId}
                                                    onChange={() => {
                                                        setSelectedAddressId(String(address.id))
                                                        setManualAddress('')
                                                    }}
                                                />
                                                <div className="stack tight grow">
                                                    <strong>{buildAddressLabel(address)}</strong>
                                                    {address.isDefault ? <span className="mini-chip">默认地址</span> : null}
                                                </div>
                                            </label>
                                        ))}
                                    </div>
                                )}

                                <label className="form-row">
                                    <span>手动地址覆盖</span>
                                    <textarea
                                        className="textarea"
                                        rows="3"
                                        value={manualAddress}
                                        onChange={(event) => setManualAddress(event.target.value)}
                                        placeholder="例如：宿舍 3 号楼 302 室。填写后会优先使用这里的地址。"
                                    />
                                </label>
                            </div>
                        </section>

                        <section className="panel">
                            <div className="panel-head">
                                <div>
                                    <h2 className="section-title">优惠与支付</h2>
                                    <p className="section-subtitle">选择可用优惠券，并确认本次订单的支付方式。</p>
                                </div>
                            </div>

                            <div className="stack">
                                <label className="form-row">
                                    <span>可用优惠券</span>
                                    <select
                                        className="select"
                                        value={selectedCouponId}
                                        onChange={(event) => setSelectedCouponId(event.target.value)}
                                    >
                                        <option value="">不使用优惠券</option>
                                        {applicableCoupons.map((coupon) => (
                                            <option key={coupon.id} value={coupon.id}>
                                                {coupon.title} - 满 {formatMoney(coupon.threshold)} 减 {formatMoney(coupon.discount)}
                                            </option>
                                        ))}
                                    </select>
                                </label>

                                <label className="form-row">
                                    <span>支付方式</span>
                                    <select
                                        className="select"
                                        value={payMethod}
                                        onChange={(event) => setPayMethod(event.target.value)}
                                    >
                                        <option value="ALIPAY">支付宝</option>
                                        <option value="WECHAT">微信支付</option>
                                    </select>
                                </label>
                            </div>
                        </section>
                    </div>

                    <aside className="panel summary-card">
                        <h2 className="section-title">支付摘要</h2>
                        <div className="summary-line"><span>商品金额</span><strong>{formatMoney(goodsTotal)}</strong></div>
                        <div className="summary-line"><span>预计配送费</span><strong>{formatMoney(deliveryFee)}</strong></div>
                        <div className="summary-line"><span>预计优惠</span><strong>{formatMoney(estimatedDiscount)}</strong></div>
                        <div className="summary-total"><span>预计应付</span><strong>{formatMoney(estimatedTotal)}</strong></div>
                        <p className="helper">
                            最终金额以实际结算结果为准，提交后将进入支付流程。
                        </p>
                        <button className="btn primary" type="submit" disabled={submitting}>
                            {submitting ? '提交并支付中...' : '确认下单并支付'}
                        </button>
                    </aside>
                </form>
            )}
        </section>
    )
}
