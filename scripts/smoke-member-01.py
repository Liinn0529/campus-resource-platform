#!/usr/bin/env python3
"""Verify M1 room blocks on the isolated local Compose stack, preserving released history."""
import argparse
from datetime import datetime, timedelta
import importlib.util
import ipaddress
from pathlib import Path
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.parse import urlencode, urlsplit
from uuid import uuid4
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("platform_smoke", ROOT / "scripts/smoke-phase-3.py")
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)
require = shared.require


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:18080")
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--restart", action="store_true", help="Restart only this Compose project's M1 while its test block is active")
    args = parser.parse_args()
    target = urlsplit(args.url)
    require(target.scheme == "http" and target.hostname in ("localhost", "127.0.0.1")
            and not target.username and not target.password and target.path in ("", "/")
            and not target.query and not target.fragment, "Only the local demo gateway is allowed")
    subprocess.run([sys.executable, str(ROOT / "scripts/smoke-phase-1.py"), "--platform-api",
                    "--expected-commit", args.expected_commit, "--url", args.url], cwd=ROOT, check=True)
    env = shared.private_environment()
    secrets = [v for k, v in env.items() if k.endswith(("PASSWORD", "TOKEN", "SECRET"))]
    public = shared.Platform(args.url, secrets)
    student = public.login("demo_student_01", env["DEV_SEED_PASSWORD"], "STUDENT")
    container = subprocess.check_output(["docker", "compose", "ps", "-q", "reservation-service"], cwd=ROOT, text=True).strip()
    require(bool(container) and "\n" not in container, "Expected one M1 container in the selected Compose project")
    address = subprocess.check_output(["docker", "inspect", "--format",
            "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", container], text=True).strip()
    require(ipaddress.ip_address(address).is_private, "M1 must be on a private Docker bridge")
    internal = shared.Platform("http://" + address + ":8080", secrets + [student])
    rooms, _ = public.api("GET", "/api/v1/reservation/rooms?limit=100", token=student)
    room = next(r for r in rooms if r["type"] == "CLASSROOM")
    start = datetime.now(ZoneInfo(env["CAMPUS_BUSINESS_ZONE"])).replace(minute=0, second=0, microsecond=0) + timedelta(hours=2)
    query = urlencode({"startAt": start.isoformat(), "endAt": (start + timedelta(hours=1)).isoformat()})
    availability = f"/api/v1/reservation/rooms/{room['id']}/availability?{query}"
    available, _ = public.api("GET", availability, token=student)
    require(available["seats"] and all(s["available"] for s in available["seats"]),
            "Smoke interval already occupied; existing business records were not changed")
    prefix = "member01-smoke:" + uuid4().hex
    active = []

    def call(method, path, body=None, module="M2", status=200):
        value, _ = internal.api(method, path, body, env["M1_CLASSROOM_TOKEN" if module == "M2" else "M1_MAINTENANCE_TOKEN"], status)
        internal.safe(value)
        return value

    def release(module, ref):
        return call("DELETE", f"/internal/v1/room-blocks/{module}/{ref}", module=module)

    payload = {"roomId": room["id"], "startAt": start.isoformat(), "endAt": (start + timedelta(hours=1)).isoformat(),
               "sourceModule": "M2", "sourceType": "CLASSROOM_BOOKING", "sourceRefId": prefix}
    try:
        internal.api("POST", "/internal/v1/room-blocks", payload, student, 401, "UNAUTHORIZED")
        call("POST", "/internal/v1/room-blocks", payload, module="M3", status=403)
        created = call("POST", "/internal/v1/room-blocks", payload, status=201)
        active.append(("M2", prefix))
        require(call("POST", "/internal/v1/room-blocks", payload) == created, "Repeated source did not return the same ledger")
        blocked, _ = public.api("GET", availability, token=student)
        require(all(not s["available"] for s in blocked["seats"]), "Room block did not cover every seat")
        if args.restart:
            subprocess.run(["docker", "compose", "restart", "reservation-service"], cwd=ROOT, check=True)
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                try:
                    if internal.raw("GET", "/actuator/health")[0] == 200:
                        break
                except (URLError, TimeoutError, ConnectionError):
                    pass
                time.sleep(1)
            else:
                raise shared.SmokeFailure("M1 did not recover after restart")
            require(call("POST", "/internal/v1/room-blocks", payload) == created, "Restart lost the original source")
            print("PASS: real M1 restart preserves the original occupancy ID and interval")
        released = release("M2", prefix)
        require(released["id"] == created["id"] and released["status"] == "RELEASED", "Release receipt mismatched")
        require(release("M2", prefix) == released, "Repeated release changed the result")
        require(call("POST", "/internal/v1/room-blocks", payload) == released, "Released source was reactivated")
        maintenance = dict(payload, sourceModule="M3", sourceType="MAINTENANCE", sourceRefId=prefix + "-m", endAt=None)
        call("POST", "/internal/v1/room-blocks", maintenance, module="M3", status=201)
        active.append(("M3", maintenance["sourceRefId"]))
        blocked, _ = public.api("GET", availability, token=student)
        require(all(not s["available"] for s in blocked["seats"]), "Open maintenance block did not cover seats")
        release("M2", prefix)
        blocked, _ = public.api("GET", availability, token=student)
        require(all(not s["available"] for s in blocked["seats"]), "Old source release affected maintenance")
        release("M3", maintenance["sourceRefId"])
        restored, _ = public.api("GET", availability, token=student)
        require(all(s["available"] for s in restored["seats"]), "Availability did not recover")
        print("PASS: internal identity, all-seat blocking, released replay and precise maintenance release")
    finally:
        for module, ref in active:
            release(module, ref)
    print("PASS: only this run's sources released; history retained; no credentials printed")


if __name__ == "__main__":
    try:
        main()
    except (shared.SmokeFailure, URLError, TimeoutError, subprocess.CalledProcessError) as error:
        print("FAIL: " + (str(error) if isinstance(error, shared.SmokeFailure) else type(error).__name__), file=sys.stderr)
        sys.exit(1)
