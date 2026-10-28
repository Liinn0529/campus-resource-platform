# Phase 9 — Member 2 Web delivery status (2026-10-27)

## Scope and branch

Work is on `feature/member-02-platform-web`, the member 2 personal feature branch. No main branch, group branch, database volume reset, commit, or push was performed. This report covers only member 2's shared Web shell and student/admin routes. It does not mark Phase 9 or the full course assignment complete.

## Implemented in this worktree

- Added the shared Axios API client, session store, login/logout/expiry handling, route permission guard, and denied/not-found views.
- Implemented `/login`, `/student/home`, `/student/rooms`, `/student/rooms/:id`, `/student/reservations`, `/student/reservations/:id`, `/student/history`, and seven `/admin/*` routes.
- Added a real local Playwright suite. The mutating student flow requires explicit `RUN_REAL_E2E=1`; it cancels its own active test reservations at teardown. Cancellation preserves history by design, so those test rows remain visible in the local demo student's history.
- Fixed a reproduced login bug: the Pinia `authenticated` getter depended on non-reactive token storage and could remain false after a successful login. It now follows the reactive authenticated user object; API token storage remains separate.
- The student E2E runs at a 375px viewport and checks that the page does not overflow horizontally.
- The phase 8 D endpoint slice, migration, OpenAPI/API docs and backend test changes are tracked separately in [phase-8-member-2.md](phase-8-member-2.md), and remain a review draft.

## Verification actually run

- `pnpm.cmd lint` — passed.
- `pnpm.cmd typecheck` — passed.
- `pnpm.cmd test` — 6 Vitest tests passed.
- `pnpm.cmd build` — passed; Vite transformed 1714 modules.
- `RUN_REAL_E2E=1 pnpm.cmd --dir apps/web exec playwright test --reporter=line` — 3 tests passed against the local API: student reservation/cancel/history/rebook and early check-in rejection, unauthenticated route redirect, student admin-route denial and administrator read-only loading of all seven management pages. The real check-in success window and admin mutations were not exercised.
- Playwright failure output directories were removed after runs; no test screenshots/traces are retained.

## Remaining work and dependencies

- `workflowRoutes` from member 3 is not present in this checkout. The six approval/maintenance routes are member 3's ownership; do not invent placeholders or ship an incomplete production router. Integrate the real fragment when supplied.
- Obtain member 3's page review and member 4's permission/privacy review. The author cannot mark those approvals complete.
- Complete manual keyboard, refresh/direct-link, narrow-screen visual, admin mutation, successful check-in, and error/409 conflict walkthroughs with the reviewers.
- Phase 8 `REQ-D-007` still needs M1/M2/M3 event producers/outbox delivery, cross-service failure/replay integration and non-author contract review. The D receiver alone is not end-to-end audit delivery.
- Confirm outstanding retention, actor provenance, and role/scope policy decisions with the owners. Review member 1's D master-data/audit use and member 5's identity forwarding when their corresponding changes are available.
- GitHub CI, full multi-service E2E, deployments and phases 10–15 were not run or approved here.

## Suggested commit message

`feat(web): deliver member 2 shared shell and student routes`

## 2026-10-28 — member 3 independent review and integration

Member 3 reviewed the earlier member 2 review pack against a separate local copy. Their report was scoped to the supplied snapshot (base `cf265a7`, without a current worktree SHA); it records real D/M1 browser results and must not be generalized to the newer uncommitted worktree. Their ZIP supplied a reviewed candidate `workflowRoutes`, six M2/M3 pages, shared workflow components/API/policy modules, and a three-case route guard test. The original package and group branch were not modified.

Integrated the six workflow routes and pages into this personal worktree. The shared router keeps its `refreshMe`, authentication, role and permission checks; approval routes additionally require an approval role and permission, maintenance list routes require eligible staff or manager role combinations, and maintenance detail remains available to authenticated reporters for backend-scoped reads. Installed Element Plus in the shared app, provided the existing Axios client and Pinia session to the workflow context, and added role-aware navigation.

Fixed the independently reproduced empty-time availability-search failure by validating both dates and the interval inside the `try/finally` that owns the loading state. Added browser-flow checks for empty start/end inputs and for route errors. Added offset controls for reservation and violation history so records beyond the first 50 can be read, and made reservation detail react to same-component ID changes without accepting a late response for the previous ID.

Also addressed member 4's static findings that were directly actionable: shared M2/M3 test properties now use distinct D and M1 credentials; the D OpenAPI shared 409/422 descriptions list the audit error codes; staff directory API tests now check both authorization conditions, current-token permission revocation, ordinary staff denial, row field minimization and ordering; audit tests cover timestamp normalization and concurrent same/conflicting replays. The microsecond timestamp equivalence is documented as a proposal pending owner/member 4 approval; no policy is marked approved.

The restricted execution sandbox initially denied Node access to the installed pnpm `vue-tsc` package metadata. The normal local dependency runtime was then used on 2026-10-28: `pnpm.cmd --dir apps/web typecheck`, `lint`, `test`, and `build` all passed. Vitest reported 2 files / 9 tests passed; the production build transformed 1749 modules. Vite emitted its standard advisory that the main generated JavaScript chunk is over 500 kB, but completed successfully. No real-browser flow was rerun in this update, so the supplied route test and new real-flow browser cases remain unverified against live services.

