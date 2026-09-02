import React, { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { FALLBACK_PRODUCT_IMAGE, FALLBACK_STORE_IMAGE, normalizeImageSrc } from '../utils/demoImages'
import { formatMoney, normalizeTags } from '../utils/format'

const MERCHANT_PAGE_SIZE = 12

export default function Home() {
    const { api, role, isAuthenticated } = useSession()
    const navigate = useNavigate()
    const [categories, setCategories] = useState([])
    const [merchants, setMerchants] = useState([])
    const [keyword, setKeyword] = useState('')
    const [category, setCategory] = useState('')
    const [loading, setLoading] = useState(true)
    const [loadingMore, setLoadingMore] = useState(false)
    const [page, setPage] = useState(1)
    const [total, setTotal] = useState(0)
    const loadingRef = useRef(false)

    async function loadMerchants(nextKeyword = keyword, nextCategory = category, nextPage = 1, append = false) {
        if (loadingRef.current) {
            return
        }

        loadingRef.current = true
        if (append) {
            setLoadingMore(true)
        } else {
            setLoading(true)
        }
        try {
            const categoryPromise = append ? Promise.resolve(categories) : api.public.getCategories()
            const [categoryList, merchantPage] = await Promise.all([
                categoryPromise,
                api.public.getMerchants({
                    page: nextPage,
                    size: MERCHANT_PAGE_SIZE,
                    keyword: nextKeyword || undefined,
                    category: nextCategory || undefined
                })
            ])

            setCategories(Array.isArray(categoryList) ? categoryList : [])
            setMerchants((current) => append ? [...current, ...(merchantPage?.items || [])] : (merchantPage?.items || []))
            setPage(Number(merchantPage?.page || nextPage))
            setTotal(Number(merchantPage?.total || 0))
        } finally {
            loadingRef.current = false
            setLoading(false)
            setLoadingMore(false)
        }
    }

    useEffect(() => {
        loadMerchants('', '')
    }, [])

    const hasMoreMerchants = total > merchants.length

    useEffect(() => {
        function handleScroll() {
            if (!hasMoreMerchants || loading || loadingMore) {
                return
            }

            const scrollBottom = window.innerHeight + window.scrollY
            const triggerLine = document.documentElement.scrollHeight - 200
            if (scrollBottom >= triggerLine) {
                loadMerchants(keyword, category, page + 1, true)
            }
        }

        window.addEventListener('scroll', handleScroll)
        return () => window.removeEventListener('scroll', handleScroll)
    }, [hasMoreMerchants, loading, loadingMore, page, keyword, category])

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
                    <div className="hero-actions">
                        <Link className="btn primary" to={isAuthenticated ? landingTarget : '/login'}>
                            {isAuthenticated ? '继续逛逛' : '登录 / 注册'}
                        </Link>
                        <a className="btn secondary" href="#merchant-list">浏览商家</a>
                    </div>
                </div>

                <div className="hero-side">
                    <div className="panel spotlight-card">
                        <p className="eyebrow">今日推荐</p>
                        <h2 className="section-title">午饭、晚饭还是夜宵，都能在这里快速找到合适的选择。</h2>
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
                    <>
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
                                                loading="lazy"
                                                decoding="async"
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
                                                    <img src={normalizeImageSrc(product.image, FALLBACK_PRODUCT_IMAGE)} alt={product.name} loading="lazy" decoding="async" />
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
                        {loadingMore ? <div className="panel empty-state">正在加载更多商家...</div> : null}
                    </>
                )}
            </section>
        </section>
    )
}
