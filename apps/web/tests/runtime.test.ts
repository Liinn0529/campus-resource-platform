import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import axios, { AxiosError } from 'axios'
import { useRuntimeStore } from '../src/stores/runtime'

const get = vi.spyOn(axios, 'get')

function reply(url: string) {
  const id = url.split('/')[2]
  return Promise.resolve({ data: url.endsWith('/health') ? { status: 'UP' } : {
    service: `${id}-service`, version: '0.1.0', gitCommit: 'a'.repeat(40), buildTime: '2026-10-20T00:00:00Z',
  } })
}

beforeEach(() => {
  setActivePinia(createPinia())
  get.mockReset()
  get.mockImplementation((url) => reply(url))
})

afterEach(() => vi.useRealTimers())

describe('runtime health aggregation', () => {
  it('reads both endpoints for each service before reporting success', async () => {
    const store = useRuntimeStore()
    expect(store.services.every((s) => s.state === 'pending')).toBe(true)
    await store.refresh()
    expect(get).toHaveBeenCalledTimes(8)
    for (const service of store.services) {
      expect(get).toHaveBeenCalledWith(`/status/${service.id}/health`, expect.objectContaining({ timeout: 5000 }))
      expect(get).toHaveBeenCalledWith(`/status/${service.id}/info`, expect.anything())
      expect(service.state).toBe('up')
      expect(service.info?.service).toBe(`${service.id}-service`)
    }
    expect(store.refreshing).toBe(false)
    expect(store.checkedAt).not.toBeNull()
  })

  it('clears stale success for a failed service without hiding healthy peers', async () => {
    const store = useRuntimeStore()
    await store.refresh()
    get.mockImplementation((url) => url === '/status/reservation/health'
      ? Promise.reject(new AxiosError('timeout', 'ECONNABORTED')) : reply(url))
    await store.refresh()
    expect(store.services.find((s) => s.id === 'reservation')).toMatchObject({
      state: 'unavailable', healthStatus: null,
      info: { service: 'reservation-service' },
      errors: ['健康接口请求超时，请检查服务是否启动'],
    })
    expect(store.services.filter((s) => s.state === 'up')).toHaveLength(3)
    expect(store.refreshing).toBe(false)
  })

  it('does not accept a DOWN response or malformed build metadata as healthy', async () => {
    get.mockImplementation((url) => {
      if (url === '/status/platform/health') return Promise.resolve({ data: { status: 'DOWN' } })
      if (url === '/status/classroom/info') return Promise.resolve({ data: '<html>fallback</html>' })
      return reply(url)
    })
    const store = useRuntimeStore()
    await store.refresh()
    expect(store.services.filter((s) => s.state === 'unavailable').map((s) => s.id))
      .toEqual(['platform', 'classroom'])
    expect(store.services.filter((s) => s.state === 'up')).toHaveLength(2)
    expect(store.services[0]?.errors).toEqual(['健康接口报告 DOWN'])
    expect(store.services[2]?.errors).toEqual(['版本接口返回格式无效或服务标识不匹配'])
  })

  it('clears old build metadata and reports network failure without inventing health', async () => {
    const store = useRuntimeStore()
    await store.refresh()
    get.mockRejectedValue(new AxiosError('Network Error', 'ERR_NETWORK'))
    await store.refresh()
    for (const service of store.services) {
      expect(service).toMatchObject({ state: 'unavailable', healthStatus: null, info: null })
      expect(service.errors).toEqual([
        '健康接口请求失败，请检查网络及服务连接',
        '版本接口请求失败，请检查网络及服务连接',
      ])
    }
  })

  it('rejects absent health and mismatched build information', async () => {
    get.mockImplementation((url) => {
      if (url === '/status/platform/health') return Promise.resolve({ data: null })
      if (url === '/status/classroom/info') return reply('/status/maintenance/info')
      return reply(url)
    })
    const store = useRuntimeStore()
    await store.refresh()
    expect(store.services[0]?.errors).toEqual(['健康接口返回格式无效'])
    expect(store.services[2]?.errors).toEqual(['版本接口返回格式无效或服务标识不匹配'])
  })

  it('deduplicates refresh while requests are pending and records completion time', async () => {
    vi.useFakeTimers()
    const completeAt = new Date('2026-10-21T01:00:00Z')
    get.mockImplementation((url) => new Promise((resolve) => {
      setTimeout(() => { void reply(url).then(resolve) }, 500)
    }))
    const store = useRuntimeStore()
    const refresh = store.refresh()
    await store.refresh()
    expect(get).toHaveBeenCalledTimes(8)
    expect(store.refreshing).toBe(true)
    expect(store.checkedAt).toBeNull()
    vi.setSystemTime(completeAt)
    await vi.advanceTimersByTimeAsync(500)
    await refresh
    expect(store.refreshing).toBe(false)
    expect(store.checkedAt).toBe(new Date(completeAt.getTime() + 500).toISOString())
  })
})
