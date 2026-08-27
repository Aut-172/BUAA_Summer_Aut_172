import React from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import Messages from './Messages'

const mockSession = {
    api: {
        messages: {
            getThreads: vi.fn(),
            list: vi.fn(),
            send: vi.fn()
        }
    },
    notify: vi.fn(),
    role: 'consumer',
    user: { id: 1, username: 'student01' }
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

const threads = [
    {
        targetId: 10,
        targetType: 'merchant',
        orderId: 900,
        targetName: '桂香米粉',
        orderNo: 'NO20260826001',
        unreadCount: 2,
        lastMessage: '订单马上出餐'
    }
]

const messages = [
    { id: 1, senderId: 10, senderType: 'merchant', content: '订单马上出餐', createTime: '2026-08-26 12:00:00' },
    { id: 2, senderId: 1, senderType: 'user', content: '好的，谢谢', createTime: '2026-08-26 12:01:00' }
]

describe('Messages page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.messages.getThreads.mockResolvedValue(threads)
        mockSession.api.messages.list.mockResolvedValue(messages)
        mockSession.api.messages.send.mockResolvedValue({})
    })

    it('loads threads and selected order messages from URL params', async () => {
        renderWithRouter(<Messages />, { route: '/messages?targetId=10&targetType=merchant&orderId=900&targetName=桂香米粉&orderNo=NO20260826001' })

        expect(await screen.findByRole('heading', { name: '桂香米粉' })).toBeInTheDocument()
        expect(screen.getAllByText('订单马上出餐')).toHaveLength(2)
        expect(screen.getByText('好的，谢谢')).toBeInTheDocument()
        expect(mockSession.api.messages.list).toHaveBeenCalledWith({
            targetId: '10',
            targetType: 'merchant',
            orderId: '900'
        })
    })

    it('opens a thread from the thread list and loads its messages', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Messages />)

        await screen.findByText('订单马上出餐')
        await user.click(screen.getByRole('button', { name: /桂香米粉/ }))

        await waitFor(() => {
            expect(mockSession.api.messages.list).toHaveBeenCalledWith({
                targetId: '10',
                targetType: 'merchant',
                orderId: '900'
            })
        })
    })

    it('sends a message in the selected order conversation and refreshes data', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Messages />, { route: '/messages?targetId=10&targetType=merchant&orderId=900&targetName=桂香米粉&orderNo=NO20260826001' })

        await screen.findByText('好的，谢谢')
        await user.type(screen.getByPlaceholderText('输入消息'), '请尽快送达')
        await user.click(screen.getByRole('button', { name: '发送' }))

        await waitFor(() => {
            expect(mockSession.api.messages.send).toHaveBeenCalledWith({
                receiverId: '10',
                receiverType: 'merchant',
                orderId: '900',
                content: '请尽快送达'
            })
        })
        expect(mockSession.api.messages.getThreads).toHaveBeenCalledTimes(2)
        expect(mockSession.api.messages.list).toHaveBeenCalledTimes(2)
    })
})
