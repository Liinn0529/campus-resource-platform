import { randomUUID } from 'node:crypto'
import { mkdir } from 'node:fs/promises'
import { expect, test, type Page, type APIRequestContext } from '@playwright/test'

const password = process.env.DEV_SEED_PASSWORD!
const suffix = Date.now().toString(36)
test.beforeEach(async ({ baseURL }) => {
  expect(baseURL).toBe('http://127.0.0.1:5174')
  expect(process.env.RUN_ISOLATED_E2E, 'Use only tests/member2/compose.yaml with its temporary database').toBe('1')
  expect(Boolean(password)).toBe(true)
})

async function login(page: Page, username = 'demo_admin', secret = password) {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(secret)
  await page.getByLabel('密码').press('Enter')
  await expect(page).not.toHaveURL(/\/login/)
}

async function tokenFor(request: APIRequestContext, username: string, secret = password) {
  const response = await request.post('/api/v1/auth/login', { data: { username, password: secret } })
  expect(response.status()).toBe(200)
  return (await response.json()).data.accessToken as string
}

function interval(hoursAhead = 2) {
  const start = new Date()
  start.setMinutes(0, 0, 0)
  start.setHours(start.getHours() + hoursAhead)
  return { startAt: start.toISOString(), endAt: new Date(start.getTime() + 3600000).toISOString() }
}

