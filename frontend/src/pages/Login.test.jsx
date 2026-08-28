import React from 'react'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Login from './Login'

const mockSession = {
    api: {
        captcha: {
            get: vi.fn()
        }
    },
    login: vi.fn(),
    register: vi.fn(),
    notify: vi.fn()
}

vi.mock('../utils/ApiProvider', () => ({
    useSession: () => mockSession
}))

function renderLogin() {
    return render(
        <MemoryRouter>
            <Login />
        </MemoryRouter>
    )
}

describe('Login page', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockSession.api.captcha.get.mockResolvedValue({
            key: 'captcha-key',
            image: 'data:image/png;base64,test'
        })
    })

    it('renders the login form and role selector', async () => {
        renderLogin()

        expect(screen.getByRole('heading', { name: '欢迎回来' })).toBeInTheDocument()
        expect(screen.getByLabelText('角色')).toHaveValue('consumer')
        expect(await screen.findByAltText('验证码')).toBeInTheDocument()
    })

    it('updates the role-specific hint when selecting merchant', async () => {
        const user = userEvent.setup()
        renderLogin()

        await user.selectOptions(screen.getByLabelText('角色'), 'merchant')

        expect(screen.getByText('适合管理商品、查看订单和处理履约。')).toBeInTheDocument()
        expect(screen.getByLabelText('角色')).toHaveValue('merchant')
    })

    it('keeps admin available for login but hides it on the register form', async () => {
        const user = userEvent.setup()
        renderLogin()

        expect(screen.getByRole('option', { name: '管理员' })).toBeInTheDocument()
        await user.selectOptions(screen.getByLabelText('角色'), 'admin')

        await user.click(screen.getByRole('button', { name: '注册' }))

        expect(screen.getByRole('heading', { name: '创建新账号' })).toBeInTheDocument()
        expect(screen.getByLabelText('角色')).toHaveValue('consumer')
        expect(screen.queryByRole('option', { name: '管理员' })).not.toBeInTheDocument()
    })

    it('submits login credentials with captcha data', async () => {
        const user = userEvent.setup()
        mockSession.login.mockResolvedValue({ role: 'consumer' })
        renderLogin()

        await screen.findByAltText('验证码')
        await user.type(screen.getByLabelText('用户名'), 'student01')
        await user.type(screen.getByLabelText('密码'), '123456')
        await user.type(screen.getByPlaceholderText('输入图形验证码'), 'abcd')
        await user.click(screen.getByRole('button', { name: '进入系统' }))

        await waitFor(() => {
            expect(mockSession.login).toHaveBeenCalledWith({
                role: 'consumer',
                username: 'student01',
                password: '123456',
                captchaKey: 'captcha-key',
                captchaCode: 'abcd'
            })
        })
    })

    it('shows an error notice when login fails', async () => {
        const user = userEvent.setup()
        mockSession.login.mockRejectedValue(new Error('用户名或密码错误'))
        renderLogin()

        await screen.findByAltText('验证码')
        await user.type(screen.getByPlaceholderText('输入图形验证码'), 'wrong')
        await user.click(screen.getByRole('button', { name: '进入系统' }))

        await waitFor(() => {
            expect(mockSession.notify).toHaveBeenCalledWith('用户名或密码错误', 'danger')
        })
    })
})
