import axios from 'axios'
import { defineStore } from 'pinia'

export interface BuildInfo {
  service: string
  version: string
  gitCommit: string
  buildTime: string
}

interface ServiceState {
  id: string
  title: string
  module: string
  state: 'pending' | 'up' | 'unavailable'
  healthStatus: string | null
  info: BuildInfo | null
  errors: string[]
}

const definitions = [
  { id: 'platform', title: '公共服务', module: 'D' },
  { id: 'reservation', title: '座位预约服务', module: 'M1' },
  { id: 'classroom', title: '教室审批服务', module: 'M2' },
  { id: 'maintenance', title: '物业报修服务', module: 'M3' },
]

function isBuildInfo(value: unknown, service: string): value is BuildInfo {
  if (typeof value !== 'object' || value === null) return false
  const info = value as Record<string, unknown>
  return info.service === `${service}-service`
    && typeof info.version === 'string' && info.version.length > 0
    && typeof info.gitCommit === 'string' && /^[a-f0-9]{40}([a-f0-9]{24})?(-dirty)?$/.test(info.gitCommit)
    && typeof info.buildTime === 'string' && Number.isFinite(Date.parse(info.buildTime))
}

function requestError(endpoint: string, error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.response) return `${endpoint}请求失败（HTTP ${error.response.status}）`
    if (error.code === 'ECONNABORTED') return `${endpoint}请求超时，请检查服务是否启动`
  }
  return `${endpoint}请求失败，请检查网络及服务连接`
}

export const useRuntimeStore = defineStore('runtime', {
  state: () => ({
    services: definitions.map((item): ServiceState => ({
      ...item, state: 'pending', healthStatus: null, info: null, errors: [],
    })),
    refreshing: false,
    checkedAt: null as string | null,
  }),
  actions: {
    async refresh() {
      if (this.refreshing) return
      this.refreshing = true
      await Promise.all(this.services.map(async (service) => {
        service.state = 'pending'
        service.healthStatus = null
        service.info = null
        service.errors = []
        const options = { timeout: 5000, headers: { Accept: 'application/json' } }
        const [health, info] = await Promise.allSettled([
          axios.get<unknown>(`/status/${service.id}/health`, options),
          axios.get<unknown>(`/status/${service.id}/info`, options),
        ])
        if (health.status === 'fulfilled') {
          const data = health.value.data
          if (typeof data === 'object' && data !== null && 'status' in data
            && typeof data.status === 'string' && data.status.length > 0) {
            service.healthStatus = data.status
            if (data.status !== 'UP') service.errors.push(`健康接口报告 ${data.status}`)
          } else {
            service.errors.push('健康接口返回格式无效')
          }
        } else {
          service.errors.push(requestError('健康接口', health.reason))
        }
        if (info.status === 'fulfilled') {
          if (isBuildInfo(info.value.data, service.id)) service.info = info.value.data
          else service.errors.push('版本接口返回格式无效或服务标识不匹配')
        } else {
          service.errors.push(requestError('版本接口', info.reason))
        }
        service.state = service.errors.length === 0 ? 'up' : 'unavailable'
      }))
      this.checkedAt = new Date().toISOString()
      this.refreshing = false
    },
  },
})
