<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { errorMessage, fetchAll } from '../lib/api'

interface Room { id: number; buildingId: number; code: string; name: string; type: 'STUDY_ROOM' | 'CLASSROOM'; enabled: boolean }
interface Building { id: number; code: string; name: string }
const rooms = ref<Room[]>([])
const buildings = ref<Building[]>([])
const buildingId = ref('')
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const filters: Record<string, number> = {}
    if (buildingId.value) filters.buildingId = Number(buildingId.value)
    const [roomData, buildingData] = await Promise.all([
      fetchAll<Room>('/reservation/rooms', filters), fetchAll<Building>('/buildings'),
    ])
    rooms.value = roomData
    buildings.value = buildingData
  } catch (reason) { error.value = errorMessage(reason) }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <main class="student-page catalog-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">
          空间目录
        </p><h1>选择学习空间</h1><p class="description">
          浏览当前启用的自习室和教室，选择空间后查询时段内可用座位。
        </p>
      </div>
      <button
        class="outline-button"
        :disabled="loading"
        @click="load"
      >
        刷新目录
      </button>
    </div>
    <div class="catalog-filter">
      <label>按楼宇筛选<select
        v-model="buildingId"
        @change="load"
      ><option value="">全部楼宇</option><option
        v-for="building in buildings"
        :key="building.id"
        :value="String(building.id)"
      >{{ building.name }}（{{ building.code }}）</option></select></label>
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
      正在读取空间目录…
    </div>
    <div
      v-else-if="rooms.length === 0 && !error"
      class="empty-state"
    >
      当前没有可预约的空间。
    </div>
    <div
      v-else
      class="room-grid"
    >
      <RouterLink
        v-for="room in rooms"
        :key="room.id"
        class="room-card"
        :to="`/student/rooms/${room.id}`"
      >
        <div class="room-card-top">
          <span class="room-type">{{ room.type === 'STUDY_ROOM' ? '自习室' : '教室' }}</span><span>楼宇 #{{ room.buildingId }}</span>
        </div>
        <h2>{{ room.name }}</h2>
        <p>{{ room.code }} <span>·</span> 空间编号 {{ room.id }}</p>
        <span class="room-action">查看可用座位 <span aria-hidden="true">→</span></span>
      </RouterLink>
    </div>
  </main>
</template>
