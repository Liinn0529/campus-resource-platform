<script setup lang="ts">
defineOptions({ name: "RepairList" })
import { computed, onMounted, ref } from 'vue'
import { WorkflowApi } from '../../workflows/api'
import { useOperation, useWorkflows } from '../../workflows/context'
import { formatTime, permitted, statusLabel } from '../../workflows/policy'
import type { Room, Ticket } from '../../workflows/types'
import TicketDetail from '../../workflows/components/TicketDetail.vue'
import ListPager from '../../workflows/components/ListPager.vue'
import WorkflowError from '../../workflows/components/WorkflowError.vue'
const { api: http, session } = useWorkflows(), api = new WorkflowApi(http), { busy, error, run } = useOperation()
const rows = ref<Ticket[]>([]), rooms = ref<Room[]>([]), offset = ref(0), selected = ref<number | null>(null), creating = ref(false)
const roomId = ref<number>(), description = ref(''), canReport = computed(() => permitted(session.user, 'STUDENT', 'maintenance:report'))
async function load(next = offset.value) { await run(async () => { rows.value = await api.tickets('mine', next); offset.value = next }) }
async function open() { await run(async () => { rooms.value = await api.rooms(); creating.value = true }) }
async function submit() {
  if (busy.value) return
  if (!roomId.value || !description.value.trim()) { error.value = '请选择房间并填写问题描述'; return }
  await run(async () => {
    await api.report({ roomId: roomId.value!, description: description.value.trim() })
    creating.value = false; description.value = ''; rows.value = await api.tickets('mine'); offset.value = 0
  })
}
onMounted(() => load())
</script>
<template>
  <section class="page">
    <div class="toolbar">
      <h1>我的报修</h1><el-button
        :loading="busy"
        @click="load()"
      >
        刷新
      </el-button><el-button
        v-if="canReport"
        type="primary"
        :disabled="busy"
        @click="open"
      >
        提交报修
      </el-button>
    </div>
    <WorkflowError :message="error" /><el-table
      :data="rows"
      empty-text="暂无报修"
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
        label="报修时间"
        min-width="170"
      >
        <template #default="{ row }">
          {{ formatTime(row.reportedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        min-width="230"
      >
        <template #default="{ row }">
          {{ statusLabel(row.status, row.syncOperation, true) }} <el-tag
            v-if="row.escalated"
            type="warning"
          >
            已升级
          </el-tag>
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
            @click="selected = row.id"
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
      title="报修详情"
      size="min(760px, 100vw)"
      destroy-on-close
      @close="selected = null"
    >
      <TicketDetail
        v-if="selected !== null"
        :id="selected"
        @changed="load()"
      />
    </el-drawer>
    <el-dialog
      v-model="creating"
      title="提交报修"
      width="min(560px, 94vw)"
    >
      <el-form
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item
          label="房间"
          required
        >
          <el-select
            v-model="roomId"
            placeholder="请选择房间"
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
          label="问题描述"
          required
        >
          <el-input
            v-model="description"
            type="textarea"
            maxlength="500"
            show-word-limit
          />
        </el-form-item><WorkflowError :message="error" />
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
          提交报修
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
<style scoped>.page{max-width:1100px;margin:auto;padding:24px}.toolbar{display:flex;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:20px}h1{flex:1}@media(max-width:600px){.page{padding:12px}}</style>
