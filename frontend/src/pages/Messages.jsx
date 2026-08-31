import React, { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useSession } from '../utils/ApiProvider'
import { FALLBACK_PROFILE_IMAGE, normalizeImageSrc } from '../utils/demoImages'
import { formatDateTime } from '../utils/format'

const POLL_MESSAGES_MS = 5000
const POLL_THREADS_MS = 10000

const roleTypeMap = {
    consumer: 'user',
    merchant: 'merchant',
    rider: 'rider'
}

const typeLabelMap = {
    user: '用户',
    merchant: '商家',
    rider: '骑手'
}

function getSelection(searchParams) {
    const targetId = searchParams.get('targetId') || ''
    const targetType = searchParams.get('targetType') || ''

    if (!targetId || !targetType) {
        return null
    }

    return {
        targetId,
        targetType,
        orderId: searchParams.get('orderId') || '',
        targetName: searchParams.get('targetName') || '',
        orderNo: searchParams.get('orderNo') || ''
    }
}

function isSameThread(thread, selection) {
    return Boolean(thread && selection)
        && String(thread.targetId) === String(selection.targetId)
        && thread.targetType === selection.targetType
        && String(thread.orderId || '') === String(selection.orderId || '')
}

export default function Messages() {
    const { api, notify, role, user } = useSession()
    const [searchParams, setSearchParams] = useSearchParams()
    const [threads, setThreads] = useState([])
    const [messages, setMessages] = useState([])
    const [draft, setDraft] = useState('')
    const [loadingThreads, setLoadingThreads] = useState(true)
    const [loadingMessages, setLoadingMessages] = useState(false)
    const [sending, setSending] = useState(false)
    const messageEndRef = useRef(null)
    const senderType = roleTypeMap[role] || ''
    const selection = useMemo(() => getSelection(searchParams), [searchParams])
    const selectedThread = threads.find((thread) => isSameThread(thread, selection))
    const currentContactName = selectedThread?.targetName || selection?.targetName || '当前联系人'
    const currentOrderNo = selectedThread?.orderNo || selection?.orderNo || selection?.orderId || ''
    const orderDetailPath = selection?.orderId
        ? `/messages/orders/${selection.orderId}?${searchParams.toString()}`
        : ''

    async function loadThreads({ silent = false } = {}) {
        if (!silent) {
            setLoadingThreads(true)
        }
        try {
            const data = await api.messages.getThreads()
            setThreads(Array.isArray(data) ? data : [])
        } catch (error) {
            notify(error.message || '加载会话失败', 'danger')
        } finally {
            if (!silent) {
                setLoadingThreads(false)
            }
        }
    }

    async function loadMessages({ silent = false } = {}) {
        if (!selection) {
            setMessages([])
            return
        }
        if (!silent) {
            setLoadingMessages(true)
        }
        try {
            const data = await api.messages.list({
                targetId: selection.targetId,
                targetType: selection.targetType,
                ...(selection.orderId ? { orderId: selection.orderId } : {})
            })
            setMessages(Array.isArray(data) ? data : [])
        } catch (error) {
            notify(error.message || '加载消息失败', 'danger')
        } finally {
            if (!silent) {
                setLoadingMessages(false)
            }
        }
    }

    useEffect(() => {
        loadThreads()
        const timer = window.setInterval(() => loadThreads({ silent: true }), POLL_THREADS_MS)
        return () => window.clearInterval(timer)
    }, [])

    useEffect(() => {
        loadMessages()
        if (!selection) {
            return undefined
        }
        const timer = window.setInterval(() => loadMessages({ silent: true }), POLL_MESSAGES_MS)
        return () => window.clearInterval(timer)
    }, [selection?.targetId, selection?.targetType, selection?.orderId])

    useEffect(() => {
        messageEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }, [messages.length, selection?.targetId, selection?.targetType, selection?.orderId])

    function openThread(thread) {
        const nextParams = {
            targetId: thread.targetId,
            targetType: thread.targetType
        }
        if (thread.orderId) {
            nextParams.orderId = thread.orderId
        }
        if (thread.targetName) {
            nextParams.targetName = thread.targetName
        }
        if (thread.orderNo) {
            nextParams.orderNo = thread.orderNo
        }
        setSearchParams(nextParams)
    }

    async function handleSend(event) {
        event.preventDefault()
        const content = draft.trim()
        if (!selection || !content) {
            return
        }
        if (!selection.orderId) {
            notify('请选择订单会话后再发送消息', 'warning')
            return
        }

        setSending(true)
        try {
            await api.messages.send({
                receiverId: selection.targetId,
                receiverType: selection.targetType,
                orderId: selection.orderId,
                content
            })
            setDraft('')
            await Promise.all([
                loadMessages({ silent: true }),
                loadThreads({ silent: true })
            ])
        } catch (error) {
            notify(error.message || '发送消息失败', 'danger')
        } finally {
            setSending(false)
        }
    }

    return (
        <section className="page messages-page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">消息</h1>
                    <p className="section-subtitle">订单相关沟通会在这里汇总。</p>
                </div>
            </div>

            <div className="messages-layout">
                <aside className="panel thread-panel">
                    <div className="panel-head compact">
                        <div>
                            <h2 className="section-title">会话</h2>
                            <p className="section-subtitle">共 {threads.length} 个</p>
                        </div>
                    </div>

                    {loadingThreads ? (
                        <div className="empty-state">正在加载会话...</div>
                    ) : threads.length === 0 ? (
                        <div className="empty-state">暂无会话。</div>
                    ) : (
                        <div className="thread-list">
                            {threads.map((thread) => (
                                <button
                                    className={`thread-item ${isSameThread(thread, selection) ? 'active' : ''}`}
                                    key={`${thread.targetType}-${thread.targetId}-${thread.orderId || 'none'}`}
                                    type="button"
                                    onClick={() => openThread(thread)}
                                >
                                    <span className="thread-avatar">
                                        {thread.targetAvatar ? <img src={normalizeImageSrc(thread.targetAvatar, FALLBACK_PROFILE_IMAGE)} alt="" /> : (thread.targetName || '?').slice(0, 1)}
                                    </span>
                                    <span className="thread-main">
                                        <span className="thread-title-row">
                                            <strong>{thread.targetName || '未知联系人'}</strong>
                                            {thread.unreadCount ? <em>{thread.unreadCount}</em> : null}
                                        </span>
                                        <span className="thread-meta">{typeLabelMap[thread.targetType] || thread.targetType} · {thread.orderNo || thread.orderId || '未关联订单'}</span>
                                        <span className="thread-preview">{thread.lastMessage || '暂无内容'}</span>
                                    </span>
                                </button>
                            ))}
                        </div>
                    )}
                </aside>

                <section className="panel chat-panel">
                    {!selection ? (
                        <div className="empty-state">请选择一个会话。</div>
                    ) : (
                        <>
                            <div className="chat-head">
                                <div>
                                    <h2 className="section-title">{currentContactName}</h2>
                                    <p className="section-subtitle">
                                        {typeLabelMap[selection.targetType] || selection.targetType} · 订单 {currentOrderNo || '未关联'}
                                    </p>
                                </div>
                                {orderDetailPath ? <Link className="btn ghost small" to={orderDetailPath}>查看订单</Link> : null}
                            </div>

                            <div className="message-list">
                                {loadingMessages ? (
                                    <div className="empty-state">正在加载消息...</div>
                                ) : messages.length === 0 ? (
                                    <div className="empty-state">还没有消息。</div>
                                ) : messages.map((message) => {
                                    const mine = String(message.senderId) === String(user?.id) && message.senderType === senderType
                                    return (
                                        <div className={`message-row ${mine ? 'mine' : 'theirs'}`} key={String(message.id)}>
                                            <div className="message-bubble">
                                                <p>{message.content}</p>
                                                <span>{formatDateTime(message.createTime)}</span>
                                            </div>
                                        </div>
                                    )
                                })}
                                <div ref={messageEndRef} />
                            </div>

                            <form className="message-compose" onSubmit={handleSend}>
                                <textarea
                                    className="textarea"
                                    rows="3"
                                    maxLength="500"
                                    value={draft}
                                    onChange={(event) => setDraft(event.target.value)}
                                    placeholder="输入消息"
                                />
                                <div className="compose-actions">
                                    <span className="helper">{draft.trim().length}/500</span>
                                    <button className="btn primary" type="submit" disabled={sending || !draft.trim()}>
                                        {sending ? '发送中...' : '发送'}
                                    </button>
                                </div>
                            </form>
                        </>
                    )}
                </section>
            </div>
        </section>
    )
}
