#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
./scripts/init-env.sh
./scripts/build.sh
docker compose up -d --wait --wait-timeout 180
