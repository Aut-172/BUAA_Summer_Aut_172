import React, { createContext, useContext, useEffect, useMemo, useState } from 'react'
import api, { setAuthToken, setUnauthorizedHandler } from './api'

const SESSION_KEY = 'life-service-session'
const ApiContext = createContext(null)

function readSession() {
    try {
        const raw = localStorage.getItem(SESSION_KEY)
        return raw ? JSON.parse(raw) : null
    } catch {
        return null
    }
}

function persistSession(session) {
    if (!session) {
        localStorage.removeItem(SESSION_KEY)
        return
    }

    localStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

function toSession(payload) {
    const user = payload?.user || {}

    return {
        token: payload?.accessToken || '',
        role: user.role || 'consumer',
        merchantId: user.merchantId || null,
        riderId: user.riderId || null,
        user
    }
}

export default function ApiProvider({ children }) {
    const [session, setSession] = useState(null)
    const [bootstrapped, setBootstrapped] = useState(false)
    const [notice, setNotice] = useState(null)

    useEffect(() => {
        const storedSession = readSession()
        setSession(storedSession)
        setAuthToken(storedSession?.token || null)
        setBootstrapped(true)
    }, [])

    useEffect(() => {
        setUnauthorizedHandler(() => {
            setSession(null)
            persistSession(null)
            setAuthToken(null)
            setNotice({
                id: Date.now(),
                message: '登录已过期，请重新登录',
                tone: 'warning'
            })
        })

        return () => {
            setUnauthorizedHandler(null)
        }
    }, [])

    useEffect(() => {
        if (!notice) {
            return undefined
        }

        const timer = window.setTimeout(() => {
            setNotice(null)
        }, 3200)

        return () => window.clearTimeout(timer)
    }, [notice])

    function commitSession(nextSession) {
        setSession(nextSession)
        persistSession(nextSession)
        setAuthToken(nextSession?.token || null)
    }

    function notify(message, tone = 'info') {
        setNotice({
            id: Date.now(),
            message,
            tone
        })
    }

    async function login({ role, username, password, captchaKey, captchaCode }) {
        const payload = await api.auth.login(role, { username, password, captchaKey, captchaCode })
        const nextSession = toSession(payload)
        commitSession(nextSession)
        notify('登录成功', 'success')
        return nextSession
    }

    async function register({ role, username, phone, password, nickname, captchaKey, captchaCode }) {
        await api.auth.register(role, { username, phone, password, nickname, captchaKey, captchaCode })

        if (role === 'merchant' || role === 'rider') {
            notify('注册成功，请等待管理员审核通过后再登录', 'success')
            return null
        }

        notify('注册成功，请使用新账号登录', 'success')
        return null
    }

    function logout(message = '已退出当前账号') {
        commitSession(null)
        notify(message, 'info')
    }

    function updateSessionUser(patch) {
        setSession((current) => {
            if (!current) {
                return current
            }

            const nextUser = typeof patch === 'function'
                ? patch(current.user || {})
                : { ...(current.user || {}), ...(patch || {}) }

            const nextSession = {
                ...current,
                role: nextUser.role || current.role,
                merchantId: nextUser.merchantId || current.merchantId,
                riderId: nextUser.riderId || current.riderId,
                user: nextUser
            }

            persistSession(nextSession)
            setAuthToken(nextSession.token || null)
            return nextSession
        })
    }

    const value = useMemo(() => ({
        api,
        session,
        user: session?.user || null,
        role: session?.role || null,
        isAuthenticated: Boolean(session?.token),
        bootstrapped,
        notice,
        notify,
        login,
        register,
        logout,
        updateSessionUser
    }), [bootstrapped, notice, session])

    return <ApiContext.Provider value={value}>{children}</ApiContext.Provider>
}

export function useSession() {
    const context = useContext(ApiContext)

    if (!context) {
        throw new Error('useSession must be used inside ApiProvider')
    }

    return context
}
