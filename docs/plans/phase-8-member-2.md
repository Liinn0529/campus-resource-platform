# Phase 8 — Member 2 D interface slice (local review draft)

## Scope

This is a local, reviewable D-side draft on top of Phase 7 commit `cf265a7`. It implements the member 2 contribution only:

- A paginated public lookup for enabled `PROPERTY_STAFF`, visible only to a current `PROPERTY_MANAGER` with `maintenance:assign`.
- A private HTTP receiver for M1/M2/M3 business audit events, using the existing distinct service credentials and a source-specific action/target allowlist.
- Idempotent receipt keyed by `(source_service, source_event_id)`, with same-payload replay returning the original audit ID and changed-payload replay returning 409.

It does not implement M1 occupancy writes/releases, M1/M2/M3 producer delivery, Web pages, or a claim that end-to-end Phase 8 is complete. No seed user or existing migration is changed.

## Decisions proposed for review

- Add nullable `source_event_id` to D audit rows and a partial unique index. D-native audit rows retain null; external event rows carry the producer's stable ID.
- Keep `actorUserId` nullable for system events. A valid internal service credential identifies the producer; producer code must take a human actor only from its verified request context. D checks the actor foreign key but does not query another schema.
- Accept only the current M1/M2/M3 event action and entity-type draft lists documented in [platform API](../api/platform.md). Each service owner must verify its list; member 4 must review the privacy and spoofing boundary.
- The D receiver persists the audit row and receipt identity in one database write. The producer remains responsible for atomically persisting its own business event and delivery intent, then retrying until it receives a matching D receipt.

These are proposals, not approved policies. In particular, retention duration, production allowlist changes, producer retry/outbox behavior, and reconciliation for delayed/unknown receipts still need owner review.

## Files

- `services/platform-service/src/main/resources/db/migration/V5__external_audit_events.sql`
- `services/platform-service/src/main/java/cn/campus/resource/platform/AuditController.java`
- `services/platform-service/src/main/java/cn/campus/resource/platform/AuditMapper.java`
- `services/platform-service/src/main/java/cn/campus/resource/platform/AuditService.java`
- `services/platform-service/src/main/java/cn/campus/resource/platform/MaintenanceStaffController.java`
- `services/platform-service/src/main/java/cn/campus/resource/platform/PlatformSecurity.java`
- `services/platform-service/src/test/java/cn/campus/resource/platform/ManagementApiTest.java` (response-shape expectation for the additive `sourceEventId` field)
- `services/platform-service/src/test/java/cn/campus/resource/platform/NonLocalDatabaseTest.java`
- `services/platform-service/src/test/java/cn/campus/resource/platform/ExternalAuditApiTest.java`
- `tests/java/cn/campus/resource/testing/DatabaseTestSupport.java`
- `docs/requirements/TRACEABILITY.md`
- `docs/ethics/ETHICS_LOG.md`
- `contracts/openapi/platform.v1.json`
- `docs/api/platform.md`

## Status and evidence

The full backend clean verify was rerun from Git Bash on 2026-10-27 and completed with `BUILD SUCCESS` at 2026-10-27T10:10:54Z. Surefire reports show 156 tests: D 54, M1 49, M2 28, M3 25; zero failures, errors, or skipped tests. This run includes V5 replay on isolated Testcontainers PostgreSQL and the real-HTTP D tests for both endpoints. The previous 54-test platform run had one wrong test password and two stale V4 assertions; all three causes were corrected and the final full reactor passed. The script used its temporary Maven container and isolated test databases; the development Compose volume was not used. Compose deployment, GitHub CI, and non-author review remain unverified. M1/M2/M3 producer delivery is not implemented by this slice. Keep related requirements `PARTIAL` until the owners integrate and independently verify the complete flow.

Suggested review order: member 1/3 verify source event identifiers, actor provenance, action names, and retry behavior; member 4 reviews access, data minimization, dedupe, and failure handling; then verify V5 against PostgreSQL and exercise both routes through real HTTP before treating this slice as accepted.

## 2026-10-28 — independent review follow-up

Member 4's static report identified a shared M2/M3 test fixture using M2 credentials for both services, missing public OpenAPI descriptions for the audit 409/422 codes, missing staff-role/permission negative cases and per-row minimal-field checks, and untested timestamp/replay concurrency behavior. The test fixture now selects distinct D and M1 test credentials by schema; the OpenAPI descriptions list the audit errors; API tests now cover role-plus-permission checks with retained JWTs, minimal fields on every row, sort order, timestamp equivalence, changed payload fields and concurrent duplicate/conflict requests.

The microsecond truncation already present in `AuditService` is now described in the API draft and replay tests: timestamps are compared after conversion to `Instant` and truncation to PostgreSQL's microsecond precision. This is a contract proposal, still awaiting human approval. No new backend tests were run after these changes; the earlier 156-test result applies only to the prior snapshot. M1/M2/M3 actor provenance and outbox delivery, retention/deletion policy, database/restart failure replay, full service credential boundary, and non-author runtime acceptance remain open. Keep REQ-D-007 PARTIAL.

## 2026-10-28 — local regression completed

The later full `bash scripts/test-backend.sh` run completed with BUILD SUCCESS (exit 0). Parsed Surefire XML: D 55, M1 49, M2 28, M3 25, total 157; zero failures, errors or skips. This supersedes the preceding unrun-test note for this snapshot and includes the new D authorization, timestamp and concurrent replay tests. PostgreSQL databases were isolated Testcontainers instances, not the development volume. The test script now handles Git Bash host paths explicitly while preserving Linux container paths. This is author verification; independent approval and producer integration remain open.

The standalone `node scripts/test-member2-recovery.mjs` also passed (exit 0) against the current D JAR and a separate in-memory PostgreSQL instance: D restart preserves the original accepted receipt; a request interrupted by database pause can be replayed with the same receipt; an invalid actor request does not consume the source event ID. The script asserts dedicated Compose labels and tmpfs before altering runtime state. This closes the receiver-side restart/interruption self-test gap only; it does not prove another service's durable outbox or delivery policy.