function localInput(instant: string) {
  const date = new Date(instant)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

test('all editable admin pages persist real changes and audit records', async ({ page, request }) => {
  await login(page)
  page.on('dialog', (dialog) => void dialog.accept())
  const buildingCode = `B_${suffix}`
  const roomCode = `R_${suffix}`
  const username = `acceptance_${suffix}`
  const initialPassword = randomUUID() + 'aA1!'
  const replacementPassword = randomUUID() + 'aA1!'

  await page.goto('/admin/spaces')
  await page.getByLabel('楼宇编码').fill(buildingCode)
  await page.getByLabel('楼宇名称').fill('隔离验收楼宇')
  await page.locator('form').getByRole('button', { name: /创建|保存/ }).click()
  const buildingRow = page.getByRole('row').filter({ hasText: buildingCode })
  await expect(buildingRow).toBeVisible()
  await buildingRow.getByRole('button', { name: '编辑' }).click()
  await page.getByLabel('楼宇名称').fill('隔离验收楼宇修改')
  await page.locator('form').getByRole('button', { name: /保存/ }).click()
  await page.reload()
  await expect(buildingRow).toContainText('隔离验收楼宇修改')

  await page.goto('/admin/rooms')
  await page.getByLabel('所属楼宇').selectOption({ label: `隔离验收楼宇修改（${buildingCode}）` })
  await page.getByLabel('房间编码').fill(roomCode)
  await page.getByLabel('房间名称').fill('隔离验收房间')
  await page.getByRole('button', { name: '创建房间' }).click()
  const roomRow = page.getByRole('row').filter({ hasText: roomCode })
  await expect(roomRow).toBeVisible()
  const roomId = Number(await roomRow.locator('td').first().innerText())
  await roomRow.getByRole('button', { name: '改名称' }).click()
  await roomRow.locator('input').fill('隔离验收房间修改')
  await roomRow.getByRole('button', { name: '保存', exact: true }).click()
  await expect(roomRow).toContainText('隔离验收房间修改')
  await roomRow.getByRole('button', { name: '停用', exact: true }).click()
  await expect(roomRow.locator('.disabled-label')).toBeVisible()
  await roomRow.getByRole('button', { name: '启用', exact: true }).click()
  await expect(roomRow.locator('.enabled-label')).toBeVisible()

  await page.goto('/admin/seats')
  await page.locator('select').selectOption(String(roomId))
  await page.getByLabel('座位编码').fill('TEST01')
  await page.getByLabel('有电源插座').check()
  await page.getByRole('button', { name: '新增座位' }).click()
  let seatRow = page.getByRole('row').filter({ hasText: 'TEST01' })
  await expect(seatRow).toBeVisible()
  const seatId = await seatRow.locator('td').first().innerText()
  await seatRow.getByRole('button', { name: '编辑' }).click()
  seatRow = page.locator('tbody tr').filter({ has: page.locator('td:first-child', { hasText: new RegExp(`^${seatId}$`) }) })
  await seatRow.locator('input[type=text], input:not([type])').fill('TEST02')
  await seatRow.getByRole('checkbox').uncheck()
  await seatRow.getByRole('button', { name: '保存', exact: true }).click()
  seatRow = page.getByRole('row').filter({ hasText: 'TEST02' })
  await expect(seatRow).toContainText('无')
  await seatRow.getByRole('button', { name: '停用', exact: true }).click()
  await expect(seatRow.locator('.disabled-label')).toBeVisible()
  await seatRow.getByRole('button', { name: '启用', exact: true }).click()
  await expect(seatRow.locator('.enabled-label')).toBeVisible()

  await page.goto('/admin/users')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('显示名称').fill('隔离验收账号')
  await page.getByLabel('初始密码').fill(initialPassword)
  await page.locator('input[value=STUDENT]').check()
  await page.getByRole('button', { name: '创建用户', exact: true }).click()
  const userRow = page.getByRole('row').filter({ hasText: username })
  await expect(userRow).toBeVisible()
  const studentToken = await tokenFor(request, username, initialPassword)
  await userRow.getByRole('button', { name: '停用', exact: true }).click()
  await expect(userRow.locator('.disabled-label')).toBeVisible()
  const denied = await request.get('/api/v1/me', { headers: { Authorization: `Bearer ${studentToken}` } })
  expect([401, 403]).toContain(denied.status())
  await userRow.getByRole('button', { name: '启用', exact: true }).click()
  await expect(userRow.locator('.enabled-label')).toBeVisible()
  await userRow.getByRole('button', { name: '重置密码' }).click()
  await page.getByLabel('新密码', { exact: true }).fill(replacementPassword)
  await page.getByRole('button', { name: '确认重置' }).click()
  await expect(page.getByRole('status')).toContainText('密码已重置')
  await tokenFor(request, username, replacementPassword)
  const oldPasswordResult = await request.post('/api/v1/auth/login', { data: { username, password: initialPassword } })
  expect(oldPasswordResult.status()).toBe(401)

  await page.goto('/admin/roles')
  await page.getByLabel('选择用户').selectOption({ label: `隔离验收账号（${username}）` })
  await page.locator('.role-assignment input[value=ROOM_ADMIN]').check()
  await page.locator(`.room-assignment input[value="${roomId}"]`).check()
  await page.getByRole('button', { name: '保存用户授权' }).click()
  await expect(page.getByRole('status')).toContainText('已更新')
  await page.reload()
  await page.getByLabel('选择用户').selectOption({ label: `隔离验收账号（${username}）` })
  await expect(page.locator('.role-assignment input[value=ROOM_ADMIN]')).toBeChecked()
  await expect(page.locator(`.room-assignment input[value="${roomId}"]`)).toBeChecked()
  await page.getByLabel('选择角色', { exact: true }).selectOption('OPERATOR')
  const permission = page.locator('.permission-checks input[value="space:view"]').first()
  const wasChecked = await permission.isChecked()
  await permission.setChecked(!wasChecked)
  await page.getByRole('button', { name: '保存角色权限' }).click()
  await expect(page.getByRole('status')).toContainText('权限已更新')
  await page.reload()
  await page.getByLabel('选择角色', { exact: true }).selectOption('OPERATOR')
  await expect(permission).toBeChecked({ checked: !wasChecked })
  await permission.setChecked(wasChecked)
  await page.getByRole('button', { name: '保存角色权限' }).click()
  await expect(page.getByRole('status')).toContainText('权限已更新')

  await page.goto('/admin/audit')
  await page.getByLabel('目标类型（可选）').fill('USER')
  await page.locator('form').getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.locator('tbody tr').first()).toBeVisible()
  expect(await page.locator('body').innerText()).not.toContain(replacementPassword)
})

