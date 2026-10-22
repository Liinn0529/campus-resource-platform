#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
GIT_COMMIT=$(git rev-parse HEAD)
if [ -n "$(git status --porcelain)" ]; then
    GIT_COMMIT="${GIT_COMMIT}-dirty"
fi
BUILD_TIME=$(date -u +%Y-%m-%dT%H:%M:%SZ)
export GIT_COMMIT BUILD_TIME
printf 'Building revision %s at %s\n' "$GIT_COMMIT" "$BUILD_TIME"
docker compose build "$@"
