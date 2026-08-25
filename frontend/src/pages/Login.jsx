import React, { useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'

const ROLE_OPTIONS = [
    { value: 'consumer', label: '消费者', canRegister: true, loginHint: '适合点餐、查看订单和管理收货地址。' },
    { value: 'merchant', label: '商家', canRegister: true, loginHint: '适合管理商品、查看订单和处理履约。' },
    { value: 'rider', label: '骑手', canRegister: true, loginHint: '适合接单配送、查看任务和维护资料。' },
    { value: 'admin', label: '管理员', canRegister: false, loginHint: '管理员账号仅支持登录。' }
]

const DEMO_ACCOUNTS = {
    consumer: { username: 'demo', phone: '13800000001', password: '123456', nickname: '演示用户', note: '适合体验找店、下单、支付和收货流程。' },
    merchant: { username: 'merchant1', phone: '13800000011', password: '123456', nickname: '校园食堂', note: '适合体验商品管理和订单处理。' },
    rider: { username: 'rider01', phone: '13800000021', password: '123456', nickname: '一号骑手', note: '适合体验配送任务和接单流程。' },
    admin: { username: 'gl1', password: 'gl1gl1gl1', nickname: '管理员', note: '适合体验平台侧查询和管理。' }
}

function getRedirectByRole(role) {
    if (role === 'merchant') {
        return '/merchant-center'
    }
    if (role === 'rider') {
        return '/rider-center'
    }
    if (role === 'admin') {
        return '/admin-center'
    }
    return '/'
}

export default function Login() {
    const navigate = useNavigate()
    const location = useLocation()
    const { login, register, notify } = useSession()
    const [mode, setMode] = useState('login')
    const [role, setRole] = useState('consumer')
    const [form, setForm] = useState({
        username: DEMO_ACCOUNTS.consumer.username,
        phone: DEMO_ACCOUNTS.consumer.phone,
        password: DEMO_ACCOUNTS.consumer.password,
        nickname: DEMO_ACCOUNTS.consumer.nickname
    })
    const [submitting, setSubmitting] = useState(false)

    const currentRole = useMemo(
        () => ROLE_OPTIONS.find((item) => item.value === role) || ROLE_OPTIONS[0],
        [role]
    )

    function applyDemo(nextRole) {
        const demo = DEMO_ACCOUNTS[nextRole]
        setRole(nextRole)
        setMode('login')
        setForm({
            username: demo.username || '',
            phone: demo.phone || '',
            password: demo.password || '',
            nickname: demo.nickname || ''
        })
    }

    function handleRoleChange(nextRole) {
        setRole(nextRole)

        if (mode === 'register' && !ROLE_OPTIONS.find((item) => item.value === nextRole)?.canRegister) {
            setMode('login')
        }

        const demo = DEMO_ACCOUNTS[nextRole]
        setForm((current) => ({
            ...current,
            username: demo?.username || current.username,
            phone: demo?.phone || current.phone,
            password: demo?.password || current.password,
            nickname: demo?.nickname || current.nickname
        }))
    }

    async function handleSubmit(event) {
        event.preventDefault()
        setSubmitting(true)

        try {
            let nextSession

            if (mode === 'register') {
                if (!currentRole.canRegister) {
                    throw new Error('当前角色暂不支持注册')
                }

                nextSession = await register({
                    role,
                    username: form.username.trim(),
                    phone: form.phone.trim(),
                    password: form.password,
                    nickname: form.nickname.trim()
                })
            } else {
                nextSession = await login({
                    role,
                    username: form.username.trim(),
                    password: form.password
                })
            }

            const from = location.state?.from
            navigate(from || getRedirectByRole(nextSession.role), { replace: true })
        } catch (error) {
            notify(error.message || (mode === 'register' ? '注册失败' : '登录失败'), 'danger')
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <section className="page login-page">
            <div className="auth-shell">
                <div className="auth-intro panel">
                    <p className="eyebrow">Welcome Back</p>
                    <h1 className="section-title">登录账号，继续点餐、管理店铺，或者处理配送任务。</h1>
                    <p className="section-subtitle">
                        选择你的身份后即可进入对应页面。
                        如果你是第一次使用，也可以先注册一个新账号再开始体验。
                    </p>

                    <div className="auth-feature-list">
                        <div className="auth-feature">
                            <strong>快速进入</strong>
                            <span>按角色分流，进入后会直接回到你最常用的页面。</span>
                        </div>
                        <div className="auth-feature">
                            <strong>多角色支持</strong>
                            <span>消费者、商家、骑手和管理员都可以从同一个入口进入。</span>
                        </div>
                        <div className="auth-feature">
                            <strong>新用户友好</strong>
                            <span>支持直接注册，也保留了现成账号方便你快速体验。</span>
                        </div>
                    </div>
                </div>

                <div className="panel auth-card">
                    <div className="auth-mode-switch">
                        <button
                            className={`tab ${mode === 'login' ? 'active' : ''}`}
                            type="button"
                            onClick={() => setMode('login')}
                        >
                            登录
                        </button>
                        <button
                            className={`tab ${mode === 'register' ? 'active' : ''}`}
                            type="button"
                            onClick={() => currentRole.canRegister && setMode('register')}
                            disabled={!currentRole.canRegister}
                        >
                            注册
                        </button>
                    </div>

                    <div className="stack tight">
                        <h2 className="section-title">{mode === 'register' ? '创建新账号' : '欢迎回来'}</h2>
                        <p className="section-subtitle">{currentRole.loginHint}</p>
                    </div>

                    <form className="form-grid" onSubmit={handleSubmit}>
                        <label className="form-row">
                            <span>角色</span>
                            <select
                                className="select"
                                value={role}
                                onChange={(event) => handleRoleChange(event.target.value)}
                            >
                                {ROLE_OPTIONS.map((item) => (
                                    <option key={item.value} value={item.value}>{item.label}</option>
                                ))}
                            </select>
                        </label>

                        <label className="form-row">
                            <span>{role === 'rider' && mode === 'login' ? '姓名或手机号' : '用户名'}</span>
                            <input
                                className="input"
                                value={form.username}
                                onChange={(event) => setForm((current) => ({ ...current, username: event.target.value }))}
                                placeholder={mode === 'register' ? '输入登录用户名' : '输入用户名或登录凭证'}
                            />
                        </label>

                        {mode === 'register' ? (
                            <>
                                <label className="form-row">
                                    <span>手机号</span>
                                    <input
                                        className="input"
                                        value={form.phone}
                                        onChange={(event) => setForm((current) => ({ ...current, phone: event.target.value }))}
                                        placeholder="输入 11 位手机号"
                                    />
                                </label>

                                <label className="form-row">
                                    <span>{role === 'merchant' ? '店铺名称' : role === 'rider' ? '骑手昵称' : '昵称'}</span>
                                    <input
                                        className="input"
                                        value={form.nickname}
                                        onChange={(event) => setForm((current) => ({ ...current, nickname: event.target.value }))}
                                        placeholder="输入展示名称"
                                    />
                                </label>
                            </>
                        ) : null}

                        <label className="form-row">
                            <span>密码</span>
                            <input
                                className="input"
                                type="password"
                                value={form.password}
                                onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))}
                                placeholder="输入密码"
                            />
                        </label>

                        <button className="btn primary" type="submit" disabled={submitting}>
                            {submitting ? (mode === 'register' ? '注册中...' : '登录中...') : (mode === 'register' ? '注册并进入' : '进入系统')}
                        </button>
                    </form>

                    <div className="demo-block">
                        <div className="panel-head compact">
                            <div>
                                <h3 className="section-title">快捷体验账号</h3>
                                <p className="section-subtitle">如果你想先看看页面流程，可以直接填充下面的现成账号。</p>
                            </div>
                        </div>
                        <div className="stack">
                            {ROLE_OPTIONS.map((item) => {
                                const account = DEMO_ACCOUNTS[item.value]
                                return (
                                    <button
                                        key={item.value}
                                        className={`demo-card ${role === item.value ? 'active' : ''}`}
                                        type="button"
                                        onClick={() => applyDemo(item.value)}
                                    >
                                        <strong>{item.label}</strong>
                                        <span>{account?.username} / {account?.password}</span>
                                        <small>{account?.note}</small>
                                    </button>
                                )
                            })}
                        </div>
                    </div>
                </div>
            </div>
        </section>
    )
}
