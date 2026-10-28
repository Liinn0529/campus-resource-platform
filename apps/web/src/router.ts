import { createRouter, createWebHistory } from 'vue-router'
import RuntimeStatus from './views/RuntimeStatus.vue'
import NotFound from './views/NotFound.vue'
import Login from './views/LoginPage.vue'
import StudentHome from './views/StudentHome.vue'
import RoomCatalog from './views/RoomCatalog.vue'
import RoomBooking from './views/RoomBooking.vue'
import ReservationDetail from './views/ReservationDetail.vue'
import AdminSpaces from './views/AdminSpaces.vue'
import AdminRooms from './views/AdminRooms.vue'
import AdminSeats from './views/AdminSeats.vue'
import AdminUsers from './views/AdminUsers.vue'
import AdminRoles from './views/AdminRoles.vue'
import AdminSettings from './views/AdminSettings.vue'
import AdminAudit from './views/AdminAudit.vue'
import Forbidden from './views/AccessDeniedPage.vue'
import { hasAccessToken } from './lib/api'
import { useSessionStore } from './stores/session'
import { workflowRoutes } from './routes/workflows'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    requiresAuth?: boolean
    requiresRole?: string
    requiresPermission?: string
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: RuntimeStatus },
    { path: '/forbidden', component: Forbidden },
    { path: '/login', component: Login, meta: { public: true } },
    { path: '/student/home', component: StudentHome, meta: { requiresAuth: true, requiresPermission: 'reservation:view:self' } },
    { path: '/student/reservations', component: StudentHome, meta: { requiresAuth: true, requiresPermission: 'reservation:view:self' } },
    { path: '/student/history', component: StudentHome, meta: { requiresAuth: true, requiresPermission: 'reservation:view:self' } },
    { path: '/student/reservations/:id', component: ReservationDetail, meta: { requiresAuth: true, requiresPermission: 'reservation:view:self' } },
    { path: '/student/rooms', component: RoomCatalog, meta: { requiresAuth: true, requiresPermission: 'space:view' } },
    { path: '/student/rooms/:id', component: RoomBooking, meta: { requiresAuth: true, requiresPermission: 'space:view' } },
    { path: '/admin/spaces', component: AdminSpaces, meta: { requiresAuth: true, requiresRole: 'SYSTEM_ADMIN', requiresPermission: 'space:manage' } },
    { path: '/admin/rooms', component: AdminRooms, meta: { requiresAuth: true, requiresPermission: 'space:manage' } },
    { path: '/admin/seats', component: AdminSeats, meta: { requiresAuth: true, requiresPermission: 'space:manage' } },
    { path: '/admin/users', component: AdminUsers, meta: { requiresAuth: true, requiresRole: 'SYSTEM_ADMIN', requiresPermission: 'user:manage' } },
    { path: '/admin/roles', component: AdminRoles, meta: { requiresAuth: true, requiresRole: 'SYSTEM_ADMIN', requiresPermission: 'role:manage' } },
    { path: '/admin/settings', component: AdminSettings, meta: { requiresAuth: true, requiresRole: 'SYSTEM_ADMIN', requiresPermission: 'settings:manage' } },
    { path: '/admin/audit', component: AdminAudit, meta: { requiresAuth: true, requiresRole: 'SYSTEM_ADMIN', requiresPermission: 'audit:view' } },
    ...workflowRoutes,
    { path: '/:pathMatch(.*)*', component: NotFound },
  ],
})

router.beforeEach(async (to) => {
  const session = useSessionStore()
  if (!session.ready && !(to.meta.public && !hasAccessToken())) await session.refreshMe()
  if (to.meta.requiresAuth && !session.authenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresRole && !session.user?.roles.includes(to.meta.requiresRole)) return '/forbidden'
  if (to.meta.requiresPermission && !session.user?.permissions.includes(to.meta.requiresPermission)) return '/forbidden'
  if (to.path === '/login' && session.authenticated) return '/student/home'
})
