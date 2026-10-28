import { defineStore } from 'pinia'
import { api, clearAccessToken, restoreAccessToken, setAccessToken, type CurrentUser, type Envelope } from '../lib/api'

interface LoginData { accessToken: string; tokenType: string; expiresAt: string; user: CurrentUser }

export const useSessionStore = defineStore('session', {
  state: () => ({ user: null as CurrentUser | null, ready: false }),
  getters: { authenticated: (state) => Boolean(state.user) },
  actions: {
    async login(input: { username: string; password: string }): Promise<void> {
      const response = await api.post<Envelope<LoginData>>('/auth/login', input)
      setAccessToken(response.data.data.accessToken)
      this.user = response.data.data.user
      this.ready = true
    },
    async refreshMe(): Promise<void> {
      const token = restoreAccessToken()
      if (!token) { this.user = null; this.ready = true; return }
      try {
        const response = await api.get<Envelope<CurrentUser>>('/me')
        this.user = response.data.data
      } catch {
        this.logout()
      } finally {
        this.ready = true
      }
    },
    logout() {
      clearAccessToken()
      this.user = null
    },
  },
})
