import React, { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import ReviewImageGallery from '../components/ReviewImageGallery'
import { useSession } from '../utils/ApiProvider'
import { FALLBACK_PRODUCT_IMAGE, normalizeImageSrc } from '../utils/demoImages'
import { formatMoney, formatStatusText } from '../utils/format'

const RATING_OPTIONS = [5, 4, 3, 2, 1]
const MAX_CONTENT_LENGTH = 200
const MAX_IMAGE_COUNT = 6

export default function Review() {
    const { orderId } = useParams()
    const navigate = useNavigate()
    const { api, notify } = useSession()
    const [order, setOrder] = useState(null)
    const [drafts, setDrafts] = useState({})
    const [loading, setLoading] = useState(true)
    const [submitting, setSubmitting] = useState(false)
    const [message, setMessage] = useState('')

    useEffect(() => {
        async function loadOrder() {
            setLoading(true)
            try {
                const data = await api.orders.getDetail(orderId)
                const items = Array.isArray(data?.items) ? data.items : []
                const initialDrafts = items.reduce((acc, item) => ({
                    ...acc,
                    [item.productId]: {
                        rating: 5,
                        content: '',
                        images: [],
                        uploading: false
                    }
                }), {})

                setOrder(data)
                setDrafts(initialDrafts)
                setMessage('')
            } catch (error) {
                setOrder(null)
                setMessage(error.message || '订单信息加载失败')
            } finally {
                setLoading(false)
            }
        }

        loadOrder()
    }, [api, orderId])

    const reviewableItems = useMemo(() => (
        (order?.items || []).filter((item) => !item.reviewed)
    ), [order])

    const isCompleted = formatStatusText(order?.status) === '已完成'
    const hasUploading = Object.values(drafts).some((draft) => draft?.uploading)

    function updateDraft(productId, patch) {
        setDrafts((current) => ({
            ...current,
            [productId]: {
                ...(current[productId] || { rating: 5, content: '', images: [], uploading: false }),
                ...patch
            }
        }))
    }

    async function handleUploadImages(productId, fileList) {
        const selectedFiles = Array.from(fileList || [])
        if (selectedFiles.length === 0) {
            return
        }

        const currentImages = drafts[productId]?.images || []
        if (currentImages.length + selectedFiles.length > MAX_IMAGE_COUNT) {
            notify(`每个商品最多上传${MAX_IMAGE_COUNT}张评价图片`, 'warning')
            return
        }

        updateDraft(productId, { uploading: true })
        try {
            const urls = await api.reviews.uploadImages(selectedFiles)
            updateDraft(productId, {
                images: [...currentImages, ...(Array.isArray(urls) ? urls : [])],
                uploading: false
            })
            notify('评价图片上传成功', 'success')
        } catch (error) {
            updateDraft(productId, { uploading: false })
            notify(error.message || '评价图片上传失败', 'danger')
        }
    }

    function removeImage(productId, imageUrl) {
        const currentImages = drafts[productId]?.images || []
        updateDraft(productId, {
            images: currentImages.filter((item) => item !== imageUrl)
        })
    }

    async function handleSubmit(event) {
        event.preventDefault()

        if (!isCompleted) {
            notify('只有已完成订单可以评价', 'warning')
            return
        }
        if (reviewableItems.length === 0) {
            notify('该订单已评价完成', 'warning')
            return
        }
        if (hasUploading) {
            notify('评价图片上传完成后再提交', 'warning')
            return
        }

        const items = reviewableItems.map((item) => {
            const draft = drafts[item.productId] || {}
            return {
                productId: item.productId,
                rating: Number(draft.rating || 5),
                content: String(draft.content || '').trim(),
                images: Array.isArray(draft.images) ? draft.images : []
            }
        })

        const oversizedContent = items.some((item) => item.content.length > MAX_CONTENT_LENGTH)
        if (oversizedContent) {
            notify(`评价内容不能超过${MAX_CONTENT_LENGTH}字`, 'warning')
            return
        }
        const tooManyImages = items.some((item) => item.images.length > MAX_IMAGE_COUNT)
        if (tooManyImages) {
            notify(`每个商品最多上传${MAX_IMAGE_COUNT}张评价图片`, 'warning')
            return
        }

        setSubmitting(true)
        try {
            await api.reviews.submit({ orderId, items })
            notify('评价提交成功', 'success')
            navigate('/orders')
        } catch (error) {
            notify(error.message || '评价提交失败', 'danger')
        } finally {
            setSubmitting(false)
        }
    }

    if (loading) {
        return <section className="page"><div className="panel empty-state">正在加载评价信息...</div></section>
    }

    if (!order) {
        return <section className="page"><div className="panel empty-state">{message || '订单不存在。'}</div></section>
    }

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">订单评价</h1>
                    <p className="section-subtitle">{order.merchant || '未知商家'} · 订单号 {order.orderNo}</p>
                </div>
                <Link className="btn ghost small" to="/orders">返回订单</Link>
            </div>

            {!isCompleted ? (
                <div className="panel empty-state">当前订单尚未完成，完成后即可评价。</div>
            ) : reviewableItems.length === 0 ? (
                <div className="panel empty-state">该订单已评价完成。</div>
            ) : (
                <form className="split-grid" onSubmit={handleSubmit}>
                    <div className="stack">
                        {reviewableItems.map((item) => {
                            const draft = drafts[item.productId] || { rating: 5, content: '', images: [], uploading: false }
                            const previews = Array.isArray(draft.images) ? draft.images : []

                            return (
                                <section className="panel review-product" key={String(item.productId)}>
                                    <div className="merchant-header">
                                        <img
                                            className="review-thumb"
                                            src={normalizeImageSrc(item.image, FALLBACK_PRODUCT_IMAGE)}
                                            alt={item.name}
                                            loading="lazy"
                                            decoding="async"
                                        />
                                        <div className="merchant-meta grow">
                                            <div className="card-line">
                                                <h2 className="section-title">{item.name}</h2>
                                                <strong className="price-text">{formatMoney(item.price)}</strong>
                                            </div>
                                            <p className="section-subtitle">
                                                x{item.quantity}{item.specLabel ? ` · ${item.specLabel}` : ''}
                                            </p>
                                        </div>
                                    </div>

                                    <label className="form-row">
                                        <span>评分</span>
                                        <div className="rating-row">
                                            {RATING_OPTIONS.map((rating) => (
                                                <button
                                                    className={`rating-option ${Number(draft.rating) === rating ? 'active' : ''}`}
                                                    key={rating}
                                                    type="button"
                                                    onClick={() => updateDraft(item.productId, { rating })}
                                                >
                                                    {rating} 星
                                                </button>
                                            ))}
                                        </div>
                                    </label>

                                    <label className="form-row">
                                        <span>评价内容</span>
                                        <textarea
                                            className="textarea"
                                            rows="4"
                                            maxLength={MAX_CONTENT_LENGTH}
                                            value={draft.content}
                                            onChange={(event) => updateDraft(item.productId, { content: event.target.value })}
                                            placeholder="说说口味、包装、配送体验..."
                                        />
                                        <small className="helper">{String(draft.content || '').length}/{MAX_CONTENT_LENGTH}</small>
                                    </label>

                                    <label className="form-row">
                                        <span>评价图片</span>
                                        <input
                                            className="input"
                                            type="file"
                                            accept="image/*"
                                            multiple
                                            disabled={draft.uploading || previews.length >= MAX_IMAGE_COUNT}
                                            onChange={(event) => {
                                                handleUploadImages(item.productId, event.target.files)
                                                event.target.value = ''
                                            }}
                                        />
                                        <small className="helper">已上传 {previews.length}/{MAX_IMAGE_COUNT}</small>
                                    </label>

                                    {previews.length > 0 ? (
                                        <ReviewImageGallery
                                            images={previews}
                                            alt="评价图片预览"
                                            onRemove={(src) => removeImage(item.productId, src)}
                                        />
                                    ) : null}

                                    {draft.uploading ? <p className="helper">图片上传中...</p> : null}
                                </section>
                            )
                        })}
                    </div>

                    <aside className="panel summary-card">
                        <div className="stack tight">
                            <h2 className="section-title">提交评价</h2>
                            <p className="section-subtitle">本次会提交 {reviewableItems.length} 个商品评价。</p>
                            <div className="summary-line"><span>订单状态</span><strong>{formatStatusText(order.status)}</strong></div>
                            <div className="summary-line"><span>实付金额</span><strong>{formatMoney(order.total)}</strong></div>
                            <div className="summary-line"><span>图片上限</span><strong>每个商品 {MAX_IMAGE_COUNT} 张</strong></div>
                        </div>
                        <div className="card-actions section-spacer">
                            <button className="btn primary" type="submit" disabled={submitting || hasUploading}>
                                {submitting ? '提交中...' : '提交图文评价'}
                            </button>
                        </div>
                    </aside>
                </form>
            )}
        </section>
    )
}
