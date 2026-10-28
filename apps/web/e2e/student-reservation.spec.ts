import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const createdReservationIds: number[] = []

function localDateTime(value: Date) {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`
}

async function expectNoHorizontalOverflow(page: Page) {
  const { scrollWidth, viewportWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    viewportWidth: window.innerWidth,
  }))
  const configuredWidth = page.viewportSize()?.width
  if (configuredWidth !== undefined) expect(viewportWidth).toBe(configuredWidth)
  expect(scrollWidth).toBeLessThanOrEqual(viewportWidth)
}

async function cancelCreatedReservations(page: Page, request: APIRequestContext) {
  const accessToken = await page.evaluate(() => sessionStorage.getItem('campus.accessToken'))
  if (!accessToken && createdReservationIds.length > 0) {
    throw new Error('The local test session ended before test-created reservations could be cleaned up.')
  }
  for (const id of createdReservationIds.splice(0)) {
    const response = await request.post(`/api/v1/reservations/${id}/cancel`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })
    expect(response.status()).toBe(200)
  }
}

test.afterEach(async ({ page, request }) => {
  await cancelCreatedReservations(page, request)
})

test('student logs in, books, cancels, views history, and rebooks through real APIs', async ({ page }) => {
  test.skip(process.env.RUN_REAL_E2E !== '1', 'Set RUN_REAL_E2E=1 to create and cancel real local reservations; cancellation intentionally preserves history.')
  const password = process.env.DEV_SEED_PASSWORD
  test.skip(!password, 'Set DEV_SEED_PASSWORD in the private workspace .env before running the real local E2E flow.')

  await page.setViewportSize({ width: 375, height: 812 })
  const apiStatuses: string[] = []
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  page.on('response', async (response) => {
    const url = new URL(response.url())
    if (url.pathname.startsWith('/api/v1/')) {
      let code = ''
      if (response.status() >= 400) {
        try {
          const body = await response.json() as { code?: unknown }
          if (typeof body.code === 'string') code = ` ${body.code}`
        } catch {
          code = ' NON_JSON_ERROR'
        }
      }
      apiStatuses.push(`${response.request().method()} ${url.pathname} ${response.status()}${code}`)
    }
  })

  await page.goto('/login?redirect=/student/home')
  await expectNoHorizontalOverflow(page)
  await page.getByLabel('用户名').fill('demo_student_01')
  await page.getByLabel('密码').fill(password!)
  const loginResponsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return url.pathname === '/api/v1/auth/login' && response.request().method() === 'POST'
  }, { timeout: 20_000 })
  await page.getByRole('button', { name: '登录' }).click()
  const loginResponse = await loginResponsePromise
  const loginEnvelope = await loginResponse.json() as {
    data?: { accessToken?: unknown; user?: { username?: unknown; permissions?: unknown } }
  }
  const loginShape = {
    tokenPresent: typeof loginEnvelope.data?.accessToken === 'string' && loginEnvelope.data.accessToken.length > 0,
    canViewOwnReservations: Array.isArray(loginEnvelope.data?.user?.permissions)
      && loginEnvelope.data.user.permissions.includes('reservation:view:self'),
  }
  let loginErrorCode = ''
  if (loginResponse.status() >= 400) {
    const body = loginEnvelope as { code?: unknown }
    if (typeof body.code === 'string') loginErrorCode = body.code
  }
  expect(loginResponse.status(), `Login API error code: ${loginErrorCode || 'none'}`).toBe(200)
  try {
    await page.waitForURL((url) => url.pathname === '/student/home', { timeout: 5_000 })
  } catch {
    const visibleText = await page.locator('body').innerText()
    throw new Error(`Login succeeded but navigation failed. Page: ${new URL(page.url()).pathname}. Session shape: ${JSON.stringify(loginShape)}. API statuses: ${apiStatuses.join('; ')}. Visible text: ${visibleText}. Browser errors: ${pageErrors.join('; ') || 'none'}`)
  }
  const welcome = page.getByRole('heading', { name: /你好/ })
  if (!(await welcome.isVisible())) {
    const pageText = await page.locator('body').innerText()
    throw new Error(`Login did not render student home. Visible text: ${pageText}. API statuses: ${apiStatuses.join('; ')}`)
  }
  await expectNoHorizontalOverflow(page)

  await page.getByRole('link', { name: '预约座位' }).click()
  await expect(page).toHaveURL(/\/student\/rooms$/)
  await expectNoHorizontalOverflow(page)
  await page.getByRole('link', { name: /查看可用座位/ }).first().click()
  await expectNoHorizontalOverflow(page)

  const timeInputs = page.locator('input[type="datetime-local"]')
  const searchButton = page.getByRole('button', { name: '查询座位' })
  await timeInputs.nth(0).fill('')
  await searchButton.click()
  await expect(page.getByRole('alert')).toContainText('请输入有效的开始和结束时间')
  await expect(searchButton).toBeEnabled()
  await timeInputs.nth(1).fill('')
  await timeInputs.nth(0).fill(localDateTime(new Date()))
  await searchButton.click()
  await expect(page.getByRole('alert')).toContainText('请输入有效的开始和结束时间')
  await expect(searchButton).toBeEnabled()

  const start = new Date()
  start.setMinutes(0, 0, 0)
  start.setHours(start.getHours() + 2)
  const end = new Date(start)
  end.setHours(end.getHours() + 1)
  await timeInputs.nth(0).fill(localDateTime(start))
  await timeInputs.nth(1).fill(localDateTime(end))
  await searchButton.click()

  const availableSeat = page.locator('.seat-tile.available').first()
  await expect(availableSeat).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await availableSeat.click()
  const firstBookingResponse = page.waitForResponse((response) =>
    response.url().includes('/api/v1/reservations')
      && response.request().method() === 'POST'
      && response.status() === 201,
  )
  await page.getByRole('button', { name: '确认预约' }).click()
  const firstBooking = await (await firstBookingResponse).json() as { data: { id: number } }
  createdReservationIds.push(firstBooking.data.id)
  await expect(page).toHaveURL(/\/student\/reservations$/)
  await expectNoHorizontalOverflow(page)
  await expect(page.locator('.booking-card')).toHaveCount(1)

  await page.getByRole('link', { name: /查看详情/ }).first().click()
  await expect(page.getByRole('heading', { name: '预约详情' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '预约详情' })).toBeVisible()
  await expect(page.locator('.reservation-facts')).toBeVisible()
  await expectNoHorizontalOverflow(page)
  await page.getByLabel('签到码').fill('ABCDEFGH')
  const earlyCheckIn = page.waitForResponse((response) =>
    response.url().includes(`/api/v1/reservations/${firstBooking.data.id}/checkin`)
      && response.request().method() === 'POST',
  )
  await page.getByRole('button', { name: '签到' }).click()
  const earlyCheckInResponse = await earlyCheckIn
  expect(earlyCheckInResponse.status()).toBe(409)
  await expect(page.getByRole('alert')).toContainText('当前还未到签到时间')
  await page.getByRole('button', { name: '取消预约' }).click()
  await expect(page.getByRole('status')).toContainText('预约已取消')

  await page.getByRole('link', { name: '预约历史' }).click()
  await expectNoHorizontalOverflow(page)
  const originalBookingCard = page.locator('.booking-card').filter({ hasText: `预约 #${firstBooking.data.id}` })
  await expect(originalBookingCard).toBeVisible()
  await originalBookingCard.getByRole('link', { name: '再次预约此座位' }).click()
  await page.getByRole('button', { name: '查询座位' }).click()
  await expect(page.getByRole('button', { name: '确认再次预约' })).toBeEnabled()

  const rebookResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/v1/reservations/${firstBooking.data.id}/rebook`)
      && response.request().method() === 'POST'
      && response.status() === 201,
  )
  await page.getByRole('button', { name: '确认再次预约' }).click()
  const rebooked = await (await rebookResponse).json() as { data: { id: number; rebookOf: number } }
  createdReservationIds.push(rebooked.data.id)
  expect(rebooked.data.rebookOf).toBe(firstBooking.data.id)
  await expect(page).toHaveURL(/\/student\/reservations$/)

  await page.locator('.booking-card').filter({ hasText: `预约 #${rebooked.data.id}` }).getByRole('link', { name: /查看详情/ }).click()
  await expect(page.locator('.reservation-facts')).toContainText('原预约')
  await expect(page.locator('.reservation-facts').getByText(`#${firstBooking.data.id}`, { exact: true })).toBeVisible()
  expect(pageErrors).toEqual([])
})

