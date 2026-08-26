import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Home from './Home'

const mockSession = {
    api: {
        public: {
            getCategories: vi.fn(),
            getMerchants: vi.fn()
        }
    },
    role: null,
    isAuthenticated: false
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

const categories = [
    { id: 1, name: '快餐' },
    { id: 2, name: '饮品' }
]

const merchants = [
    {
        id: 10,
        name: '桂香米粉',
        description: '热汤米粉和小吃',
        category: '快餐',
        tags: '米粉,热汤',
        minDeliveryFee: 15,
        deliveryFee: 2,
        monthlySales: 88,
        products: [
            { id: 101, name: '牛肉米粉', price: 18 }
        ]
    }
]

function LocationProbe() {
    const location = useLocation()
    return <output aria-label="current-location">{location.pathname + location.search}</output>
}

function renderHome() {
    return render(
        <MemoryRouter initialEntries={['/']}>
            <Routes>
                <Route path="/" element={<><Home /><LocationProbe /></>} />
                <Route path="/search" element={<LocationProbe />} />
            </Routes>
        </MemoryRouter>
    )
}

describe('Home page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.public.getCategories.mockResolvedValue(categories)
        mockSession.api.public.getMerchants.mockResolvedValue({ items: merchants })
    })

    it('loads categories and merchant cards from the public API', async () => {
        renderHome()

        expect(await screen.findByText('桂香米粉')).toBeInTheDocument()
        expect(screen.getByText('牛肉米粉')).toBeInTheDocument()
        expect(screen.getByRole('option', { name: '快餐' })).toBeInTheDocument()
        expect(mockSession.api.public.getMerchants).toHaveBeenCalledWith({
            page: 1,
            size: 12,
            keyword: undefined,
            category: undefined
        })
    })

    it('navigates to Search with keyword and category query params', async () => {
        const user = userEvent.setup()
        renderHome()

        await screen.findByRole('option', { name: '快餐' })
        await user.type(screen.getByPlaceholderText('搜索商家名称、菜品风格或标签'), '米粉')
        await user.selectOptions(screen.getByRole('combobox'), '快餐')
        await user.click(screen.getByRole('button', { name: '查找商家' }))

        await waitFor(() => {
            expect(screen.getByLabelText('current-location')).toHaveTextContent('/search?keyword=%E7%B1%B3%E7%B2%89&category=%E5%BF%AB%E9%A4%90')
        })
    })
})
