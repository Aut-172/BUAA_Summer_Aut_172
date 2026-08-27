import React from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import AdminConsole from './AdminConsole'

const mockSession = {
    api: {
        admin: {
            getUsers: vi.fn(),
            getMerchants: vi.fn(),
            getRiders: vi.fn(),
            getOrders: vi.fn(),
            auditMerchant: vi.fn(),
            auditRider: vi.fn(),
            freezeUser: vi.fn(),
            unfreezeUser: vi.fn(),
            freezeMerchant: vi.fn(),
            unfreezeMerchant: vi.fn(),
            freezeRider: vi.fn(),
            unfreezeRider: vi.fn()
        }
    },
    notify: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

describe('AdminConsole page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.admin.getUsers.mockResolvedValue({ total: 1, items: [{ id: 1, username: 'student01', nickname: '校园用户', phone: '13800000001', status: 'active' }] })
        mockSession.api.admin.getMerchants.mockResolvedValue({ total: 1, items: [{ id: 10, name: '桂香米粉', category: '快餐', status: 'pending' }] })
        mockSession.api.admin.getRiders.mockResolvedValue({ total: 1, items: [{ id: 20, name: '一号骑手', phone: '13800000021', status: 'pending' }] })
        mockSession.api.admin.getOrders.mockResolvedValue({ total: 1, items: [{ id: 900, orderNo: 'NO20260826001', userId: 1, merchantId: 10, status: 'completed', actualAmount: 38, createTime: '2026-08-26 12:00:00' }] })
        mockSession.api.admin.freezeUser.mockResolvedValue({})
        mockSession.api.admin.auditMerchant.mockResolvedValue({})
        mockSession.api.admin.auditRider.mockResolvedValue({})
    })

    it('loads platform management totals and user table', async () => {
        renderWithRouter(<AdminConsole />)

        expect(await screen.findByText('校园用户')).toBeInTheDocument()
        expect(mockSession.api.admin.getUsers).toHaveBeenCalledWith({ page: 1, pageSize: 20 })
        expect(screen.getByText('消费者')).toBeInTheDocument()
        expect(screen.getByRole('button', { name: '订单' })).toBeInTheDocument()
    })

    it('freezes an active user and reloads management data', async () => {
        const user = userEvent.setup()
        renderWithRouter(<AdminConsole />)

        await screen.findByText('校园用户')
        await user.click(screen.getByRole('button', { name: '冻结' }))

        await waitFor(() => {
            expect(mockSession.api.admin.freezeUser).toHaveBeenCalledWith(1)
        })
        expect(mockSession.notify).toHaveBeenCalledWith('用户已冻结', 'success')
        expect(mockSession.api.admin.getUsers).toHaveBeenCalledTimes(2)
    })

    it('approves a pending merchant from the merchant tab', async () => {
        const user = userEvent.setup()
        renderWithRouter(<AdminConsole />)

        await screen.findByText('校园用户')
        await user.click(screen.getByRole('button', { name: '商家' }))
        await user.click(screen.getByRole('button', { name: '通过审核' }))

        await waitFor(() => {
            expect(mockSession.api.admin.auditMerchant).toHaveBeenCalledWith(10, { status: 'active', opinion: '通过' })
        })
        expect(mockSession.notify).toHaveBeenCalledWith('商家已通过审核', 'success')
    })

    it('shows platform orders in the order tab', async () => {
        const user = userEvent.setup()
        renderWithRouter(<AdminConsole />)

        await screen.findByText('校园用户')
        await user.click(screen.getByRole('button', { name: '订单' }))

        expect(screen.getByText('NO20260826001')).toBeInTheDocument()
        expect(screen.getByText('￥38.00')).toBeInTheDocument()
        expect(screen.getByText('已完成')).toBeInTheDocument()
    })
})
