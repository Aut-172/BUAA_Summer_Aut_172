import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { FALLBACK_PRODUCT_IMAGE, normalizeImageSrc } from '../utils/demoImages'
import { formatMoney, groupCartItems } from '../utils/format'

export default function Cart() {
    const { api, notify } = useSession()
    const [items, setItems] = useState([])
    const [loading, setLoading] = useState(true)
    const [busyId, setBusyId] = useState(null)

    async function loadCart() {
        setLoading(true)
        try {
            const data = await api.user.getCart()
            setItems(data || [])
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadCart()
    }, [])

    async function changeQuantity(item, quantity) {
        setBusyId(item.id)
        try {
            await api.user.updateCartQuantity(item.id, quantity)
            await loadCart()
        } catch (error) {
            notify(error.message || '更新数量失败', 'danger')
        } finally {
            setBusyId(null)
        }
    }

    async function removeItem(itemId) {
        setBusyId(itemId)
        try {
            await api.user.deleteCart(itemId)
            await loadCart()
        } catch (error) {
            notify(error.message || '删除失败', 'danger')
        } finally {
            setBusyId(null)
        }
    }

    async function clearCart() {
        try {
            await api.user.clearCart()
            setItems([])
            notify('购物车已清空', 'success')
        } catch (error) {
            notify(error.message || '清空失败', 'danger')
        }
    }

    const groups = groupCartItems(items)
    const totalAmount = groups.reduce((sum, group) => sum + group.subtotal, 0)

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">购物车</h1>
                </div>
                <button className="btn ghost small" type="button" onClick={clearCart} disabled={items.length === 0}>
                    清空购物车
                </button>
            </div>

            {loading ? (
                <div className="panel empty-state">正在加载购物车...</div>
            ) : items.length === 0 ? (
                <div className="panel empty-state">
                    购物车还是空的。<Link className="text-link" to="/">去首页挑选商品</Link>
                </div>
            ) : (
                <div className="split-grid cart-layout">
                    <div className="stack">
                        {groups.map((group) => (
                            <section className="panel" key={group.merchantId}>
                                <div className="panel-head">
                                    <div>
                                        <h2 className="section-title">{group.merchantName}</h2>
                                        <p className="section-subtitle">该商家商品会生成一笔独立订单。</p>
                                    </div>
                                    <Link className="btn primary small" to={`/checkout?merchantId=${group.merchantId}`}>
                                        结算该商家
                                    </Link>
                                </div>

                                <div className="stack">
                                    {group.items.map((item) => (
                                        <article className="cart-item-card" key={item.id}>
                                            <img
                                                className="cart-thumb"
                                                src={normalizeImageSrc(item.image, FALLBACK_PRODUCT_IMAGE)}
                                                alt={item.name}
                                            />
                                            <div className="stack tight grow">
                                                <div className="card-line">
                                                    <strong>{item.name}</strong>
                                                    <span className="price-text">{formatMoney(item.subtotal)}</span>
                                                </div>
                                                <p className="muted-text">
                                                    单价 {formatMoney(item.price)}
                                                    {item.specLabel ? ` · 规格 ${item.specLabel}` : ''}
                                                </p>
                                                <div className="card-actions">
                                                    <div className="quantity-box">
                                                        <button
                                                            className="btn ghost small"
                                                            type="button"
                                                            onClick={() => changeQuantity(item, item.quantity - 1)}
                                                            disabled={busyId === item.id}
                                                        >
                                                            -
                                                        </button>
                                                        <span>{item.quantity}</span>
                                                        <button
                                                            className="btn ghost small"
                                                            type="button"
                                                            onClick={() => changeQuantity(item, item.quantity + 1)}
                                                            disabled={busyId === item.id}
                                                        >
                                                            +
                                                        </button>
                                                    </div>

                                                    <button
                                                        className="btn danger small"
                                                        type="button"
                                                        onClick={() => removeItem(item.id)}
                                                        disabled={busyId === item.id}
                                                    >
                                                        删除
                                                    </button>
                                                </div>
                                            </div>
                                        </article>
                                    ))}
                                </div>
                            </section>
                        ))}
                    </div>

                    <aside className="panel summary-card">
                        <h2 className="section-title">汇总</h2>
                        <div className="summary-line"><span>商家分组</span><strong>{groups.length}</strong></div>
                        <div className="summary-line"><span>商品条目</span><strong>{items.length}</strong></div>
                        <div className="summary-total"><span>购物车小计</span><strong>{formatMoney(totalAmount)}</strong></div>
                        <p className="helper">
                            配送费和优惠会在结算页按商家实时计算。
                        </p>
                    </aside>
                </div>
            )}
        </section>
    )
}
