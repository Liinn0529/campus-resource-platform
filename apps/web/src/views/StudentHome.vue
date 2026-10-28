<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, errorMessage, type Booking, type Envelope } from '../lib/api'
import { useSessionStore } from '../stores/session'

const session = useSessionStore()
const route = useRoute()
const router = useRouter()
const canCreateReservation = session.user?.permissions.includes('reservation:create') ?? false
const view = ref<'current' | 'history'>(route.path === '/student/history' ? 'history' : 'current')
const bookings = ref<Booking[]>([])
interface Violation { id: number; reservationId: number; reason: 'NO_SHOW'; occurredAt: string }
const violations = ref<Violation[]>([])
const bookingOffset = ref(0)
const violationOffset = ref(0)
const violationError = ref('')
const loading = ref(false)
const error = ref('')
const cancelling = ref<number | null>(null)
let loadSequence = 0
const currentCount = computed(() => bookings.value.filter((booking) => booking.status === 'CREATED' || booking.status === 'CHECKED_IN').length)

async function load(nextBookingOffset = bookingOffset.value, nextViolationOffset = violationOffset.value) {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const bookingRequest = api.get<Envelope<Booking[]>>('/reservations', { params: { view: view.value, limit: 50, offset: nextBookingOffset } })
    const violationRequest = view.value === 'history'
      ? api.get<Envelope<Violation[]>>('/violations', { params: { limit: 50, offset: nextViolationOffset } })
      : Promise.resolve(null)
    const [bookingResult, violationResult] = await Promise.allSettled([bookingRequest, violationRequest])
    if (sequence !== loadSequence) return
    if (bookingResult.status === 'rejected') throw bookingResult.reason
    if (bookingResult.value.data.data.length === 0 && nextBookingOffset > 0) {
      await load(nextBookingOffset - 50, nextViolationOffset)
      return
    }
    bookings.value = bookingResult.value.data.data
    bookingOffset.value = nextBookingOffset
    if (violationResult?.status === 'fulfilled' && violationResult.value) {
      if (violationResult.value.data.data.length === 0 && nextViolationOffset > 0) {
        await load(nextBookingOffset, nextViolationOffset - 50)
        return
      }
      violations.value = violationResult.value.data.data
      violationOffset.value = nextViolationOffset
      violationError.value = ''
    } else if (violationResult?.status === 'rejected') {
      violations.value = []
      violationError.value = errorMessage(violationResult.reason)
    } else {
      violations.value = []
      violationError.value = ''
    }
  } catch (reason) { if (sequence === loadSequence) error.value = errorMessage(reason) }
  finally { if (sequence === loadSequence) loading.value = false }
}

async function cancel(booking: Booking) {
  if (cancelling.value !== null) return
  cancelling.value = booking.id
  error.value = ''
  try { await api.post(`/reservations/${booking.id}/cancel`); await load() }
  catch (reason) { error.value = errorMessage(reason) }
  finally { cancelling.value = null }
}

onMounted(load)
watch(() => route.path, (path) => {
  const nextView = path === '/student/history' ? 'history' : 'current'
  if (view.value !== nextView) {
    view.value = nextView
    bookingOffset.value = 0
    violationOffset.value = 0
    void load(0, 0)
  }
})
</script>

