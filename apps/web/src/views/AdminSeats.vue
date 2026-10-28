<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, errorMessage, fetchAll } from '../lib/api'

interface Room { id: number; buildingId: number; code: string; name: string; type: string; enabled: boolean }
interface Seat { id: number; roomId: number; code: string; hasPowerOutlet: boolean; enabled: boolean }
const rooms = ref<Room[]>([])
const roomId = ref('')
const seats = ref<Seat[]>([])
const code = ref('')
const hasPowerOutlet = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const editingSeatId = ref<number | null>(null)
const editedCode = ref('')
const editedOutlet = ref(false)
const selectedRoom = computed(() => rooms.value.find((room) => String(room.id) === roomId.value))

async function loadRooms() {
  error.value = ''
  try {
    rooms.value = await fetchAll<Room>('/managed-rooms')
    if (selectedRoom.value) await loadSeats()
  } catch (reason) { error.value = errorMessage(reason) }
}

async function loadSeats() {
  if (!roomId.value) { seats.value = []; return }
  loading.value = true
  error.value = ''
  try {
    seats.value = await fetchAll<Seat>(`/rooms/${roomId.value}/seats`)
  } catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}

async function createSeat() {
  if (!roomId.value || saving.value) return
  saving.value = true
  error.value = ''; notice.value = ''
  try {
    await api.post(`/rooms/${roomId.value}/seats`, { code: code.value.trim(), hasPowerOutlet: hasPowerOutlet.value, enabled: true })
    code.value = ''
    notice.value = '座位已创建。'
    await loadSeats()
  } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}

async function toggle(seat: Seat) {
  if (seat.enabled && !globalThis.window.confirm(`确定停用座位 ${seat.code}？这不会释放该座位的预约占用。`)) return
  error.value = ''; notice.value = ''
  try {
    await api.patch(`/seats/${seat.id}`, { enabled: !seat.enabled })
    notice.value = `座位 ${seat.code} 已${seat.enabled ? '停用' : '启用'}。`
    await loadSeats()
  } catch (reason) { error.value = errorMessage(reason) }
}

function edit(seat: Seat) {
  editingSeatId.value = seat.id
  editedCode.value = seat.code
  editedOutlet.value = seat.hasPowerOutlet
}

async function save(seat: Seat) {
  error.value = ''
  notice.value = ''
  try {
    await api.patch(`/seats/${seat.id}`, { code: editedCode.value.trim(), hasPowerOutlet: editedOutlet.value })
    editingSeatId.value = null
    notice.value = `座位 #${seat.id} 信息已更新。`
    await loadSeats()
  } catch (reason) { error.value = errorMessage(reason) }
}

onMounted(loadRooms)
</script>

<template>
  <main class="student-page catalog-page admin-page">
    <p class="eyebrow">
      管理 · 空间主数据
    </p>
    <h1>座位维护</h1>
    <p class="description">
      维护座位编码、插座属性和启用状态；启用状态不代表占用账本状态或释放预约。
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
    <section class="admin-panel">
      <div class="admin-toolbar">
        <label>选择房间<select
          v-model="roomId"
          @change="loadSeats"
        ><option
          disabled
          value=""
        >请选择管理范围内房间</option><option
          v-for="room in rooms"
          :key="room.id"
          :value="String(room.id)"
        >{{ room.name }}（{{ room.code }} · #{{ room.id }}）</option></select></label><button
          class="outline-button"
          :disabled="loading || !roomId"
          @click="loadSeats"
        >
          刷新座位
        </button>
      </div>
      <form
        v-if="selectedRoom"
        class="inline-create"
        @submit.prevent="createSeat"
      >
        <label>座位编码<input
          v-model="code"
          required
          maxlength="64"
          pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,63}"
        ></label><label class="checkbox-label"><input
          v-model="hasPowerOutlet"
          type="checkbox"
        >有电源插座</label><button
          class="primary-button"
          :disabled="saving"
        >
          {{ saving ? '创建中…' : '新增座位' }}
        </button>
      </form>
      <p
        v-if="loading"
        class="empty-state"
      >
        正在读取座位…
      </p><p
        v-else-if="roomId && seats.length === 0"
        class="empty-state"
      >
        该房间没有座位记录。
      </p><p
        v-else-if="!roomId"
        class="empty-state"
      >
        先选择房间查看或维护座位。
      </p>
      <div
        v-else
        class="admin-table-wrap"
      >
        <table class="admin-table">
          <thead><tr><th>ID</th><th>编码</th><th>插座</th><th>启用状态</th><th>操作</th></tr></thead><tbody>
            <tr
              v-for="seat in seats"
              :key="seat.id"
            >
              <td>{{ seat.id }}</td><td>
                <input
                  v-if="editingSeatId === seat.id"
                  v-model="editedCode"
                  class="table-input"
                  maxlength="64"
                ><span v-else>{{ seat.code }}</span>
              </td><td>
                <label
                  v-if="editingSeatId === seat.id"
                  class="table-checkbox"
                ><input
                  v-model="editedOutlet"
                  type="checkbox"
                >有</label><span v-else>{{ seat.hasPowerOutlet ? '有' : '无' }}</span>
              </td><td><span :class="seat.enabled ? 'enabled-label' : 'disabled-label'">{{ seat.enabled ? '启用' : '停用' }}</span></td><td>
                <div class="table-actions">
                  <template v-if="editingSeatId === seat.id">
                    <button
                      class="text-button"
                      @click="save(seat)"
                    >
                      保存
                    </button><button
                      class="text-button"
                      @click="editingSeatId = null"
                    >
                      取消
                    </button>
                  </template><button
                    v-else
                    class="text-button"
                    @click="edit(seat)"
                  >
                    编辑
                  </button><button
                    class="text-button"
                    @click="toggle(seat)"
                  >
                    {{ seat.enabled ? '停用' : '启用' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </main>
</template>