test('real history crosses 50 rows, recovers from network failure, and rejects stale seat selection', async ({ page, request }) => {
  const token = await tokenFor(request, 'demo_student_01')
  const headers = { Authorization: `Bearer ${token}` }
  const times = interval()
  // Real create/cancel operations are confined to the disposable PostgreSQL instance.
  const ids: number[] = []
  for (let i = 0; i < 51; i++) {
    const response = await request.post('/api/v1/reservations', { headers, data: { roomId: 1, seatId: 1, ...times } })
    expect(response.status()).toBe(201)
    const id = (await response.json()).data.id as number
    ids.push(id)
    expect((await request.post(`/api/v1/reservations/${id}/cancel`, { headers })).status()).toBe(200)
  }
  await login(page, 'demo_student_01')
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/student/history')
  await expect(page.locator('.booking-card')).toHaveCount(50)
  const pager = page.getByRole('navigation', { name: '预约分页', exact: true })
  const firstPageIds = await page.locator('.booking-id').allTextContents()
  await pager.getByRole('button', { name: '下一页' }).click()
  await expect(pager).toContainText('第 2 页')
  await expect(page.locator('.booking-card').filter({ hasText: `预约 #${ids[0]}` })).toBeVisible()
  const secondPageIds = await page.locator('.booking-id').allTextContents()
  expect(secondPageIds.some((id) => firstPageIds.includes(id))).toBe(false)
  await pager.getByRole('button', { name: '上一页' }).click()
  await expect(pager).toContainText('第 1 页')
  await expect(page.locator('.booking-card')).toHaveCount(50)
  const viewport = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth }))
  expect(viewport.width).toBe(375)
  expect(viewport.scroll).toBeLessThanOrEqual(375)

  await page.route('**/api/v1/reservations?*', (route) => route.abort('connectionfailed'))
  await page.reload()
  await expect(page.getByRole('alert').first()).toContainText('无法连接服务')
  await page.unroute('**/api/v1/reservations?*')
  await page.reload()
  await expect(page.locator('.booking-card')).toHaveCount(50)

  let releaseHistory!: () => void
  let historyArrived!: () => void
  const holdHistory = new Promise<void>((resolve) => { releaseHistory = resolve })
  const capturedHistory = new Promise<void>((resolve) => { historyArrived = resolve })
  await page.route('**/api/v1/reservations?*', async (route) => {
    if (new URL(route.request().url()).searchParams.get('view') !== 'history') return route.continue()
    const response = await route.fetch()
    historyArrived()
    await holdHistory
    await route.fulfill({ response })
  })
  try {
    await page.reload()
    await capturedHistory
    await page.getByRole('button', { name: '当前预约', exact: true }).click()
    await expect(page.locator('.empty-state').filter({ hasText: '暂无当前预约' })).toBeVisible()
    const delivered = page.waitForResponse((response) => response.url().includes('/api/v1/reservations?') && response.url().includes('view=history'))
    releaseHistory()
    await delivered
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.booking-card')).toHaveCount(0)
  } finally {
    releaseHistory()
    await page.unroute('**/api/v1/reservations?*')
  }

  await page.goto('/student/rooms/1')
  const dates = page.locator('input[type=datetime-local]')
  await dates.nth(0).fill(localInput(times.startAt))
  await dates.nth(1).fill(localInput(times.endAt))
  await page.getByRole('button', { name: '查询座位' }).click()
  await page.locator('.seat-tile.available').filter({ hasText: 'S01' }).click()
  const competing = await request.post('/api/v1/reservations', { headers, data: { roomId: 1, seatId: 1, ...times } })
  expect(competing.status()).toBe(201)
  const competingId = (await competing.json()).data.id
  try {
    const rejected = page.waitForResponse((response) => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/v1/reservations')
    await page.getByRole('button', { name: '确认预约', exact: true }).click()
    expect((await rejected).status()).toBe(409)
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page).toHaveURL(/\/student\/rooms\/1$/)
  } finally {
    expect((await request.post(`/api/v1/reservations/${competingId}/cancel`, { headers })).status()).toBe(200)
  }
})

test('availability includes real seats beyond the first 100 and applies outlet filtering', async ({ page, request }) => {
  const token = await tokenFor(request, 'demo_admin')
  const headers = { Authorization: `Bearer ${token}` }
  const building = await request.post('/api/v1/buildings', { headers, data: { code: `BIG_${suffix}`, name: '隔离分页楼宇' } })
  expect(building.status()).toBe(201)
  const buildingId = (await building.json()).data.id
  const room = await request.post('/api/v1/rooms', { headers, data: { buildingId, code: `BIG_${suffix}`, name: '隔离百座房间', type: 'STUDY_ROOM', enabled: true } })
  expect(room.status()).toBe(201)
  const roomId = (await room.json()).data.id
  for (let i = 1; i <= 101; i++) {
    const seat = await request.post(`/api/v1/rooms/${roomId}/seats`, { headers, data: { code: `P${String(i).padStart(3, '0')}`, hasPowerOutlet: i === 101, enabled: true } })
    expect(seat.status()).toBe(201)
  }
  await login(page, 'demo_student_01')
  await page.goto(`/student/rooms/${roomId}`)
  await page.getByRole('button', { name: '查询座位' }).click()
  await expect(page.locator('.seat-tile')).toHaveCount(101)
  await expect(page.locator('.seat-tile').filter({ hasText: 'P101' })).toBeVisible()
  await page.getByLabel('插座筛选').selectOption('yes')
  await page.getByRole('button', { name: '查询座位' }).click()
  await expect(page.locator('.seat-tile')).toHaveCount(1)
  await expect(page.locator('.seat-tile')).toContainText('P101')
})

