<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useOperation, useWorkflows } from '../context'
import { WorkflowApi } from '../api'
import { canCancelClassroom, canDecide, formatTime, permitted, statusLabel } from '../policy'
import type { ClassroomDetail } from '../types'
import WorkflowError from './WorkflowError.vue'
const props = defineProps<{ id: number }>()
const emit = defineEmits<{ changed: [] }>()
const { api: http, session } = useWorkflows(), api = new WorkflowApi(http)
const { busy, error, run } = useOperation()
const detail = ref<ClassroomDetail | null>(null), reason = ref(''), action = ref<'approve' | 'reject' | 'cancel' | 'recover' | null>(null)
const dialogOpen = computed({ get: () => action.value !== null, set: value => { if (!value) action.value = null } })
const reviewer = computed(() => ['ROOM_ADMIN', 'DEPARTMENT_ADMIN'].some(r => permitted(session.user, r, 'classroom:approve')))
async function load() {
  const id = props.id; detail.value = null; action.value = null; reason.value = ''
  await run(async () => { const result = await api.classroom(id); if (props.id === id) detail.value = result })
}
watch(() => props.id, load, { immediate: true })
async function submit() {
  if (!detail.value || !action.value || busy.value) return
  const id = detail.value.request.id, selected = action.value
  if (selected === 'recover' && !reason.value.trim()) { error.value = '请填写核实与恢复原因'; return }
  await run(async () => {
    if (selected === 'cancel') await api.cancelClassroom(id)
    else if (selected === 'recover') await api.recoverClassroom(id, reason.value.trim())
    else await api.decide(id, selected, reason.value.trim())
    action.value = null; reason.value = ''; emit('changed')
  })
  // Re-read after any attempt: the server owns current permissions, deadlines and state.
  const previousError = error.value; await load(); if (previousError) error.value = previousError
}
</script>
<template>
  <section
    aria-label="教室申请详情"
    class="detail"
  >
    <div class="toolbar">
      <h2>教室申请 #{{ id }}</h2><el-button
        :loading="busy"
        @click="load"
      >
        刷新
      </el-button>
    </div>
    <WorkflowError :message="error" />
    <template v-if="detail">
      <el-alert
        :title="statusLabel(detail.request.status, detail.request.syncOperation)"
        :type="detail.request.status === 'SYNC_FAILED' ? 'error' : 'info'"
        :closable="false"
      />
      <dl>
        <dt>房间</dt><dd>{{ detail.request.roomId }}</dd><dt>时间</dt><dd>{{ formatTime(detail.request.startAt) }} 至 {{ formatTime(detail.request.endAt) }}</dd>
        <dt>用途</dt><dd>{{ detail.request.purpose }}</dd><dt>审批期限</dt><dd>{{ formatTime(detail.request.approvalDueAt) }}</dd>
        <dt>占用编号</dt><dd>{{ detail.request.occupancyId ?? '尚未确认' }}</dd><dt>同步尝试</dt><dd>{{ detail.request.syncAttempts }} 次 {{ detail.request.syncError || '' }}</dd>
      </dl>
      <p v-if="detail.request.status === 'SYNC_FAILED'">
        房间占用或释放尚未确认，请联系当前房间审批人核实。
      </p>
      <div class="toolbar">
        <template v-if="canDecide(session.user, detail)">
          <el-button
            type="primary"
            :disabled="busy"
            @click="action = 'approve'"
          >
            批准
          </el-button><el-button
            type="danger"
            :disabled="busy"
            @click="action = 'reject'"
          >
            拒绝
          </el-button>
        </template>
        <el-button
          v-if="canCancelClassroom(session.user, detail.request)"
          :disabled="busy"
          @click="action = 'cancel'"
        >
          取消申请
        </el-button>
        <el-button
          v-if="detail.request.status === 'SYNC_FAILED' && reviewer && session.user?.id !== detail.request.applicantId"
          :disabled="busy"
          @click="action = 'recover'"
        >
          核实并重试同步
        </el-button>
      </div>
      <h3>审批步骤</h3><ol>
        <li
          v-for="step in detail.steps"
          :key="step.id"
        >
          第{{ step.stepNumber }}级 · {{ step.requiredRole === 'ROOM_ADMIN' ? '房间管理员' : '部门管理员' }} · {{ step.status }} · 截止 {{ formatTime(step.dueAt) }}
        </li>
      </ol>
      <h3>审批意见</h3><ul>
        <li
          v-for="item in detail.actions"
          :key="item.id"
        >
          {{ formatTime(item.occurredAt) }} · {{ item.action }} · {{ item.reason || '无补充意见' }}
        </li>
      </ul>
      <h3>业务记录</h3><ul>
        <li
          v-for="event in detail.events"
          :key="event.id"
        >
          {{ formatTime(event.occurredAt) }} · {{ event.action }}<span v-if="event.note"> · {{ event.note }}</span>
        </li>
      </ul>
    </template>
    <el-dialog
      v-model="dialogOpen"
      :title="action === 'cancel' ? '确认取消申请' : action === 'recover' ? '核实并重试同步' : '提交审批意见'"
      width="min(520px, 94vw)"
    >
      <p v-if="action === 'cancel'">
        已有占用的申请会先申请释放，确认成功后才显示已取消。
      </p>
      <el-input
        v-if="action !== 'cancel'"
        v-model="reason"
        type="textarea"
        maxlength="500"
        show-word-limit
        :aria-label="action === 'recover' ? '核实与恢复原因' : '审批意见'"
      />
      <template #footer>
        <el-button
          :disabled="busy"
          @click="action = null"
        >
          返回
        </el-button><el-button
          type="primary"
          :loading="busy"
          @click="submit"
        >
          确认提交
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
<style scoped>.detail{padding:12px}.toolbar{display:flex;flex-wrap:wrap;align-items:center;gap:10px;margin:16px 0}.toolbar h2{flex:1}dl{display:grid;grid-template-columns:90px 1fr;gap:10px}dd{margin:0;overflow-wrap:anywhere}li{margin:10px 0}h3{margin-top:24px}</style>
