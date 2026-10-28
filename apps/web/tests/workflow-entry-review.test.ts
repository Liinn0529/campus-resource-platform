import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { RouteLocationNormalized } from 'vue-router'
import { useSessionStore } from '../src/stores/session'
import { workflowRoutes } from '../src/routes/workflows'
function gate(path: string) {
 const guard = workflowRoutes.find(route => route.path === path)?.beforeEnter
 if (typeof guard !== 'function') throw new Error('Missing entry guard')
 return guard.call(undefined, {} as RouteLocationNormalized, {} as RouteLocationNormalized, () => {})
}
function actor(roles: string[], permissions: string[]) {
 useSessionStore().user = { id: 100, username: 'review-unit-actor', displayName: 'Review', roles, permissions }
}
beforeEach(() => setActivePinia(createPinia()))
describe('workflow guards against shared Pinia session', () => {
 it('requires an approval role as well as permission; SYSTEM_ADMIN gets no implicit bypass', () => {
  actor(['STUDENT'], []); expect(gate('/approvals')).toBe('/forbidden')
  actor(['SYSTEM_ADMIN'], ['classroom:approve']); expect(gate('/approvals')).toBe('/forbidden')
  actor(['ROOM_ADMIN'], ['classroom:approve']); expect(gate('/approvals')).toBe(true)
  actor(['DEPARTMENT_ADMIN'], ['classroom:approve']); expect(gate('/approvals/:id')).toBe(true)
 })
 it('allows property staff or a manager with assign permission', () => {
  actor(['PROPERTY_MANAGER'], []); expect(gate('/maintenance')).toBe('/forbidden')
  actor(['PROPERTY_MANAGER'], ['maintenance:assign']); expect(gate('/maintenance')).toBe(true)
  actor(['PROPERTY_STAFF'], []); expect(gate('/maintenance')).toBe(true)
  actor(['SYSTEM_ADMIN'], ['maintenance:assign']); expect(gate('/maintenance')).toBe('/forbidden')
 })
 it('keeps all routes protected and preserves reporter access to an authorized ticket detail', () => {
  expect(workflowRoutes).toHaveLength(6)
  expect(workflowRoutes.every(route => route.meta?.requiresAuth)).toBe(true)
  expect(workflowRoutes.find(route => route.path === '/maintenance/:id')?.beforeEnter).toBeUndefined()
 })
})