Remaining before Phase 9 acceptance: run authorized real-browser checks in the normal local environment; verify 375px rendering with an actually configured 375px browser viewport; directly test all six workflow routes and refresh; check successful check-in, admin mutations, error/409 flows, and workflow views against real M2/M3; complete member 4's full-source review. Phase 8 producer outbox/delivery, cross-service failure/replay, retention policy, and actor provenance remain separate dependencies. Keep affected requirements PARTIAL.

## 2026-10-28 — member 2 local browser regression

After starting Docker Desktop, the existing campus-member-02 development containers were healthy. With NO_PROXY/no_proxy set to 127.0.0.1,localhost, `RUN_REAL_E2E=1 pnpm.cmd --dir apps/web test:e2e` passed 4/4 tests (exit 0). The automatic server check initially timed out with the inherited proxy; starting Vite and bypassing the proxy resolved this environment issue.

Verified on real D/M1 APIs: student login, both empty time inputs independently, availability, reservation/cancel/history/rebook, early check-in rejection, detail reload, actual 375px viewport equality and absence of horizontal overflow; unauthenticated redirects; student denial of admin/approval/maintenance lists; all seven admin read pages; Enter-key login; settings update persisted across reload and restoration of the original value in finally; logout removes the stored access token and prevents direct protected-route access. Created reservations were cancelled, preserving their ordinary history. Settings were restored. No local seed users were added.

The reservation list now ignores stale responses after view changes. Lint and typecheck passed after these changes. Backend regression also passed 157 tests; see the Phase 8 report. The browser used the existing local backend deployment, while the backend regression compiled current source in isolated containers.

At this intermediate checkpoint, member-2 validation gaps were successful browser check-in inside the real opening window, history pagination beyond 50 real rows, complete admin mutation coverage and browser conflict/network-error walkthroughs. Subsequent results below supersede this checkpoint. Other-member workflow acceptance and independent review are separate dependencies. The previous review ZIP predates this follow-up and is not evidence for these latest source changes.

## 2026-10-28 — isolated member 2 acceptance and final source checks

Built current backend jars were mounted into a separate Compose project, `campus-member-02-acceptance`, with a tmpfs PostgreSQL database. No development database was reset. Its five seed users remain unchanged; additional test users, 51 cancelled bookings and 101 seats were created through real APIs solely in this disposable database.

Five non-window browser tests passed together (24.5 seconds): all seven administrator pages and management mutations; history pagination/network recovery/real conflict; availability beyond 100 seats and outlet filtering; 14-route screenshot and viewport checks; invalid login/session and rejected CORS origin. The history test was then extended with a delayed real HTTP response while changing views and passed again. Request abort/delay simulates client transport conditions without inventing server responses.

Admin checks include building/room/seat create and edit, room/seat/user enable-disable, user password reset and rejection of old credentials, role/room-scope persistence, permission edit and restoration, and audit filtering. Student checks verify 51 real history rows across pages, non-overlap of page IDs, 101 available seats including the last record, and a competing real booking producing HTTP 409 after seat selection.

Mobile screenshot inspection found navigation labels squeezed vertically despite no document overflow. The navigation now occupies its own full-width row with unwrapped horizontally scrollable links; a browser geometry assertion prevents regression. Fourteen screenshots cover the login, six student routes at 375px, and seven administrator routes at 1440px. Screenshot/trace/video recording is disabled during password and check-in-code submission. Reservation detail now accurately says its input is cleared and not stored in the browser; it does not falsely claim the server never stores room codes.

Current frontend `lint`, `typecheck`, `test` and `build` passed. Vitest: 3 files / 11 tests; build: 1749 modules. The Vite main-chunk size advisory remains a performance advisory, not a failed build. API transport tests verify foreign-origin/alternate-base requests are rejected before attaching credentials and logout clears authentication headers. Actual source and generated bundles were scanned against private environment values with no matches.

`node scripts/test-member2-recovery.mjs` passed three real D receiver cases: receipt persistence after D restart; timeout/unknown-result replay after pausing and resuming the isolated database; rejected actor followed by a valid event with the same unused event key. The script verifies Compose project labels and tmpfs before operating containers. This is receiver recovery evidence, not proof of other members' outbox delivery.

Current Java regression passed 157 tests (D55, M1 49, M2 28, M3 25), with zero failures/errors/skips. Normal development browser regression passed 4/4 separately. New delivery packaging includes full current source, a SHA-256 manifest and the 14 screenshots, excluding private environment files, build/dependency directories and Git metadata. See [member-2-delivery.md](member-2-delivery.md) for reproduction commands and responsibility boundaries.

At 18:45 Asia/Shanghai, the real-window check-in passed after 26.4 minutes of waiting: a genuine booking for 19:00, a real D room code, browser submission and CHECKED_IN persisted after reload. That run reported 3/3 passed (admin/history/check-in); the newer five non-window cases were separately run successfully, so all six distinct isolated cases have passed, not one single six-test invocation. No clock was changed and no successful response was mocked. The final screenshot refresh follows a wording correction in AdminUsers: the browser does not display/cache password hashes, while the backend correctly retains BCrypt hashes.

Member 2's independently executable implementation and author verification are complete for this scope. External sign-off, cross-service producer integration, policy approvals, GitHub CI/cloud deployment and other members' features are not claimed complete. The source remains a local personal-branch worktree snapshot, without commit/push or main-branch changes; the manifest identifies that distinction explicitly.

Final build passed and the 14-route screenshot test passed again (1/1). The dedicated acceptance Compose project was then removed successfully, disposing only its tmpfs test data. The normal campus-member-02 development containers and database were preserved.
