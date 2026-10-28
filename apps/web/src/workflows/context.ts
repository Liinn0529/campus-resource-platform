import { computed, inject, ref, type App, type InjectionKey } from 'vue'
import axios, { type AxiosInstance } from 'axios'
import type { Session } from './types'

export interface WorkflowContext { api: AxiosInstance; session: Session }
export const workflowKey: InjectionKey<WorkflowContext> = Symbol('campus-workflows')
// Member 2 supplies the existing api instance and useSessionStore(); this owns no token or login state.
export function installWorkflows(app: App, api: AxiosInstance, session: Session) {
  app.provide(workflowKey, { api, session })
}
export function useWorkflows() {
  const context = inject(workflowKey)
  if (!context) throw new Error('Workflow integration requires the shared api and session store')
  return context
}
export function errorMessage(error: unknown) {
  if (!axios.isAxiosError(error)) return '操作未完成，请刷新后重试'
  const payload = error.response?.data as { message?: unknown; code?: unknown; requestId?: unknown } | undefined
  const status = error.response?.status
  const message = status === 401 ? '登录已失效，请重新登录' : status === 403 ? '当前身份无权执行此操作' :
    status === 404 ? '记录不存在或当前身份无权查看' : status === 409 ? '记录状态已变化，请刷新后再操作' :
      status === 503 ? '服务暂时不可用，请稍后重试' : typeof payload?.message === 'string' ? payload.message : '请求失败，请稍后重试'
  return message + (typeof payload?.code === 'string' ? `（${payload.code}）` : '') +
    (typeof payload?.requestId === 'string' ? `，请求编号：${payload.requestId}` : '')
}
export function useOperation() {
  const { session } = useWorkflows()
  const pending = ref(0), error = ref('')
  async function run<T>(action: () => Promise<T>): Promise<T | undefined> {
    pending.value++; error.value = ''
    try { return await action() } catch (failure) {
      if (axios.isAxiosError(failure) && failure.response?.status === 401) session.logout()
      error.value = errorMessage(failure)
    } finally { pending.value-- }
  }
  return { busy: computed(() => pending.value > 0), error, run }
}
