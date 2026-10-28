import axios from 'axios'

export interface Envelope<T> { data: T; requestId: string }
export interface Actor {
  id: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}
export type CurrentUser = Actor
export interface Booking {
  id: number
  userId: number
  roomId: number
  seatId: number
  startAt: string
  endAt: string
  status: 'CREATED' | 'CANCELLED' | 'CHECKED_IN' | 'NO_SHOW' | 'COMPLETED'
  createdAt: string
  cancelledAt: string | null
  rebookOf: number | null
}

let accessToken: string | null = null

export function setAccessToken(token: string) {
  accessToken = token
  if (typeof sessionStorage !== 'undefined') sessionStorage.setItem('campus.accessToken', token)
}

export function clearAccessToken() {
  accessToken = null
  if (typeof sessionStorage !== 'undefined') sessionStorage.removeItem('campus.accessToken')
}

export function restoreAccessToken() {
  if (typeof sessionStorage === 'undefined') return null
  accessToken = sessionStorage.getItem('campus.accessToken')
  return accessToken
}

export function hasAccessToken() {
  return Boolean(accessToken || (typeof sessionStorage !== 'undefined' && sessionStorage.getItem('campus.accessToken')))
}

export const api = axios.create({ baseURL: '/api/v1', timeout: 10000, headers: { Accept: 'application/json' } })
api.interceptors.request.use((config) => {
  const path = config.url ?? ''
  if (config.baseURL !== '/api/v1' || !path.startsWith('/') || path.startsWith('//')
    || path.includes('\\') || !new URL(`/api/v1${path}`, 'http://campus.invalid').pathname.startsWith('/api/v1/')) {
    throw new Error('The authenticated client only accepts same-origin API paths.')
  }
  const token = accessToken ?? restoreAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
api.interceptors.response.use((response) => response, (error: unknown) => {
  if (axios.isAxiosError(error) && error.response?.status === 401
    && error.config?.url !== '/auth/login' && error.config?.url !== '/me') {
    clearAccessToken()
    if (typeof window !== 'undefined') window.dispatchEvent(new Event('campus:session-expired'))
  }
  return Promise.reject(error)
})

export async function fetchAll<T>(path: string, filters: Record<string, string | number | boolean> = {}): Promise<T[]> {
  const results: T[] = []
  let offset = 0
  while (true) {
    const response = await api.get<Envelope<T[]>>(path, { params: { ...filters, limit: 100, offset } })
    results.push(...response.data.data)
    if (response.data.data.length < 100) return results
    offset += 100
  }
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    const code = error.response?.data?.code
    if (status === 401) return error.config?.url === '/auth/login'
      ? '用户名或密码不正确，或账号已停用。'
      : '登录状态已失效，请重新登录。'
    if (status === 403) return '当前账号没有执行此操作的权限。'
    if (status === 404) return '记录不存在或已不可访问。'
    if (status === 409) return '数据冲突或当前状态不允许此操作，请刷新后重试。'
    if (status === 422) return '提交内容不符合业务规则，请检查输入内容、时间和资源。'
    if (status === 503) return '服务暂不可用，请稍后重试。'
    if (status) return `请求失败（HTTP ${status}${typeof code === 'string' ? `，${code}` : ''}）。`
    return '无法连接服务，请检查服务是否启动。'
  }
  return '操作失败，请稍后重试。'
}
