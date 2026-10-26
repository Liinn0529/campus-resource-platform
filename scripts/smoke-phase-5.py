#!/usr/bin/env python3
"""Verify the local Phase 5 gateway without changing clocks or fabricating lifecycle rows."""

import argparse
from datetime import datetime, timedelta, timezone
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
from urllib.error import URLError
from urllib.parse import urlsplit
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent.parent
_spec = importlib.util.spec_from_file_location("phase3_smoke", ROOT / "scripts/smoke-phase-3.py")
_phase3 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_phase3)
SmokeFailure = _phase3.SmokeFailure
require = _phase3.require


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--url", default="http://127.0.0.1:18080")
    parser.add_argument("--wait", type=int, default=180)
    parser.add_argument("--evidence", type=Path)
    args = parser.parse_args()
    destination = urlsplit(args.url)
    require(destination.scheme in ("http", "https") and destination.hostname in ("127.0.0.1", "localhost", "::1")
            and destination.username is None and destination.password is None
            and destination.path in ("", "/") and not destination.query and not destination.fragment,
            "Private demo credentials may only be sent to the local loopback gateway")
    subprocess.run([sys.executable, str(ROOT / "scripts/smoke-phase-4.py"), "--expected-commit",
                    args.expected_commit, "--url", args.url.rstrip("/"), "--wait", str(args.wait)],
                   cwd=ROOT, check=True)
    env = _phase3.private_environment()
    api = _phase3.Platform(args.url, [value for key, value in env.items()
                                    if key.endswith(("PASSWORD", "TOKEN", "SECRET"))])
    tokens = {username: api.login(username, env["DEV_SEED_PASSWORD"], role)
              for username, role in _phase3.DEMO_ROLES.items()}
    student = tokens["demo_student_01"]
    other = tokens["demo_student_02"]
    admin = tokens["demo_admin"]
    room_admin = tokens["demo_room_admin"]

    def call(method, path, body=None, token=student, status=200, code=None):
        value, request_id = api.api(method, path, body, token, status, code)
        api.safe(value)
        return value, request_id

    for path in ("/api/v1/violations", "/api/v1/notifications"):
        call("GET", path, token=None, status=401, code="UNAUTHORIZED")
        call("GET", path, token=room_admin, status=403, code="FORBIDDEN")
        call("GET", path + "?limit=0", status=400, code="VALIDATION_ERROR")
        rows, _ = call("GET", path)
        require(isinstance(rows, list), "Expected a real own-record list")
        for row in rows:
            if path.endswith("violations"):
                require(set(row) == {"id", "reservationId", "reason", "occurredAt"}
                        and row["reason"] == "NO_SHOW", "Violation response exposed unexpected fields")
            else:
                require(row.get("sourceModule") == "M1" and row.get("kind") in ("CHECKIN_FIRST", "CHECKIN_SECOND"),
                        "Inbox returned an unexpected notification source or kind")
    rooms, _ = call("GET", "/api/v1/reservation/rooms?limit=100")
    room_id = rooms[0]["id"]
    seats, _ = call("GET", f"/api/v1/reservation/rooms/{room_id}/seats?limit=100")
    # Stay outside the check-in window so this short smoke can always cancel its own booking.
    # Exact reminder/deadline behavior is exercised by real HTTP/PostgreSQL tests with an injected Clock.
    start = datetime.now(ZoneInfo(env["CAMPUS_BUSINESS_ZONE"])).replace(minute=0, second=0, microsecond=0)
    start += timedelta(hours=3)
    payload = {"roomId": room_id, "seatId": seats[-1]["id"], "startAt": start.isoformat(),
               "endAt": (start + timedelta(hours=1)).isoformat()}
    daily, _ = api.api("POST", f"/api/v1/rooms/{room_id}/checkin-code", token=admin)
    require(isinstance(daily, dict) and isinstance(daily.get("code"), str), "D did not supply today's room code")
    code = daily["code"]
    api.secrets.append(code)
    booking, request_id = call("POST", "/api/v1/reservations", payload, status=201)
    reservation_id = booking["id"]
    try:
        path = f"/api/v1/reservations/{reservation_id}/checkin"
        call("POST", path, {"code": code}, token=None, status=401, code="UNAUTHORIZED")
        call("POST", path, {"code": code}, token=other, status=404, code="RESERVATION_NOT_FOUND")
        call("POST", path, {"code": code}, token=admin, status=404, code="RESERVATION_NOT_FOUND")
        call("POST", path, {"code": code}, token=room_admin, status=403, code="FORBIDDEN")
        call("POST", path, {"code": "invalid"}, status=400, code="VALIDATION_ERROR")
        call("POST", path, {"code": code, "userId": 1}, status=400, code="VALIDATION_ERROR")
        call("POST", path, {"code": code}, status=409, code="CHECKIN_NOT_OPEN")
        current, _ = call("GET", f"/api/v1/reservations/{reservation_id}")
        require(current["status"] == "CREATED", "An early check-in incorrectly changed reservation state")
    finally:
        cancelled, _ = call("POST", f"/api/v1/reservations/{reservation_id}/cancel")
        require(cancelled.get("status") == "CANCELLED", "Smoke-created reservation was not cancelled")
    call("POST", f"/api/v1/reservations/{reservation_id}/checkin", {"code": code},
         status=409, code="CHECKIN_NOT_ALLOWED")
    for path in ("/internal/v1/notifications", "/internal/v1/checkin-codes/verify"):
        status, _, _ = api.raw("POST", path, {}, env["PLATFORM_RESERVATION_TOKEN"])
        require(status == 404, "The public gateway exposed a private Phase 5 endpoint")
    evidence = {"verifiedAt": datetime.now(timezone.utc).isoformat(), "gitCommit": args.expected_commit,
                "reservationId": reservation_id, "createRequestId": request_id,
                "checks": {"phase4Regression": True, "ownInboxAndViolations": True,
                           "missingAndWrongIdentityRejected": True, "unknownInputRejected": True,
                           "earlyCheckinRejected": True, "cancelledCheckinRejected": True,
                           "createdBookingCancelled": True, "internalEndpointsHidden": True},
                "scope": "Gateway guards and real own-record reads. Exact time thresholds, successful check-in, "
                         "reminders, retries and NO_SHOW are verified by real HTTP/PostgreSQL integration tests "
                         "with an injected M1 Clock; this script does not change the live clock or insert lifecycle rows."}
    if args.evidence:
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_text(json.dumps(evidence, indent=2) + "\n")
    print("PASS: Phase 5 real gateway identities, inbox/violation reads, check-in guards and hidden internal APIs")
    print("PASS: smoke-created booking cancelled; no clock changes, fabricated lifecycle rows or new seed identities")


if __name__ == "__main__":
    try:
        main()
    except (SmokeFailure, OSError, URLError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        print("FAIL:", str(error), file=sys.stderr)
        sys.exit(1)
