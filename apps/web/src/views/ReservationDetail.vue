<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { api, errorMessage, type Booking, type Envelope } from '../lib/api'
import { useSessionStore } from '../stores/session'

const route = useRoute()
const session = useSessionStore()
const canCreateReservation = session.user?.permissions.includes('reservation:create') ?? false
const id = computed(() => Number(route.params.id))
const booking = ref<Booking | null>(null)
const code = ref('')
const loading = ref(false)
const checkingIn = ref(false)
const cancelling = ref(false)
const error = ref('')
const notice = ref('')
const canCheckIn = computed(() => booking.value?.status === 'CHECKED_IN' || (booking.value?.status === 'CREATED' && canCreateReservation))

function explain(errorValue: unknown) {
  if (axios.isAxiosError(errorValue)) {
    const codeValue = errorValue.response?.data?.code
    if (codeValue === 'CHECKIN_CODE_INVALID') return '签到码不正确，请确认后重试。'
    if (codeValue === 'CHECKIN_NOT_OPEN') return '当前还未到签到时间，请在预约开始前 15 分钟内签到。'
    if (codeValue === 'CHECKIN_CLOSED') return '签到时间窗口已结束。'
    if (codeValue === 'CHECKIN_NOT_ALLOWED') return '当前预约状态不能签到。'
  }
  return errorMessage(errorValue)
}

async function load(requestedId = id.value) {
  if (!Number.isSafeInteger(requestedId) || requestedId < 1) { error.value = '预约编号无效。'; booking.value = null; return }
  loading.value = true
  error.value = ''
  try {
    const response = await api.get<Envelope<Booking>>(`/reservations/${requestedId}`)
    if (requestedId === id.value) booking.value = response.data.data
  } catch (reason) { if (requestedId === id.value) error.value = errorMessage(reason) }
  finally { if (requestedId === id.value) loading.value = false }
}

async function checkIn() {
  if (checkingIn.value || !/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$/.test(code.value)) return
  checkingIn.value = true
  error.value = ''
  notice.value = ''
  try {
    const response = await api.post<Envelope<Booking>>(`/reservations/${id.value}/checkin`, { code: code.value })
    booking.value = response.data.data
    code.value = ''
    notice.value = '签到成功。'
  } catch (reason) { error.value = explain(reason) }
  finally { code.value = ''; checkingIn.value = false }
}

async function cancel() {
  if (!booking.value || cancelling.value) return
  cancelling.value = true
  error.value = ''
  notice.value = ''
  try {
    const response = await api.post<Envelope<Booking>>(`/reservations/${id.value}/cancel`)
    booking.value = response.data.data
    notice.value = '预约已取消。'
  } catch (reason) { error.value = errorMessage(reason) }
  finally { cancelling.value = false }
}

watch(id, (nextId) => {
  booking.value = null
  code.value = ''
  notice.value = ''
  void load(nextId)
}, { immediate: true })
</script>

<template>
  <main class="student-page catalog-page">
    <RouterLink
      class="back-link"
      to="/student/reservations"
    >
      ← 返回我的预约
    </RouterLink>
    <p class="eyebrow">
      预约记录
    </p>
    <h1>预约详情</h1>
    <div
      v-if="loading"
      class="empty-state"
    >
      正在读取预约详情…
    </div>
    <p
      v-else-if="error && !booking"
      class="inline-error"
      role="alert"
    >
      {{ error }}
    </p>
    <section
      v-else-if="booking"
      class="reservation-detail-card"
    >
      <div class="detail-heading">
        <div><span class="booking-id">预约 #{{ booking.id }}</span><h2>{{ booking.status }}</h2></div><span
          class="status-pill"
          :class="booking.status.toLowerCase()"
        >{{ booking.status }}</span>
      </div>
      <dl class="reservation-facts">
        <div><dt>房间编号</dt><dd>#{{ booking.roomId }}</dd></div>
        <div><dt>座位编号</dt><dd>#{{ booking.seatId }}</dd></div>
        <div><dt>开始时间</dt><dd>{{ new Date(booking.startAt).toLocaleString('zh-CN', { hour12: false }) }}</dd></div>
        <div><dt>结束时间</dt><dd>{{ new Date(booking.endAt).toLocaleString('zh-CN', { hour12: false }) }}</dd></div>
        <div><dt>创建时间</dt><dd>{{ new Date(booking.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</dd></div>
        <div v-if="booking.rebookOf !== null">
          <dt>原预约</dt><dd>#{{ booking.rebookOf }}</dd>
        </div>
      </dl>
      <div
        v-if="booking.status === 'CREATED' && canCreateReservation"
        class="detail-actions"
      >
        <button
          class="outline-button cancel-button"
          :disabled="cancelling"
          @click="cancel"
        >
          {{ cancelling ? '正在取消…' : '取消预约' }}
        </button><span>能否取消由当前预约状态与服务端时间校验决定。</span>
      </div>
      <div
        v-if="canCheckIn"
        class="checkin-panel"
      >
        <h3>{{ booking.status === 'CHECKED_IN' ? '已完成签到' : '到场签到' }}</h3>
        <p>请输入房间管理人员提供的 8 位签到码。本页面提交后清空输入，不写入浏览器存储；签到时间与预约状态由服务端校验。</p>
        <form
          v-if="booking.status === 'CREATED'"
          class="checkin-form"
          @submit.prevent="checkIn"
        >
          <label for="checkin-code">签到码</label>
          <input
            id="checkin-code"
            v-model="code"
            autocomplete="off"
            inputmode="text"
            minlength="8"
            maxlength="8"
            pattern="[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}"
            required
          >
          <button
            class="primary-button"
            :disabled="checkingIn || !/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$/.test(code)"
          >
            {{ checkingIn ? '验证中…' : '签到' }}
          </button>
        </form>
      </div>
      <p
        v-if="notice"
        class="success-note"
        role="status"
      >
        {{ notice }}
      </p>
      <p
        v-if="error"
        class="inline-error"
        role="alert"
      >
        {{ error }}
      </p>
    </section>
  </main>
</template>
