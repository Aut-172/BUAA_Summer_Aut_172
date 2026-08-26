import { expect, test } from '@playwright/test'
import { fetchApi, installBusinessScenarioMock, loginAs } from './support/mockApi'

function formRow(page, label) {
    return page.locator('label.form-row').filter({ hasText: label })
}

async function fillFormRow(page, label, value) {
    await formRow(page, label).locator('input, textarea').fill(value)
}

async function selectFormRow(page, label, value) {
    await formRow(page, label).locator('select').selectOption(value)
}

async function expectToast(page, text) {
    await expect(page.locator('.toast')).toContainText(text)
}

test.describe('UC01-UC21 业务场景覆盖', () => {
    test.beforeEach(async ({ page }) => {
        await installBusinessScenarioMock(page)
    })

    test('UC01 UC12 多角色注册、登录与会话分流', async ({ page }) => {
        await page.goto('/login')

        await page.getByRole('button', { name: '注册', exact: true }).click()
        await fillFormRow(page, '用户名', 'new-consumer')
        await fillFormRow(page, '手机号', '13900000001')
        await fillFormRow(page, '昵称', '新消费者')
        await fillFormRow(page, '密码', '123456')
        await page.getByPlaceholder('输入图形验证码').fill('abcd')
        await page.getByRole('button', { name: '注册账号' }).click()
        await expectToast(page, '注册成功')

        await page.getByRole('button', { name: '注册', exact: true }).click()
        await page.getByRole('combobox').selectOption('merchant')
        await fillFormRow(page, '用户名', 'new-merchant')
        await fillFormRow(page, '手机号', '13900000011')
        await fillFormRow(page, '店铺名称', '新店铺')
        await fillFormRow(page, '密码', '123456')
        await page.getByPlaceholder('输入图形验证码').fill('abcd')
        await page.getByRole('button', { name: '注册账号' }).click()
        await expectToast(page, '注册成功')

        await page.getByRole('button', { name: '注册', exact: true }).click()
        await page.getByRole('combobox').selectOption('rider')
        await fillFormRow(page, '用户名', 'new-rider')
        await fillFormRow(page, '手机号', '13900000021')
        await fillFormRow(page, '骑手昵称', '新骑手')
        await fillFormRow(page, '密码', '123456')
        await page.getByPlaceholder('输入图形验证码').fill('abcd')
        await page.getByRole('button', { name: '注册账号' }).click()
        await expectToast(page, '注册成功')

        await page.getByRole('combobox').selectOption('admin')
        await expect(page.getByRole('button', { name: '注册', exact: true })).toBeDisabled()

        for (const [role, path, heading] of [
            ['consumer', '/', '附近热门商家'],
            ['merchant', '/merchant-center', '商家工作台'],
            ['rider', '/rider-center', '骑手工作台'],
            ['admin', '/admin-center', '管理员工作台']
        ]) {
            await page.goto('/login')
            await page.getByRole('combobox').selectOption(role)
            await page.getByPlaceholder('输入图形验证码').fill('abcd')
            await page.getByRole('button', { name: '进入系统' }).click()
            await expect(page).toHaveURL(path)
            await expect(page.getByRole('heading', { name: heading })).toBeVisible()
            await page.getByRole('button', { name: '退出' }).click()
        }
    })

    test('UC02 UC03 UC04 UC05 UC06 UC07 UC08 UC09 UC10 UC11 UC20 UC21 消费者下单闭环', async ({ page }) => {
        await loginAs(page, 'consumer')
        await expect(page.getByRole('heading', { name: '附近热门商家' })).toBeVisible()

        const dashboard = await fetchApi(page, '/api/dashboard')
        expect(dashboard.data.consumer.orders).toBeGreaterThan(0)

        const recommended = await fetchApi(page, '/api/recommend?lat=30&lng=120')
        expect(recommended.data[0].name).toBe('桂香米粉')

        await page.getByRole('link', { name: '我的' }).click()
        await expect(page.getByRole('heading', { name: '个人资料' })).toBeVisible()
        await fillFormRow(page, '昵称', '演示用户已更新')
        await fillFormRow(page, '手机号', '13800000002')
        await page.getByRole('button', { name: '保存资料', exact: true }).click()
        await expectToast(page, '个人资料已更新')

        await fillFormRow(page, '收货人', '测试同学')
        await fillFormRow(page, '联系电话', '13900000002')
        await fillFormRow(page, '详细地址', '图书馆一层大厅')
        await page.getByRole('button', { name: '新增地址' }).click()
        await expectToast(page, '地址已新增')
        const addressCard = page.locator('article.select-card').filter({ hasText: '图书馆一层大厅' })
        await addressCard.getByRole('button', { name: '编辑' }).click()
        await fillFormRow(page, '详细地址', '图书馆二层自习区')
        await page.getByRole('button', { name: '更新地址' }).click()
        await expectToast(page, '地址已更新')
        await page.locator('article.select-card').filter({ hasText: '图书馆二层自习区' }).getByRole('button', { name: '删除' }).click()
        await expectToast(page, '地址已删除')

        await page.getByRole('link', { name: '优惠券' }).click()
        await page.getByRole('button', { name: '立即领取' }).click()
        await expectToast(page, '优惠券领取成功')
        await expect(page.getByRole('button', { name: '已领取' })).toBeVisible()

        await page.getByRole('link', { name: '首页' }).click()
        await page.getByPlaceholder('搜索商家名称、菜品风格或标签').fill('米粉')
        await page.getByRole('combobox').selectOption('快餐')
        await page.getByRole('button', { name: '查找商家' }).click()
        await expect(page.getByRole('heading', { name: '搜索结果' })).toBeVisible()
        await page.getByRole('link', { name: '进入店铺' }).first().click()
        await expect(page.getByRole('heading', { name: '桂香米粉' })).toBeVisible()
        await expect(page.getByRole('heading', { name: '牛肉米粉' })).toBeVisible()
        await selectFormRow(page, '可选规格', '加粉')
        await page.getByRole('button', { name: '收藏商家' }).click()
        await expectToast(page, '商家已收藏')
        await page.getByRole('button', { name: '加入购物车' }).click()
        await expectToast(page, '牛肉米粉 已加入购物车')

        await page.getByRole('link', { name: '去购物车' }).click()
        const cartItem = page.locator('.cart-item-card').filter({ hasText: '牛肉米粉' })
        await expect(cartItem).toContainText('规格 加粉')
        await cartItem.getByRole('button', { name: '+' }).click()
        await expect(cartItem).toContainText('2')
        await cartItem.getByRole('button', { name: '-' }).click()
        await expect(cartItem).toContainText('1')
        await page.getByRole('link', { name: '结算该商家' }).click()

        await expect(page.getByRole('heading', { name: '结算' })).toBeVisible()
        await selectFormRow(page, '可用优惠券', '301')
        await selectFormRow(page, '支付方式', 'WECHAT')
        await page.getByRole('button', { name: '确认下单并支付' }).click()
        await expect(page).toHaveURL('/orders')
        await expectToast(page, '已支付')

        const couponsAfterCheckout = await fetchApi(page, '/api/coupons')
        expect(couponsAfterCheckout.data.find((coupon) => coupon.id === 301).status).toBe('locked')

        const paidOrder = page.locator('article.order-card').filter({ hasText: 'NO20260826951' })
        await paidOrder.getByRole('button', { name: '查看支付记录' }).click()
        await expect(paidOrder).toContainText('WECHAT')
        await expect(paidOrder).toContainText('成功')

        const cancelOrder = page.locator('article.order-card').filter({ hasText: 'NO20260826002' })
        await cancelOrder.getByRole('button', { name: '取消订单' }).click()
        await expectToast(page, '订单已取消')
        await expect(cancelOrder).toContainText('已取消')

        const deliveryOrder = page.locator('article.order-card').filter({ hasText: 'NO20260826003' })
        await deliveryOrder.getByRole('button', { name: '确认收货' }).click()
        await expectToast(page, '订单已确认完成')
        await expect(deliveryOrder).toContainText('已完成')
        await deliveryOrder.getByRole('link', { name: '评价订单' }).click()
        await fillFormRow(page, '评价内容', '很好吃，送达也及时。')
        await page.getByRole('button', { name: '提交图文评价' }).click()
        await expectToast(page, '评价提交成功')
        await expect(page).toHaveURL('/orders')

        await page.goto('/messages?targetId=10&targetType=merchant&orderId=900&targetName=桂香米粉&orderNo=NO20260826001')
        await expect(page.getByRole('heading', { name: '消息' })).toBeVisible()
        await expect(page.locator('.message-list').getByText('订单马上出餐')).toBeVisible()
        await page.getByPlaceholder('输入消息').fill('请尽快送达')
        await page.getByRole('button', { name: '发送' }).click()
        await expect(page.locator('.message-list').getByText('请尽快送达')).toBeVisible()
        await page.getByRole('link', { name: '查看订单' }).click()
        await expect(page.getByRole('heading', { name: '订单详情' })).toBeVisible()
        await expect(page.getByText('NO20260826001')).toBeVisible()
    })

    test('UC13 UC14 UC15 UC20 商家资料、商品、订单和评价入口', async ({ page }) => {
        await loginAs(page, 'merchant')
        await expect(page.getByRole('heading', { name: '商家工作台' })).toBeVisible()
        await expect(page.getByText('今日订单')).toBeVisible()

        await fillFormRow(page, '店铺名称', '桂香米粉升级店')
        await fillFormRow(page, '联系电话', '13800000012')
        await fillFormRow(page, '地址', '校园南区商业街')
        await fillFormRow(page, '营业时间', '08:00-23:00')
        await fillFormRow(page, '标签', '米粉,新品')
        await fillFormRow(page, '起送价', '10')
        await fillFormRow(page, '配送费', '3')
        await fillFormRow(page, '配送范围', '5')
        await fillFormRow(page, '商家介绍', '更新后的店铺介绍')
        await page.getByRole('button', { name: '保存商家资料' }).click()
        await expectToast(page, '商家资料已更新')

        await page.getByRole('button', { name: '商品管理' }).click()
        await fillFormRow(page, '商品名', '桂花糕')
        await selectFormRow(page, '分类', '1')
        await fillFormRow(page, '价格', '12')
        await fillFormRow(page, '库存', '50')
        await fillFormRow(page, '描述', '新上架点心')
        await page.getByRole('button', { name: '新增商品' }).click()
        await expectToast(page, '商品已新增')
        const productCard = page.locator('article.select-card').filter({ hasText: '桂花糕' })
        await productCard.getByRole('button', { name: '编辑' }).click()
        await fillFormRow(page, '价格', '13')
        await fillFormRow(page, '库存', '48')
        await page.getByRole('button', { name: '更新商品' }).click()
        await expectToast(page, '商品已更新')
        await page.locator('article.select-card').filter({ hasText: '桂花糕' }).getByRole('button', { name: '删除' }).click()
        await expectToast(page, '商品已删除')

        await page.getByRole('button', { name: '订单处理' }).click()
        const merchantOrder = page.locator('article.order-card').filter({ hasText: 'NO20260826010' })
        await merchantOrder.getByRole('button', { name: '标记配送中' }).click()
        await expectToast(page, '订单状态已更新')
        await expect(merchantOrder).toContainText('配送中')
        await merchantOrder.getByRole('button', { name: '标记已完成' }).click()
        await expectToast(page, '订单状态已更新')
        await expect(merchantOrder).toContainText('已完成')

        await page.getByRole('button', { name: '消费者评价' }).click()
        await expect(page.getByText('汤底很香。')).toBeVisible()
    })

    test('UC16 UC17 UC20 骑手资料、接单与完成配送', async ({ page }) => {
        await loginAs(page, 'rider')
        await expect(page.getByRole('heading', { name: '骑手工作台' })).toBeVisible()
        await expect(page.getByText('今日配送', { exact: true })).toBeVisible()

        await fillFormRow(page, '昵称', '一号骑手更新')
        await fillFormRow(page, '手机号', '13800000022')
        await fillFormRow(page, '服务范围', '校园西区')
        await page.getByRole('button', { name: '保存资料', exact: true }).click()
        await expectToast(page, '骑手资料已更新')

        const availableTask = page.locator('article.select-card').filter({ hasText: 'NO20260826002' })
        await availableTask.getByRole('button', { name: '立即接单' }).click()
        await expectToast(page, '任务状态已更新')
        const assignedTask = page.locator('article.select-card').filter({ hasText: 'NO20260826002' })
        await expect(assignedTask).toContainText('配送中')
        await assignedTask.getByRole('button', { name: '完成配送' }).click()
        await expectToast(page, '任务状态已更新')
        await expect(page.locator('article.select-card').filter({ hasText: 'NO20260826002' })).toContainText('已完成')
    })

    test('UC18 UC19 管理员主体状态管理与平台订单查看', async ({ page }) => {
        await loginAs(page, 'admin')
        await expect(page.getByRole('heading', { name: '管理员工作台' })).toBeVisible()
        await expect(page.getByText('消费者')).toBeVisible()

        await page.getByRole('button', { name: '冻结' }).click()
        await expectToast(page, '用户已冻结')
        await expect(page.locator('tbody')).toContainText('已冻结')
        await page.getByRole('button', { name: '解冻' }).click()
        await expectToast(page, '用户已解冻')

        await page.getByRole('button', { name: '商家' }).click()
        await page.getByRole('button', { name: '通过审核' }).click()
        await expectToast(page, '商家已通过审核')
        await page.getByRole('button', { name: '冻结' }).click()
        await expectToast(page, '商家已冻结')

        await page.getByRole('button', { name: '骑手' }).click()
        await page.getByRole('button', { name: '通过审核' }).click()
        await expectToast(page, '骑手已通过审核')
        await page.getByRole('button', { name: '冻结' }).click()
        await expectToast(page, '骑手已冻结')

        await page.getByRole('button', { name: '订单' }).click()
        await expect(page.locator('tbody')).toContainText('NO20260826001')
        await expect(page.locator('tbody')).toContainText('￥20.00')
    })
})