test('direct student route redirects an unauthenticated visitor to login', async ({ page }) => {
  await page.goto('/student/home')
  await expect.poll(() => new URL(page.url()).searchParams.get('redirect')).toBe('/student/home')
  await expect(page).toHaveURL(/\/login\?/)
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
})

test('student is denied admin routes and administrator can read all seven management pages', async ({ page, browser }) => {
  const password = process.env.DEV_SEED_PASSWORD
  test.skip(!password, 'Set DEV_SEED_PASSWORD in the private workspace .env before running real local role checks.')

  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo_student_01')
  await page.getByLabel('密码').fill(password!)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/student\/home$/)
  await page.goto('/admin/spaces')
  await expect(page).toHaveURL(/\/forbidden$/)
  for (const path of ['/approvals', '/maintenance']) {
    await page.goto(path)
    await expect(page).toHaveURL(/\/forbidden$/)
  }

  const adminPage = await browser.newPage()
  const failedRequests: string[] = []
  adminPage.on('response', (response) => {
    if (response.url().includes('/api/v1/') && response.status() >= 400) {
      failedRequests.push(`${response.request().method()} ${new URL(response.url()).pathname} ${response.status()}`)
    }
  })
  try {
    await adminPage.goto('/login')
    await adminPage.getByLabel('用户名').fill('demo_admin')
    await adminPage.getByLabel('密码').fill(password!)
    await adminPage.getByRole('button', { name: '登录' }).click()
    await expect(adminPage).toHaveURL(/\/student\/home$/)

    const routes = [
      ['/admin/spaces', '楼宇维护'],
      ['/admin/rooms', '房间维护'],
      ['/admin/seats', '座位维护'],
      ['/admin/users', '用户管理'],
      ['/admin/roles', '角色与权限'],
      ['/admin/settings', '系统设置'],
      ['/admin/audit', '操作审计'],
    ] as const
    for (const [path, heading] of routes) {
      await adminPage.goto(path)
      await expect(adminPage.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    }
    expect(failedRequests).toEqual([])
  } finally {
    await adminPage.close()
  }
})

