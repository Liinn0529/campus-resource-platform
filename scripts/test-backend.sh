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
    --mount "type=bind,source=${TESTCONTAINERS_SOCKET_PATH:-/var/run/docker.sock},target=/var/run/docker.sock" \
    --add-host host.docker.internal:host-gateway \
    --env DOCKER_HOST=unix:///var/run/docker.sock \
    --env TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    --env TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
    --workdir /workspace \
    maven:3.9.11-eclipse-temurin-21 \
    mvn -B -ntp clean verify "-DgitCommit=$revision" "-DbuildTime=$build_time" "$@"
