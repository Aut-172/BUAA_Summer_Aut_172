import React from 'react'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '../test/render'
import Search from './Search'

const mockSession = {
    api: {
        public: {
            getCategories: vi.fn(),
            search: vi.fn(),
            recommend: vi.fn()
        }
    }
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

const categories = [
    { id: 1, name: '快餐' },
    { id: 2, name: '饮品' }
]

const searchMerchants = [
    {
        id: 20,
        name: '青柠茶餐厅',
        description: '茶饮和简餐',
        category: '饮品',
        tags: ['奶茶', '柠檬茶'],
        deliveryFee: 3,
        monthlySales: 120,
        rating: 4.8,
        products: [
            { id: 201, name: '青柠冰茶', price: 12 }
        ]
    }
]

describe('Search page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.public.getCategories.mockResolvedValue(categories)
        mockSession.api.public.search.mockResolvedValue(searchMerchants)
        mockSession.api.public.recommend.mockResolvedValue([])
    })

    it('calls search API with keyword, category, and sort from the URL', async () => {
        renderWithRouter(<Search />, { route: '/search?keyword=奶茶&category=饮品&sort=sales' })

        expect(await screen.findByText('青柠茶餐厅')).toBeInTheDocument()
        expect(screen.getByText('青柠冰茶')).toBeInTheDocument()
        expect(mockSession.api.public.search).toHaveBeenCalledWith({
            keyword: '奶茶',
            category: '饮品',
            sort: 'sales'
        })
    })

    it('updates search params from user input before reloading results', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Search />, { route: '/search' })

        await screen.findByText('青柠茶餐厅')
        await user.clear(screen.getByPlaceholderText('商家名、标签或商品名'))
        await user.type(screen.getByPlaceholderText('商家名、标签或商品名'), '米粉')
        await user.selectOptions(screen.getAllByRole('combobox')[0], '快餐')
        await user.click(screen.getByRole('button', { name: '搜索' }))

        await waitFor(() => {
            expect(mockSession.api.public.search).toHaveBeenLastCalledWith({
                keyword: '米粉',
                category: '快餐',
                sort: 'rating'
            })
        })
    })

    it('searches with a clicked merchant or product suggestion', async () => {
        const user = userEvent.setup()
        renderWithRouter(<Search />, { route: '/search' })

        await screen.findByText('青柠茶餐厅')
        await user.type(screen.getByPlaceholderText('商家名、标签或商品名'), '冰')
        await user.click(screen.getByRole('button', { name: /商品青柠冰茶/ }))

        await waitFor(() => {
            expect(mockSession.api.public.search).toHaveBeenLastCalledWith({
                keyword: '青柠冰茶',
                category: undefined,
                sort: 'rating'
            })
        })
    })

    it('shows an empty state when no open merchant matches the query', async () => {
        mockSession.api.public.search.mockResolvedValue([])
        renderWithRouter(<Search />, { route: '/search?keyword=不存在' })

        expect(await screen.findByText('没有找到符合条件的营业中商家。')).toBeInTheDocument()
    })
})
