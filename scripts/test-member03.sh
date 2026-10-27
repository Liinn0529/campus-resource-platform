#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
./scripts/test-backend.sh -pl services/classroom-service,services/maintenance-service -am \
  '-Dtest=Member03ReleaseWorkflowTest,Member03MaintenanceWorkflowTest,OccupancyReleaseClientTest,PlatformClientBoundaryTest' \
  '-Dsurefire.failIfNoSpecifiedTests=false'
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm test
pnpm --filter @campus/web exec vite build --config vite.member03.config.ts
