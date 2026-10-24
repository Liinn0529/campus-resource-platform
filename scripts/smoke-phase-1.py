#!/usr/bin/env python3
"""Exercise the real local gateway and check the running Compose topology."""

import argparse
import json
import re
import subprocess
import time
from datetime import datetime, timezone
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener

HTTP = build_opener(ProxyHandler({}))


def request(base, path, method="GET"):
    try:
        with HTTP.open(Request(base + path, method=method), timeout=8) as response:
            return response.status, response.read(), response.headers
    except HTTPError as error:
        return error.code, error.read(), error.headers


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:18080")
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--wait", type=int, default=180)
    parser.add_argument("--platform-api", action="store_true", help="Phase 3 opens authenticated platform APIs")
    args = parser.parse_args()

    expected = {"postgres", "platform-service", "reservation-service", "classroom-service",
                "maintenance-service", "web", "nginx"}
    deadline = time.monotonic() + args.wait
    while True:
        output = subprocess.check_output(
            ["docker", "compose", "ps", "--all", "--format", "json"], text=True)
        # Compose v2 emits JSON lines; accept the array form used by some versions too.
        rows = json.loads(output) if output.lstrip().startswith("[") else [
            json.loads(line) for line in output.splitlines() if line.strip()]
        by_service = {row["Service"]: row for row in rows}
        if set(by_service) == expected and all(
                row["State"] == "running" and row.get("Health") == "healthy" for row in rows):
            break
        if time.monotonic() >= deadline:
            summary = {name: (row["State"], row.get("Health")) for name, row in by_service.items()}
            raise AssertionError(f"Seven healthy containers required; got {summary}")
        time.sleep(2)

    for name, row in by_service.items():
        published = [port for port in row.get("Publishers", []) or [] if port.get("PublishedPort")]
        if name == "nginx":
            assert len(published) == 1 and published[0]["URL"] == "127.0.0.1", published
        else:
            assert not published, f"Unexpected host port on {name}: {published}"
    print("PASS: seven containers healthy; only the gateway publishes a loopback port")

    for name in ("platform", "reservation", "classroom", "maintenance"):
        status, body, _ = request(args.url, f"/status/{name}/health")
        assert status == 200 and json.loads(body) == {"status": "UP"}, (name, status, body)
        status, body, _ = request(args.url, f"/status/{name}/info")
        info = json.loads(body)
        assert status == 200 and set(info) == {"service", "version", "gitCommit", "buildTime"}, info
        assert info["service"] == f"{name}-service", info
        assert info["version"] == "0.1.0-SNAPSHOT", info
        assert info["gitCommit"] == args.expected_commit, info
        assert re.fullmatch(r"[0-9a-f]{40}([0-9a-f]{24})?(-dirty)?", info["gitCommit"]), info
        built = datetime.fromisoformat(info["buildTime"].replace("Z", "+00:00"))
        assert built <= datetime.now(timezone.utc), info
        for endpoint in ("health", "info"):
            assert request(args.url, f"/status/{name}/{endpoint}", "POST")[0] == 405
        print(f"PASS: {name} health/info, real metadata and read-only gateway")

    for path in ("/actuator", "/actuator/env", "/internal/v1/room-blocks",
                 "/status/platform/env", "/status/unknown/info", "/status/platform/info/extra"):
        assert request(args.url, path)[0] == 404, path
    print("PASS: internal, unimplemented and non-whitelisted management paths hidden")
    assert request(args.url, "/api/v1/users")[0] == (401 if args.platform_api else 404)

    status, body, _ = request(args.url, "/")
    assert status == 200 and '<html lang="zh-CN">' in body.decode(), (status, body[:200])
    assets = re.findall(r'(?:src|href)="(/assets/[^\"]+)"', body.decode())
    assert assets, "Built frontend assets missing"
    for asset in assets:
        status, content, headers = request(args.url, asset)
        assert status == 200 and content and "immutable" in headers.get("Cache-Control", ""), asset
    status, body, _ = request(args.url, "/unknown-client-route")
    assert status == 200 and b'<div id="app">' in body, status
    assert request(args.url, "/assets/missing.js")[0] == 404
    print("PASS: built Web assets, client routing and missing-asset handling")


if __name__ == "__main__":
    main()
