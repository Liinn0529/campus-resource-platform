<script setup lang="ts">
defineOptions({ name: "ClassroomApplications" })
import { computed, onMounted, ref } from 'vue'
import { WorkflowApi } from '../../workflows/api'
import { useOperation, useWorkflows } from '../../workflows/context'
import { formatTime, permitted, statusLabel } from '../../workflows/policy'
import type { ClassroomRequest, Room } from '../../workflows/types'
import ClassroomDetail from '../../workflows/components/ClassroomDetail.vue'
import ListPager from '../../workflows/components/ListPager.vue'
import WorkflowError from '../../workflows/components/WorkflowError.vue'
const { api: http, session } = useWorkflows(), api = new WorkflowApi(http)
const { busy, error, run } = useOperation()
const rows = ref<ClassroomRequest[]>([]), rooms = ref<Room[]>([]), offset = ref(0), selected = ref<number | null>(null), creating = ref(false)
const roomId = ref<number>(), times = ref<[Date, Date] | null>(null), purpose = ref('')
const canApply = computed(() => permitted(session.user, 'STUDENT', 'classroom:apply'))
async function load(next = offset.value) { await run(async () => { rows.value = await api.classrooms('mine', next); offset.value = next }) }
async function open() { await run(async () => { rooms.value = await api.rooms(true); creating.value = true }) }
async function submit() {
  if (busy.value) return
  if (!roomId.value || !times.value || !purpose.value.trim()) { error.value = '请选择教室、时间并填写用途'; return }
  const [start, end] = times.value
  if (start.getTime() <= Date.now() || end <= start) { error.value = '请选择未来的有效时间区间'; return }
  await run(async () => {
    await api.apply({ roomId: roomId.value!, startAt: start.toISOString(), endAt: end.toISOString(), purpose: purpose.value.trim() })
    creating.value = false; purpose.value = ''; times.value = null; rows.value = await api.classrooms('mine'); offset.value = 0
  })
}
onMounted(() => load())
</script>
<template>
  <section class="page">
    <div class="toolbar">
      <h1>我的教室申请</h1><el-button
        :loading="busy"
        @click="load()"
      >
        刷新
      </el-button><el-button
        v-if="canApply"
        type="primary"
        :disabled="busy"
        @click="open"
      >
        申请教室
      </el-button>
    </div>
    <WorkflowError :message="error" /><el-table
      :data="rows"
      empty-text="暂无申请"
      style="width:100%"
      @row-click="(row: ClassroomRequest) => selected = row.id"
    >
      <el-table-column
        prop="id"
        label="编号"
        width="90"
      /><el-table-column
        prop="roomId"
        label="房间"
        width="90"
      />
      <el-table-column
        label="开始时间"
        min-width="170"
      >
        <template #default="{ row }">
          {{ formatTime(row.startAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        min-width="230"
      >
        <template #default="{ row }">
          {{ statusLabel(row.status, row.syncOperation) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="90"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click.stop="selected = row.id"
          >
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table><ListPager
      :offset="offset"
      :count="rows.length"
      :busy="busy"
      @change="load"
    />
    <el-drawer
      :model-value="selected !== null"
      title="申请详情"
      size="min(760px, 100vw)"
      destroy-on-close
      @close="selected = null"
    >
      <ClassroomDetail
        v-if="selected !== null"
        :id="selected"
        @changed="load()"
      />
    </el-drawer>
    <el-dialog
      v-model="creating"
      title="申请整间教室"
      width="min(560px, 94vw)"
    >
      <el-form
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item
          label="教室"
          required
        >
          <el-select
            v-model="roomId"
            placeholder="请选择教室"
            style="width:100%"
          >
            <el-option
              v-for="room in rooms"
              :key="room.id"
              :label="`${room.name}（${room.code}）`"
              :value="room.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="开始与结束时间（本地时间）"
          required
        >
          <el-date-picker
            v-model="times"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item
          label="用途"
          required
        >
          <el-input
            v-model="purpose"
            type="textarea"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <WorkflowError :message="error" />
      </el-form><template #footer>
        <el-button
          :disabled="busy"
          @click="creating = false"
        >
          返回
        </el-button><el-button
          type="primary"
          :loading="busy"
          @click="submit"
        >
          提交申请
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
<style scoped>.page{max-width:1100px;margin:auto;padding:24px}.toolbar{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:20px}h1{flex:1}@media(max-width:600px){.page{padding:12px}}</style>
