<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, errorMessage, fetchAll, type Envelope } from '../lib/api'

interface Role { id: number; code: string; name: string; permissionCodes: string[] }
interface Permission { id: number; code: string; name: string }
interface User { id: number; username: string; displayName: string; enabled: boolean; roles: string[]; roomIds: number[] }
interface Room { id: number; buildingId: number; code: string; name: string; enabled: boolean }
const roles = ref<Role[]>([])
const permissions = ref<Permission[]>([])
const users = ref<User[]>([])
const rooms = ref<Room[]>([])
const selectedRole = ref('')
const selectedUserId = ref('')
const permissionCodes = ref<string[]>([])
const assignedRoles = ref<string[]>([])
const roomIds = ref<number[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const selectedUser = computed(() => users.value.find((user) => String(user.id) === selectedUserId.value))
const canEditRole = computed(() => selectedRole.value !== 'SYSTEM_ADMIN')

async function load() {
  loading.value = true; error.value = ''
  try {
    const [rolesResponse, permissionsResponse, usersResponse, roomsResponse] = await Promise.all([
      api.get<Envelope<Role[]>>('/roles'), api.get<Envelope<Permission[]>>('/permissions'),
      fetchAll<User>('/users'), fetchAll<Room>('/managed-rooms'),
    ])
    roles.value = rolesResponse.data.data; permissions.value = permissionsResponse.data.data
    users.value = usersResponse; rooms.value = roomsResponse
    if (!selectedRole.value) selectedRole.value = roles.value[0]?.code ?? ''
    if (!selectedUserId.value) selectedUserId.value = users.value[0] ? String(users.value[0].id) : ''
    syncRole(); syncUser()
  } catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}

function syncRole() { permissionCodes.value = roles.value.find((role) => role.code === selectedRole.value)?.permissionCodes ?? [] }
function syncUser() {
  const user = selectedUser.value
  assignedRoles.value = user ? [...user.roles] : []
  roomIds.value = user ? [...user.roomIds] : []
}

async function savePermissions() {
  if (!canEditRole.value || saving.value) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    await api.put(`/roles/${selectedRole.value}/permissions`, { permissionCodes: permissionCodes.value })
    notice.value = `${selectedRole.value} 的权限已更新。`; await load()
  } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}

async function saveAssignments() {
  if (!selectedUser.value || saving.value) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    await api.put(`/users/${selectedUser.value.id}/roles`, { roleCodes: assignedRoles.value })
    const canHaveScope = assignedRoles.value.includes('ROOM_ADMIN') || assignedRoles.value.includes('DEPARTMENT_ADMIN')
    if (canHaveScope) await api.put(`/users/${selectedUser.value.id}/room-scopes`, { roomIds: roomIds.value })
    else roomIds.value = []
    notice.value = `用户 ${selectedUser.value.username} 的角色${canHaveScope ? '与房间范围' : ''}已更新。`
    await load()
  } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <main class="student-page catalog-page admin-page">
    <p class="eyebrow">
      管理 · RBAC
    </p><h1>角色与权限</h1>
    <p class="description">
      按既有角色、权限和显式房间范围分配访问。SYSTEM_ADMIN 的权限不可通过此页面修改；服务端仍是最终授权依据。
    </p>
    <p
      v-if="error"
      class="inline-error"
      role="alert"
    >
      {{ error }}
    </p><p
      v-if="notice"
      class="success-note"
      role="status"
    >
      {{ notice }}
    </p>
    <div class="admin-columns roles-columns">
      <section class="admin-panel">
        <div class="admin-list-heading">
          <h2>角色权限</h2><select
            v-model="selectedRole"
            aria-label="选择角色"
            @change="syncRole"
          >
            <option
              v-for="role in roles"
              :key="role.code"
              :value="role.code"
            >
              {{ role.name }}（{{ role.code }}）
            </option>
          </select>
        </div>
        <fieldset
          class="permission-checks"
          :disabled="!canEditRole"
        >
          <legend>权限列表</legend><label
            v-for="permission in permissions"
            :key="permission.code"
          ><input
            v-model="permissionCodes"
            type="checkbox"
            :value="permission.code"
          > <span>{{ permission.name }}<small>{{ permission.code }}</small></span></label>
        </fieldset>
        <p
          v-if="!canEditRole"
          class="form-hint"
        >
          SYSTEM_ADMIN 权限由系统基线保护，不能修改。
        </p><button
          class="primary-button"
          :disabled="saving || !canEditRole"
          @click="savePermissions"
        >
          保存角色权限
        </button>
      </section>
      <section class="admin-panel">
        <h2>用户角色与房间范围</h2>
        <label class="selector-label">选择用户<select
          v-model="selectedUserId"
          @change="syncUser"
        ><option
          v-for="user in users"
          :key="user.id"
          :value="String(user.id)"
        >{{ user.displayName }}（{{ user.username }}）</option></select></label>
        <fieldset class="permission-checks role-assignment">
          <legend>分配角色</legend><label
            v-for="role in roles"
            :key="role.code"
          ><input
            v-model="assignedRoles"
            type="checkbox"
            :value="role.code"
          ><span>{{ role.name }}<small>{{ role.code }}</small></span></label>
        </fieldset>
        <fieldset
          v-if="assignedRoles.includes('ROOM_ADMIN') || assignedRoles.includes('DEPARTMENT_ADMIN')"
          class="permission-checks room-assignment"
        >
          <legend>显式房间范围</legend><label
            v-for="room in rooms"
            :key="room.id"
          ><input
            v-model="roomIds"
            type="checkbox"
            :value="room.id"
          ><span>{{ room.name }}<small>{{ room.code }} · 房间 #{{ room.id }}</small></span></label><p
            v-if="rooms.length === 0"
            class="form-hint"
          >
            当前没有可选启用房间。
          </p>
        </fieldset>
        <p class="form-hint">
          移除房间管理员/部门管理员角色时，后端会按规则清除相应范围。
        </p><button
          class="primary-button"
          :disabled="saving || !selectedUser"
          @click="saveAssignments"
        >
          保存用户授权
        </button>
      </section>
    </div>
  </main>
</template>
