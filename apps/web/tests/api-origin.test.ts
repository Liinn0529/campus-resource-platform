import { afterEach, expect, test, vi } from 'vitest'
import { api, clearAccessToken, setAccessToken } from '../src/lib/api'

afterEach(clearAccessToken)

test('authenticated requests cannot send a session credential to another origin or outside the API prefix', async () => {
  setAccessToken('test-only-session-marker')
  const adapter = vi.fn()
  for (const path of ['https://example.invalid/collect', '//example.invalid/collect', '/\\example.invalid/collect', '/../collect']) {
    await expect(api.get(path, { adapter })).rejects.toThrow('same-origin API paths')
  }
  await expect(api.get('/me', { baseURL: 'https://example.invalid', adapter })).rejects.toThrow('same-origin API paths')
  expect(adapter).not.toHaveBeenCalled()
})

test('same-origin API keeps the envelope and removes the credential after logout', async () => {
  const seen: unknown[] = []
  const adapter: NonNullable<import('axios').AxiosRequestConfig['adapter']> = async (config) => {
    seen.push(config.headers.Authorization)
    return { config, status: 200, statusText: 'OK', headers: {}, data: { data: { id: 1 }, requestId: 'test-request' } }
  }
  setAccessToken('test-only-session-marker')
  const response = await api.get('/me', { adapter })
  expect(response.data).toEqual({ data: { id: 1 }, requestId: 'test-request' })
  clearAccessToken()
  await api.get('/me', { adapter })
  expect(seen).toEqual(['Bearer test-only-session-marker', undefined])
})
