import React, { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { formatMoney, normalizeTags } from '../utils/format'

function MerchantResultCard({ merchant }) {
    const tags = normalizeTags(merchant.tags)
    const products = merchant.products || []

    return (
        <article className="merchant-card merchant-card-pro panel">
            <div className="merchant-header">
                <img
                    className="merchant-avatar"
                    src={merchant.avatar || 'https://picsum.photos/seed/search-store/120/120'}
                    alt={merchant.name}
                />
                <div className="merchant-meta">
                    <h3>{merchant.name}</h3>
                    <p>{merchant.description || '这家店正在准备更多店铺介绍。'}</p>
                    <div className="badge-row">
                        <span className="badge info">{merchant.category || '未分类'}</span>
                        <span className="badge success">{merchant.sales || `月售 ${merchant.monthlySales || 0}`}</span>
                        <span className="badge warning">{merchant.fee || `配送 ${formatMoney(merchant.deliveryFee)}`}</span>
                        <span className="badge muted">{merchant.distance || '距离未知'}</span>
                    </div>
                </div>
            </div>

            {tags.length > 0 ? (
                <div className="chip-row">
                    {tags.map((tag) => <span className="mini-chip" key={tag}>{tag}</span>)}
                </div>
            ) : null}

            {products.length > 0 ? (
                <div className="mini-product-grid">
                    {products.slice(0, 4).map((product) => (
                        <div className="mini-product" key={product.id}>
                            <img src={product.image || 'https://picsum.photos/seed/search-product/120/120'} alt={product.name} />
                            <strong>{product.name}</strong>
                            <span>{formatMoney(product.price)}</span>
                        </div>
                    ))}
                </div>
            ) : (
                <p className="muted-text">暂无可展示商品。</p>
            )}

            <div className="card-actions">
                <Link className="btn primary small" to={`/merchants/${merchant.id}`}>进入店铺</Link>
                <span className="helper">评分 {merchant.rating || '暂无'}</span>
            </div>
        </article>
    )
}

export default function Search() {
    const { api } = useSession()
    const [searchParams, setSearchParams] = useSearchParams()
    const [categories, setCategories] = useState([])
    const [results, setResults] = useState([])
    const [recommendations, setRecommendations] = useState([])
    const [keyword, setKeyword] = useState(searchParams.get('keyword') || '')
    const [category, setCategory] = useState(searchParams.get('category') || '')
    const [sort, setSort] = useState(searchParams.get('sort') || 'rating')
    const [loading, setLoading] = useState(true)

    const hasQuery = useMemo(() => {
        return Boolean((searchParams.get('keyword') || '').trim() || (searchParams.get('category') || '').trim())
    }, [searchParams])

    async function loadData(params = searchParams) {
        setLoading(true)
        try {
            const query = {
                keyword: params.get('keyword') || undefined,
                category: params.get('category') || undefined,
                sort: params.get('sort') || 'rating'
            }

            const [categoryList, searchResult, recommendResult] = await Promise.all([
                api.public.getCategories(),
                api.public.search(query),
                api.public.recommend()
            ])

            setCategories(Array.isArray(categoryList) ? categoryList : [])
            setResults(Array.isArray(searchResult) ? searchResult : [])
            setRecommendations(Array.isArray(recommendResult) ? recommendResult : [])
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        setKeyword(searchParams.get('keyword') || '')
        setCategory(searchParams.get('category') || '')
        setSort(searchParams.get('sort') || 'rating')
        loadData(searchParams)
    }, [searchParams])

    function handleSubmit(event) {
        event.preventDefault()
        const next = {}
        if (keyword.trim()) {
            next.keyword = keyword.trim()
        }
        if (category) {
            next.category = category
        }
        if (sort) {
            next.sort = sort
        }
        setSearchParams(next)
    }

    const primaryList = hasQuery ? results : recommendations
    const secondaryList = hasQuery ? recommendations : results

    return (
        <section className="page search-page">
            <div className="panel search-hero">
                <div>
                    <p className="eyebrow">Search & Recommend</p>
                    <h1 className="hero-title">搜索店铺、标签和商品</h1>
                    <p className="hero-copy">输入想吃的菜品或口味，系统会返回仍在营业状态的匹配商家。</p>
                </div>
                <form className="toolbar search-toolbar" onSubmit={handleSubmit}>
                    <input
                        className="input"
                        value={keyword}
                        onChange={(event) => setKeyword(event.target.value)}
                        placeholder="商家名、标签或商品名"
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
                    <select
                        className="select"
                        value={sort}
                        onChange={(event) => setSort(event.target.value)}
                    >
                        <option value="rating">评分优先</option>
                        <option value="sales">销量优先</option>
                    </select>
                    <button className="btn primary" type="submit">搜索</button>
                </form>
            </div>

            <section className="page-section">
                <div className="panel-head">
                    <div>
                        <h2 className="section-title">{hasQuery ? '搜索结果' : '推荐商家'}</h2>
                        <p className="section-subtitle">
                            {hasQuery ? `共找到 ${results.length} 家匹配商家` : '根据评分、销量和距离权重生成的推荐列表'}
                        </p>
                    </div>
                </div>

                {loading ? (
                    <div className="panel empty-state">正在加载商家...</div>
                ) : primaryList.length === 0 ? (
                    <div className="panel empty-state">没有找到符合条件的 active 商家。</div>
                ) : (
                    <div className="merchant-grid">
                        {primaryList.map((merchant) => (
                            <MerchantResultCard merchant={merchant} key={merchant.id} />
                        ))}
                    </div>
                )}
            </section>

            {secondaryList.length > 0 ? (
                <section className="page-section">
                    <div className="panel-head">
                        <div>
                            <h2 className="section-title">{hasQuery ? '也可以看看' : '全部可搜索商家'}</h2>
                            <p className="section-subtitle">
                                {hasQuery ? '推荐接口返回的其他 active 商家' : '搜索接口返回的 active 商家全集'}
                            </p>
                        </div>
                    </div>
                    <div className="merchant-grid">
                        {secondaryList.slice(0, 8).map((merchant) => (
                            <MerchantResultCard merchant={merchant} key={`secondary-${merchant.id}`} />
                        ))}
                    </div>
                </section>
            ) : null}
        </section>
    )
}
