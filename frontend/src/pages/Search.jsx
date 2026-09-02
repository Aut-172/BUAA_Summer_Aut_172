import React, { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { FALLBACK_PRODUCT_IMAGE, FALLBACK_STORE_IMAGE, normalizeImageSrc } from '../utils/demoImages'
import { formatMoney, normalizeTags } from '../utils/format'

function buildSuggestions(keyword, merchants) {
    const query = keyword.trim().toLowerCase()
    if (!query) {
        return []
    }

    const seen = new Set()
    const suggestions = []

    function addSuggestion(type, value) {
        const label = String(value || '').trim()
        const key = `${type}:${label.toLowerCase()}`

        if (!label || seen.has(key) || !label.toLowerCase().includes(query)) {
            return
        }

        seen.add(key)
        suggestions.push({ type, label })
    }

    merchants.forEach((merchant) => {
        addSuggestion('商家', merchant.name)
        ;(merchant.products || []).forEach((product) => addSuggestion('商品', product.name))
    })

    return suggestions.slice(0, 5)
}

function MerchantResultCard({ merchant }) {
    const tags = normalizeTags(merchant.tags)
    const products = merchant.products || []

    return (
        <article className="merchant-card merchant-card-pro panel">
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
                            <img src={normalizeImageSrc(product.image, FALLBACK_PRODUCT_IMAGE)} alt={product.name} loading="lazy" decoding="async" />
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
        submitKeyword(keyword)
    }

    function submitKeyword(nextKeyword) {
        const next = {}
        const trimmedKeyword = nextKeyword.trim()
        if (trimmedKeyword) {
            next.keyword = trimmedKeyword
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
    const suggestionSource = useMemo(() => {
        const merchantMap = new Map()
        ;[...results, ...recommendations].forEach((merchant) => {
            if (merchant?.id && !merchantMap.has(merchant.id)) {
                merchantMap.set(merchant.id, merchant)
            }
        })
        return Array.from(merchantMap.values())
    }, [results, recommendations])
    const suggestions = useMemo(() => buildSuggestions(keyword, suggestionSource), [keyword, suggestionSource])

    return (
        <section className="page search-page">
            <div className="panel search-hero">
                <div>
                    <p className="eyebrow">Search & Recommend</p>
                    <h1 className="hero-title">搜索店铺、标签和商品</h1>
                </div>
                <form className="toolbar search-toolbar" onSubmit={handleSubmit}>
                    <div className="search-suggest">
                        <input
                            className="input"
                            value={keyword}
                            onChange={(event) => setKeyword(event.target.value)}
                            placeholder="商家名、标签或商品名"
                        />
                        {suggestions.length > 0 ? (
                            <div className="suggestion-list">
                                {suggestions.map((suggestion) => (
                                    <button
                                        className="suggestion-item"
                                        key={`${suggestion.type}-${suggestion.label}`}
                                        type="button"
                                        onMouseDown={(event) => event.preventDefault()}
                                        onClick={() => {
                                            setKeyword(suggestion.label)
                                            submitKeyword(suggestion.label)
                                        }}
                                    >
                                        <span>{suggestion.type}</span>
                                        <strong>{suggestion.label}</strong>
                                    </button>
                                ))}
                            </div>
                        ) : null}
                    </div>
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
                        {hasQuery ? <p className="section-subtitle">共找到 {results.length} 家匹配商家</p> : null}
                    </div>
                </div>

                {loading ? (
                    <div className="panel empty-state">正在加载商家...</div>
                ) : primaryList.length === 0 ? (
                    <div className="panel empty-state">没有找到符合条件的营业中商家。</div>
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
                                {hasQuery ? '根据当前搜索条件补充推荐的其他商家' : '当前可浏览的营业中商家'}
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
