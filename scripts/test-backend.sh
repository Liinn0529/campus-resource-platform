#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
revision=$(git rev-parse HEAD)
if [ -n "$(git status --porcelain)" ]; then
    revision="${revision}-dirty"
fi
build_time=$(date -u +%Y-%m-%dT%H:%M:%SZ)
docker run --rm \
    --mount "type=bind,source=$PWD,target=/workspace" \
    --mount type=volume,source=campus-resource-maven-cache,target=/root/.m2 \
    --workdir /workspace \
    maven:3.9.11-eclipse-temurin-21 \
    mvn -B -ntp verify "-DgitCommit=$revision" "-DbuildTime=$build_time" "$@"
