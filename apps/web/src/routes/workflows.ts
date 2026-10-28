import type { NavigationGuard, RouteRecordRaw } from 'vue-router'
import { useSessionStore } from '../stores/session'

// The shared beforeEach refreshes /me and handles requiresAuth before these guards.
const approvalEntry: NavigationGuard = () => {
  const user = useSessionStore().user
  return !!user && user.permissions.includes('classroom:approve') &&
    user.roles.some(role => role === 'ROOM_ADMIN' || role === 'DEPARTMENT_ADMIN')
    ? true : '/forbidden'
}
const maintenanceEntry: NavigationGuard = () => {
  const user = useSessionStore().user
  return !!user && (user.roles.includes('PROPERTY_STAFF') ||
    (user.roles.includes('PROPERTY_MANAGER') && user.permissions.includes('maintenance:assign')))
    ? true : '/forbidden'
}

export const workflowRoutes: RouteRecordRaw[] = [
  { path: '/student/classroom-applications', name: 'classroom-applications', component: () => import('../views/classroom/Applications.vue'), meta: { requiresAuth: true } },
  { path: '/student/repairs', name: 'student-repairs', component: () => import('../views/repairs/List.vue'), meta: { requiresAuth: true } },
  { path: '/approvals', name: 'approvals', component: () => import('../views/approval/List.vue'), meta: { requiresAuth: true, requiresPermission: 'classroom:approve' }, beforeEnter: approvalEntry },
  { path: '/approvals/:id', name: 'approval-detail', component: () => import('../views/approval/Detail.vue'), meta: { requiresAuth: true, requiresPermission: 'classroom:approve' }, beforeEnter: approvalEntry },
  { path: '/maintenance', name: 'maintenance', component: () => import('../views/maintenance/List.vue'), meta: { requiresAuth: true }, beforeEnter: maintenanceEntry },
  // Reporters may read their own ticket; the backend checks reporter/assignee/manager scope.
  { path: '/maintenance/:id', name: 'maintenance-detail', component: () => import('../views/maintenance/Detail.vue'), meta: { requiresAuth: true } },
]
