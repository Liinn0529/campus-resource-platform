import { describe, expect, it, vi } from 'vitest'
import axios, { AxiosError } from 'axios'
import { WorkflowApi } from '../src/workflows/api'
import { canCancelClassroom, canDecide, permitted, routeId, statusLabel, ticketActions } from '../src/workflows/policy'
import { errorMessage } from '../src/workflows/context'
import { workflowRoutes } from '../src/routes/workflows'
import type { ClassroomDetail, CurrentUser, TicketDetail } from '../src/workflows/types'

const student: CurrentUser = { id: 1, username: 'test-student', roles: ['STUDENT'], permissions: ['classroom:apply', 'maintenance:report'] }
const roomAdmin: CurrentUser = { id: 2, username: 'test-reviewer', roles: ['ROOM_ADMIN'], permissions: ['classroom:approve'] }
const manager: CurrentUser = { id: 3, username: 'test-manager', roles: ['PROPERTY_MANAGER'], permissions: ['maintenance:assign'] }
const staff: CurrentUser = { id: 4, username: 'test-staff', roles: ['PROPERTY_STAFF'], permissions: [] }
function classroom(): ClassroomDetail {
  return { request: { id: 9, applicantId: 1, roomId: 7, startAt: '2026-11-03T00:00:00Z', endAt: '2026-11-03T02:00:00Z', purpose: 'test', status: 'SUBMITTED', submittedAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-01T00:00:00Z', approvalDueAt: '2026-11-02T00:00:00Z', syncAttempts: 0, syncError: null, occupancyId: null },
    steps: [{ id: 1, stepNumber: 1, requiredRole: 'ROOM_ADMIN', status: 'PENDING', actorId: null, dueAt: '2026-11-02T00:00:00Z' }], actions: [], events: [] }
}
function ticket(): TicketDetail {
  return { ticket: { id: 10, reporterId: 1, roomId: 7, description: 'test', status: 'REPORTED', assigneeId: 4, reportedAt: '', updatedAt: '', dueAt: null, repairCycle: 1, escalated: false, blockStartAt: null, occupancyId: null, syncOperation: null, syncAttempts: 0, syncError: null }, events: [], review: null }
}
const now = Date.parse('2026-11-01T12:00:00Z')

describe('workflow presentation permissions and state', () => {
  it('does not give SYSTEM_ADMIN implicit business powers', () => {
    const admin = { ...student, roles: ['SYSTEM_ADMIN'], permissions: ['classroom:approve', 'maintenance:assign'] }
    expect(permitted(admin, 'ROOM_ADMIN', 'classroom:approve')).toBe(false)
    expect(canDecide(admin, classroom(), now)).toBe(false)
    expect(ticketActions(admin, ticket()).assign).toBe(false)
  })
  it('requires role and permission, and prevents self-approval', () => {
    expect(canDecide(roomAdmin, classroom(), now)).toBe(true)
    expect(canDecide({ ...roomAdmin, permissions: [] }, classroom(), now)).toBe(false)
    expect(canDecide({ ...roomAdmin, id: 1 }, classroom(), now)).toBe(false)
    expect(canDecide(null, classroom(), now)).toBe(false)
  })
  it('does not let the first approver complete the second level', () => {
    const d = classroom(); d.request.status = 'APPROVING'; d.steps[0]!.requiredRole = 'DEPARTMENT_ADMIN'
    d.actions.push({ id: 1, actorId: 2, stepNumber: 1, action: 'APPROVE', reason: null, occurredAt: '' })
    expect(canDecide({ ...roomAdmin, roles: ['ROOM_ADMIN', 'DEPARTMENT_ADMIN'] }, d, now)).toBe(false)
    expect(canDecide({ ...roomAdmin, id: 5, roles: ['DEPARTMENT_ADMIN'] }, d, now)).toBe(true)
  })
  it('expires an approval exactly at its deadline', () => {
    const d = classroom(); expect(canDecide(roomAdmin, d, Date.parse(d.request.approvalDueAt!))).toBe(false)
  })
  it('allows cancellation before start but not after the exact boundary', () => {
    const d = classroom(); d.request.status = 'APPROVED'
    expect(canCancelClassroom(student, d.request, now)).toBe(true)
    expect(canCancelClassroom(student, d.request, Date.parse(d.request.startAt))).toBe(false)
    expect(canCancelClassroom(roomAdmin, d.request, now)).toBe(false)
  })
  it('keeps uncertain synchronization and releasing states out of cancellation', () => {
    const d = classroom(); d.request.status = 'APPROVED_PENDING_SYNC'; d.request.syncAttempts = 1
    expect(canCancelClassroom(student, d.request, now)).toBe(false)
    d.request.syncAttempts = 0; expect(canCancelClassroom(student, d.request, now)).toBe(true)
    d.request.status = 'RELEASING'; expect(canCancelClassroom(student, d.request, now)).toBe(false)
  })
  it('only assigned staff can begin/finish repairs', () => {
    const d = ticket(); d.ticket.status = 'ASSIGNED'
    expect(ticketActions(staff, d).start).toBe(true)
    expect(ticketActions({ ...staff, id: 8 }, d).start).toBe(false)
    expect(ticketActions(manager, d).start).toBe(false)
    d.ticket.status = 'IN_PROGRESS'; expect(ticketActions(staff, d).finish).toBe(true)
  })
  it('keeps acceptance with the reporter and disables repeats during release', () => {
    const d = ticket(); d.ticket.status = 'WAITING_ACCEPTANCE'
    expect(ticketActions(student, d).accept).toBe(true)
    expect(ticketActions(manager, d).accept).toBe(false)
    d.ticket.syncOperation = 'RELEASE'; expect(ticketActions(student, d).accept).toBe(false)
  })
  it('allows one review only after confirmed completion', () => {
    const d = ticket(); expect(ticketActions(student, d).review).toBe(false)
    d.ticket.status = 'COMPLETED'; expect(ticketActions(student, d).review).toBe(true)
    d.review = { ticketId: 10, reviewerId: 1, rating: 5, comment: null, createdAt: '' }
    expect(ticketActions(student, d).review).toBe(false)
  })
  it('limits recovery to the current manager', () => {
    const d = ticket(); d.ticket.status = 'SYNC_FAILED'
    expect(ticketActions(manager, d).recover).toBe(true)
    expect(ticketActions(staff, d).recover).toBe(false)
    expect(ticketActions({ ...manager, permissions: [] }, d).recover).toBe(false)
  })
  it('distinguishes requested operations from confirmed occupancy/restoration', () => {
    expect(statusLabel('APPROVED_PENDING_SYNC')).toContain('正在确认')
    expect(statusLabel('APPROVED')).toBe('已确认占用')
    expect(statusLabel('WAITING_ACCEPTANCE', 'RELEASE', true)).toContain('正在恢复')
    expect(statusLabel('COMPLETED', null, true)).toContain('已确认恢复')
    expect(statusLabel('SYNC_FAILED')).toContain('需核实')
  })
  it('rejects malformed direct route identifiers', () => {
    for (const v of ['0', '-1', '1.2', '1e3', '9007199254740993', ['1'], undefined]) expect(routeId(v)).toBeNull()
    expect(routeId('123')).toBe(123)
  })
  it('exports exactly the six agreed routes with authentication metadata', () => {
    expect(workflowRoutes.map(r => r.path)).toEqual(['/student/classroom-applications', '/student/repairs', '/approvals', '/approvals/:id', '/maintenance', '/maintenance/:id'])
    expect(workflowRoutes.every(r => r.meta?.requiresAuth)).toBe(true)
  })
})

describe('shared Axios and backend envelope contract', () => {
  function setup() {
    const calls: { method?: string; url?: string; data?: string; params?: unknown; auth?: string }[] = []
    const client = axios.create({ baseURL: '/api/v1', headers: { Authorization: 'Bearer test-only-user-token' }, adapter: async config => {
      calls.push({ method: config.method, url: config.url, data: config.data, params: config.params, auth: config.headers.Authorization as string })
      return { status: 200, statusText: 'OK', headers: {}, config, data: { data: { id: 123 }, requestId: 'test-only-trace' } }
    } })
    return { calls, api: new WorkflowApi(client), client }
  }
  it('uses the supplied API instance and unwraps exactly one envelope', async () => {
    const { api, calls } = setup(); expect(await api.classroom(9)).toEqual({ id: 123 })
    expect(calls[0]).toMatchObject({ url: '/classroom-requests/9', auth: 'Bearer test-only-user-token' })
  })
  it('uses the actual pending/mine and managed/assigned list contracts', async () => {
    const { api, calls } = setup(); await api.classrooms('pending', 50); await api.tickets('managed', 100)
    expect(calls[0]?.params).toEqual({ view: 'pending', offset: 50, limit: 50 })
    expect(calls[1]?.params).toEqual({ view: 'managed', offset: 100, limit: 50 })
  })
  it('sends note, reason and bodyless operations using their distinct contracts', async () => {
    const { api, calls } = setup()
    await api.ticketAction(10, 'finish', 'done'); await api.ticketAction(10, 'recover', 'verified'); await api.ticketAction(10, 'start'); await api.cancelClassroom(9)
    expect(JSON.parse(calls[0]!.data!)).toEqual({ note: 'done' })
    expect(JSON.parse(calls[1]!.data!)).toEqual({ reason: 'verified' })
    expect(calls[2]?.data).toBeUndefined(); expect(calls[3]?.data).toBeUndefined()
  })
  it('retrieves staff from the minimal public endpoint with pagination', async () => {
    const { api, client } = setup()
    const get = vi.spyOn(client, 'get').mockResolvedValueOnce({ data: { data: Array.from({ length: 100 }, (_, id) => ({ id, displayName: `staff-${id}` })) } }).mockResolvedValueOnce({ data: { data: [{ id: 101, displayName: 'last' }] } })
    expect(await api.staff()).toHaveLength(101)
    expect(get).toHaveBeenLastCalledWith('/maintenance-staff', { params: { limit: 100, offset: 100 } })
  })
  it('keeps failed requests rejected rather than inventing success', async () => {
    const { api, client } = setup(); vi.spyOn(client, 'post').mockRejectedValue(new AxiosError('denied'))
    await expect(api.decide(9, 'approve', '')).rejects.toThrow()
  })
  it('retains the error code/request ID without displaying an arbitrary server body', () => {
    const e = new AxiosError('error'); e.response = { status: 409, statusText: '', headers: {}, config: {} as never, data: { code: 'STATE_CHANGED', requestId: 'trace-1', message: 'private debug body' } }
    expect(errorMessage(e)).toContain('STATE_CHANGED'); expect(errorMessage(e)).toContain('trace-1')
    expect(errorMessage(e)).not.toContain('private debug body')
  })
})
