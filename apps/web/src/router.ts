import { createRouter, createWebHistory } from 'vue-router'
import RuntimeStatus from './views/RuntimeStatus.vue'
import NotFound from './views/NotFound.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: RuntimeStatus },
    { path: '/:pathMatch(.*)*', component: NotFound },
  ],
})
