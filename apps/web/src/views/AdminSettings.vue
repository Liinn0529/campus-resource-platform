<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage, type Envelope } from '../lib/api'

const maxHours = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')

async function load() {
  loading.value = true; error.value = ''
  try {
    const response = await api.get<Envelope<{ maxReservationHours: number }>>('/settings')
    maxHours.value = String(response.data.data.maxReservationHours)
  } catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}

async function save() {
  const hours = Number(maxHours.value)
  if (!Number.isSafeInteger(hours) || hours < 1 || saving.value) return
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const response = await api.put<Envelope<{ maxReservationHours: number }>>('/settings', { maxReservationHours: hours })
    maxHours.value = String(response.data.data.maxReservationHours)
    notice.value = '预约时长参数已保存。新预约和再次预约会读取该值。'
  } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <main class="student-page catalog-page admin-page">
    <p class="eyebrow">
      管理 · 平台参数
    </p><h1>系统设置</h1>
    <p class="description">
      读取并维护公共平台参数。更新只影响后续提交的新预约与再次预约。
    </p>
    <section class="admin-panel settings-panel">
      <div class="admin-list-heading">
        <h2>预约规则</h2><button
          class="outline-button"
          :disabled="loading"
          @click="load"
        >
          重新读取
        </button>
      </div>
      <form
        class="admin-form"
        @submit.prevent="save"
      >
        <label>单次预约最长时长（小时）<input
          v-model="maxHours"
          type="number"
          min="1"
          step="1"
          required
        ></label>
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
        <button
          class="primary-button"
          :disabled="loading || saving || !maxHours"
        >
          {{ saving ? '保存中…' : '保存参数' }}
        </button>
      </form>
    </section>
  </main>
</template>
