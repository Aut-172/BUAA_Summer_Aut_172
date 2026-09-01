import React from 'react'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import MerchantConsole from './MerchantConsole'

const mockSession = {
    api: {
        merchant: {
            getDashboard: vi.fn(),
            getProfile: vi.fn(),
            updateProfile: vi.fn(),
            getProducts: vi.fn(),
            createProduct: vi.fn(),
            updateProduct: vi.fn(),
            deleteProduct: vi.fn(),
            getOrders: vi.fn(),
            updateOrder: vi.fn()
        },
        public: { getCategories: vi.fn() },
        reviews: { getMerchant: vi.fn() },
        uploads: { images: vi.fn() }
    },
    notify: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

const profile = {
    id: 10,
    name: '桂香米粉',
    phone: '13800000011',
    address: '一号食堂二楼',
    businessHours: '09:00-22:00',
    category: '快餐',
    description: '热汤米粉和小吃',
    tags: '米粉,夜宵',
    minDeliveryFee: 15,
    deliveryFee: 2,
    deliveryRadius: 3,
    status: 'active',
    rating: 4.7
}

const categories = [{ id: 1, name: '招牌米粉' }]
const products = [{ id: 101, name: '牛肉米粉', categoryId: 1, price: 18, stock: 20, status: 'active' }]
const orders = [{ id: 900, orderNo: 'NO20260826001', status: 'delivering', total: 38, riderName: '一号骑手', userId: 1 }]

describe('MerchantConsole page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.merchant.getDashboard.mockResolvedValue({ merchant: { todayOrders: 3, todayRevenue: 120, pendingOrders: 1 } })
        mockSession.api.merchant.getProfile.mockResolvedValue(profile)
        mockSession.api.public.getCategories.mockResolvedValue(categories)
        mockSession.api.merchant.getProducts.mockResolvedValue(products)
        mockSession.api.merchant.getOrders.mockResolvedValue(orders)
        mockSession.api.reviews.getMerchant.mockResolvedValue([{ id: 1, userName: '测试用户', productName: '牛肉米粉', rating: 5, content: '很好吃' }])
        mockSession.api.merchant.updateProfile.mockResolvedValue({ ...profile, name: '新店名' })
        mockSession.api.merchant.createProduct.mockResolvedValue({})
        mockSession.api.merchant.updateOrder.mockResolvedValue({})
    })

    it('loads merchant dashboard metrics, profile, and recent reviews', async () => {
        renderWithRouter(<MerchantConsole />)

        expect(await screen.findByText('今日订单')).toBeInTheDocument()
        expect(screen.getByDisplayValue('桂香米粉')).toBeInTheDocument()
        expect(screen.getByText('很好吃')).toBeInTheDocument()
        expect(screen.getByText('￥120.00')).toBeInTheDocument()
    })

    it('saves merchant profile changes with numeric delivery fields', async () => {
        const user = userEvent.setup()
        renderWithRouter(<MerchantConsole />)

        const nameInput = await screen.findByLabelText('店铺名称')
        await user.clear(nameInput)
        await user.type(nameInput, '新店名')
        await user.click(screen.getByRole('button', { name: '保存商家资料' }))

        await waitFor(() => {
            expect(mockSession.api.merchant.updateProfile).toHaveBeenCalledWith(expect.objectContaining({
                name: '新店名',
                minDeliveryFee: 15,
                deliveryFee: 2,
                deliveryRadius: 3
            }))
        })
        expect(mockSession.notify).toHaveBeenCalledWith('商家资料已更新', 'success')
    })

    it('rejects merchant profile text above the allowed limit before submit', async () => {
        const user = userEvent.setup()
        renderWithRouter(<MerchantConsole />)

        const nameInput = await screen.findByLabelText('店铺名称')
        fireEvent.change(nameInput, { target: { value: '很'.repeat(101) } })
        await user.click(screen.getByRole('button', { name: '保存商家资料' }))

        expect(mockSession.api.merchant.updateProfile).not.toHaveBeenCalled()
        expect(mockSession.notify).toHaveBeenCalledWith('店铺名称不能超过 100 个字符', 'warning')
    })

    it('creates a product from the product management tab', async () => {
        const user = userEvent.setup()
        renderWithRouter(<MerchantConsole />)

        await screen.findByText('今日订单')
        await user.click(screen.getByRole('button', { name: '商品管理' }))
        await user.type(screen.getByLabelText('商品名'), '酸辣米粉')
        await user.selectOptions(screen.getByLabelText('分类'), '1')
        await user.type(screen.getByLabelText('价格'), '16')
        await user.type(screen.getByLabelText('库存'), '30')
        await user.type(screen.getByLabelText('描述'), '酸辣开胃')
        await user.click(screen.getByRole('button', { name: '新增商品' }))

        await waitFor(() => {
            expect(mockSession.api.merchant.createProduct).toHaveBeenCalledWith({
                id: null,
                name: '酸辣米粉',
                categoryId: 1,
                price: 16,
                stock: 30,
                image: null,
                description: '酸辣开胃',
                status: 'active'
            })
        })
        expect(mockSession.notify).toHaveBeenCalledWith('商品已新增', 'success')
    })

    it('keeps product categories available when merchant orders fail to load', async () => {
        const user = userEvent.setup()
        mockSession.api.merchant.getOrders.mockRejectedValueOnce(new Error('订单服务暂不可用'))
        renderWithRouter(<MerchantConsole />)

        await screen.findByText('今日订单')
        await user.click(screen.getByRole('button', { name: '商品管理' }))

        expect(screen.getByRole('option', { name: '招牌米粉' })).toBeInTheDocument()
        expect(mockSession.notify).toHaveBeenCalledWith('订单服务暂不可用', 'danger')
    })

    it('rejects a product name above the allowed limit before submit', async () => {
        const user = userEvent.setup()
        renderWithRouter(<MerchantConsole />)

        await screen.findByText('今日订单')
        await user.click(screen.getByRole('button', { name: '商品管理' }))
        fireEvent.change(screen.getByLabelText('商品名'), { target: { value: '粉'.repeat(101) } })
        await user.selectOptions(screen.getByLabelText('分类'), '1')
        await user.type(screen.getByLabelText('价格'), '16')
        await user.type(screen.getByLabelText('库存'), '30')
        await user.click(screen.getByRole('button', { name: '新增商品' }))

        expect(mockSession.api.merchant.createProduct).not.toHaveBeenCalled()
        expect(mockSession.notify).toHaveBeenCalledWith('商品名不能超过 100 个字符', 'warning')
    })

    it('rejects a product price above the allowed limit before submit', async () => {
        const user = userEvent.setup()
        renderWithRouter(<MerchantConsole />)

        await screen.findByText('今日订单')
        await user.click(screen.getByRole('button', { name: '商品管理' }))
        await user.type(screen.getByLabelText('商品名'), '天价米粉')
        await user.selectOptions(screen.getByLabelText('分类'), '1')
        await user.type(screen.getByLabelText('价格'), '100000')
        await user.type(screen.getByLabelText('库存'), '30')
        await user.click(screen.getByRole('button', { name: '新增商品' }))

        expect(mockSession.api.merchant.createProduct).not.toHaveBeenCalled()
        expect(mockSession.notify).toHaveBeenCalledWith('商品价格不能超过 99999.99 元', 'warning')
    })

    it('rejects a product stock above the allowed limit before submit', async () => {
        const user = userEvent.setup()
        renderWithRouter(<MerchantConsole />)

        await screen.findByText('今日订单')
        await user.click(screen.getByRole('button', { name: '商品管理' }))
        await user.type(screen.getByLabelText('商品名'), '超量米粉')
        await user.selectOptions(screen.getByLabelText('分类'), '1')
        await user.type(screen.getByLabelText('价格'), '16')
        await user.type(screen.getByLabelText('库存'), '1000000')
        await user.click(screen.getByRole('button', { name: '新增商品' }))

        expect(mockSession.api.merchant.createProduct).not.toHaveBeenCalled()
        expect(mockSession.notify).toHaveBeenCalledWith('商品库存不能超过 999999', 'warning')
    })

    it('marks a delivering order as completed', async () => {
        const user = userEvent.setup()
        renderWithRouter(<MerchantConsole />)

        await screen.findByText('今日订单')
        await user.click(screen.getByRole('button', { name: '订单处理' }))
        await user.click(screen.getByRole('button', { name: '标记已完成' }))

        await waitFor(() => {
            expect(mockSession.api.merchant.updateOrder).toHaveBeenCalledWith(900, {
                status: '已完成',
                eta: '预计 20 分钟送达'
            })
        })
        expect(mockSession.notify).toHaveBeenCalledWith('订单状态已更新', 'success')
    })
})
