import React from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import RiderConsole from './RiderConsole'

const mockSession = {
    api: {
        dashboard: { getMine: vi.fn() },
        rider: {
            getTasks: vi.fn(),
            getProfile: vi.fn(),
            updateTask: vi.fn(),
            updateProfile: vi.fn()
        }
    },
    notify: vi.fn(),
    updateSessionUser: vi.fn(),
    user: { id: 20, username: 'rider01', nickname: '一号骑手', phone: '13800000021' }
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

const availableTask = {
    id: 900,
    orderNo: 'NO20260826001',
    merchant: '桂香米粉',
    merchantId: 10,
    userId: 1,
    items: '牛肉米粉 x2',
    pickup: '一号食堂二楼',
    destination: '宿舍 3 号楼 302 室',
    status: 'pending_accept',
    total: 38
}

const assignedTask = { ...availableTask, id: 901, orderNo: 'NO20260826002', status: 'delivering' }

describe('RiderConsole page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.dashboard.getMine.mockResolvedValue({ rider: { todayDeliveries: 2, todayEarnings: 12, status: 'active' } })
        mockSession.api.rider.getTasks.mockResolvedValue({ available: [availableTask], assigned: [assignedTask], completed: [] })
        mockSession.api.rider.getProfile.mockResolvedValue({ name: '一号骑手', phone: '13800000021', serviceArea: '校园东区', status: 'active' })
        mockSession.api.rider.updateTask.mockResolvedValue({})
        mockSession.api.rider.updateProfile.mockResolvedValue({ name: '新骑手', phone: '13900000021', serviceArea: '校园西区', status: 'active' })
    })

    it('loads rider metrics, profile, and task lists', async () => {
        renderWithRouter(<RiderConsole />)

        expect(await screen.findByText('今日配送')).toBeInTheDocument()
        expect(screen.getByDisplayValue('一号骑手')).toBeInTheDocument()
        expect(screen.getByText('NO20260826001')).toBeInTheDocument()
        expect(screen.getByText('NO20260826002')).toBeInTheDocument()
    })

    it('accepts an available delivery task', async () => {
        const user = userEvent.setup()
        renderWithRouter(<RiderConsole />)

        await screen.findByText('NO20260826001')
        await user.click(screen.getByRole('button', { name: '立即接单' }))

        await waitFor(() => {
            expect(mockSession.api.rider.updateTask).toHaveBeenCalledWith(900, { status: '待取餐' })
        })
        expect(mockSession.notify).toHaveBeenCalledWith('任务状态已更新', 'success')
    })

    it('completes an assigned delivery task', async () => {
        const user = userEvent.setup()
        renderWithRouter(<RiderConsole />)

        await screen.findByText('NO20260826002')
        await user.click(screen.getByRole('button', { name: '完成配送' }))

        await waitFor(() => {
            expect(mockSession.api.rider.updateTask).toHaveBeenCalledWith(901, { status: '已完成' })
        })
    })

    it('saves rider profile changes and updates session user', async () => {
        const user = userEvent.setup()
        renderWithRouter(<RiderConsole />)

        const nicknameInput = await screen.findByLabelText('昵称')
        await user.clear(nicknameInput)
        await user.type(nicknameInput, '新骑手')
        await user.clear(screen.getByLabelText('手机号'))
        await user.type(screen.getByLabelText('手机号'), '13900000021')
        await user.clear(screen.getByLabelText('服务范围'))
        await user.type(screen.getByLabelText('服务范围'), '校园西区')
        await user.click(screen.getByRole('button', { name: '保存资料' }))

        await waitFor(() => {
            expect(mockSession.api.rider.updateProfile).toHaveBeenCalledWith({
                nickname: '新骑手',
                phone: '13900000021',
                serviceArea: '校园西区'
            })
        })
        expect(mockSession.updateSessionUser).toHaveBeenCalledWith({
            nickname: '新骑手',
            phone: '13900000021',
            status: 'active'
        })
    })
})
