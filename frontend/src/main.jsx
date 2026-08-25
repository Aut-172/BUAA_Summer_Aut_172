import React from 'react'
import { createRoot } from 'react-dom/client'
import {
    BrowserRouter,
    Link,
    NavLink,
    Navigate,
    Route,
    Routes,
    useLocation
} from 'react-router-dom'
import Home from './pages/Home'
import Login from './pages/Login'
import MerchantDetail from './pages/MerchantDetail'
import Cart from './pages/Cart'
import Checkout from './pages/Checkout'
import Orders from './pages/Orders'
import Coupons from './pages/Coupons'
import Profile from './pages/Profile'
import Delivery from './pages/Delivery'
import MerchantConsole from './pages/MerchantConsole'
import RiderConsole from './pages/RiderConsole'
import AdminConsole from './pages/AdminConsole'
import ApiProvider, { useSession } from './utils/ApiProvider'
import { formatRole } from './utils/format'
import './style.css'

function getDefaultRoute(role) {
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

function ProtectedRoute({ roles, children }) {
    const { bootstrapped, isAuthenticated, role } = useSession()
    const location = useLocation()

    if (!bootstrapped) {
        return <div className="page"><div className="panel empty-state">正在加载登录状态...</div></div>
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
    }

    if (roles && !roles.includes(role)) {
        return <Navigate to={getDefaultRoute(role)} replace />
    }

    return children
}

function AppLayout() {
    const { user, role, isAuthenticated, logout, notice } = useSession()
    const showConsumerNav = !isAuthenticated || role === 'consumer'
    const primaryActionTarget = isAuthenticated ? getDefaultRoute(role) : '/login'
    const primaryActionLabel = isAuthenticated ? '进入首页' : '登录或注册'

    return (
        <>
            <header className="topbar">
                <div className="topbar-inner">
                    <Link className="brand" to="/">
                        <span className="brand-mark">LS</span>
                        <span className="brand-copy">
                            <strong>Life Service</strong>
                            <span>校园生活服务平台</span>
                        </span>
                    </Link>

                    <nav className="nav-links">
                        <NavLink className="nav-link" to="/">首页</NavLink>
                        {showConsumerNav ? <NavLink className="nav-link" to="/cart">购物车</NavLink> : null}
                        {showConsumerNav ? <NavLink className="nav-link" to="/orders">订单</NavLink> : null}
                        {showConsumerNav ? <NavLink className="nav-link" to="/coupons">优惠券</NavLink> : null}
                        {showConsumerNav ? <NavLink className="nav-link" to="/profile">我的</NavLink> : null}
                        {role === 'merchant' ? <NavLink className="nav-link" to="/merchant-center">商家中心</NavLink> : null}
                        {role === 'rider' ? <NavLink className="nav-link" to="/rider-center">骑手中心</NavLink> : null}
                        {role === 'admin' ? <NavLink className="nav-link" to="/admin-center">管理中心</NavLink> : null}
                    </nav>

                    <div className="session-box">
                        {isAuthenticated ? (
                            <>
                                <span className={`role-chip ${role || 'guest'}`}>{formatRole(role)}</span>
                                <div className="session-meta">
                                    <strong>{user?.nickname || user?.username || '已登录用户'}</strong>
                                    <span>{user?.phone || '欢迎回来'}</span>
                                </div>
                                <button className="btn ghost small" type="button" onClick={() => logout()}>
                                    退出
                                </button>
                            </>
                        ) : (
                            <Link className="btn primary small" to={primaryActionTarget}>{primaryActionLabel}</Link>
                        )}
                    </div>
                </div>
            </header>

            <main className="shell">
                <Routes>
                    <Route path="/" element={<Home />} />
                    <Route path="/login" element={<Login />} />
                    <Route path="/merchants/:merchantId" element={<MerchantDetail />} />
                    <Route
                        path="/cart"
                        element={(
                            <ProtectedRoute roles={['consumer']}>
                                <Cart />
                            </ProtectedRoute>
                        )}
                    />
                    <Route
                        path="/checkout"
                        element={(
                            <ProtectedRoute roles={['consumer']}>
                                <Checkout />
                            </ProtectedRoute>
                        )}
                    />
                    <Route
                        path="/orders"
                        element={(
                            <ProtectedRoute roles={['consumer']}>
                                <Orders />
                            </ProtectedRoute>
                        )}
                    />
                    <Route
                        path="/coupons"
                        element={(
                            <ProtectedRoute roles={['consumer']}>
                                <Coupons />
                            </ProtectedRoute>
                        )}
                    />
                    <Route
                        path="/profile"
                        element={(
                            <ProtectedRoute roles={['consumer']}>
                                <Profile />
                            </ProtectedRoute>
                        )}
                    />
                    <Route
                        path="/delivery/:orderId"
                        element={(
                            <ProtectedRoute roles={['consumer']}>
                                <Delivery />
                            </ProtectedRoute>
                        )}
                    />
                    <Route
                        path="/merchant-center"
                        element={(
                            <ProtectedRoute roles={['merchant']}>
                                <MerchantConsole />
                            </ProtectedRoute>
                        )}
                    />
                    <Route
                        path="/rider-center"
                        element={(
                            <ProtectedRoute roles={['rider']}>
                                <RiderConsole />
                            </ProtectedRoute>
                        )}
                    />
                    <Route
                        path="/admin-center"
                        element={(
                            <ProtectedRoute roles={['admin']}>
                                <AdminConsole />
                            </ProtectedRoute>
                        )}
                    />
                    <Route path="*" element={<NotFound />} />
                </Routes>
            </main>

            {notice ? <div className={`toast ${notice.tone}`}>{notice.message}</div> : null}
        </>
    )
}

function NotFound() {
    return (
        <section className="page">
            <div className="panel empty-state">
                页面不存在，请返回首页继续浏览。
            </div>
        </section>
    )
}

function App() {
    return (
        <ApiProvider>
            <BrowserRouter>
                <AppLayout />
            </BrowserRouter>
        </ApiProvider>
    )
}

createRoot(document.getElementById('app')).render(<App />)
