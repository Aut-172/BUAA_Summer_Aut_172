import { beforeEach, describe, expect, it, vi } from 'vitest'

const axiosMock = vi.hoisted(() => {
    const request = vi.fn()
    return {
        request,
        create: vi.fn(() => ({ request }))
    }
})

vi.mock('axios', () => ({
    default: {
        create: axiosMock.create
    }
}))

const { default: api, setAuthToken, setUnauthorizedHandler, ApiError, resolveApiBaseUrl } = await import('./api')

describe('api client', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        setAuthToken(null)
        setUnauthorizedHandler(null)
    })

    it('unwraps standard Result payloads and attaches bearer tokens', async () => {
        axiosMock.request.mockResolvedValue({
            data: {
                code: 200,
                message: 'success',
                data: { nickname: '校园用户' }
            }
        })
        setAuthToken('consumer-token')

        await expect(api.user.getProfile()).resolves.toEqual({ nickname: '校园用户' })
        expect(axiosMock.request).toHaveBeenCalledWith(expect.objectContaining({
            url: '/user/profile',
            headers: { Authorization: 'Bearer consumer-token' }
        }))
    })

    it('converts page shaped Result payloads to page result objects', async () => {
        axiosMock.request.mockResolvedValue({
            data: {
                code: 200,
                message: 'success',
                data: [{ id: 1 }],
                total: 1,
                page: 2,
                pageSize: 10
            }
        })

        await expect(api.admin.getUsers({ page: 2 })).resolves.toEqual({
            items: [{ id: 1 }],
            total: 1,
            page: 2,
            pageSize: 10
        })
    })

    it('calls unauthorized handler when backend returns 401 Result', async () => {
        const unauthorizedHandler = vi.fn()
        axiosMock.request.mockRejectedValue({
            response: {
                data: {
                    code: 401,
                    message: '登录已过期，请重新登录'
                }
            }
        })
        setUnauthorizedHandler(unauthorizedHandler)

        await expect(api.user.getProfile()).rejects.toMatchObject({
            name: 'ApiError',
            code: 401,
            message: '登录已过期，请重新登录'
        })
        expect(unauthorizedHandler).toHaveBeenCalledWith(expect.any(ApiError))
    })

    it('targets the gateway node port when served from the frontend node port', () => {
        expect(resolveApiBaseUrl({
            protocol: 'http:',
            hostname: '47.120.37.61',
            port: '30080'
        })).toBe('http://47.120.37.61:30081/api')
    })

    it('uses an explicit API base URL and appends /api for gateway origins', () => {
        expect(resolveApiBaseUrl(null, 'http://example.com:30081')).toBe('http://example.com:30081/api')
        expect(resolveApiBaseUrl(null, 'https://api.example.com/custom-api')).toBe('https://api.example.com/custom-api')
    })
})
