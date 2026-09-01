import React from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import Checkout from './Checkout'

const mockSession = {
    api: {
        user: {
            getCart: vi.fn(),
            getAddresses: vi.fn()
        },
        coupons: {
            getMine: vi.fn()
        },
        public: {
            getMerchant: vi.fn()
        },
        orders: {
            checkout: vi.fn(),
            pay: vi.fn()
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

const addresses = [
    {
        id: '1897269742190125056',
        name: '测试用户',
        phone: '13800000001',
        detail: '宿舍 3 号楼 302 室',
        isDefault: true
    }
]

const coupons = [
    {
        id: 500,
        title: '满三十减五',
        threshold: 30,
        discount: 5,
        status: 'unused'
    }
]

describe('Checkout page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.user.getCart.mockResolvedValue(cartItems)
        mockSession.api.user.getAddresses.mockResolvedValue(addresses)
        mockSession.api.coupons.getMine.mockResolvedValue(coupons)
        mockSession.api.public.getMerchant.mockResolvedValue({ id: 10, name: '桂香米粉', deliveryFee: 2 })
        mockSession.api.orders.checkout.mockResolvedValue({ id: 900, orderNo: 'NO20260826001' })
        mockSession.api.orders.pay.mockResolvedValue({})
    })

    it('loads cart, address, coupon, and merchant delivery fee for checkout', async () => {
        renderWithRouter(<Checkout />, { route: '/checkout?merchantId=10' })

        expect(await screen.findByText('牛肉米粉')).toBeInTheDocument()
        expect(screen.getByText('测试用户 13800000001 宿舍 3 号楼 302 室')).toBeInTheDocument()
        expect(screen.getByRole('option', { name: '满三十减五 - 满 ￥30.00 减 ￥5.00' })).toBeInTheDocument()
        expect(screen.getByText('预计配送费')).toBeInTheDocument()
        expect(await screen.findByText('￥2.00')).toBeInTheDocument()
    })

    it('submits checkout data and pays the created order', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Checkout />, { route: '/checkout?merchantId=10' })

        await screen.findByText('牛肉米粉')
        await user.selectOptions(screen.getByLabelText('可用优惠券'), '500')
        await user.selectOptions(screen.getByLabelText('支付方式'), 'WECHAT')
        await user.click(screen.getByRole('button', { name: '确认下单并支付' }))

        await waitFor(() => {
            expect(mockSession.api.orders.checkout).toHaveBeenCalledWith({
                merchantId: 10,
                addressId: '1897269742190125056',
                address: '宿舍 3 号楼 302 室',
                couponId: 500,
                items: [
                    {
                        productId: 101,
                        quantity: 2,
                        specLabel: '大份'
                    }
                ]
            })
        })
        expect(mockSession.api.orders.pay).toHaveBeenCalledWith(900, { payMethod: 'WECHAT' })
        expect(mockSession.notify).toHaveBeenCalledWith('订单 NO20260826001 已支付', 'success')
    })

    it('warns when no delivery address is available', async () => {
        const user = userEvent.setup()
        mockSession.api.user.getAddresses.mockResolvedValue([])
        renderWithRouter(<Checkout />, { route: '/checkout?merchantId=10' })

        await screen.findByText('牛肉米粉')
        await user.click(screen.getByRole('button', { name: '确认下单并支付' }))

        expect(mockSession.notify).toHaveBeenCalledWith('请先选择一个地址，或者手动填写收货地址', 'warning')
        expect(mockSession.api.orders.checkout).not.toHaveBeenCalled()
    })
})
