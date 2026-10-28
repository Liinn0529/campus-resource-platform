<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorMessage } from '../lib/api'
import { useSessionStore } from '../stores/session'

const username = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)
const session = useSessionStore()
const router = useRouter()
const route = useRoute()

async function submit() {
  if (submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    await session.login({ username: username.value.trim(), password: password.value })
    await router.replace(typeof route.query.redirect === 'string' ? route.query.redirect : '/student/home')
  } catch (reason) { error.value = errorMessage(reason) }
  finally { submitting.value = false }
}
</script>

<template>
  <main class="login-page">
    <form
      class="login-card"
      @submit.prevent="submit"
    >
      <span class="identity-mark">校</span>
      <p class="eyebrow">
        CAMPUS RESOURCE PLATFORM
      </p>
      <h1>欢迎回来</h1>
      <p class="description">
        使用校园平台账号登录，管理你的学习空间预约。
      </p>
      <label for="username">用户名</label>
      <input
        id="username"
        v-model="username"
        autocomplete="username"
        required
        maxlength="64"
      >
      <label for="password">密码</label>
      <input
        id="password"
        v-model="password"
        autocomplete="current-password"
        type="password"
        required
      >
      <p
        v-if="error"
        class="form-error"
        role="alert"
      >
        {{ error }}
      </p>
      <button
        class="primary-button"
        type="submit"
        :disabled="submitting"
      >
        {{ submitting ? '正在登录…' : '登录' }}
      </button>
      <p class="login-note">
        登录凭据仅发送至校园平台认证接口。
      </p>
    </form>
  </main>
</template>
