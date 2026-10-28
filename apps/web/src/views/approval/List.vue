<script setup lang="ts">
defineOptions({ name: "ApprovalList" })
import { onMounted, ref } from 'vue'
import { WorkflowApi } from '../../workflows/api'
import { useOperation, useWorkflows } from '../../workflows/context'
import { formatTime, statusLabel } from '../../workflows/policy'
import type { ClassroomRequest } from '../../workflows/types'
import ListPager from '../../workflows/components/ListPager.vue'
import WorkflowError from '../../workflows/components/WorkflowError.vue'
const api = new WorkflowApi(useWorkflows().api), { busy, error, run } = useOperation()
const rows = ref<ClassroomRequest[]>([]), offset = ref(0)
async function load(next = offset.value) { await run(async () => { rows.value = await api.classrooms('pending', next); offset.value = next }) }
onMounted(() => load())
</script>
<template>
  <section class="page">
    <h1>我的审批待办</h1><p>仅展示当前身份、房间范围及审批阶段允许处理的申请。</p><el-button
      :loading="busy"
      @click="load()"
    >
      刷新
    </el-button><WorkflowError :message="error" />
    <el-table
      :data="rows"
      empty-text="暂无待办"
    >
      <el-table-column
        prop="id"
        label="编号"
      /><el-table-column
        prop="roomId"
        label="房间"
      />
      <el-table-column
        label="阶段"
        min-width="190"
      >
        <template #default="{ row }">
          {{ statusLabel(row.status) }}
        </template>
      </el-table-column>
      <el-table-column
        label="截止时间"
        min-width="170"
      >
        <template #default="{ row }">
          {{ formatTime(row.approvalDueAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="130"
      >
        <template #default="{ row }">
          <RouterLink :to="`/approvals/${row.id}`">
            查看并审批
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
<style scoped>.page{max-width:1100px;margin:auto;padding:24px}a{display:inline-block}</style>
