export interface CurrentUser { id: number; username: string; displayName?: string; roles: string[]; permissions: string[] }
export interface Session { readonly user: CurrentUser | null; logout(): void; refreshMe(): Promise<void> }
export interface Envelope<T> { data: T; requestId: string }
export interface Room { id: number; name: string; code: string; type: string; enabled: boolean }
export interface Staff { id: number; displayName: string }
export interface ClassroomRequest {
  id: number; applicantId: number; roomId: number; startAt: string; endAt: string; purpose: string; status: string;
  submittedAt: string; updatedAt: string; approvalDueAt: string | null; syncAttempts: number; syncError: string | null;
  occupancyId: number | null; syncOperation?: string; releaseReason?: string | null;
}
export interface ApprovalStep { id: number; stepNumber: number; requiredRole: string; status: string; actorId: number | null; dueAt: string | null }
export interface ApprovalAction { id: number; stepNumber: number; actorId: number; action: string; reason: string | null; occurredAt: string }
export interface ClassroomEvent { id: number; action: string; actorId: number | null; occurredAt: string; note?: string | null }
export interface ClassroomDetail { request: ClassroomRequest; steps: ApprovalStep[]; actions: ApprovalAction[]; events: ClassroomEvent[] }
export interface Ticket {
  id: number; reporterId: number; roomId: number; description: string; status: string; assigneeId: number | null;
  reportedAt: string; updatedAt: string; dueAt: string | null; repairCycle: number; escalated: boolean;
  blockStartAt: string | null; occupancyId: number | null; syncOperation: string | null; syncAttempts: number; syncError: string | null;
}
export interface TicketEvent { id: number; action: string; actorUserId: number | null; repairCycle: number; note: string | null; createdAt: string }
export interface Review { ticketId: number; reviewerId: number; rating: number; comment: string | null; createdAt: string }
export interface TicketDetail { ticket: Ticket; events: TicketEvent[]; review: Review | null }
