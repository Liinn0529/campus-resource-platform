import type { AxiosInstance } from 'axios'
import type { ClassroomDetail, ClassroomRequest, Envelope, Review, Room, Staff, Ticket, TicketDetail } from './types'

export class WorkflowApi {
  constructor(private readonly http: AxiosInstance) {}
  private async get<T>(path: string, params?: Record<string, string | number>) {
    const response = await this.http.get<Envelope<T>>(path, { params })
    return response.data.data
  }
  private async post<T>(path: string, body?: unknown) {
    const response = await this.http.post<Envelope<T>>(path, body)
    return response.data.data
  }
  classrooms(view: 'mine' | 'pending', offset = 0, limit = 50) { return this.get<ClassroomRequest[]>('/classroom-requests', { view, offset, limit }) }
  classroom(id: number) { return this.get<ClassroomDetail>(`/classroom-requests/${id}`) }
  apply(body: { roomId: number; startAt: string; endAt: string; purpose: string }) { return this.post<ClassroomRequest>('/classroom-requests', body) }
  decide(id: number, action: 'approve' | 'reject', reason: string) { return this.post<ClassroomRequest>(`/classroom-requests/${id}/${action}`, { reason }) }
  cancelClassroom(id: number) { return this.post<ClassroomRequest>(`/classroom-requests/${id}/cancel`) }
  recoverClassroom(id: number, reason: string) { return this.post<ClassroomRequest>(`/classroom-requests/${id}/recover`, { reason }) }
  tickets(view: 'mine' | 'assigned' | 'managed', offset = 0, limit = 50) { return this.get<Ticket[]>('/maintenance-tickets', { view, offset, limit }) }
  ticket(id: number) { return this.get<TicketDetail>(`/maintenance-tickets/${id}`) }
  report(body: { roomId: number; description: string }) { return this.post<Ticket>('/maintenance-tickets', body) }
  assign(id: number, assigneeId: number) { return this.post<Ticket>(`/maintenance-tickets/${id}/assign`, { assigneeId }) }
  ticketAction(id: number, action: 'start' | 'finish' | 'accept' | 'reject-acceptance' | 'cancel' | 'recover', note?: string) {
    return this.post<Ticket>(`/maintenance-tickets/${id}/${action}`, action === 'start' || action === 'cancel' ? undefined : action === 'recover' ? { reason: note } : { note })
  }
  review(id: number, rating: number, comment: string) { return this.post<Review>(`/maintenance-tickets/${id}/review`, { rating, comment }) }
  private async all<T>(path: string) {
    const rows: T[] = []
    for (let offset = 0; ; offset += 100) {
      const page = await this.get<T[]>(path, { limit: 100, offset }); rows.push(...page)
      if (page.length < 100) return rows
    }
  }
  async rooms(classroomOnly = false) { return (await this.all<Room>('/rooms')).filter(r => r.enabled && (!classroomOnly || r.type === 'CLASSROOM')) }
  staff() { return this.all<Staff>('/maintenance-staff') }
}
