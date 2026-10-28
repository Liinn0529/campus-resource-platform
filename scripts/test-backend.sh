#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Docker needs Linux container paths unchanged and Windows host paths for cp.
docker_host_path() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}
if command -v cygpath >/dev/null 2>&1; then
    export MSYS_NO_PATHCONV=1
fi
revision=$(git rev-parse HEAD)
if [ -n "$(git status --porcelain)" ]; then
    revision="${revision}-dirty"
fi
build_time=$(date -u +%Y-%m-%dT%H:%M:%SZ)
test_temporary=$(mktemp -d)
test_container=''
cleanup() {
    if [ -n "$test_container" ]; then docker rm -f "$test_container" >/dev/null 2>&1 || :; fi
    rm -rf "$test_temporary"
}
trap cleanup EXIT HUP INT TERM
# Keep private/ignored files out and compile on Linux, away from host sync/virtual filesystem artifacts.
git ls-files --cached --others --exclude-standard -z | tar --null -T - -cf "$test_temporary/source.tar"
test_container=$(docker create -i \
    --mount type=volume,source=campus-resource-maven-cache,target=/root/.m2 \
    --mount "type=bind,source=${TESTCONTAINERS_SOCKET_PATH:-/var/run/docker.sock},target=/var/run/docker.sock" \
    --add-host host.docker.internal:host-gateway \
    --env DOCKER_HOST=unix:///var/run/docker.sock \
    --env TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    --env TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
    --workdir /workspace \
    maven:3.9.11-eclipse-temurin-21 sh -ec '
        tar -xf /tmp/campus-source.tar
        for module in platform reservation classroom maintenance; do
            if [ -f "/tmp/existing-$module.jar" ]; then
                mkdir -p "services/$module-service/target"
                cp "/tmp/existing-$module.jar" "services/$module-service/target/$module-service-0.1.0-SNAPSHOT.jar"
            fi
        done
        exec mvn -B -ntp clean verify "$@"
    ' campus-test "-DgitCommit=$revision" "-DbuildTime=$build_time" "$@")
docker cp "$(docker_host_path "$test_temporary/source.tar")" "$test_container:/tmp/campus-source.tar"
# Selected-module runs may need already packaged real upstream services for their HTTP fixtures.
for module in platform reservation classroom maintenance; do
    test_jar="services/$module-service/target/$module-service-0.1.0-SNAPSHOT.jar"
    if [ -f "$test_jar" ]; then docker cp "$(docker_host_path "$test_jar")" "$test_container:/tmp/existing-$module.jar"; fi
done
set +e
docker start -a "$test_container"
test_attach_status=$?
set -e
test_status=$(docker inspect --format '{{.State.ExitCode}}' "$test_container")
for module in platform reservation classroom maintenance; do
    test_target="services/$module-service/target"
    mkdir -p "$test_target"
    if docker cp "$test_container:/workspace/$test_target/surefire-reports" "$(docker_host_path "$test_temporary/reports")" 2>/dev/null; then
        rm -rf "$test_target/surefire-reports"
        mv "$test_temporary/reports" "$test_target/surefire-reports"
    fi
    docker cp "$test_container:/workspace/$test_target/$module-service-0.1.0-SNAPSHOT.jar" \
        "$(docker_host_path "$test_target/$module-service-0.1.0-SNAPSHOT.jar")" 2>/dev/null || :
    mkdir -p "$test_target/site/jacoco"
    docker cp "$test_container:/workspace/$test_target/site/jacoco/jacoco.xml" \
        "$(docker_host_path "$test_target/site/jacoco/jacoco.xml")" 2>/dev/null || :
done
if [ "$test_attach_status" -ne 0 ] && [ "$test_status" -eq 0 ]; then exit "$test_attach_status"; fi
exit "$test_status"
