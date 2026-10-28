<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage, fetchAll, type Envelope } from '../lib/api'
import { useSessionStore } from '../stores/session'

interface Room { id: number; buildingId: number; code: string; name: string; type: 'STUDY_ROOM' | 'CLASSROOM'; enabled: boolean }
interface Building { id: number; code: string; name: string }
const session = useSessionStore()
const rooms = ref<Room[]>([])
const buildings = ref<Building[]>([])
const page = ref(0)
const filterBuilding = ref('')
const buildingId = ref('')
const code = ref('')
const name = ref('')
const type = ref<'STUDY_ROOM' | 'CLASSROOM'>('STUDY_ROOM')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const canCreate = session.user?.roles.includes('SYSTEM_ADMIN') && session.user.permissions.includes('space:manage')
const editingRoomId = ref<number | null>(null)
const editedName = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, string | number> = { limit: 50, offset: page.value * 50 }
    if (filterBuilding.value) params.buildingId = Number(filterBuilding.value)
    const [roomResponse, buildingData] = await Promise.all([
      api.get<Envelope<Room[]>>('/managed-rooms', { params }),
      fetchAll<Building>('/buildings'),
    ])
    rooms.value = roomResponse.data.data
    buildings.value = buildingData
  } catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}

async function createRoom() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    await api.post('/rooms', { buildingId: Number(buildingId.value), code: code.value.trim(), name: name.value.trim(), type: type.value, enabled: true })
    code.value = ''; name.value = ''
    notice.value = '房间已创建。'
    await load()
  } catch (reason) { error.value = errorMessage(reason) }
  finally { saving.value = false }
}

async function toggle(room: Room) {
  if (room.enabled && !globalThis.window.confirm(`确定停用房间 ${room.name}？既有预约状态仍由后端规则处理。`)) return
  error.value = ''; notice.value = ''
  try {
    await api.patch(`/rooms/${room.id}`, { enabled: !room.enabled })
    notice.value = `房间 #${room.id} 的启用状态已更新。`
    await load()
  } catch (reason) { error.value = errorMessage(reason) }
}

function editName(room: Room) {
  editingRoomId.value = room.id
  editedName.value = room.name
}

async function saveName(room: Room) {
  error.value = ''
  notice.value = ''
  try {
    await api.patch(`/rooms/${room.id}`, { name: editedName.value.trim() })
    editingRoomId.value = null
    notice.value = `房间 #${room.id} 名称已更新。`
    await load()
  } catch (reason) { error.value = errorMessage(reason) }
}

onMounted(load)
</script>

<template>
  <main class="student-page catalog-page admin-page">
    <p class="eyebrow">
      管理 · 空间主数据
    </p>
    <h1>房间维护</h1>
    <p class="description">
      管理范围内查看房间并调整启用状态；停用或调整资源不代表既有预约自动释放。
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
    <div class="admin-columns">
      <section
        v-if="canCreate"
        class="admin-panel"
      >
        <h2>新增房间</h2>
        <form
          class="admin-form"
          @submit.prevent="createRoom"
        >
          <label>所属楼宇<select
            v-model="buildingId"
            required
          ><option
            disabled
            value=""
          >请选择楼宇</option><option
            v-for="building in buildings"
            :key="building.id"
            :value="String(building.id)"
          >{{ building.name }}（{{ building.code }}）</option></select></label>
          <label>房间编码<input
            v-model="code"
            required
            maxlength="64"
            pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,63}"
          ></label>
          <label>房间名称<input
            v-model="name"
            required
            maxlength="100"
          ></label>
          <label>空间类型<select v-model="type"><option value="STUDY_ROOM">自习室</option><option value="CLASSROOM">教室</option></select></label>
          <button
            class="primary-button"
            :disabled="saving || buildings.length === 0"
          >
            {{ saving ? '创建中…' : '创建房间' }}
          </button>
        </form>
      </section>
      <section class="admin-panel">
        <div class="admin-list-heading">
          <h2>管理范围内房间</h2><button
            class="outline-button"
            :disabled="loading"
            @click="load"
          >
            刷新
          </button>
        </div>
        <div class="room-filter">
          <label>按楼宇筛选<select
            v-model="filterBuilding"
            @change="page = 0; load()"
          ><option value="">全部可见楼宇</option><option
            v-for="building in buildings"
            :key="building.id"
            :value="String(building.id)"
          >{{ building.name }}</option></select></label>
        </div>
        <p
          v-if="loading"
          class="empty-state"
        >
          正在读取房间…
        </p><p
          v-else-if="rooms.length === 0"
          class="empty-state"
        >
          当前范围没有房间记录。
        </p>
        <div
          v-else
          class="admin-table-wrap"
        >
          <table class="admin-table">
            <thead><tr><th>ID</th><th>楼宇</th><th>房间</th><th>类型</th><th>状态</th><th>操作</th></tr></thead><tbody>
              <tr
                v-for="room in rooms"
                :key="room.id"
              >
                <td>{{ room.id }}</td><td>#{{ room.buildingId }}</td><td>
                  <input
                    v-if="editingRoomId === room.id"
                    v-model="editedName"
                    class="table-input"
                    maxlength="100"
                  ><template v-else>
                    {{ room.name }}
                  </template><small>{{ room.code }}</small>
                </td><td>{{ room.type === 'STUDY_ROOM' ? '自习室' : '教室' }}</td><td><span :class="room.enabled ? 'enabled-label' : 'disabled-label'">{{ room.enabled ? '启用' : '停用' }}</span></td><td>
                  <div class="table-actions">
                    <template v-if="editingRoomId === room.id">
                      <button
                        class="text-button"
                        @click="saveName(room)"
                      >
                        保存
                      </button><button
                        class="text-button"
                        @click="editingRoomId = null"
                      >
                        取消
                      </button>
                    </template><button
                      v-else
                      class="text-button"
                      @click="editName(room)"
                    >
                      改名称
                    </button><button
                      class="text-button"
                      @click="toggle(room)"
                    >
                      {{ room.enabled ? '停用' : '启用' }}
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
            :disabled="page === 0 || loading"
            @click="page--; load()"
          >
            上一页
          </button><span>第 {{ page + 1 }} 页</span><button
            class="outline-button"
            :disabled="rooms.length < 50 || loading"
            @click="page++; load()"
          >
            下一页
          </button>
        </div>
      </section>
    </div>
  </main>
</template>
