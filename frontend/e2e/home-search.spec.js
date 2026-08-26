import { expect, test } from '@playwright/test'
import { mockPublicApi } from './support/mockApi'

test('consumer can search merchants from the home page', async ({ page }) => {
    await mockPublicApi(page)

    await page.goto('/')

    await expect(page.getByRole('heading', { name: '附近热门商家' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '桂香米粉' })).toBeVisible()

    await page.getByPlaceholder('搜索商家名称、菜品风格或标签').fill('米粉')
    await page.getByRole('combobox').selectOption('快餐')
    await page.getByRole('button', { name: '查找商家' }).click()

    await expect(page).toHaveURL(/\/search\?keyword=.*category=.*/)
    await expect(page.getByRole('heading', { name: '搜索结果' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '桂香米粉' })).toBeVisible()
    await expect(page.getByText('青柠茶餐厅')).toHaveCount(0)
})
