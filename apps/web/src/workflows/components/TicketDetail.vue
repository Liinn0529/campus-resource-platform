<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { WorkflowApi } from '../api'
import { useOperation, useWorkflows } from '../context'
import { formatTime, statusLabel, ticketActions } from '../policy'
import type { Staff, TicketDetail } from '../types'
import WorkflowError from './WorkflowError.vue'
const props = defineProps<{ id: number }>(), emit = defineEmits<{ changed: [] }>()
const { api: http, session } = useWorkflows(), api = new WorkflowApi(http), { busy, error, run } = useOperation()
const detail = ref<TicketDetail | null>(null), staff = ref<Staff[]>([]), assignee = ref<number>()
const selectedAction = ref<'assign' | 'start' | 'finish' | 'accept' | 'reject-acceptance' | 'cancel' | 'recover' | 'review' | null>(null)
const note = ref(''), rating = ref(5)
const actions = computed(() => detail.value ? ticketActions(session.user, detail.value) : null)
const dialogOpen = computed({ get: () => selectedAction.value !== null, set: v => { if (!v) selectedAction.value = null } })
const labels = { assign: '指派维修人员', start: '开始维修', finish: '提交完工', accept: '通过验收', 'reject-acceptance': '拒绝验收并返修', cancel: '取消报修', recover: '核实并重试同步', review: '评价工单' }
const needsNote = computed(() => ['finish', 'accept', 'reject-acceptance', 'recover'].includes(selectedAction.value ?? ''))
async function load() {
  const id = props.id; detail.value = null; selectedAction.value = null
  await run(async () => { const result = await api.ticket(id); if (props.id === id) detail.value = result })
}
watch(() => props.id, load, { immediate: true })
async function open(action: NonNullable<typeof selectedAction.value>) {
  note.value = ''; assignee.value = undefined; rating.value = 5
  if (action === 'assign') {
    const result = await run(() => api.staff()); if (!result) return; staff.value = result
  }
  selectedAction.value = action
}
async function submit() {
  if (!detail.value || !selectedAction.value || busy.value) return
  const id = detail.value.ticket.id, action = selectedAction.value
  if (needsNote.value && !note.value.trim()) { error.value = '请填写说明'; return }
  if (action === 'assign' && !assignee.value) { error.value = '请选择当前可派单人员'; return }
  if (action === 'review' && (!Number.isInteger(rating.value) || rating.value < 1 || rating.value > 5)) { error.value = '请选择1至5分'; return }
  const result = await run(async () => {
    if (action === 'assign') await api.assign(id, assignee.value!)
    else if (action === 'review') await api.review(id, rating.value, note.value.trim())
    else await api.ticketAction(id, action, note.value.trim())
    return true
  })
  if (result) { selectedAction.value = null; emit('changed') }
  const previousError = error.value; await load(); if (previousError) error.value = previousError
}
</script>
<template>
  <section
    class="detail"
    aria-label="维修工单详情"
  >
    <div class="toolbar">
      <h2>维修工单 #{{ id }}</h2><el-button
        :loading="busy"
        @click="load"
      >
        刷新
      </el-button>
    </div>
    <WorkflowError :message="error" />
    <template v-if="detail && actions">
      <el-alert
        :title="statusLabel(detail.ticket.status, detail.ticket.syncOperation, true)"
        :type="detail.ticket.status === 'SYNC_FAILED' ? 'error' : 'info'"
        :closable="false"
      />
      <el-alert
        v-if="detail.ticket.escalated"
        :title="`本周期已升级：第${detail.ticket.repairCycle}次维修，期限 ${formatTime(detail.ticket.dueAt)}`"
        type="warning"
        :closable="false"
      />
      <dl>
        <dt>房间</dt><dd>{{ detail.ticket.roomId }}</dd><dt>问题描述</dt><dd>{{ detail.ticket.description }}</dd><dt>维修人员</dt><dd>{{ detail.ticket.assigneeId ?? '尚未指派' }}</dd>
        <dt>维修期限</dt><dd>{{ formatTime(detail.ticket.dueAt) }}</dd><dt>维修周期</dt><dd>{{ detail.ticket.repairCycle }}</dd>
        <dt>占用编号</dt><dd>{{ detail.ticket.occupancyId ?? '尚未确认' }}</dd><dt>同步情况</dt><dd>{{ detail.ticket.syncOperation || '无待办' }} · {{ detail.ticket.syncAttempts }} 次 {{ detail.ticket.syncError || '' }}</dd>
      </dl>
      <p v-if="detail.ticket.status === 'SYNC_FAILED'">
        房间占用或恢复尚未确认，请联系物业主管核实。
      </p>
      <div class="toolbar">
        <el-button
          v-if="actions.assign"
          type="primary"
          :disabled="busy"
          @click="open('assign')"
        >
          指派
        </el-button>
        <el-button
          v-if="actions.start"
          type="primary"
          :disabled="busy"
          @click="open('start')"
        >
          开始维修
        </el-button>
        <el-button
          v-if="actions.finish"
          type="primary"
          :disabled="busy"
          @click="open('finish')"
        >
          提交完工
        </el-button>
        <template v-if="actions.accept">
          <el-button
            type="primary"
            :disabled="busy"
            @click="open('accept')"
          >
            通过验收
          </el-button><el-button
            :disabled="busy"
            @click="open('reject-acceptance')"
          >
            拒绝验收
          </el-button>
        </template>
        <el-button
          v-if="actions.cancel"
          :disabled="busy"
          @click="open('cancel')"
        >
          取消报修
        </el-button>
        <el-button
          v-if="actions.review"
          :disabled="busy"
          @click="open('review')"
        >
          评价
        </el-button>
        <el-button
          v-if="actions.recover"
          :disabled="busy"
          @click="open('recover')"
        >
          核实并重试同步
        </el-button>
      </div>
      <template v-if="detail.review">
        <h3>工单评价</h3><p>{{ detail.review.rating }}分 · {{ detail.review.comment || '无补充意见' }}</p>
      </template>
      <h3>处理记录</h3><ul>
        <li
          v-for="event in detail.events"
          :key="event.id"
        >
          {{ formatTime(event.createdAt) }} · {{ event.action }} · 第{{ event.repairCycle }}周期<span v-if="event.note"> · {{ event.note }}</span>
        </li>
      </ul>
    </template>
    <el-dialog
      v-model="dialogOpen"
      :title="selectedAction ? labels[selectedAction] : ''"
      width="min(520px, 94vw)"
    >
      <p v-if="selectedAction === 'start'">
        提交后先确认房间下架，成功后进入维修中。
      </p><p v-if="selectedAction === 'accept'">
        提交验收后开始恢复预约，确认释放成功后工单才完成。
      </p><p v-if="selectedAction === 'cancel'">
        确认取消当前报修？
      </p>
      <el-select
        v-if="selectedAction === 'assign'"
        v-model="assignee"
        placeholder="选择当前可派单人员"
        style="width:100%"
      >
        <el-option
          v-for="person in staff"
          :key="person.id"
          :label="person.displayName"
          :value="person.id"
        />
      </el-select>
      <p v-if="selectedAction === 'assign' && staff.length === 0">
        暂无可派单人员，请联系人员管理员。
      </p>
      <el-rate
        v-if="selectedAction === 'review'"
        v-model="rating"
        aria-label="评价分数"
      />
      <el-input
        v-if="needsNote || selectedAction === 'review'"
        v-model="note"
        type="textarea"
        maxlength="500"
        show-word-limit
        :placeholder="selectedAction === 'review' ? '评价意见（选填）' : '请填写说明'"
        aria-label="操作说明"
      />
      <WorkflowError :message="error" />
      <template #footer>
        <el-button
          :disabled="busy"
          @click="selectedAction = null"
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