test('member 2 pages have usable direct routes and retained visual evidence', async ({ page, request }) => {
  await mkdir('../../work/member2-evidence', { recursive: true })
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/login')
  await page.screenshot({ path: '../../work/member2-evidence/login-375.png', fullPage: true })
  await login(page, 'demo_student_01')
  const token = await tokenFor(request, 'demo_student_01')
  const history = await request.get('/api/v1/reservations?view=history&limit=1&offset=0', { headers: { Authorization: `Bearer ${token}` } })
  expect(history.status()).toBe(200)
  const bookingId = (await history.json()).data[0].id
  for (const [path, name] of [
    ['/student/home', 'home'], ['/student/rooms', 'rooms'], ['/student/rooms/1', 'room'],
    ['/student/reservations', 'reservations'], [`/student/reservations/${bookingId}`, 'detail'], ['/student/history', 'history'],
  ]) {
    await page.goto(path!)
    await page.waitForLoadState('networkidle')
    await expect(page.locator('h1')).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
    const widths = await page.evaluate(() => [innerWidth, document.documentElement.scrollWidth])
    expect(widths[0]).toBe(375)
    expect(widths[1]).toBeLessThanOrEqual(375)
    const navigation = await page.locator('.app-header').evaluate((header) => {
      const identity = header.querySelector('.identity')!.getBoundingClientRect()
      const nav = header.querySelector('nav')!.getBoundingClientRect()
      return { identityBottom: identity.bottom, navTop: nav.top, navWidth: nav.width }
    })
    expect(navigation.navTop).toBeGreaterThanOrEqual(navigation.identityBottom)
    expect(navigation.navWidth).toBeGreaterThan(300)
    await page.screenshot({ path: `../../work/member2-evidence/${name}-375.png`, fullPage: true })
  }
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await login(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  for (const name of ['spaces', 'rooms', 'seats', 'users', 'roles', 'settings', 'audit']) {
    await page.goto(`/admin/${name}`)
    await page.waitForLoadState('networkidle')
    await expect(page.locator('h1')).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
    await page.screenshot({ path: `../../work/member2-evidence/admin-${name}-1440.png`, fullPage: true })
  }
})

test('invalid login, invalid stored session, and untrusted CORS origin are rejected', async ({ page, request }) => {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('demo_student_01')
  await page.getByLabel('密码').fill('intentionally-invalid-test-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('用户名或密码不正确')
  expect(await page.evaluate(() => sessionStorage.getItem('campus.accessToken'))).toBeNull()
  await login(page, 'demo_student_01')
  await page.evaluate(() => sessionStorage.setItem('campus.accessToken', 'invalid-session-test-marker'))
  await page.goto('/student/history')
  await expect(page).toHaveURL(/\/login/)
  expect(await page.evaluate(() => sessionStorage.getItem('campus.accessToken'))).toBeNull()
  const preflight = await request.fetch('http://127.0.0.1:18082/api/v1/auth/login', {
    method: 'OPTIONS', headers: { Origin: 'https://untrusted.invalid', 'Access-Control-Request-Method': 'POST' },
  })
  expect(preflight.status()).toBe(403)
  expect(preflight.headers()['access-control-allow-origin']).toBeUndefined()
})

test('real current-day code checks in through the browser during the opening window', async ({ page, request }) => {
  test.skip(process.env.RUN_WINDOW_CHECKIN !== '1', 'Run separately with RUN_WINDOW_CHECKIN=1; waits for the real opening window.')
  test.setTimeout(65 * 60_000)
  // Wait only on real time; neither host clocks nor application clocks are changed.
  const times = interval(1)
  const opensAt = new Date(times.startAt).getTime() - 15 * 60_000
  while (Date.now() < opensAt + 1500) {
    await new Promise((resolve) => setTimeout(resolve, Math.min(30_000, opensAt + 1500 - Date.now())))
  }
  const adminToken = await tokenFor(request, 'demo_admin')
  const studentToken = await tokenFor(request, 'demo_student_02')
  const created = await request.post('/api/v1/reservations', {
    headers: { Authorization: `Bearer ${studentToken}` }, data: { roomId: 1, seatId: 2, ...times },
  })
  expect(created.status()).toBe(201)
  const id = (await created.json()).data.id
  const issued = await request.post('/api/v1/rooms/1/checkin-code', { headers: { Authorization: `Bearer ${adminToken}` } })
  expect(issued.status()).toBe(200)
  const code = (await issued.json()).data.code as string
  await login(page, 'demo_student_02')
  await page.goto(`/student/reservations/${id}`)
  await page.getByLabel('签到码').fill(code)
  await page.getByRole('button', { name: '签到', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('签到成功')
  await expect(page.locator('.status-pill')).toHaveText('CHECKED_IN')
  await page.reload()
  await expect(page.locator('.status-pill')).toHaveText('CHECKED_IN')
  // A checked-in booking cannot be cancelled. Disposal is by removal of this
  // dedicated in-memory test database, never by rewriting business rows.
})
