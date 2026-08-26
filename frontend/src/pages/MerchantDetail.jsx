import React, { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import ReviewImageGallery, { LightboxImage } from '../components/ReviewImageGallery'
import { useSession } from '../utils/ApiProvider'
import { formatDateTime, formatMoney, normalizeTags } from '../utils/format'

export default function MerchantDetail() {
    const { merchantId } = useParams()
    const navigate = useNavigate()
    const location = useLocation()
    const { api, isAuthenticated, notify, role } = useSession()
    const [merchant, setMerchant] = useState(null)
    const [reviews, setReviews] = useState([])
    const [selectedSpecs, setSelectedSpecs] = useState({})
    const [addingId, setAddingId] = useState(null)
    const [loading, setLoading] = useState(true)
    const [reviewsLoading, setReviewsLoading] = useState(true)

    async function loadMerchant() {
        setLoading(true)
        setReviewsLoading(true)
        try {
            const data = await api.public.getMerchant(merchantId)
            setMerchant(data)
            try {
                const reviewData = await api.reviews.getMerchant(merchantId)
                setReviews(Array.isArray(reviewData) ? reviewData : [])
            } catch (error) {
                setReviews([])
                notify(error.message || '加载评价失败', 'danger')
            } finally {
                setReviewsLoading(false)
            }
        } finally {
            setLoading(false)
            setReviewsLoading(false)
        }
    }

    useEffect(() => {
        loadMerchant()
    }, [merchantId])

    function getSingleSpecOptions(product) {
        if ((product.specGroups || []).length !== 1) {
            return []
        }

        return product.specGroups?.[0]?.specs || []
    }

    function resolveSpecLabel(product) {
        const options = getSingleSpecOptions(product)
        if (options.length === 0) {
            return null
        }

        return selectedSpecs[product.id] || options[0]?.name || null
    }

    async function handleAddToCart(product) {
        if (!isAuthenticated) {
            navigate('/login', { state: { from: location.pathname } })
            return
        }

        if (role !== 'consumer') {
            notify('只有消费者账号可以加入购物车', 'warning')
            return
        }

        setAddingId(product.id)
        try {
            await api.user.addCart({
                merchantId: merchant.id,
                productId: product.id,
                quantity: 1,
                specLabel: resolveSpecLabel(product)
            })
            notify(`${product.name} 已加入购物车`, 'success')
        } catch (error) {
            notify(error.message || '加入购物车失败', 'danger')
        } finally {
            setAddingId(null)
        }
    }

    if (loading) {
        return <section className="page"><div className="panel empty-state">正在加载商家详情...</div></section>
    }

    if (!merchant) {
        return <section className="page"><div className="panel empty-state">商家不存在或已下线。</div></section>
    }

    const tags = normalizeTags(merchant.tags)
    const averageRating = reviews.length === 0
        ? Number(merchant.rating || 0)
        : reviews.reduce((sum, review) => sum + Number(review.rating || 0), 0) / reviews.length

    return (
        <section className="page">
            <div className="panel merchant-hero">
                <div className="merchant-header">
                    <img
                        className="merchant-avatar large"
                        src={merchant.avatar || 'https://picsum.photos/seed/default-store/160/160'}
                        alt={merchant.name}
                    />
                    <div className="merchant-meta">
                        <h1>{merchant.name}</h1>
                        <p>{merchant.description || '暂无商家介绍'}</p>
                        <div className="badge-row">
                            <span className="badge info">{merchant.category || '未分类'}</span>
                            <span className="badge success">评分 {averageRating.toFixed(1)}</span>
                            <span className="badge success">起送 {formatMoney(merchant.minDeliveryFee)}</span>
                            <span className="badge warning">配送 {formatMoney(merchant.deliveryFee)}</span>
                            <span className="badge muted">月售 {merchant.monthlySales || 0}</span>
                        </div>
                        <div className="detail-meta">
                            <span>{merchant.address || '暂无地址'}</span>
                            <span>{merchant.businessHours || '营业时间未设置'}</span>
                        </div>
                    </div>
                </div>

                <div className="card-actions">
                    <Link className="btn secondary" to="/cart">去购物车</Link>
                    <a className="btn ghost" href="#reviews">查看评价</a>
                    <Link className="btn ghost" to="/">返回首页</Link>
                </div>
            </div>

            {tags.length > 0 ? (
                <div className="chip-row section-spacer">
                    {tags.map((tag) => <span className="mini-chip" key={tag}>{tag}</span>)}
                </div>
            ) : null}

            <div className="stack">
                {(merchant.categoryList || []).map((category) => (
                    <section className="panel" key={category.id}>
                        <div className="panel-head">
                            <div>
                                <h2 className="section-title">{category.name}</h2>
                                <p className="section-subtitle">这里只展示后端真实返回的商品字段，不再拼接前端假数据。</p>
                            </div>
                        </div>

                        <div className="product-grid">
                            {(category.products || []).map((product) => {
                                const oneGroupOptions = getSingleSpecOptions(product)
                                const hasComplexSpecs = (product.specGroups || []).length > 1

                                return (
                                    <article className="product-card panel" key={product.id}>
                                        <LightboxImage
                                            className="product-image-large"
                                            src={product.image || 'https://picsum.photos/seed/default-product/360/240'}
                                            alt={product.name}
                                            buttonClassName="product-image-button"
                                        />
                                        <div className="stack tight">
                                            <div className="card-line">
                                                <h3>{product.name}</h3>
                                                <span className="price-text">{formatMoney(product.price)}</span>
                                            </div>
                                            <p className="muted-text">{product.description || '暂无商品描述'}</p>
                                            <div className="badge-row">
                                                <span className="badge muted">库存 {product.stock ?? 0}</span>
                                                <span className="badge info">月售 {product.monthlySales || 0}</span>
                                            </div>

                                            {oneGroupOptions.length > 0 ? (
                                                <label className="form-row">
                                                    <span>可选规格</span>
                                                    <select
                                                        className="select"
                                                        value={selectedSpecs[product.id] || oneGroupOptions[0]?.name || ''}
                                                        onChange={(event) => setSelectedSpecs((current) => ({
                                                            ...current,
                                                            [product.id]: event.target.value
                                                        }))}
                                                    >
                                                        {oneGroupOptions.map((option) => (
                                                            <option key={option.id} value={option.name}>
                                                                {option.name}
                                                                {option.extraPrice ? ` (+${formatMoney(option.extraPrice).replace('￥', '')})` : ''}
                                                            </option>
                                                        ))}
                                                    </select>
                                                </label>
                                            ) : null}

                                            {hasComplexSpecs ? (
                                                <p className="helper">
                                                    该商品存在多组规格，当前后端仅接收单个 `specLabel`，为保证链路稳定，此页面按基础款加入购物车。
                                                </p>
                                            ) : null}

                                            <div className="card-actions">
                                                <button
                                                    className="btn primary small"
                                                    type="button"
                                                    disabled={addingId === product.id || product.stock === 0}
                                                    onClick={() => handleAddToCart(product)}
                                                >
                                                    {addingId === product.id ? '加入中...' : product.stock === 0 ? '已售罄' : '加入购物车'}
                                                </button>
                                            </div>
                                        </div>
                                    </article>
                                )
                            })}
                        </div>
                    </section>
                ))}
            </div>

            <section className="panel" id="reviews">
                <div className="panel-head">
                    <div>
                        <h2 className="section-title">消费者评价</h2>
                        <p className="section-subtitle">共 {reviews.length} 条评价，来自已完成订单。</p>
                    </div>
                    <span className="badge warning">{averageRating.toFixed(1)} 分</span>
                </div>

                {reviewsLoading ? (
                    <div className="empty-state">正在加载评价...</div>
                ) : reviews.length === 0 ? (
                    <div className="empty-state">当前还没有消费者评价。</div>
                ) : (
                    <div className="review-list">
                        {reviews.map((review) => (
                            <article className="review-card" key={String(review.id)}>
                                <div className="panel-head compact">
                                    <div>
                                        <strong>{review.userName || '匿名用户'}</strong>
                                        <p className="section-subtitle">{review.productName || '订单商品'} · {formatDateTime(review.createTime)}</p>
                                    </div>
                                    <span className="badge warning">{review.rating || 0}/5</span>
                                </div>
                                <p className="review-content">{review.content || '用户未填写文字评价。'}</p>
                                {(review.images || []).length > 0 ? (
                                    <ReviewImageGallery images={review.images} alt="评价图片" />
                                ) : null}
                            </article>
                        ))}
                    </div>
                )}
            </section>
        </section>
    )
}
