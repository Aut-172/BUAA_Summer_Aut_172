import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { formatMoney, formatStatusText, getStatusTone } from '../utils/format'

function buildMessagePath({ targetId, targetType, orderId, targetName, orderNo }) {
    const params = new URLSearchParams({
        targetId: String(targetId),
        targetType,
        orderId: String(orderId)
    })
    if (targetName) {
        params.set('targetName', targetName)
    }
    if (orderNo) {
        params.set('orderNo', orderNo)
    }
    return `/messages?${params.toString()}`
}

export default function RiderConsole() {
    const { api, notify, updateSessionUser, user } = useSession()
    const [dashboard, setDashboard] = useState(null)
    const [tasks, setTasks] = useState(null)
    const [profile, setProfile] = useState(null)
    const [profileForm, setProfileForm] = useState({
        nickname: user?.nickname || user?.username || '',
        phone: user?.phone || '',
        serviceArea: ''
    })
    const [loading, setLoading] = useState(true)
    const [savingProfile, setSavingProfile] = useState(false)
    const [busyTaskId, setBusyTaskId] = useState(null)
    const featureLocked = profile?.status !== 'active'

    async function loadData() {
        setLoading(true)
        try {
            const [dashboardData, taskData, profileData] = await Promise.all([
                api.rider.getDashboard(),
                api.rider.getTasks(),
                api.rider.getProfile()
            ])

            setDashboard(dashboardData?.rider || null)
            setTasks(taskData || null)
            setProfile(profileData || null)
            setProfileForm({
                nickname: profileData?.name || user?.nickname || user?.username || '',
                phone: profileData?.phone || user?.phone || '',
                serviceArea: profileData?.serviceArea || ''
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
            const data = await api.rider.updateProfile(profileForm)
            setProfile(data || null)
            updateSessionUser({
                nickname: data?.name || profileForm.nickname,
                phone: data?.phone || profileForm.phone,
                status: data?.status || user?.status
            })
            notify('骑手资料已更新', 'success')
        } catch (error) {
            notify(error.message || '更新骑手资料失败', 'danger')
        } finally {
            setSavingProfile(false)
        }
    }

    async function updateTask(taskId, status) {
        if (featureLocked) {
            notify('骑手账号审核通过后才能处理配送任务', 'warning')
            return
        }

        setBusyTaskId(taskId)
        try {
            await api.rider.updateTask(taskId, { status })
            notify('任务状态已更新', 'success')
            await loadData()
        } catch (error) {
            notify(error.message || '更新任务失败', 'danger')
        } finally {
            setBusyTaskId(null)
        }
    }

    function renderTaskList(title, description, list, actionLabel, actionStatus) {
        const canOpenChat = actionStatus !== '待取餐'

        return (
            <section className="panel">
                <div className="panel-head">
                    <div>
                        <h2 className="section-title">{title}</h2>
                        <p className="section-subtitle">{description}</p>
                    </div>
                </div>

                <div className="stack">
                    {(list || []).length === 0 ? (
                        <div className="empty-state">暂无任务。</div>
                    ) : (list || []).map((task) => (
                        <article className="select-card" key={task.id}>
                            <div className="stack tight grow">
                                <strong>{task.orderNo}</strong>
                                <span className="muted-text">{task.merchant} · {task.items}</span>
                                <span className="muted-text">取餐点：{task.pickup || '未设置'}</span>
                                <span className="muted-text">送达点：{task.destination || '未设置'}</span>
                                <div className="badge-row">
                                    <span className={`status-chip ${getStatusTone(task.status)}`}>{formatStatusText(task.status)}</span>
                                    <span className="badge muted">{formatMoney(task.total)}</span>
                                </div>
                            </div>
                            <div className="card-actions wrap">
                                {actionLabel ? (
                                    <button
                                        className="btn primary small"
                                        type="button"
                                        disabled={busyTaskId === task.id || featureLocked}
                                        onClick={() => updateTask(task.id, actionStatus)}
                                    >
                                        {busyTaskId === task.id ? '处理中...' : actionLabel}
                                    </button>
                                ) : null}

                                {canOpenChat && task.merchantId ? (
                                    <Link
                                        className="btn ghost small"
                                        to={buildMessagePath({
                                            targetId: task.merchantId,
                                            targetType: 'merchant',
                                            orderId: task.id,
                                            targetName: task.merchant,
                                            orderNo: task.orderNo
                                        })}
                                    >
                                        联系商家
                                    </Link>
                                ) : null}

                                {canOpenChat && task.userId ? (
                                    <Link
                                        className="btn ghost small"
                                        to={buildMessagePath({
                                            targetId: task.userId,
                                            targetType: 'user',
                                            orderId: task.id,
                                            targetName: '下单用户',
                                            orderNo: task.orderNo
                                        })}
                                    >
                                        联系用户
                                    </Link>
                                ) : null}
                            </div>
                        </article>
                    ))}
                </div>
            </section>
        )
    }

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">骑手工作台</h1>
                    <p className="section-subtitle">可真实接单、完成配送，并更新当前骑手资料。</p>
                </div>
            </div>

            {loading ? (
                <div className="panel empty-state">正在加载骑手数据...</div>
            ) : (
                <>
                    {featureLocked ? (
                        <div className="panel empty-state">账号审核中，当前可维护资料；审核通过后可接单和处理配送任务。</div>
                    ) : null}

                    <div className="split-grid">
                        <section className="panel">
                            <div className="metric-grid">
                                <div className="metric-card">
                                    <span>今日配送</span>
                                    <strong>{dashboard?.todayDeliveries || 0}</strong>
                                </div>
                                <div className="metric-card">
                                    <span>今日配送费收入</span>
                                    <strong>{formatMoney(dashboard?.todayEarnings || 0)}</strong>
                                </div>
                                <div className="metric-card">
                                    <span>账号状态</span>
                                    <strong>{formatStatusText(dashboard?.status || profile?.status)}</strong>
                                </div>
                            </div>
                        </section>

                        <section className="panel">
                            <div className="panel-head">
                                <div>
                                    <h2 className="section-title">骑手资料</h2>
                                    <p className="section-subtitle">维护昵称、手机号和服务范围。</p>
                                </div>
                            </div>

                            <form className="form-grid" onSubmit={handleSaveProfile}>
                                <label className="form-row">
                                    <span>昵称</span>
                                    <input className="input" value={profileForm.nickname} onChange={(event) => setProfileForm((current) => ({ ...current, nickname: event.target.value }))} />
                                </label>
                                <label className="form-row">
                                    <span>手机号</span>
                                    <input className="input" value={profileForm.phone} onChange={(event) => setProfileForm((current) => ({ ...current, phone: event.target.value }))} />
                                </label>
                                <label className="form-row">
                                    <span>服务范围</span>
                                    <input className="input" value={profileForm.serviceArea} onChange={(event) => setProfileForm((current) => ({ ...current, serviceArea: event.target.value }))} />
                                </label>
                                <button className="btn primary" type="submit" disabled={savingProfile}>
                                    {savingProfile ? '保存中...' : '保存资料'}
                                </button>
                            </form>
                        </section>
                    </div>

                    <div className="stack">
                        {renderTaskList('可接订单', '等待骑手接单的订单', tasks?.available, '立即接单', '待取餐')}
                        {renderTaskList('配送中订单', '接单后会进入这里', tasks?.assigned, '完成配送', '已完成')}
                        {renderTaskList('已完成订单', '仅做只读展示', tasks?.completed, null, null)}
                    </div>
                </>
            )}
        </section>
    )
}
