import React from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import Cart from './Cart'

const mockSession = {
    api: {
        user: {
            getCart: vi.fn(),
            updateCartQuantity: vi.fn(),
            deleteCart: vi.fn(),
            clearCart: vi.fn()
        }
    },
    notify: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

const cartItems = [
    {
        id: 1,
        merchantId: 10,
        merchantName: '桂香米粉',
        productId: 101,
        name: '牛肉米粉',
        price: 18,
        quantity: 2,
        subtotal: 36,
        specLabel: '大份'
    }
]

describe('Cart page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.user.getCart.mockResolvedValue(cartItems)
        mockSession.api.user.updateCartQuantity.mockResolvedValue({})
        mockSession.api.user.deleteCart.mockResolvedValue({})
        mockSession.api.user.clearCart.mockResolvedValue({})
    })

    it('loads cart items and grouped merchant summary', async () => {
        renderWithRouter(<Cart />)

        expect(await screen.findByRole('heading', { name: '桂香米粉' })).toBeInTheDocument()
        expect(screen.getByText('牛肉米粉')).toBeInTheDocument()
        expect(screen.getByText('单价 ￥18.00 · 规格 大份')).toBeInTheDocument()
        expect(screen.getAllByText('￥36.00')).toHaveLength(2)
        expect(screen.getByRole('link', { name: '结算该商家' })).toHaveAttribute('href', '/checkout?merchantId=10')
    })

    it('updates item quantity and reloads cart data', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Cart />)

        await screen.findByText('牛肉米粉')
        await user.click(screen.getByRole('button', { name: '+' }))

        await waitFor(() => {
            expect(mockSession.api.user.updateCartQuantity).toHaveBeenCalledWith(1, 3)
        })
        expect(mockSession.api.user.getCart).toHaveBeenCalledTimes(2)
    })

    it('removes a cart item', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Cart />)

        await screen.findByText('牛肉米粉')
        await user.click(screen.getByRole('button', { name: '删除' }))

        await waitFor(() => {
            expect(mockSession.api.user.deleteCart).toHaveBeenCalledWith(1)
        })
    })

    it('clears the cart and shows a success notice', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Cart />)

        await screen.findByText('牛肉米粉')
        await user.click(screen.getByRole('button', { name: '清空购物车' }))

        await waitFor(() => {
            expect(mockSession.api.user.clearCart).toHaveBeenCalled()
        })
        expect(mockSession.notify).toHaveBeenCalledWith('购物车已清空', 'success')
        expect(screen.getByText(/购物车还是空的/)).toBeInTheDocument()
    })
})
