import React from 'react'
import { Route, Routes } from 'react-router-dom'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import Review from './Review'

const mockSession = {
    api: {
        orders: {
            getDetail: vi.fn()
        },
        reviews: {
            submit: vi.fn(),
            uploadImages: vi.fn()
        }
    },
    notify: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

function renderReview(route = '/reviews/900') {
    return renderWithRouter(
        <Routes>
            <Route path="/reviews/:orderId" element={<Review />} />
            <Route path="/orders" element={<div>订单列表</div>} />
        </Routes>,
        { route }
    )
}

const completedOrder = {
    id: 900,
    orderNo: 'NO20260826001',
    merchant: '桂香米粉',
    status: 'completed',
    total: 38,
    items: [
        { productId: 101, name: '牛肉米粉', price: 18, quantity: 2, specLabel: '大份', reviewed: false }
    ]
}

describe('Review page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.orders.getDetail.mockResolvedValue(completedOrder)
        mockSession.api.reviews.submit.mockResolvedValue({})
        mockSession.api.reviews.uploadImages.mockResolvedValue(['https://example.com/review.png'])
    })

    it('loads completed order items and submits a text review', async () => {
        const user = userEvent.setup()
        renderReview()

        expect(await screen.findByRole('heading', { name: '订单评价' })).toBeInTheDocument()
        await user.click(screen.getByRole('button', { name: '4 星' }))
        await user.type(screen.getByPlaceholderText('说说口味、包装、配送体验...'), '包装完整，汤底很好')
        await user.click(screen.getByRole('button', { name: '提交图文评价' }))

        await waitFor(() => {
            expect(mockSession.api.reviews.submit).toHaveBeenCalledWith({
                orderId: '900',
                items: [
                    { productId: 101, rating: 4, content: '包装完整，汤底很好', images: [] }
                ]
            })
        })
        expect(mockSession.notify).toHaveBeenCalledWith('评价提交成功', 'success')
        expect(await screen.findByText('订单列表')).toBeInTheDocument()
    })

    it('uploads and removes review images before submit', async () => {
        const user = userEvent.setup()
        renderReview()

        await screen.findByText('评价图片')
        const input = document.querySelector('input[type="file"]')
        expect(input).toBeInTheDocument()
        await user.upload(input, new File(['image'], 'review.png', { type: 'image/png' }))

        await waitFor(() => {
            expect(mockSession.api.reviews.uploadImages).toHaveBeenCalledTimes(1)
        })
        expect(screen.getByAltText('评价图片预览')).toBeInTheDocument()

        await user.click(screen.getByRole('button', { name: '移除' }))
        expect(screen.queryByAltText('评价图片预览')).not.toBeInTheDocument()
    })

    it('warns when the order is not completed', async () => {
        mockSession.api.orders.getDetail.mockResolvedValue({ ...completedOrder, status: 'pending_accept' })

        renderReview()

        expect(await screen.findByText('当前订单尚未完成，完成后即可评价。')).toBeInTheDocument()
        expect(screen.queryByRole('button', { name: '提交图文评价' })).not.toBeInTheDocument()
    })
})
