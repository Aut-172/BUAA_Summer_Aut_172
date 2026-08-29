import React from 'react'
import { Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import MessageOrderDetail from './MessageOrderDetail'

const mockSession = {
    api: {
        messages: {
            getOrder: vi.fn()
        }
    },
    notify: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

function renderOrderDetail(route = '/messages/orders/900?targetId=10&targetType=merchant&orderId=900') {
    return renderWithRouter(
        <Routes>
            <Route path="/messages/orders/:orderId" element={<MessageOrderDetail />} />
            <Route path="/messages" element={<div>会话列表</div>} />
        </Routes>,
        { route }
    )
}

const order = {
    id: 900,
    orderNo: 'NO20260826001',
    merchant: '桂香米粉',
    status: 'delivering',
    createdAt: '2026-08-26 12:00:00',
    address: '宿舍 3 号楼 302 室',
    riderName: '一号骑手',
    deliveryFee: 2,
    discount: 5,
    total: 35,
    items: [
        { productId: 101, name: '牛肉米粉', specLabel: '大份', price: 18, quantity: 2 }
    ],
    timeline: [
        { label: '已支付', time: '2026-08-26 12:01:00' },
        { label: '配送中', time: '2026-08-26 12:10:00' }
    ]
}

describe('MessageOrderDetail page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.messages.getOrder.mockResolvedValue(order)
    })

    it('loads conversation order detail, items, and timeline', async () => {
        renderOrderDetail()

        expect(await screen.findByRole('heading', { name: 'NO20260826001' })).toBeInTheDocument()
        expect(mockSession.api.messages.getOrder).toHaveBeenCalledWith('900')
        expect(screen.getAllByText('配送中').length).toBeGreaterThanOrEqual(1)
        expect(screen.getByText('桂香米粉')).toBeInTheDocument()
        expect(screen.getByText('牛肉米粉 · 大份')).toBeInTheDocument()
        expect(screen.getByText('已支付')).toBeInTheDocument()
    })

    it('shows an empty order message when the API returns null', async () => {
        mockSession.api.messages.getOrder.mockResolvedValue(null)

        renderOrderDetail()

        expect(await screen.findByText('订单不存在。')).toBeInTheDocument()
    })

    it('notifies and renders API errors', async () => {
        mockSession.api.messages.getOrder.mockRejectedValue(new Error('订单详情加载失败'))

        renderOrderDetail()

        expect(await screen.findByText('订单详情加载失败')).toBeInTheDocument()
        expect(mockSession.notify).toHaveBeenCalledWith('订单详情加载失败', 'danger')
    })
})
