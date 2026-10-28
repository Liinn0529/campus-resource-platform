<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { computed, onMounted, onUnmounted } from 'vue'
import { useSessionStore } from './stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const adminLinks = computed(() => {
  const user = session.user
  if (!user) return []
  return [
    { path: '/admin/spaces', label: '楼宇', permission: 'space:manage', role: 'SYSTEM_ADMIN' },
    { path: '/admin/rooms', label: '房间', permission: 'space:manage' },
    { path: '/admin/seats', label: '座位', permission: 'space:manage' },
    { path: '/admin/users', label: '用户', permission: 'user:manage', role: 'SYSTEM_ADMIN' },
    { path: '/admin/roles', label: '角色', permission: 'role:manage', role: 'SYSTEM_ADMIN' },
    { path: '/admin/settings', label: '参数', permission: 'settings:manage', role: 'SYSTEM_ADMIN' },
    { path: '/admin/audit', label: '审计', permission: 'audit:view', role: 'SYSTEM_ADMIN' },
  ].filter((link) => user.permissions.includes(link.permission) && (!link.role || user.roles.includes(link.role)))
})
const canViewReservations = computed(() => session.user?.permissions.includes('reservation:view:self') ?? false)
const canViewSpaces = computed(() => session.user?.permissions.includes('space:view') ?? false)
const canViewClassroomApplications = computed(() => session.user?.roles.includes('STUDENT') ?? false)
const canViewRepairs = computed(() => session.user?.roles.includes('STUDENT') ?? false)
const canApproveClassrooms = computed(() => {
  const user = session.user
  return Boolean(user && user.permissions.includes('classroom:approve')
    && user.roles.some((role) => role === 'ROOM_ADMIN' || role === 'DEPARTMENT_ADMIN'))
})
const canViewMaintenance = computed(() => {
  const user = session.user
  return Boolean(user && (user.roles.includes('PROPERTY_STAFF')
    || (user.roles.includes('PROPERTY_MANAGER') && user.permissions.includes('maintenance:assign'))))
})
const homeLink = computed(() => route.path.startsWith('/admin')
  ? adminLinks.value[0]?.path ?? '/'
  : canViewReservations.value ? '/student/home' : canViewSpaces.value ? '/student/rooms' : '/')

async function logout() {
  session.logout()
  await router.replace('/login')
}

function handleSessionExpired() {
  if (!session.user) return
  session.logout()
  void router.replace({ path: '/login', query: { redirect: route.fullPath } })
}

onMounted(() => globalThis.window.addEventListener('campus:session-expired', handleSessionExpired))
onUnmounted(() => globalThis.window.removeEventListener('campus:session-expired', handleSessionExpired))
</script>

<template>
  <div
    v-if="route.meta.requiresAuth"
    class="app-frame"
  >
    <header class="app-header">
      <RouterLink
        class="identity"
        :to="homeLink"
      >
        <span class="identity-mark">校</span><span>校园资源服务平台</span>
      </RouterLink>
      <nav aria-label="主导航">
        <template v-if="route.path.startsWith('/admin')">
          <RouterLink
            v-for="link in adminLinks"
            :key="link.path"
            :to="link.path"
          >
            {{ link.label }}
          </RouterLink>
        </template>
        <template v-else>
          <RouterLink
            v-if="canViewReservations"
            to="/student/home"
          >
            首页
          </RouterLink><RouterLink
            v-if="canViewReservations"
            to="/student/reservations"
          >
            我的预约
          </RouterLink><RouterLink
            v-if="canViewSpaces"
            to="/student/rooms"
          >
            预约座位
          </RouterLink><RouterLink
            v-if="canViewReservations"
            to="/student/history"
          >
            预约历史
          </RouterLink>
          <RouterLink
            v-if="canViewClassroomApplications"
            to="/student/classroom-applications"
          >
            教室申请
          </RouterLink>
          <RouterLink
            v-if="canViewRepairs"
            to="/student/repairs"
          >
            物业报修
          </RouterLink>
          <RouterLink
            v-if="canApproveClassrooms"
            to="/approvals"
          >
            教室审批
          </RouterLink>
          <RouterLink
            v-if="canViewMaintenance"
            to="/maintenance"
          >
            维修工单
          </RouterLink>
          <RouterLink
            v-if="adminLinks.length"
            :to="adminLinks[0].path"
          >
            管理
          </RouterLink>
        </template>
      </nav>
      <div class="account">
        <span>{{ session.user?.displayName }}</span><button
          class="text-button"
          @click="logout"
        >
          退出
        </button>
      </div>
    </header>
    <RouterView />
    <footer class="app-footer">
      校园资源服务平台 · 学生服务
    </footer>
  </div>
  <RouterView v-else />
</template>
