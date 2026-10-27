import type { RouteRecordRaw } from 'vue-router'
export const workflowRoutes: RouteRecordRaw[] = [
  { path: '/student/classroom-applications', name: 'classroom-applications', component: () => import('../views/classroom/Applications.vue'), meta: { requiresAuth: true } },
  { path: '/student/repairs', name: 'student-repairs', component: () => import('../views/repairs/List.vue'), meta: { requiresAuth: true } },
  { path: '/approvals', name: 'approvals', component: () => import('../views/approval/List.vue'), meta: { requiresAuth: true } },
  { path: '/approvals/:id', name: 'approval-detail', component: () => import('../views/approval/Detail.vue'), meta: { requiresAuth: true } },
  { path: '/maintenance', name: 'maintenance', component: () => import('../views/maintenance/List.vue'), meta: { requiresAuth: true } },
  { path: '/maintenance/:id', name: 'maintenance-detail', component: () => import('../views/maintenance/Detail.vue'), meta: { requiresAuth: true } },
]