test('administrator saves a setting through the page and restores its original value', async ({ page, request }) => {
  test.skip(process.env.RUN_REAL_E2E !== '1', 'Requires explicit local mutation opt-in.')
  const password = process.env.DEV_SEED_PASSWORD
  test.skip(!password, 'Requires the private local seed password.')
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo_admin')
  await page.getByLabel('密码').fill(password!)
  await page.getByLabel('密码').press('Enter')
  await expect(page).toHaveURL(/\/student\/home$/)
  await page.goto('/admin/settings')
  const input = page.getByLabel('单次预约最长时长（小时）')
  await expect(input).not.toHaveValue('')
  const original = Number(await input.inputValue())
  const changed = original === 1 ? 2 : 1
  const token = await page.evaluate(() => sessionStorage.getItem('campus.accessToken'))
  try {
    await input.fill(String(changed))
    await page.getByRole('button', { name: '保存参数' }).click()
    await expect(page.getByRole('status')).toContainText('预约时长参数已保存')
    await page.reload()
    await expect(input).toHaveValue(String(changed))
  } finally {
    const restored = await request.put('/api/v1/settings', {
      headers: { Authorization: `Bearer ${token}` },
      data: { maxReservationHours: original },
    })
    expect(restored.status()).toBe(200)
    await page.reload()
    await expect(input).toHaveValue(String(original))
  }
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
  expect(await page.evaluate(() => sessionStorage.getItem('campus.accessToken'))).toBeNull()
  await page.goto('/admin/settings')
  await expect(page).toHaveURL(/\/login/)
})
