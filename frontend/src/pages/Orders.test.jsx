import React from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import Orders from './Orders'

const mockSession = {
    api: {
        orders: {
            list: vi.fn(),
            pay: vi.fn(),
            cancel: vi.fn(),
            complete: vi.fn(),
            getPayments: vi.fn()
        }
    },
    notify: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

function buildOrder(patch = {}) {
    return {
        id: 900,
        orderNo: 'NO20260826001',
        merchant: '桂香米粉',
        merchantId: 10,
        riderId: 20,
        riderName: '一号骑手',
        status: 'pending_payment',
        createdAt: '2026-08-26 12:00:00',
        address: '宿舍 3 号楼 302 室',
        total: 38,
        items: [
            { productId: 101, name: '牛肉米粉', quantity: 2, specLabel: '大份', reviewed: false }
        ],
        timeline: [],
        ...patch
    }
}

describe('Orders page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.orders.list.mockResolvedValue([buildOrder()])
        mockSession.api.orders.pay.mockResolvedValue({})
        mockSession.api.orders.cancel.mockResolvedValue({})
        mockSession.api.orders.complete.mockResolvedValue({})
        mockSession.api.orders.getPayments.mockResolvedValue([
            { id: 1, payMethod: 'ALIPAY', status: 'SUCCESS', amount: 38, payTime: '2026-08-26 12:01:00' }
        ])
    })

    it('loads orders and pays a pending payment order', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Orders />)

        expect(await screen.findByText('订单号 NO20260826001')).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: '立即支付' }))

        await waitFor(() => {
            expect(mockSession.api.orders.pay).toHaveBeenCalledWith(900, { payMethod: 'ALIPAY' })
        })
        expect(mockSession.notify).toHaveBeenCalledWith('订单支付成功', 'success')
        expect(mockSession.api.orders.list).toHaveBeenCalledTimes(2)
    })

    it('cancels a cancelable order', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Orders />)

        await screen.findByText('订单号 NO20260826001')
        await user.click(screen.getByRole('button', { name: '取消订单' }))

        await waitFor(() => {
            expect(mockSession.api.orders.cancel).toHaveBeenCalledWith(900)
        })
        expect(mockSession.notify).toHaveBeenCalledWith('订单已取消', 'success')
    })

    it('completes a delivering order', async () => {
        const user = userEvent.setup()
        mockSession.api.orders.list.mockResolvedValue([buildOrder({ status: 'delivering' })])
        renderWithRouter(<Orders />)

        await screen.findByText('配送中')
        await user.click(screen.getByRole('button', { name: '确认收货' }))

        await waitFor(() => {
            expect(mockSession.api.orders.complete).toHaveBeenCalledWith(900)
        })
        expect(mockSession.notify).toHaveBeenCalledWith('订单已确认完成', 'success')
    })

    it('loads and displays payment records', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Orders />)

        await screen.findByText('订单号 NO20260826001')
        await user.click(screen.getByRole('button', { name: '查看支付记录' }))

        await waitFor(() => {
            expect(mockSession.api.orders.getPayments).toHaveBeenCalledWith(900)
        })
        expect(screen.getByText('ALIPAY · 成功')).toBeInTheDocument()
        expect(screen.getByText('￥38.00 · 2026-08-26 12:01:00')).toBeInTheDocument()
    })
})
