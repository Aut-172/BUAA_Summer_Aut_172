import React, { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { FALLBACK_PRODUCT_IMAGE, FALLBACK_STORE_IMAGE, normalizeImageSrc } from '../utils/demoImages'
import { formatMoney, normalizeTags } from '../utils/format'

const FLOW_STEPS = [
    { title: '挑选喜欢的店铺', detail: '按分类和关键词快速找到想吃的商家。' },
    { title: '下单并完成支付', detail: '把商品加入购物车，确认地址后即可结算。' },
    { title: '等待配送送达', detail: '下单后可以在订单页查看状态并确认完成。' }
]

export default function Home() {
    const { api, role, isAuthenticated } = useSession()
    const navigate = useNavigate()
    const [categories, setCategories] = useState([])
    const [merchants, setMerchants] = useState([])
    const [keyword, setKeyword] = useState('')
    const [category, setCategory] = useState('')
    const [loading, setLoading] = useState(true)

    async function loadMerchants(nextKeyword = keyword, nextCategory = category) {
        setLoading(true)
        try {
            const [categoryList, merchantPage] = await Promise.all([
                api.public.getCategories(),
                api.public.getMerchants({
                    page: 1,
                    size: 12,
                    keyword: nextKeyword || undefined,
                    category: nextCategory || undefined
                })
            ])

            setCategories(Array.isArray(categoryList) ? categoryList : [])
            setMerchants(merchantPage?.items || [])
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadMerchants('', '')
    }, [])

    function handleSearch(event) {
        event.preventDefault()
        const params = new URLSearchParams()
        if (keyword.trim()) {
            params.set('keyword', keyword.trim())
        }
        if (category) {
            params.set('category', category)
        }
        navigate(`/search${params.toString() ? `?${params.toString()}` : ''}`)
    }

    const stats = useMemo(() => {
        const categoryCount = categories.length
        const merchantCount = merchants.length
        const featuredProducts = merchants.reduce((sum, merchant) => sum + (merchant.products?.length || 0), 0)

        return [
            { label: '在线商家', value: merchantCount, note: '覆盖多种口味和餐品选择' },
            { label: '分类标签', value: categoryCount, note: '找店更快，筛选更清晰' },
            { label: '精选商品', value: featuredProducts, note: '首页即可先看热门菜品' }
        ]
    }, [categories, merchants])

    const landingTarget = role === 'merchant'
        ? '/merchant-center'
        : role === 'rider'
            ? '/rider-center'
            : role === 'admin'
                ? '/admin-center'
                : '/'

    return (
        <section className="page">
            <div className="hero hero-enhanced">
                <div className="hero-card hero-primary panel">
                    <p className="eyebrow">Campus Service</p>
                    <h1 className="hero-title">想吃什么，几分钟内就能从附近店铺下单到送达。</h1>
                    <p className="hero-copy">
                        从早餐、简餐到夜宵，都可以在这里快速找到合适的商家。
                        收藏口味、填写地址、完成支付后，就能在订单页查看后续状态。
                    </p>
                    <div className="hero-actions">
                        <Link className="btn primary" to={isAuthenticated ? landingTarget : '/login'}>
                            {isAuthenticated ? '继续逛逛' : '登录 / 注册'}
                        </Link>
                        <a className="btn secondary" href="#merchant-list">浏览商家</a>
                    </div>
                    <div className="hero-points">
                        {FLOW_STEPS.map((step, index) => (
                            <div className="hero-point" key={step.title}>
                                <span>{String(index + 1).padStart(2, '0')}</span>
                                <div>
                                    <strong>{step.title}</strong>
                                    <small>{step.detail}</small>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                <div className="hero-side">
                    <div className="panel spotlight-card">
                        <p className="eyebrow">今日推荐</p>
                        <h2 className="section-title">午饭、晚饭还是夜宵，都能在这里快速找到合适的选择。</h2>
                        <p className="section-subtitle">
                            先按分类缩小范围，再打开店铺详情看商品和价格。下单后，订单状态会在订单页持续更新。
                        </p>
                        <div className="spotlight-tags">
                            <span className="mini-chip">快速下单</span>
                            <span className="mini-chip">多角色协同</span>
                            <span className="mini-chip">订单可管理</span>
                        </div>
                    </div>

                    <div className="stats-grid">
                        {stats.map((item) => (
                            <div className="stat-card panel" key={item.label}>
                                <span>{item.label}</span>
                                <strong>{item.value}</strong>
                                <small>{item.note}</small>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            <div className="panel toolbar-panel">
                <form className="toolbar" onSubmit={handleSearch}>
                    <input
                        className="input"
                        value={keyword}
                        onChange={(event) => setKeyword(event.target.value)}
                        placeholder="搜索商家名称、菜品风格或标签"
                    />
                    <select
                        className="select"
                        value={category}
                        onChange={(event) => setCategory(event.target.value)}
                    >
                        <option value="">全部分类</option>
                        {categories.map((item) => (
                            <option key={item.id} value={item.name}>{item.name}</option>
                        ))}
                    </select>
                    <button className="btn primary" type="submit">查找商家</button>
                </form>
            </div>

            <section id="merchant-list" className="page-section">
                <div className="panel-head">
                    <div>
                        <h2 className="section-title">附近热门商家</h2>
                        <p className="section-subtitle">看看大家最近都在点什么，选一家顺手下单。</p>
                    </div>
                </div>

                {loading ? (
                    <div className="panel empty-state">正在加载商家列表...</div>
                ) : merchants.length === 0 ? (
                    <div className="panel empty-state">当前筛选条件下还没有找到合适的商家。</div>
                ) : (
                    <div className="merchant-grid">
                        {merchants.map((merchant) => {
                            const tags = normalizeTags(merchant.tags)

                            return (
                                <article className="merchant-card merchant-card-pro panel" key={merchant.id}>
                                    <div className="merchant-header">
                                        <img
                                            className="merchant-avatar"
                                            src={normalizeImageSrc(merchant.avatar, FALLBACK_STORE_IMAGE)}
                                            alt={merchant.name}
                                        />
                                        <div className="merchant-meta">
                                            <h3>{merchant.name}</h3>
                                            <p>{merchant.description || '这家店正在准备更多店铺介绍。'}</p>
                                            <div className="badge-row">
                                                <span className="badge info">{merchant.category || '分类待更新'}</span>
                                                <span className="badge success">起送 {formatMoney(merchant.minDeliveryFee)}</span>
                                                <span className="badge warning">配送 {formatMoney(merchant.deliveryFee)}</span>
                                            </div>
                                        </div>
                                    </div>

                                    {tags.length > 0 ? (
                                        <div className="chip-row">
                                            {tags.map((tag) => <span className="mini-chip" key={tag}>{tag}</span>)}
                                        </div>
                                    ) : null}

                                    <div className="mini-product-grid">
                                        {(merchant.products || []).slice(0, 4).map((product) => (
                                            <div className="mini-product" key={product.id}>
                                                <img src={normalizeImageSrc(product.image, FALLBACK_PRODUCT_IMAGE)} alt={product.name} />
                                                <strong>{product.name}</strong>
                                                <span>{formatMoney(product.price)}</span>
                                            </div>
                                        ))}
                                    </div>

                                    <div className="card-actions">
                                        <Link className="btn primary small" to={`/merchants/${merchant.id}`}>进入店铺</Link>
                                        <span className="helper">月售 {merchant.monthlySales || 0} 单</span>
                                    </div>
                                </article>
                            )
                        })}
                    </div>
                )}
            </section>
        </section>
    )
}
