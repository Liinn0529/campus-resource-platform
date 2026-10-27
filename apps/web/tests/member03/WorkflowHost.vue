<script setup lang="ts">
import { ref } from 'vue'
import type { Session } from '../../src/workflows/types'
import { errorMessage } from '../../src/workflows/context'
const props = defineProps<{ session: Session; login: (username: string, password: string) => Promise<void> }>()
const username = ref(''), password = ref(''), error = ref(''), busy = ref(false)
async function submit() {
  if (busy.value || !username.value || !password.value) return
  busy.value = true; error.value = ''
  try { await props.login(username.value, password.value); password.value = '' }
  catch (e) { props.session.logout(); error.value = errorMessage(e) }
  finally { busy.value = false }
}
</script>
<template>
  <div class="host">
    <header>
      <strong>校园资源服务平台 · 成员三验收入口</strong><span v-if="session.user">{{ session.user.displayName || session.user.username }} <el-button
        text
        @click="session.logout()"
      >退出</el-button></span>
    </header>
    <main
      v-if="!session.user"
      class="login"
    >
      <h1>登录业务验收环境</h1><p>使用当前真实服务账号；此入口仅用于隔离验收。</p>
      <el-form
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名">
          <el-input
            v-model="username"
            autocomplete="username"
          />
        </el-form-item><el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            autocomplete="current-password"
            show-password
          />
        </el-form-item><el-alert
          v-if="error"
          :title="error"
          type="error"
          :closable="false"
        /><el-button
          native-type="submit"
          type="primary"
          :loading="busy"
        >
          登录
        </el-button>
      </el-form>
    </main>
    <template v-else>
      <nav>
        <RouterLink to="/student/classroom-applications">
          教室申请
        </RouterLink><RouterLink to="/student/repairs">
          我的报修
        </RouterLink><RouterLink
          v-if="session.user.permissions.includes('classroom:approve')"
          to="/approvals"
        >
          审批待办
        </RouterLink><RouterLink
          v-if="session.user.roles.some(r => ['PROPERTY_STAFF','PROPERTY_MANAGER'].includes(r))"
          to="/maintenance"
        >
          维修工单
        </RouterLink>
      </nav><RouterView />
    </template>
  </div>
</template>
<style scoped>.host{font-family:system-ui,sans-serif;color:#1f2937}header{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #ddd;padding:18px;gap:16px}.login{max-width:380px;margin:60px auto;padding:20px}nav{display:flex;flex-wrap:wrap;gap:24px;padding:20px;border-bottom:1px solid #eee}a{color:#2563eb}.router-link-active{font-weight:700}p{line-height:1.7}</style>
