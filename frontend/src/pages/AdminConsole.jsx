import React, { useEffect, useState } from 'react'
import { useSession } from '../utils/ApiProvider'
import { formatDateTime, formatMoney, formatStatusText, getStatusTone } from '../utils/format'

export default function AdminConsole() {
    const { api, notify } = useSession()
    const [usersPage, setUsersPage] = useState({ items: [], total: 0 })
    const [merchantsPage, setMerchantsPage] = useState({ items: [], total: 0 })
    const [ridersPage, setRidersPage] = useState({ items: [], total: 0 })
    const [ordersPage, setOrdersPage] = useState({ items: [], total: 0 })
    const [activeTab, setActiveTab] = useState('users')
    const [loading, setLoading] = useState(true)

    async function loadData() {
        setLoading(true)
        try {
            const [users, merchants, riders, orders] = await Promise.all([
                api.admin.getUsers({ page: 1, pageSize: 20 }),
                api.admin.getMerchants({ page: 1, pageSize: 20 }),
                api.admin.getRiders({ page: 1, pageSize: 20 }),
                api.admin.getOrders({ page: 1, pageSize: 20 })
            ])

            setUsersPage(users || { items: [], total: 0 })
            setMerchantsPage(merchants || { items: [], total: 0 })
            setRidersPage(riders || { items: [], total: 0 })
            setOrdersPage(orders || { items: [], total: 0 })
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadData()
    }, [])

    async function withReload(task, successMessage) {
        try {
            await task()
            notify(successMessage, 'success')
            await loadData()
        } catch (error) {
            notify(error.message || '操作失败', 'danger')
        }
    }

    function renderUserTable() {
        return (
            <div className="table-wrap">
                <table className="table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>昵称</th>
                            <th>手机号</th>
                            <th>状态</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {usersPage.items.map((item) => (
                            <tr key={item.id}>
                                <td>{item.id}</td>
                                <td>{item.nickname || item.username}</td>
                                <td>{item.phone || '-'}</td>
                                <td><span className={`status-chip ${getStatusTone(item.status)}`}>{formatStatusText(item.status)}</span></td>
                                <td className="table-actions">
                                    {item.status === 'frozen' ? (
                                        <button className="btn primary small" type="button" onClick={() => withReload(() => api.admin.unfreezeUser(item.id), '用户已解冻')}>解冻</button>
                                    ) : (
                                        <button className="btn danger small" type="button" onClick={() => withReload(() => api.admin.freezeUser(item.id), '用户已冻结')}>冻结</button>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        )
    }

    function renderMerchantTable() {
        return (
            <div className="table-wrap">
                <table className="table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>店铺名</th>
                            <th>分类</th>
                            <th>状态</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {merchantsPage.items.map((item) => (
                            <tr key={item.id}>
                                <td>{item.id}</td>
                                <td>{item.name}</td>
                                <td>{item.category || '-'}</td>
                                <td><span className={`status-chip ${getStatusTone(item.status)}`}>{formatStatusText(item.status)}</span></td>
                                <td className="table-actions">
                                    <button className="btn ghost small" type="button" onClick={() => withReload(() => api.admin.auditMerchant(item.id, { status: 'active', opinion: '通过' }), '商家已通过审核')}>通过审核</button>
                                    {item.status === 'frozen' ? (
                                        <button className="btn primary small" type="button" onClick={() => withReload(() => api.admin.unfreezeMerchant(item.id), '商家已解冻')}>解冻</button>
                                    ) : (
                                        <button className="btn danger small" type="button" onClick={() => withReload(() => api.admin.freezeMerchant(item.id), '商家已冻结')}>冻结</button>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        )
    }

    function renderRiderTable() {
        return (
            <div className="table-wrap">
                <table className="table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>姓名</th>
                            <th>手机号</th>
                            <th>状态</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        {ridersPage.items.map((item) => (
                            <tr key={item.id}>
                                <td>{item.id}</td>
                                <td>{item.name}</td>
                                <td>{item.phone}</td>
                                <td><span className={`status-chip ${getStatusTone(item.status)}`}>{formatStatusText(item.status)}</span></td>
                                <td className="table-actions">
                                    <button className="btn ghost small" type="button" onClick={() => withReload(() => api.admin.auditRider(item.id, { status: 'active', opinion: '通过' }), '骑手已通过审核')}>通过审核</button>
                                    {item.status === 'frozen' ? (
                                        <button className="btn primary small" type="button" onClick={() => withReload(() => api.admin.unfreezeRider(item.id), '骑手已解冻')}>解冻</button>
                                    ) : (
                                        <button className="btn danger small" type="button" onClick={() => withReload(() => api.admin.freezeRider(item.id), '骑手已冻结')}>冻结</button>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        )
    }

    function renderOrdersTable() {
        return (
            <div className="table-wrap">
                <table className="table">
                    <thead>
                        <tr>
                            <th>订单号</th>
                            <th>用户</th>
                            <th>商家</th>
                            <th>状态</th>
                            <th>金额</th>
                            <th>创建时间</th>
                        </tr>
                    </thead>
                    <tbody>
                        {ordersPage.items.map((item) => (
                            <tr key={item.id}>
                                <td>{item.orderNo}</td>
                                <td>{item.userId}</td>
                                <td>{item.merchantId}</td>
                                <td><span className={`status-chip ${getStatusTone(item.status)}`}>{formatStatusText(item.status)}</span></td>
                                <td>{formatMoney(item.actualAmount || item.totalAmount || 0)}</td>
                                <td>{formatDateTime(item.createTime)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        )
    }

    return (
        <section className="page">
            <div className="panel-head">
                <div>
                    <h1 className="section-title">管理员工作台</h1>
                    <p className="section-subtitle">当前聚焦列表、审核、冻结与订单查看等后端已稳定支持的接口。</p>
                </div>
            </div>

            <div className="metric-grid">
                <div className="metric-card"><span>消费者</span><strong>{usersPage.total}</strong></div>
                <div className="metric-card"><span>商家</span><strong>{merchantsPage.total}</strong></div>
                <div className="metric-card"><span>骑手</span><strong>{ridersPage.total}</strong></div>
                <div className="metric-card"><span>订单</span><strong>{ordersPage.total}</strong></div>
            </div>

            <div className="tabs">
                <button className={`tab ${activeTab === 'users' ? 'active' : ''}`} type="button" onClick={() => setActiveTab('users')}>用户</button>
                <button className={`tab ${activeTab === 'merchants' ? 'active' : ''}`} type="button" onClick={() => setActiveTab('merchants')}>商家</button>
                <button className={`tab ${activeTab === 'riders' ? 'active' : ''}`} type="button" onClick={() => setActiveTab('riders')}>骑手</button>
                <button className={`tab ${activeTab === 'orders' ? 'active' : ''}`} type="button" onClick={() => setActiveTab('orders')}>订单</button>
            </div>

            {loading ? (
                <div className="panel empty-state">正在加载管理数据...</div>
            ) : (
                <section className="panel">
                    {activeTab === 'users' ? renderUserTable() : null}
                    {activeTab === 'merchants' ? renderMerchantTable() : null}
                    {activeTab === 'riders' ? renderRiderTable() : null}
                    {activeTab === 'orders' ? renderOrdersTable() : null}
                </section>
            )}
        </section>
    )
}