<template>
  <main class="student-page">
    <section class="welcome-panel">
      <p class="eyebrow">
        学生服务
      </p>
      <h1>你好，{{ session.user?.displayName }}</h1>
      <p>查看你的空间预约。预约状态和可操作范围以服务端实时校验为准。</p>
      <div class="welcome-stat">
        <strong>{{ view === 'current' ? currentCount : bookings.length }}</strong><span>{{ view === 'current' ? '当前列表有效预约' : '本页历史记录' }}</span>
      </div>
    </section>
    <section class="booking-section">
      <div class="section-header">
        <div>
          <h2>我的预约</h2><p class="updated">
            每页最多 50 条
          </p>
        </div>
        <div
          class="view-switch"
          aria-label="预约类别"
        >
          <button
            :class="{ selected: view === 'current' }"
            @click="router.push('/student/reservations')"
          >
            当前预约
          </button>
          <button
            :class="{ selected: view === 'history' }"
            @click="router.push('/student/history')"
          >
            历史记录
          </button>
        </div>
      </div>
      <p
        v-if="error"
        class="inline-error"
        role="alert"
      >
        {{ error }}
      </p>
      <div
        v-if="loading"
        class="empty-state"
      >
        正在读取预约…
      </div>
      <div
        v-else-if="bookings.length === 0"
        class="empty-state"
      >
        {{ view === 'current' ? '暂无当前预约' : '暂无历史记录' }}
      </div>
      <div
        v-else
        class="booking-list"
      >
        <article
          v-for="booking in bookings"
          :key="booking.id"
          class="booking-card"
        >
          <div class="booking-top">
            <span class="booking-id">预约 #{{ booking.id }}</span><span
              class="status-pill"
              :class="booking.status.toLowerCase()"
            >{{ booking.status }}</span>
          </div>
          <div class="booking-details">
            <div><span>房间</span><strong>#{{ booking.roomId }}</strong></div><div><span>座位</span><strong>#{{ booking.seatId }}</strong></div>
          </div>
          <p class="booking-time">
            {{ new Date(booking.startAt).toLocaleString('zh-CN', { hour12: false }) }} — {{ new Date(booking.endAt).toLocaleTimeString('zh-CN', { hour12: false }) }}
          </p>
          <RouterLink
            class="detail-link"
            :to="`/student/reservations/${booking.id}`"
          >
            查看详情 →
          </RouterLink>
          <button
            v-if="view === 'current' && booking.status === 'CREATED' && canCreateReservation"
            class="text-button"
            :disabled="cancelling === booking.id"
            @click="cancel(booking)"
          >
            {{ cancelling === booking.id ? '正在取消…' : '取消预约' }}
          </button>
          <RouterLink
            v-if="view === 'history' && canCreateReservation"
            class="detail-link"
            :to="{ path: `/student/rooms/${booking.roomId}`, query: { rebook: String(booking.id), seatId: String(booking.seatId) } }"
          >
            再次预约此座位
          </RouterLink>
        </article>
      </div>
      <nav
        v-if="!loading && bookings.length > 0"
        class="history-pager"
        aria-label="预约分页"
      >
        <button
          class="outline-button"
          :disabled="bookingOffset === 0"
          @click="load(Math.max(0, bookingOffset - 50), violationOffset)"
        >
          上一页
        </button>
        <span>第 {{ bookingOffset / 50 + 1 }} 页</span>
        <button
          class="outline-button"
          :disabled="bookings.length < 50"
          @click="load(bookingOffset + 50, violationOffset)"
        >
          下一页
        </button>
      </nav>
      <section
        v-if="view === 'history'"
        class="violation-section"
      >
        <div class="section-header">
          <div>
            <h2>本人违约记录</h2><p class="updated">
              仅显示后端返回的记录，不附加积分或处罚推断
            </p>
          </div>
        </div>
        <p
          v-if="violationError"
          class="inline-error"
          role="alert"
        >
          违约记录读取失败：{{ violationError }}
        </p>
        <p
          v-else-if="violations.length === 0"
          class="empty-state"
        >
          暂无违约记录。
        </p>
        <div
          v-else
          class="violation-list"
        >
          <article
            v-for="violation in violations"
            :key="violation.id"
          >
            <span>记录 #{{ violation.id }}</span><strong>预约 #{{ violation.reservationId }} · {{ violation.reason }}</strong><time>{{ new Date(violation.occurredAt).toLocaleString('zh-CN', { hour12: false }) }}</time>
          </article>
        </div>
        <nav
          v-if="!violationError && violations.length > 0"
          class="history-pager"
          aria-label="违约记录分页"
        >
          <button
            class="outline-button"
            :disabled="violationOffset === 0 || loading"
            @click="load(bookingOffset, Math.max(0, violationOffset - 50))"
          >
            上一页
          </button>
          <span>第 {{ violationOffset / 50 + 1 }} 页</span>
          <button
            class="outline-button"
            :disabled="violations.length < 50 || loading"
            @click="load(bookingOffset, violationOffset + 50)"
          >
            下一页
          </button>
        </nav>
      </section>
    </section>
  </main>
</template>
