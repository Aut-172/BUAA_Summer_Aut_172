import React, { useEffect, useState } from 'react'
import ReviewImageGallery, { LightboxImage } from '../components/ReviewImageGallery'
import { useSession } from '../utils/ApiProvider'
import { formatDateTime, formatMoney, formatStatusText, getStatusTone } from '../utils/format'

const EMPTY_PRODUCT = {
    id: null,
    name: '',
    categoryId: '',
    price: '',
    stock: '',
    image: '',
    description: '',
    status: 'active'
}

export default function MerchantConsole() {
    const { api, notify } = useSession()
    const [dashboard, setDashboard] = useState(null)
    const [profile, setProfile] = useState(null)
    const [categories, setCategories] = useState([])
    const [products, setProducts] = useState([])
    const [orders, setOrders] = useState([])
    const [reviews, setReviews] = useState([])
    const [profileForm, setProfileForm] = useState({
        name: '',
        phone: '',
        address: '',
        businessHours: '',
        category: '',
        description: '',
        avatar: '',
        tags: '',
        minDeliveryFee: '',
        deliveryFee: '',
        deliveryRadius: ''
    })
    const [productForm, setProductForm] = useState(EMPTY_PRODUCT)
    const [activeTab, setActiveTab] = useState('overview')
    const [loading, setLoading] = useState(true)
    const [savingProfile, setSavingProfile] = useState(false)
    const [savingProduct, setSavingProduct] = useState(false)
    const [uploadingProfileImage, setUploadingProfileImage] = useState(false)
    const [uploadingProductImage, setUploadingProductImage] = useState(false)
    const [busyOrderId, setBusyOrderId] = useState(null)
    const featureLocked = profile?.status !== 'active'
    const averageReviewRating = reviews.length === 0
        ? Number(profile?.rating || 0)
        : reviews.reduce((sum, review) => sum + Number(review.rating || 0), 0) / reviews.length
    const recentReviews = reviews.slice(0, 3)

    async function loadData() {
        setLoading(true)
        try {
            const [dashboardData, profileData, categoryData, productData, orderData] = await Promise.all([
                api.dashboard.getMine(),
                api.merchant.getProfile(),
                api.public.getCategories(),
                api.merchant.getProducts(),
                api.merchant.getOrders()
            ])

            setDashboard(dashboardData?.merchant || null)
            setProfile(profileData)
            setCategories(categoryData || [])
            setProducts(productData || [])
            setOrders(orderData || [])
            if (profileData?.id) {
                try {
                    const reviewData = await api.reviews.getMerchant(profileData.id)
                    setReviews(Array.isArray(reviewData) ? reviewData : [])
                } catch (error) {
                    setReviews([])
                    notify(error.message || '加载消费者评价失败', 'danger')
                }
            } else {
                setReviews([])
            }
            setProfileForm({
                name: profileData?.name || '',
                phone: profileData?.phone || '',
                address: profileData?.address || '',
                businessHours: profileData?.businessHours || '',
                category: profileData?.category || '',
                description: profileData?.description || '',
                avatar: profileData?.avatar || '',
                tags: profileData?.tags || '',
                minDeliveryFee: profileData?.minDeliveryFee ?? '',
                deliveryFee: profileData?.deliveryFee ?? '',
                deliveryRadius: profileData?.deliveryRadius ?? ''
            })
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadData()
    }, [])

    async function handleSaveProfile(event) {
        event.preventDefault()
        setSavingProfile(true)
        try {
            const data = await api.merchant.updateProfile({
                ...profile,
                ...profileForm,
                minDeliveryFee: profileForm.minDeliveryFee === '' ? null : Number(profileForm.minDeliveryFee),
                deliveryFee: profileForm.deliveryFee === '' ? null : Number(profileForm.deliveryFee),
                deliveryRadius: profileForm.deliveryRadius === '' ? null : Number(profileForm.deliveryRadius)
            })
            setProfile(data)
            notify('商家资料已更新', 'success')
        } catch (error) {
            notify(error.message || '更新商家资料失败', 'danger')
        } finally {
            setSavingProfile(false)
        }
    }

    async function handleUploadProfileImage(fileList) {
        const file = Array.from(fileList || [])[0]
        if (!file) {
            return
        }

        setUploadingProfileImage(true)
        try {
            const urls = await api.uploads.images([file], 'avatars')
            setProfileForm((current) => ({ ...current, avatar: urls?.[0] || current.avatar }))
            notify('头像上传成功', 'success')
        } catch (error) {
            notify(error.message || '头像上传失败', 'danger')
        } finally {
            setUploadingProfileImage(false)
        }
    }

    async function handleUploadProductImage(fileList) {
        const file = Array.from(fileList || [])[0]
        if (!file) {
            return
        }

        setUploadingProductImage(true)
        try {
            const urls = await api.uploads.images([file], 'products')
            setProductForm((current) => ({ ...current, image: urls?.[0] || current.image }))
            notify('商品图片上传成功', 'success')
        } catch (error) {
            notify(error.message || '商品图片上传失败', 'danger')
        } finally {
            setUploadingProductImage(false)
        }
    }

    function editProduct(product) {
        setActiveTab('products')
        setProductForm({
            id: product.id,
            name: product.name || '',
            categoryId: product.categoryId || '',
            price: product.price ?? '',
            stock: product.stock ?? '',
            image: product.image || '',
            description: product.description || '',
            status: product.status || 'active'
        })
    }

    function resetProductForm() {
        setProductForm(EMPTY_PRODUCT)
    }

    async function handleSaveProduct(event) {
        event.preventDefault()

        if (featureLocked) {
            notify('商家账号审核通过后才能维护商品', 'warning')
            return
        }

        if (!productForm.categoryId) {
            notify('请选择商品分类，否则商品不会在商家详情页中展示', 'warning')
            return
        }

        setSavingProduct(true)
        try {
            const payload = {
                id: productForm.id || null,
                name: productForm.name.trim(),
                categoryId: Number(productForm.categoryId),
                price: Number(productForm.price),
                stock: Number(productForm.stock),
                image: productForm.image.trim() || null,
                description: productForm.description.trim() || null,
                status: productForm.status || 'active'
            }

            if (productForm.id) {
                await api.merchant.updateProduct(payload)
                notify('商品已更新', 'success')
            } else {
                await api.merchant.createProduct(payload)
                notify('商品已新增', 'success')
            }

            resetProductForm()
            await loadData()
        } catch (error) {
            notify(error.message || '保存商品失败', 'danger')
        } finally {
            setSavingProduct(false)
        }
    }

    async function handleDeleteProduct(productId) {
        if (featureLocked) {
            notify('商家账号审核通过后才能维护商品', 'warning')
            return
        }

        try {
            await api.merchant.deleteProduct(productId)
            notify('商品已删除', 'success')
            await loadData()
        } catch (error) {
            notify(error.message || '删除商品失败', 'danger')
        }
    }

    async function handleUpdateOrder(orderId, status) {
        if (featureLocked) {
            notify('商家账号审核通过后才能处理订单', 'warning')
            return
        }

        setBusyOrderId(orderId)
        try {
            await api.merchant.updateOrder(orderId, {
                status,
                eta: '预计 20 分钟送达'
            })
            notify('订单状态已更新', 'success')
            await loadData()
        } catch (error) {
            notify(error.message || '更新订单状态失败', 'danger')
        } finally {
            setBusyOrderId(null)
        }
    }

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">商家工作台</h1>
                    <p className="section-subtitle">只保留当前后端真正支持的概览、资料、商品和订单能力。</p>
                </div>
            </div>

            <div className="tabs">
                <button className={`tab ${activeTab === 'overview' ? 'active' : ''}`} type="button" onClick={() => setActiveTab('overview')}>概览与资料</button>
                <button className={`tab ${activeTab === 'products' ? 'active' : ''}`} type="button" onClick={() => setActiveTab('products')}>商品管理</button>
                <button className={`tab ${activeTab === 'orders' ? 'active' : ''}`} type="button" onClick={() => setActiveTab('orders')}>订单处理</button>
                <button className={`tab ${activeTab === 'reviews' ? 'active' : ''}`} type="button" onClick={() => setActiveTab('reviews')}>消费者评价</button>
            </div>

            {loading ? (
                <div className="panel empty-state">正在加载商家数据...</div>
            ) : (
                <>
                    {featureLocked ? (
                        <div className="panel empty-state">账号审核中，当前可维护资料；审核通过后可使用商品和订单功能。</div>
                    ) : null}

                    {activeTab === 'overview' ? (
                        <div className="split-grid">
                            <section className="panel">
                                <div className="metric-grid">
                                    <div className="metric-card">
                                        <span>今日订单</span>
                                        <strong>{dashboard?.todayOrders || 0}</strong>
                                    </div>
                                    <div className="metric-card">
                                        <span>今日营收</span>
                                        <strong>{formatMoney(dashboard?.todayRevenue || 0)}</strong>
                                    </div>
                                    <div className="metric-card">
                                        <span>待处理订单</span>
                                        <strong>{dashboard?.pendingOrders || 0}</strong>
                                    </div>
                                    <div className="metric-card">
                                        <span>消费者评价</span>
                                        <strong>{reviews.length}</strong>
                                        <small>均分 {averageReviewRating.toFixed(1)}</small>
                                    </div>
                                </div>
                            </section>

                            <section className="panel">
                                <div className="panel-head">
                                    <div>
                                        <h2 className="section-title">最近评价</h2>
                                        <p className="section-subtitle">来自消费者已完成订单后的反馈。</p>
                                    </div>
                                    <button className="btn ghost small" type="button" onClick={() => setActiveTab('reviews')}>查看全部</button>
                                </div>

                                {recentReviews.length === 0 ? (
                                    <div className="empty-state">当前还没有消费者评价。</div>
                                ) : (
                                    <div className="review-list compact-list">
                                        {recentReviews.map((review) => (
                                            <article className="review-card" key={String(review.id)}>
                                                <div className="panel-head compact">
                                                    <div>
                                                        <strong>{review.userName || '匿名用户'}</strong>
                                                        <p className="section-subtitle">{review.productName || '订单商品'} · {formatDateTime(review.createTime)}</p>
                                                    </div>
                                                    <span className="badge warning">{review.rating || 0}/5</span>
                                                </div>
                                                <p className="review-content">{review.content || '用户未填写文字评价。'}</p>
                                            </article>
                                        ))}
                                    </div>
                                )}
                            </section>

                            <section className="panel">
                                <div className="panel-head">
                                    <div>
                                        <h2 className="section-title">商家资料</h2>
                                        <p className="section-subtitle">直接调用 `/api/merchant/profile` 更新。</p>
                                    </div>
                                </div>

                                <form className="form-grid" onSubmit={handleSaveProfile}>
                                    <label className="form-row">
                                        <span>店铺名称</span>
                                        <input className="input" value={profileForm.name} onChange={(event) => setProfileForm((current) => ({ ...current, name: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>联系电话</span>
                                        <input className="input" value={profileForm.phone} onChange={(event) => setProfileForm((current) => ({ ...current, phone: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>地址</span>
                                        <input className="input" value={profileForm.address} onChange={(event) => setProfileForm((current) => ({ ...current, address: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>营业时间</span>
                                        <input className="input" value={profileForm.businessHours} onChange={(event) => setProfileForm((current) => ({ ...current, businessHours: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>分类</span>
                                        <input className="input" value={profileForm.category} onChange={(event) => setProfileForm((current) => ({ ...current, category: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>头像</span>
                                        {profileForm.avatar ? (
                                            <img className="profile-avatar" src={profileForm.avatar} alt="商家头像预览" />
                                        ) : null}
                                        <input
                                            className="input"
                                            type="file"
                                            accept="image/*"
                                            disabled={uploadingProfileImage}
                                            onChange={(event) => {
                                                handleUploadProfileImage(event.target.files)
                                                event.target.value = ''
                                            }}
                                        />
                                        {profileForm.avatar ? <small className="helper">已上传头像，可保存资料生效。</small> : null}
                                    </label>
                                    <label className="form-row">
                                        <span>标签</span>
                                        <input className="input" value={profileForm.tags} onChange={(event) => setProfileForm((current) => ({ ...current, tags: event.target.value }))} placeholder="使用英文逗号分隔" />
                                    </label>
                                    <label className="form-row">
                                        <span>起送价</span>
                                        <input className="input" type="number" min="0" step="0.01" value={profileForm.minDeliveryFee} onChange={(event) => setProfileForm((current) => ({ ...current, minDeliveryFee: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>配送费</span>
                                        <input className="input" type="number" min="0" step="0.01" value={profileForm.deliveryFee} onChange={(event) => setProfileForm((current) => ({ ...current, deliveryFee: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>配送范围</span>
                                        <input className="input" type="number" min="0" value={profileForm.deliveryRadius} onChange={(event) => setProfileForm((current) => ({ ...current, deliveryRadius: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>商家介绍</span>
                                        <textarea className="textarea" rows="4" value={profileForm.description} onChange={(event) => setProfileForm((current) => ({ ...current, description: event.target.value }))} />
                                    </label>
                                    <button className="btn primary" type="submit" disabled={savingProfile || uploadingProfileImage}>
                                        {savingProfile ? '保存中...' : '保存商家资料'}
                                    </button>
                                </form>
                            </section>
                        </div>
                    ) : null}

                    {activeTab === 'products' ? (
                        <div className="split-grid">
                            <section className="panel">
                                <div className="panel-head">
                                    <div>
                                        <h2 className="section-title">{productForm.id ? '编辑商品' : '新增商品'}</h2>
                                        <p className="section-subtitle">商品表单只包含当前后端真正持久化的字段。</p>
                                    </div>
                                    {productForm.id ? <button className="btn ghost small" type="button" onClick={resetProductForm}>取消编辑</button> : null}
                                </div>

                                <form className="form-grid" onSubmit={handleSaveProduct}>
                                    <label className="form-row">
                                        <span>商品名</span>
                                        <input className="input" value={productForm.name} onChange={(event) => setProductForm((current) => ({ ...current, name: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>分类</span>
                                        <select className="select" value={productForm.categoryId} onChange={(event) => setProductForm((current) => ({ ...current, categoryId: event.target.value }))}>
                                            <option value="">请选择分类</option>
                                            {categories.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
                                        </select>
                                    </label>
                                    <label className="form-row">
                                        <span>价格</span>
                                        <input className="input" type="number" min="0" step="0.01" value={productForm.price} onChange={(event) => setProductForm((current) => ({ ...current, price: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>库存</span>
                                        <input className="input" type="number" min="0" value={productForm.stock} onChange={(event) => setProductForm((current) => ({ ...current, stock: event.target.value }))} />
                                    </label>
                                    <label className="form-row">
                                        <span>商品图片</span>
                                        {productForm.image ? (
                                            <LightboxImage
                                                className="product-image-large compact-preview"
                                                src={productForm.image}
                                                alt="商品图片预览"
                                                buttonClassName="product-image-button"
                                            />
                                        ) : null}
                                        <input
                                            className="input"
                                            type="file"
                                            accept="image/*"
                                            disabled={uploadingProductImage}
                                            onChange={(event) => {
                                                handleUploadProductImage(event.target.files)
                                                event.target.value = ''
                                            }}
                                        />
                                        {productForm.image ? <small className="helper">已上传商品图片。</small> : null}
                                    </label>
                                    <label className="form-row">
                                        <span>状态</span>
                                        <select className="select" value={productForm.status} onChange={(event) => setProductForm((current) => ({ ...current, status: event.target.value }))}>
                                            <option value="active">active</option>
                                            <option value="inactive">inactive</option>
                                        </select>
                                    </label>
                                    <label className="form-row">
                                        <span>描述</span>
                                        <textarea className="textarea" rows="4" value={productForm.description} onChange={(event) => setProductForm((current) => ({ ...current, description: event.target.value }))} />
                                    </label>
                                    <button className="btn primary" type="submit" disabled={savingProduct || uploadingProductImage || featureLocked}>
                                        {savingProduct ? '保存中...' : productForm.id ? '更新商品' : '新增商品'}
                                    </button>
                                </form>
                            </section>

                            <section className="panel">
                                <div className="panel-head">
                                    <div>
                                        <h2 className="section-title">当前商品</h2>
                                        <p className="section-subtitle">支持编辑和删除。规格组相关的复杂管理入口暂不保留，避免前端能力超出后端链路。</p>
                                    </div>
                                </div>

                                <div className="stack">
                                    {products.length === 0 ? (
                                        <div className="empty-state">当前没有商品。</div>
                                    ) : products.map((product) => (
                                        <article className="select-card" key={product.id}>
                                            <div className="stack tight grow">
                                                <strong>{product.name}</strong>
                                                <span className="muted-text">
                                                    分类 #{product.categoryId} · 价格 {formatMoney(product.price)} · 库存 {product.stock ?? 0}
                                                </span>
                                            </div>
                                            <div className="card-actions">
                                                <button className="btn ghost small" type="button" disabled={featureLocked} onClick={() => editProduct(product)}>编辑</button>
                                                <button className="btn danger small" type="button" disabled={featureLocked} onClick={() => handleDeleteProduct(product.id)}>删除</button>
                                            </div>
                                        </article>
                                    ))}
                                </div>
                            </section>
                        </div>
                    ) : null}

                    {activeTab === 'orders' ? (
                        <div className="stack">
                            {orders.length === 0 ? (
                                <div className="panel empty-state">当前没有订单。</div>
                            ) : orders.map((order) => (
                                <article className="panel order-card" key={order.id}>
                                    <div className="panel-head">
                                        <div>
                                            <h2 className="section-title">{order.orderNo}</h2>
                                            <p className="section-subtitle">下单时间 {formatDateTime(order.createdAt)}</p>
                                        </div>
                                        <span className={`status-chip ${getStatusTone(order.status)}`}>{formatStatusText(order.status)}</span>
                                    </div>

                                    <div className="summary-line"><span>收货地址</span><strong>{order.address || '未填写'}</strong></div>
                                    <div className="summary-line"><span>订单金额</span><strong>{formatMoney(order.total)}</strong></div>
                                    <div className="summary-line"><span>骑手</span><strong>{order.riderName || '暂未分配'}</strong></div>

                                    <div className="card-actions wrap">
                                        {formatStatusText(order.status) === '待取餐' ? (
                                            <>
                                                <button className="btn primary small" type="button" disabled={busyOrderId === order.id || featureLocked} onClick={() => handleUpdateOrder(order.id, '配送中')}>
                                                    标记配送中
                                                </button>
                                                <button className="btn ghost small" type="button" disabled={busyOrderId === order.id || featureLocked} onClick={() => handleUpdateOrder(order.id, '已完成')}>
                                                    直接完成
                                                </button>
                                            </>
                                        ) : null}

                                        {formatStatusText(order.status) === '配送中' ? (
                                            <button className="btn primary small" type="button" disabled={busyOrderId === order.id || featureLocked} onClick={() => handleUpdateOrder(order.id, '已完成')}>
                                                标记已完成
                                            </button>
                                        ) : null}
                                    </div>
                                </article>
                            ))}
                        </div>
                    ) : null}

                    {activeTab === 'reviews' ? (
                        <section className="panel">
                            <div className="panel-head">
                                <div>
                                    <h2 className="section-title">消费者评价</h2>
                                    <p className="section-subtitle">共 {reviews.length} 条评价，平均 {averageReviewRating.toFixed(1)} 分。</p>
                                </div>
                            </div>

                            {reviews.length === 0 ? (
                                <div className="empty-state">当前还没有消费者评价。</div>
                            ) : (
                                <div className="review-list">
                                    {reviews.map((review) => (
                                        <article className="review-card" key={String(review.id)}>
                                            <div className="panel-head compact">
                                                <div>
                                                    <strong>{review.userName || '匿名用户'}</strong>
                                                    <p className="section-subtitle">
                                                        {review.productName || '订单商品'} · 订单 #{review.orderId} · {formatDateTime(review.createTime)}
                                                    </p>
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
                    ) : null}
                </>
            )}
        </section>
    )
}
