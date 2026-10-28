<script setup lang="ts">
defineOptions({ name: "MaintenanceList" })
import { computed, onMounted, ref } from 'vue'
import { WorkflowApi } from '../../workflows/api'
import { useOperation, useWorkflows } from '../../workflows/context'
import { formatTime, permitted, statusLabel } from '../../workflows/policy'
import type { Ticket } from '../../workflows/types'
import ListPager from '../../workflows/components/ListPager.vue'
import WorkflowError from '../../workflows/components/WorkflowError.vue'
const { api: http, session } = useWorkflows(), api = new WorkflowApi(http), { busy, error, run } = useOperation()
const rows = ref<Ticket[]>([]), offset = ref(0), view = ref<'assigned' | 'managed'>('assigned')
const manager = computed(() => permitted(session.user, 'PROPERTY_MANAGER', 'maintenance:assign'))
const staff = computed(() => permitted(session.user, 'PROPERTY_STAFF'))
async function load(next = offset.value) { await run(async () => { rows.value = await api.tickets(view.value, next); offset.value = next }) }
function changeView() { rows.value = []; load(0) }
onMounted(() => { view.value = manager.value ? 'managed' : 'assigned'; load() })
</script>
<template>
  <section class="page">
    <h1>维修工单</h1><div class="toolbar">
      <el-radio-group
        v-model="view"
        :disabled="busy"
        @change="changeView"
      >
        <el-radio-button
          v-if="staff"
          value="assigned"
        >
          分派给我
        </el-radio-button><el-radio-button
          v-if="manager"
          value="managed"
        >
          主管派单
        </el-radio-button>
      </el-radio-group><el-button
        :loading="busy"
        @click="load()"
      >
        刷新
      </el-button>
    </div>
    <WorkflowError :message="error" /><el-table
      :data="rows"
      empty-text="暂无工单"
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
        label="状态"
        min-width="220"
      >
        <template #default="{ row }">
          {{ statusLabel(row.status, row.syncOperation, true) }}
        </template>
      </el-table-column>
      <el-table-column
        label="维修期限"
        min-width="180"
      >
        <template #default="{ row }">
          {{ formatTime(row.dueAt) }} <el-tag
            v-if="row.escalated"
            type="warning"
          >
            已升级 · 第{{ row.repairCycle }}周期
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="100"
      >
        <template #default="{ row }">
          <RouterLink :to="`/maintenance/${row.id}`">
            处理工单
          </RouterLink>
        </template>
      </el-table-column>
    </el-table><ListPager
      :offset="offset"
      :count="rows.length"
      :busy="busy"
      @change="load"
    />
  </section>
</template>
<style scoped>.page{max-width:1100px;margin:auto;padding:24px}.toolbar{display:flex;align-items:center;gap:12px;margin:16px 0;flex-wrap:wrap}</style>
