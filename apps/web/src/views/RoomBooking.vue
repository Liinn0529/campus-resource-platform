<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, errorMessage, type Envelope } from '../lib/api'
import { useSessionStore } from '../stores/session'

interface Room { id: number; buildingId: number; code: string; name: string; type: 'STUDY_ROOM' | 'CLASSROOM'; enabled: boolean }
interface Seat { id: number; code: string; hasPowerOutlet: boolean; available: boolean }
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const canCreateReservation = session.user?.permissions.includes('reservation:create') ?? false
const roomId = ref(Number(route.params.id))
const rebookId = ref(Number(route.query.rebook))
const rebookSeatId = ref(Number(route.query.seatId))
const isRebooking = computed(() => Number.isSafeInteger(rebookId.value) && rebookId.value > 0)
const room = ref<Room | null>(null)
const seats = ref<Seat[]>([])
const selectedSeat = ref<number | null>(null)
const startAt = ref('')
const endAt = ref('')
const outletFilter = ref<'any' | 'yes' | 'no'>('any')
const loading = ref(false)
const booking = ref(false)
const error = ref('')
const searched = ref(false)
const availableCount = computed(() => seats.value.filter((seat) => seat.available).length)

function localInput(value: Date) {
  const offset = value.getTimezoneOffset() * 60000
  return new Date(value.getTime() - offset).toISOString().slice(0, 16)
}
const initial = new Date()
initial.setMinutes(0, 0, 0)
initial.setHours(initial.getHours() + 1)
startAt.value = localInput(initial)
const nextHour = new Date(initial)
nextHour.setHours(nextHour.getHours() + 1)
endAt.value = localInput(nextHour)

function getSearchInstants(): { startAt: string; endAt: string } | null {
  if (!startAt.value || !endAt.value) return null
  const start = new Date(startAt.value)
  const end = new Date(endAt.value)
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || start >= end) return null
  return { startAt: start.toISOString(), endAt: end.toISOString() }
}

async function loadRoom() {
  if (!Number.isSafeInteger(roomId.value) || roomId.value < 1) { error.value = '空间编号无效。'; return }
  try {
    let offset = 0
    while (true) {
      const response = await api.get<Envelope<Room[]>>('/reservation/rooms', { params: { limit: 100, offset } })
      room.value = response.data.data.find((item) => item.id === roomId.value) ?? null
      if (room.value || response.data.data.length < 100) break
      offset += 100
    }
    if (!room.value) error.value = '空间不存在或当前不可用。'
  } catch (reason) { error.value = errorMessage(reason) }
}

async function searchAvailability() {
  if (!room.value || loading.value) return
  loading.value = true
  error.value = ''
  searched.value = true
  selectedSeat.value = null
  try {
    const interval = getSearchInstants()
    if (!interval) {
      seats.value = []
      error.value = '请输入有效的开始和结束时间，并确保开始时间早于结束时间。'
      return
    }
    const params: Record<string, string | number | boolean> = { ...interval, limit: 100, offset: 0 }
    if (outletFilter.value !== 'any') params.hasPowerOutlet = outletFilter.value === 'yes'
    const found: Seat[] = []
    while (true) {
      const response = await api.get<Envelope<{ roomId: number; startAt: string; endAt: string; seats: Seat[] }>>(`/reservation/rooms/${roomId.value}/availability`, { params })
      found.push(...response.data.data.seats)
      if (response.data.data.seats.length < 100) break
      params.offset = Number(params.offset) + 100
    }
    seats.value = found
    if (isRebooking.value) {
      const originalSeat = seats.value.find((seat) => seat.id === rebookSeatId.value && seat.available)
      selectedSeat.value = originalSeat?.id ?? null
    }
  } catch (reason) { seats.value = []; error.value = errorMessage(reason) }
  finally { loading.value = false }
}

