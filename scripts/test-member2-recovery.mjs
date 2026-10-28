import { randomUUID } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import assert from 'node:assert/strict'

process.loadEnvFile('.env')
const project = 'campus-member-02-acceptance'
const postgres = `${project}-postgres-1`
const platform = `${project}-platform-service-1`
const base = 'http://127.0.0.1:18082'
function docker(...args) {
  const result = spawnSync('docker', args, { encoding: 'utf8', windowsHide: true })
  if (result.status !== 0) throw new Error(`Docker ${args[0]} failed (exit ${result.status}).`)
  return result.stdout.trim()
}
for (const name of [postgres, platform]) {
  assert.equal(docker('inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', name), project)
}
assert.match(docker('inspect', '--format', '{{json .HostConfig.Tmpfs}}', postgres), /\/var\/lib\/postgresql\/data/)

async function post(event, timeout = 10_000) {
  return fetch(`${base}/internal/v1/audit-events`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${process.env.PLATFORM_RESERVATION_TOKEN}` },
    body: JSON.stringify(event), signal: AbortSignal.timeout(timeout),
  })
}
function event() {
  return { sourceModule: 'M1', sourceEventId: `acceptance:${randomUUID()}`, action: 'CREATED',
    entityType: 'RESERVATION', entityId: `acceptance:${randomUUID()}`, actorUserId: null,
    occurredAt: new Date().toISOString(), sourceRequestId: randomUUID() }
}
async function ready() {
  for (let attempt = 0; attempt < 60; attempt++) {
    try { if ((await fetch(`${base}/actuator/health`, { signal: AbortSignal.timeout(1000) })).ok) return } catch { /* Service restart may temporarily close the port. */ }
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  throw new Error('Isolated D service did not recover.')
}

const persisted = event()
const first = await post(persisted)
assert.equal(first.status, 201)
const receipt = (await first.json()).data.id
docker('restart', '--time', '10', platform)
await ready()
const replay = await post(persisted)
assert.equal(replay.status, 200)
assert.equal((await replay.json()).data.id, receipt)
console.log('PASS: D restart preserves receipt and idempotent replay.')

const uncertain = event()
docker('pause', postgres)
try {
  let timeoutObserved = false
  try { await post(uncertain, 2000) } catch (error) {
    if (error.name !== 'TimeoutError') throw error
    timeoutObserved = true
  }
  assert.equal(timeoutObserved, true, 'Paused database must not produce an accepted receipt.')
} finally {
  docker('unpause', postgres)
}
await ready()
const recovered = await post(uncertain)
assert.ok([200, 201].includes(recovered.status))
const recoveredId = (await recovered.json()).data.id
const repeated = await post(uncertain)
assert.equal(repeated.status, 200)
assert.equal((await repeated.json()).data.id, recoveredId)
console.log('PASS: interrupted database request can be replayed without another receipt.')

const invalidActor = event()
const rejected = await post({ ...invalidActor, actorUserId: 9223372036854775 })
assert.ok(rejected.status >= 400, 'Invalid actor must not be accepted.')
const corrected = await post(invalidActor)
assert.equal(corrected.status, 201, 'Rejected event must not consume its source event ID.')
console.log('PASS: rejected actor leaves source event ID available for a valid transaction.')
