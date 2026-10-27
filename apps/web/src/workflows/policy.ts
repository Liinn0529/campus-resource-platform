import type { ClassroomDetail, ClassroomRequest, CurrentUser, TicketDetail } from './types'
export function permitted(user: CurrentUser | null, role: string, permission?: string) {
  return !!user && user.roles.includes(role) && (!permission || user.permissions.includes(permission))
}
export function canDecide(user: CurrentUser | null, detail: ClassroomDetail, now = Date.now()) {
  const r = detail.request, pending = detail.steps.find(s => s.status === 'PENDING')
  return !!user && user.id !== r.applicantId && ['SUBMITTED', 'APPROVING'].includes(r.status) && !!pending &&
    permitted(user, pending.requiredRole, 'classroom:approve') && !detail.actions.some(a => a.actorId === user.id && a.action === 'APPROVE') &&
    !!r.approvalDueAt && now < Date.parse(r.approvalDueAt) && now < Date.parse(r.startAt)
}
export function canCancelClassroom(user: CurrentUser | null, r: ClassroomRequest, now = Date.now()) {
  if (!user || user.id !== r.applicantId || now >= Date.parse(r.startAt)) return false
  if (['SUBMITTED', 'APPROVING'].includes(r.status)) return !!r.approvalDueAt && now < Date.parse(r.approvalDueAt)
  return r.status === 'APPROVED' || (r.status === 'APPROVED_PENDING_SYNC' && r.syncAttempts === 0)
}
export function ticketActions(user: CurrentUser | null, d: TicketDetail) {
  const t = d.ticket, owner = user?.id === t.reporterId
  const manager = permitted(user, 'PROPERTY_MANAGER', 'maintenance:assign')
  const staff = permitted(user, 'PROPERTY_STAFF') && user?.id === t.assigneeId
  return {
    assign: manager && t.status === 'REPORTED', start: staff && t.status === 'ASSIGNED',
    finish: staff && t.status === 'IN_PROGRESS',
    accept: owner && t.status === 'WAITING_ACCEPTANCE' && !t.syncOperation,
    cancel: owner && ['REPORTED', 'ASSIGNED'].includes(t.status),
    review: owner && t.status === 'COMPLETED' && !d.review,
    recover: manager && t.status === 'SYNC_FAILED',
  }
}
const states: Record<string, string> = {
  SUBMITTED: '待房间管理员审批', APPROVING: '待部门管理员审批', APPROVED_PENDING_SYNC: '审批通过，正在确认占用',
  APPROVED: '已确认占用', RELEASING: '正在释放教室', COMPLETED: '已结束，已确认释放', REJECTED: '已拒绝',
  CANCELLED: '已取消', AUTO_CANCELLED: '审批超时，已取消', SYNC_FAILED: '同步失败，需核实占用状态',
  REPORTED: '待派单', ASSIGNED: '已派单，待开始', BLOCKING_ROOM: '正在确认房间下架', IN_PROGRESS: '维修中', WAITING_ACCEPTANCE: '完工待验收',
}
export function statusLabel(status: string, operation?: string | null, maintenance = false) {
  if (status === 'WAITING_ACCEPTANCE' && operation === 'RELEASE') return '验收通过，正在恢复预约'
  if (maintenance && status === 'COMPLETED') return '工单完成，已确认恢复预约'
  return states[status] ?? status
}
export function formatTime(value?: string | null) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
export function routeId(value: unknown) {
  const text = typeof value === 'string' ? value : ''
  const id = /^[1-9]\d*$/.test(text) ? Number(text) : NaN
  return Number.isSafeInteger(id) ? id : null
}
