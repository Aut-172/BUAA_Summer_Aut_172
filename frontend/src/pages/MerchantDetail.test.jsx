import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MerchantDetail from './MerchantDetail'

const mockSession = {
    api: {
        public: {
            getMerchant: vi.fn()
        },
        user: {
            isFavoriteMerchant: vi.fn(),
            addFavoriteMerchant: vi.fn(),
            deleteFavoriteMerchant: vi.fn(),
            addCart: vi.fn()
        },
        reviews: {
            getMerchant: vi.fn()
        }
    },
    isAuthenticated: true,
    role: 'consumer',
    notify: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

const merchant = {
    id: 10,
    name: '桂香米粉',
    description: '热汤米粉和小吃',
    category: '快餐',
    tags: '米粉,夜宵',
    rating: 4.6,
    minDeliveryFee: 15,
    deliveryFee: 2,
    monthlySales: 88,
    address: '一号食堂二楼',
    businessHours: '09:00-22:00',
    categoryList: [
        {
            id: 1,
            name: '招牌米粉',
            products: [
                {
                    id: 101,
                    name: '牛肉米粉',
                    description: '牛肉和热汤',
                    price: 18,
                    stock: 20,
                    monthlySales: 50,
                    specGroups: [
                        {
                            id: 1001,
                            name: '份量',
                            specs: [
                                { id: 1, name: '小份', extraPrice: 0 },
                                { id: 2, name: '大份', extraPrice: 4 }
                            ]
                        }
                    ]
                }
            ]
        }
    ]
}

const reviews = [
    {
        id: 1,
        userName: '测试用户',
        productName: '牛肉米粉',
        rating: 5,
        content: '味道很好',
        createTime: '2026-08-26 12:00:00',
        images: []
    }
]

function renderMerchantDetail() {
    return render(
        <MemoryRouter initialEntries={['/merchants/10']}>
            <Routes>
                <Route path="/merchants/:merchantId" element={<MerchantDetail />} />
                <Route path="/login" element={<div>登录页</div>} />
            </Routes>
        </MemoryRouter>
    )
}

describe('MerchantDetail page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.isAuthenticated = true
        mockSession.role = 'consumer'
        mockSession.api.public.getMerchant.mockResolvedValue(merchant)
        mockSession.api.user.isFavoriteMerchant.mockResolvedValue(false)
        mockSession.api.user.addFavoriteMerchant.mockResolvedValue({})
        mockSession.api.user.deleteFavoriteMerchant.mockResolvedValue({})
        mockSession.api.user.addCart.mockResolvedValue({})
        mockSession.api.reviews.getMerchant.mockResolvedValue(reviews)
    })

    it('loads merchant detail, products, favorite state, and reviews', async () => {
        renderMerchantDetail()

        expect(await screen.findByRole('heading', { name: '桂香米粉' })).toBeInTheDocument()
        expect(screen.getByText('牛肉米粉')).toBeInTheDocument()
        expect(screen.getByText('味道很好')).toBeInTheDocument()
        expect(mockSession.api.public.getMerchant).toHaveBeenCalledWith('10')
        expect(mockSession.api.user.isFavoriteMerchant).toHaveBeenCalledWith('10')
    })

    it('adds a product to cart with the selected single spec label', async () => {
        const user = userEvent.setup()
        renderMerchantDetail()

        await screen.findByText('牛肉米粉')
        await user.selectOptions(screen.getByLabelText('可选规格'), '大份')
        await user.click(screen.getByRole('button', { name: '加入购物车' }))

        await waitFor(() => {
            expect(mockSession.api.user.addCart).toHaveBeenCalledWith({
                merchantId: 10,
                productId: 101,
                quantity: 1,
                specLabel: '大份'
            })
        })
        expect(mockSession.notify).toHaveBeenCalledWith('牛肉米粉 已加入购物车', 'success')
    })

    it('favorites the merchant for an authenticated consumer', async () => {
        const user = userEvent.setup()
        renderMerchantDetail()

        await screen.findByRole('button', { name: '收藏商家' })
        await user.click(screen.getByRole('button', { name: '收藏商家' }))

        await waitFor(() => {
            expect(mockSession.api.user.addFavoriteMerchant).toHaveBeenCalledWith(10)
        })
        expect(screen.getByRole('button', { name: '已收藏' })).toBeInTheDocument()
        expect(mockSession.notify).toHaveBeenCalledWith('商家已收藏', 'success')
    })
})
