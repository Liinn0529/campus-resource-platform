<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage, type Envelope } from '../lib/api'

interface User { id: number; username: string; displayName: string; enabled: boolean; roles: string[]; roomIds: number[] }
interface Role { id: number; code: string; name: string; permissionCodes: string[] }
const rows = ref<User[]>([])
const roles = ref<Role[]>([])
const offset = ref(0)
const username = ref('')
const displayName = ref('')
const password = ref('')
const roleCodes = ref<string[]>([])
const resetTarget = ref<User | null>(null)
const resetPassword = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')

async function load() {
  loading.value = true; error.value = ''
  try {
    const [usersResponse, rolesResponse] = await Promise.all([
      api.get<Envelope<User[]>>('/users', { params: { limit: 50, offset: offset.value } }),
      api.get<Envelope<Role[]>>('/roles'),
    ])
    rows.value = usersResponse.data.data
    roles.value = rolesResponse.data.data
  } catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}

async function createUser() {
  if (saving.value) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    await api.post('/users', { username: username.value.trim(), displayName: displayName.value.trim(), password: password.value, roleCodes: roleCodes.value })
    username.value = ''; displayName.value = ''; password.value = ''; roleCodes.value = []
    notice.value = '用户已创建。密码不会再次显示，请按组织流程安全交付。'
    await load()
  } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}

async function toggle(user: User) {
  if (user.enabled && !globalThis.window.confirm(`确定停用用户 ${user.username}？`)) return
  error.value = ''; notice.value = ''
  try {
    await api.patch(`/users/${user.id}`, { enabled: !user.enabled })
    notice.value = `用户 ${user.username} 已${user.enabled ? '停用' : '启用'}。`
    await load()
  } catch (reason) { error.value = errorMessage(reason) }
}

async function applyPassword() {
  if (!resetTarget.value || !resetPassword.value || saving.value) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    await api.put(`/users/${resetTarget.value.id}/password`, { password: resetPassword.value })
    notice.value = `用户 ${resetTarget.value.username} 的密码已重置。`
    resetTarget.value = null; resetPassword.value = ''
  } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <main class="student-page catalog-page admin-page">
    <p class="eyebrow">
      管理 · 身份与权限
    </p><h1>用户管理</h1>
    <p class="description">
      维护用户启用状态、重置密码和创建账号。平台不提供硬删除；本页面不会展示或缓存密码哈希。
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
    <div class="admin-columns users-columns">
      <section class="admin-panel">
        <h2>创建用户</h2>
        <form
          class="admin-form"
          @submit.prevent="createUser"
        >
          <label>用户名<input
            v-model="username"
            required
            minlength="3"
            maxlength="64"
            pattern="[A-Za-z0-9][A-Za-z0-9._-]{2,63}"
          ></label>
          <label>显示名称<input
            v-model="displayName"
            required
            maxlength="100"
          ></label>
          <label>初始密码<input
            v-model="password"
            type="password"
            autocomplete="new-password"
            required
            minlength="12"
            maxlength="72"
          ></label>
          <fieldset class="role-checks">
            <legend>角色（可选）</legend><label
              v-for="role in roles"
              :key="role.code"
            ><input
              v-model="roleCodes"
              type="checkbox"
              :value="role.code"
            >{{ role.name }}（{{ role.code }}）</label>
          </fieldset>
          <button
            class="primary-button"
            :disabled="saving"
          >
            {{ saving ? '创建中…' : '创建用户' }}
          </button>
        </form>
      </section>
      <section class="admin-panel">
        <div class="admin-list-heading">
          <h2>用户列表</h2><button
            class="outline-button"
            :disabled="loading"
            @click="load"
          >
            刷新
          </button>
        </div>
        <p
          v-if="loading"
          class="empty-state"
        >
          正在读取用户…
        </p><p
          v-else-if="rows.length === 0"
          class="empty-state"
        >
          当前页没有用户。
        </p>
        <div
          v-else
          class="admin-table-wrap"
        >
          <table class="admin-table">
            <thead><tr><th>用户</th><th>角色</th><th>状态</th><th>操作</th></tr></thead><tbody>
              <tr
                v-for="user in rows"
                :key="user.id"
              >
                <td>#{{ user.id }}<strong>{{ user.displayName }}</strong><small>{{ user.username }}</small></td><td>{{ user.roles.length ? user.roles.join('、') : '未分配' }}<small v-if="user.roomIds.length">房间范围：{{ user.roomIds.join(', ') }}</small></td><td><span :class="user.enabled ? 'enabled-label' : 'disabled-label'">{{ user.enabled ? '启用' : '停用' }}</span></td><td>
                  <div class="table-actions">
                    <button
                      class="text-button"
                      @click="toggle(user)"
                    >
                      {{ user.enabled ? '停用' : '启用' }}
                    </button><button
                      class="text-button"
                      @click="resetTarget = user; resetPassword = ''"
                    >
                      重置密码
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination">
          <button
            class="outline-button"
            :disabled="offset === 0 || loading"
            @click="offset = Math.max(0, offset - 50); load()"
          >
            上一页
          </button><span>每页 50 条</span><button
            class="outline-button"
            :disabled="rows.length < 50 || loading"
            @click="offset += 50; load()"
          >
            下一页
          </button>
        </div>
      </section>
    </div>
    <section
      v-if="resetTarget"
      class="modal-backdrop"
      @click.self="resetTarget = null"
    >
      <form
        class="admin-modal"
        @submit.prevent="applyPassword"
      >
        <h2>重置用户密码</h2><p>{{ resetTarget.displayName }}（{{ resetTarget.username }}）</p><label>新密码<input
          v-model="resetPassword"
          type="password"
          autocomplete="new-password"
          required
          minlength="12"
          maxlength="72"
        ></label><p
          v-if="error"
          class="inline-error"
          role="alert"
        >
          {{ error }}
        </p><div class="form-actions">
          <button
            class="primary-button"
            :disabled="saving"
          >
            {{ saving ? '提交中…' : '确认重置' }}
          </button><button
            type="button"
            class="outline-button"
            @click="resetTarget = null"
          >
            取消
          </button>
        </div>
      </form>
    </section>
  </main>
</template>
