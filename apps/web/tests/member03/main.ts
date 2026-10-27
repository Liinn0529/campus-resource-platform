import { createApp, reactive, ref } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import axios from 'axios'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Host from './WorkflowHost.vue'
import { workflowRoutes } from '../../src/routes/workflows'
import { installWorkflows } from '../../src/workflows/context'
import type { CurrentUser, Envelope, Session } from '../../src/workflows/types'

// This test-only host talks to real services. It owns no production login/store/router file.
const token = ref('')
const api = axios.create({ baseURL: '/api/v1', timeout: 10000 })
api.interceptors.request.use(config => {
  if (token.value) config.headers.set('Authorization', `Bearer ${token.value}`)
  return config
})
const session: Session = reactive({
  user: null as CurrentUser | null,
  logout() { token.value = ''; this.user = null },
  async refreshMe() { this.user = (await api.get<Envelope<CurrentUser>>('/me')).data.data },
})
const router = createRouter({ history: createWebHashHistory(), routes: workflowRoutes })
const app = createApp(Host, {
  session,
  async login(username: string, password: string) {
    const result = await api.post<Envelope<{ accessToken: string }>>('/auth/login', { username, password })
    token.value = result.data.data.accessToken
    await session.refreshMe()
    if (!router.currentRoute.value.matched.length) await router.replace('/student/classroom-applications')
  },
})
installWorkflows(app, api, session)
app.use(ElementPlus).use(router).mount('#app')
