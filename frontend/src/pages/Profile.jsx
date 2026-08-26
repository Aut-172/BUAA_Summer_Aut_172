import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { buildAddressLabel, formatMoney, normalizeTags } from '../utils/format'

const EMPTY_ADDRESS = {
    name: '',
    phone: '',
    detail: '',
    isDefault: false
}

export default function Profile() {
    const { api, notify, updateSessionUser } = useSession()
    const [profile, setProfile] = useState(null)
    const [profileForm, setProfileForm] = useState({
        nickname: '',
        phone: '',
        avatar: ''
    })
    const [addresses, setAddresses] = useState([])
    const [favoriteMerchants, setFavoriteMerchants] = useState([])
    const [addressForm, setAddressForm] = useState(EMPTY_ADDRESS)
    const [editingAddressId, setEditingAddressId] = useState(null)
    const [loading, setLoading] = useState(true)
    const [savingProfile, setSavingProfile] = useState(false)
    const [savingAddress, setSavingAddress] = useState(false)

    async function loadProfile() {
        setLoading(true)
        try {
            const [profileData, addressData, favoriteData] = await Promise.all([
                api.user.getProfile(),
                api.user.getAddresses(),
                api.user.getFavoriteMerchants()
            ])

            setProfile(profileData)
            setAddresses(addressData || [])
            setFavoriteMerchants(favoriteData || [])
            setProfileForm({
                nickname: profileData?.nickname || '',
                phone: profileData?.phone || '',
                avatar: profileData?.avatar || ''
            })
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadProfile()
    }, [])

    async function handleSaveProfile(event) {
        event.preventDefault()
        setSavingProfile(true)
        try {
            const data = await api.user.updateProfile(profileForm)
            setProfile(data)
            updateSessionUser({
                nickname: data.nickname,
                phone: data.phone,
                avatar: data.avatar
            })
            notify('个人资料已更新', 'success')
        } catch (error) {
            notify(error.message || '更新资料失败', 'danger')
        } finally {
            setSavingProfile(false)
        }
    }

    function startEditAddress(address) {
        setEditingAddressId(address.id)
        setAddressForm({
            name: address.name || '',
            phone: address.phone || '',
            detail: address.detail || '',
            isDefault: Boolean(address.isDefault)
        })
    }

    function resetAddressForm() {
        setEditingAddressId(null)
        setAddressForm(EMPTY_ADDRESS)
    }

    async function handleSaveAddress(event) {
        event.preventDefault()
        setSavingAddress(true)
        try {
            if (editingAddressId) {
                await api.user.updateAddress(editingAddressId, addressForm)
                notify('地址已更新', 'success')
            } else {
                await api.user.addAddress(addressForm)
                notify('地址已新增', 'success')
            }

            resetAddressForm()
            await loadProfile()
        } catch (error) {
            notify(error.message || '保存地址失败', 'danger')
        } finally {
            setSavingAddress(false)
        }
    }

    async function handleDeleteAddress(addressId) {
        try {
            await api.user.deleteAddress(addressId)
            notify('地址已删除', 'success')
            await loadProfile()
        } catch (error) {
            notify(error.message || '删除地址失败', 'danger')
        }
    }

    async function handleRemoveFavorite(merchantId) {
        try {
            await api.user.deleteFavoriteMerchant(merchantId)
            notify('已取消收藏', 'success')
            setFavoriteMerchants((current) => current.filter((item) => item.merchantId !== merchantId))
        } catch (error) {
            notify(error.message || '取消收藏失败', 'danger')
        }
    }

    return (
        <section className="page">
            {loading ? (
                <div className="panel empty-state">正在加载个人资料...</div>
            ) : (
                <>
                    <div className="split-grid">
                        <section className="panel">
                            <div className="panel-head">
                                <div>
                                    <h1 className="section-title">个人资料</h1>
                                    <p className="section-subtitle">这里仅保留后端 `profile` 和 `addresses` 已支持的编辑能力。</p>
                                </div>
                            </div>

                            <div className="profile-banner">
                                <img
                                    className="profile-avatar"
                                    src={profileForm.avatar || 'https://picsum.photos/seed/profile-avatar/160/160'}
                                    alt={profile?.nickname || profile?.username || '用户头像'}
                                />
                                <div className="stack tight">
                                    <strong>{profile?.nickname || profile?.username}</strong>
                                    <span className="muted-text">{profile?.phone || '未绑定手机号'}</span>
                                </div>
                            </div>

                            <form className="form-grid" onSubmit={handleSaveProfile}>
                                <label className="form-row">
                                    <span>昵称</span>
                                    <input
                                        className="input"
                                        value={profileForm.nickname}
                                        onChange={(event) => setProfileForm((current) => ({ ...current, nickname: event.target.value }))}
                                    />
                                </label>

                                <label className="form-row">
                                    <span>手机号</span>
                                    <input
                                        className="input"
                                        value={profileForm.phone}
                                        onChange={(event) => setProfileForm((current) => ({ ...current, phone: event.target.value }))}
                                    />
                                </label>

                                <label className="form-row">
                                    <span>头像链接</span>
                                    <input
                                        className="input"
                                        value={profileForm.avatar}
                                        onChange={(event) => setProfileForm((current) => ({ ...current, avatar: event.target.value }))}
                                        placeholder="可选，填写图片 URL"
                                    />
                                </label>

                                <button className="btn primary" type="submit" disabled={savingProfile}>
                                    {savingProfile ? '保存中...' : '保存资料'}
                                </button>
                            </form>
                        </section>

                        <section className="panel">
                            <div className="panel-head">
                                <div>
                                    <h2 className="section-title">收货地址</h2>
                                    <p className="section-subtitle">支持新增、编辑、删除和默认地址。</p>
                                </div>
                                {editingAddressId ? (
                                    <button className="btn ghost small" type="button" onClick={resetAddressForm}>取消编辑</button>
                                ) : null}
                            </div>

                            <div className="stack">
                                {addresses.length === 0 ? (
                                    <div className="empty-state">还没有收货地址，请先新增一个。</div>
                                ) : addresses.map((address) => (
                                    <article className="select-card" key={address.id}>
                                        <div className="stack tight grow">
                                            <strong>{buildAddressLabel(address)}</strong>
                                            {address.isDefault ? <span className="mini-chip">默认地址</span> : null}
                                        </div>
                                        <div className="card-actions">
                                            <button className="btn ghost small" type="button" onClick={() => startEditAddress(address)}>编辑</button>
                                            <button className="btn danger small" type="button" onClick={() => handleDeleteAddress(address.id)}>删除</button>
                                        </div>
                                    </article>
                                ))}
                            </div>

                            <form className="form-grid section-spacer" onSubmit={handleSaveAddress}>
                                <label className="form-row">
                                    <span>收货人</span>
                                    <input
                                        className="input"
                                        value={addressForm.name}
                                        onChange={(event) => setAddressForm((current) => ({ ...current, name: event.target.value }))}
                                    />
                                </label>

                                <label className="form-row">
                                    <span>联系电话</span>
                                    <input
                                        className="input"
                                        value={addressForm.phone}
                                        onChange={(event) => setAddressForm((current) => ({ ...current, phone: event.target.value }))}
                                    />
                                </label>

                                <label className="form-row">
                                    <span>详细地址</span>
                                    <textarea
                                        className="textarea"
                                        rows="3"
                                        value={addressForm.detail}
                                        onChange={(event) => setAddressForm((current) => ({ ...current, detail: event.target.value }))}
                                    />
                                </label>

                                <label className="checkbox-row">
                                    <input
                                        type="checkbox"
                                        checked={Boolean(addressForm.isDefault)}
                                        onChange={(event) => setAddressForm((current) => ({ ...current, isDefault: event.target.checked }))}
                                    />
                                    <span>设为默认地址</span>
                                </label>

                                <button className="btn primary" type="submit" disabled={savingAddress}>
                                    {savingAddress ? '保存中...' : editingAddressId ? '更新地址' : '新增地址'}
                                </button>
                            </form>
                        </section>
                    </div>

                    <section className="panel">
                        <div className="panel-head">
                            <div>
                                <h2 className="section-title">我的收藏商家</h2>
                                <p className="section-subtitle">只展示当前仍为正常营业状态的收藏商家。</p>
                            </div>
                        </div>

                        {favoriteMerchants.length === 0 ? (
                            <div className="empty-state">还没有收藏商家，可以在商家详情页收藏常点店铺。</div>
                        ) : (
                            <div className="merchant-grid">
                                {favoriteMerchants.map((merchant) => {
                                    const tags = normalizeTags(merchant.tags)

                                    return (
                                        <article className="merchant-card merchant-card-pro panel" key={merchant.favoriteId}>
                                            <div className="merchant-header">
                                                <img
                                                    className="merchant-avatar"
                                                    src={merchant.avatar || 'https://picsum.photos/seed/favorite-store/120/120'}
                                                    alt={merchant.name}
                                                />
                                                <div className="merchant-meta">
                                                    <h3>{merchant.name}</h3>
                                                    <p>{merchant.description || '暂无商家介绍'}</p>
                                                    <div className="badge-row">
                                                        <span className="badge info">{merchant.category || '未分类'}</span>
                                                        <span className="badge success">月售 {merchant.monthlySales || 0}</span>
                                                        <span className="badge warning">配送 {formatMoney(merchant.deliveryFee)}</span>
                                                    </div>
                                                </div>
                                            </div>

                                            {tags.length > 0 ? (
                                                <div className="chip-row">
                                                    {tags.map((tag) => <span className="mini-chip" key={tag}>{tag}</span>)}
                                                </div>
                                            ) : null}

                                            <div className="card-actions">
                                                <Link className="btn primary small" to={`/merchants/${merchant.merchantId}`}>进入店铺</Link>
                                                <button className="btn ghost small" type="button" onClick={() => handleRemoveFavorite(merchant.merchantId)}>取消收藏</button>
                                            </div>
                                        </article>
                                    )
                                })}
                            </div>
                        )}
                    </section>
                </>
            )}
        </section>
    )
}