async function createBooking() {
  if ((selectedSeat.value === null && !isRebooking.value) || !room.value || booking.value) return
  const interval = getSearchInstants()
  if (!interval) {
    error.value = '请输入有效的开始和结束时间，并确保开始时间早于结束时间。'
    return
  }
  booking.value = true
  error.value = ''
  try {
    if (isRebooking.value) {
      await api.post(`/reservations/${rebookId.value}/rebook`, interval)
    } else {
      await api.post('/reservations', { roomId: roomId.value, seatId: selectedSeat.value, ...interval })
    }
    await router.push('/student/reservations')
  } catch (reason) {
    const bookingError = errorMessage(reason)
    await searchAvailability()
    error.value = bookingError
  }
  finally { booking.value = false }
}

onMounted(loadRoom)
watch(() => [route.params.id, route.query.rebook, route.query.seatId], () => {
  roomId.value = Number(route.params.id)
  rebookId.value = Number(route.query.rebook)
  rebookSeatId.value = Number(route.query.seatId)
  room.value = null
  seats.value = []
  selectedSeat.value = null
  searched.value = false
  error.value = ''
  void loadRoom()
})
</script>

<template>
  <main class="student-page catalog-page">
    <RouterLink
      class="back-link"
      to="/student/rooms"
    >
      ← 返回空间目录
    </RouterLink>
    <p class="eyebrow">
      预约座位
    </p>
    <h1>{{ room?.name ?? '空间详情' }}</h1>
    <p
      v-if="room"
      class="description"
    >
      {{ room.code }} · 楼宇 #{{ room.buildingId }} · {{ room.type === 'STUDY_ROOM' ? '自习室' : '教室' }}
    </p>
    <section
      v-if="room"
      class="availability-panel"
    >
      <h2>查询可用座位</h2>
      <div class="search-controls">
        <label>开始时间<input
          v-model="startAt"
          type="datetime-local"
        ></label>
        <label>结束时间<input
          v-model="endAt"
          type="datetime-local"
        ></label>
        <label>插座筛选<select v-model="outletFilter"><option value="any">不限</option><option value="yes">需要插座</option><option value="no">不需要插座</option></select></label>
        <button
          class="primary-button"
          :disabled="loading"
          @click="searchAvailability"
        >
          {{ loading ? '查询中…' : '查询座位' }}
        </button>
      </div>
      <p
        v-if="error"
        class="inline-error"
        role="alert"
      >
        {{ error }}
      </p>
      <div
        v-if="searched && !loading && !error"
        class="seat-results"
      >
        <div class="seat-result-heading">
          <h3>查询结果</h3><span>{{ availableCount }} 个可用</span>
        </div>
        <div
          v-if="seats.length === 0"
          class="empty-state"
        >
          该时段没有符合筛选条件的座位。
        </div>
        <div
          v-else
          class="seat-grid"
        >
          <button
            v-for="seat in seats"
            :key="seat.id"
            class="seat-tile"
            :class="{ available: seat.available, chosen: selectedSeat === seat.id }"
            :disabled="!seat.available"
            @click="selectedSeat = seat.id"
          >
            <strong>{{ seat.code }}</strong><span>{{ seat.hasPowerOutlet ? '有插座' : '无插座' }}</span><small>{{ seat.available ? '可预约' : '时段已占用' }}</small>
          </button>
        </div>
        <div
          v-if="(selectedSeat !== null || isRebooking) && canCreateReservation"
          class="booking-confirm"
        >
          <span v-if="isRebooking">再次预约将沿用原座位 #{{ rebookSeatId }}；服务端会重新验证资源与时间。</span>
          <span v-else>已选择座位 {{ seats.find((seat) => seat.id === selectedSeat)?.code }}。提交时服务端会重新验证资源与时间。</span>
          <button
            class="primary-button"
            :disabled="booking || (isRebooking && selectedSeat === null)"
            @click="createBooking"
          >
            {{ booking ? '提交中…' : isRebooking ? '确认再次预约' : '确认预约' }}
          </button>
        </div>
        <p
          v-if="!canCreateReservation"
          class="form-hint"
        >
          当前账号只有空间查看权限，不能提交预约。
        </p>
      </div>
      <p class="booking-footnote">
        可用性查询是当前状态快照，不会锁定座位；预约以提交时服务端校验结果为准。
      </p>
    </section>
  </main>
</template>
