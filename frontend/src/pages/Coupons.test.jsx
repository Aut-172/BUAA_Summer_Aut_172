import React from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import Coupons from './Coupons'

const mockSession = {
    api: {
        coupons: {
            getMine: vi.fn(),
            getAvailable: vi.fn(),
            claim: vi.fn()
        }
    },
    notify: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

const availableCoupon = {
    id: 301,
    title: '新人满减券',
    description: '满 20 减 5',
    threshold: 20,
    discount: 5,
    status: 'released',
    expireAt: '2026-12-31 23:59:59'
}

describe('Coupons page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.coupons.getMine.mockResolvedValue([])
        mockSession.api.coupons.getAvailable.mockResolvedValue([availableCoupon])
        mockSession.api.coupons.claim.mockResolvedValue({})
    })

    it('loads available and owned coupons', async () => {
        mockSession.api.coupons.getMine.mockResolvedValue([{ ...availableCoupon, status: 'unused' }])

        renderWithRouter(<Coupons />)

        expect(await screen.findByRole('heading', { name: '优惠券中心' })).toBeInTheDocument()
        expect(screen.getAllByText('新人满减券')).toHaveLength(2)
        expect(screen.getByRole('heading', { name: '可领取' })).toBeInTheDocument()
        expect(screen.getByText('未使用')).toBeInTheDocument()
    })

    it('claims a coupon and reloads both coupon lists', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Coupons />)

        await screen.findByText('新人满减券')
        await user.click(screen.getByRole('button', { name: '立即领取' }))

        await waitFor(() => {
            expect(mockSession.api.coupons.claim).toHaveBeenCalledWith(301)
        })
        expect(mockSession.notify).toHaveBeenCalledWith('优惠券领取成功', 'success')
        expect(mockSession.api.coupons.getMine).toHaveBeenCalledTimes(2)
        expect(mockSession.api.coupons.getAvailable).toHaveBeenCalledTimes(2)
    })

    it('shows empty states when no coupons are returned', async () => {
        mockSession.api.coupons.getAvailable.mockResolvedValue([])

        renderWithRouter(<Coupons />)

        expect(await screen.findByText('当前没有可领取优惠券。')).toBeInTheDocument()
        expect(screen.getByText('你还没有领取优惠券。')).toBeInTheDocument()
    })
})
