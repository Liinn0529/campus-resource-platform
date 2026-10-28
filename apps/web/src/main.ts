import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import { api } from './lib/api'
import { useSessionStore } from './stores/session'
import { installWorkflows } from './workflows/context'
import './style.css'

const app = createApp(App)
const pinia = createPinia()

installWorkflows(app, api, useSessionStore(pinia))
app.use(pinia).use(ElementPlus).use(router).mount('#app')
