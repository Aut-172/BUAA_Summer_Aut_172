import React from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import Profile from './Profile'

const mockSession = {
    api: {
        user: {
            getProfile: vi.fn(),
            updateProfile: vi.fn(),
            getAddresses: vi.fn(),
            addAddress: vi.fn(),
            updateAddress: vi.fn(),
            deleteAddress: vi.fn(),
            getFavoriteMerchants: vi.fn(),
            deleteFavoriteMerchant: vi.fn()
        },
        uploads: {
            images: vi.fn()
        }
    },
    notify: vi.fn(),
    updateSessionUser: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

const profile = {
    username: 'student01',
    nickname: '校园用户',
    phone: '13800000001',
    avatar: 'https://example.com/avatar.png'
}

const addresses = [
    { id: 100, name: '测试用户', phone: '13800000001', detail: '宿舍 3 号楼 302 室', isDefault: true }
]

const favorites = [
    {
        favoriteId: 1,
        merchantId: 10,
        name: '桂香米粉',
        description: '热汤米粉和小吃',
        category: '快餐',
        monthlySales: 88,
        deliveryFee: 2,
        tags: '米粉,夜宵'
    }
]

describe('Profile page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.user.getProfile.mockResolvedValue(profile)
        mockSession.api.user.getAddresses.mockResolvedValue(addresses)
        mockSession.api.user.getFavoriteMerchants.mockResolvedValue(favorites)
        mockSession.api.user.updateProfile.mockResolvedValue({ ...profile, nickname: '新昵称' })
        mockSession.api.user.addAddress.mockResolvedValue({})
        mockSession.api.user.updateAddress.mockResolvedValue({})
        mockSession.api.user.deleteAddress.mockResolvedValue({})
        mockSession.api.user.deleteFavoriteMerchant.mockResolvedValue({})
    })

    it('loads profile, addresses, and favorite merchants', async () => {
        renderWithRouter(<Profile />)

        expect(await screen.findByRole('heading', { name: '个人资料' })).toBeInTheDocument()
        expect(screen.getByDisplayValue('校园用户')).toBeInTheDocument()
        expect(screen.getByText('测试用户 13800000001 宿舍 3 号楼 302 室')).toBeInTheDocument()
        expect(screen.getByRole('heading', { name: '桂香米粉' })).toBeInTheDocument()
    })

    it('saves profile changes and updates the current session user', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Profile />)

        const nicknameInput = await screen.findByLabelText('昵称')
        await user.clear(nicknameInput)
        await user.type(nicknameInput, '新昵称')
        await user.click(screen.getByRole('button', { name: '保存资料' }))

        await waitFor(() => {
            expect(mockSession.api.user.updateProfile).toHaveBeenCalledWith({
                nickname: '新昵称',
                phone: '13800000001',
                avatar: 'https://example.com/avatar.png'
            })
        })
        expect(mockSession.updateSessionUser).toHaveBeenCalledWith({
            nickname: '新昵称',
            phone: '13800000001',
            avatar: 'https://example.com/avatar.png'
        })
        expect(mockSession.notify).toHaveBeenCalledWith('个人资料已更新', 'success')
    })

    it('adds a new default address', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Profile />)

        await screen.findByRole('heading', { name: '个人资料' })
        await user.type(screen.getByLabelText('收货人'), '新用户')
        await user.type(screen.getByLabelText('联系电话'), '13900000000')
        await user.type(screen.getByLabelText('详细地址'), '教学楼 A101')
        await user.click(screen.getByLabelText('设为默认地址'))
        await user.click(screen.getByRole('button', { name: '新增地址' }))

        await waitFor(() => {
            expect(mockSession.api.user.addAddress).toHaveBeenCalledWith({
                name: '新用户',
                phone: '13900000000',
                detail: '教学楼 A101',
                isDefault: true
            })
        })
        expect(mockSession.notify).toHaveBeenCalledWith('地址已新增', 'success')
    })

    it('removes a favorite merchant from the list', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Profile />)

        await screen.findByRole('heading', { name: '桂香米粉' })
        await user.click(screen.getByRole('button', { name: '取消收藏' }))

        await waitFor(() => {
            expect(mockSession.api.user.deleteFavoriteMerchant).toHaveBeenCalledWith(10)
        })
        expect(mockSession.notify).toHaveBeenCalledWith('已取消收藏', 'success')
        expect(screen.queryByRole('heading', { name: '桂香米粉' })).not.toBeInTheDocument()
    })
})
