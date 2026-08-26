import { expect, test } from '@playwright/test'
import { mockLoginApi, mockPublicApi } from './support/mockApi'

test('consumer can log in and persist session data', async ({ page }) => {
    await mockLoginApi(page)
    await mockPublicApi(page)

    await page.goto('/login')

    await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
    await page.getByPlaceholder('输入图形验证码').fill('abcd')
    await page.getByRole('button', { name: '进入系统' }).click()

    await expect(page).toHaveURL('/')
    await expect(page.getByText('演示用户')).toBeVisible()

    const session = await page.evaluate(() => JSON.parse(localStorage.getItem('life-service-session')))
    expect(session.token).toBe('consumer-token')
    expect(session.role).toBe('consumer')
    expect(session.user.nickname).toBe('演示用户')
})
