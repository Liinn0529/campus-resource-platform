#!/usr/bin/env python3
"""Verify real M1 reservations and cancel all bookings created by this local smoke run."""

import argparse
from datetime import datetime, timedelta, timezone
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
from urllib.error import URLError
from urllib.parse import urlencode, urlsplit
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
    parser.add_argument("--evidence", type=Path, help="Save nonsensitive reservation verification results")
    args = parser.parse_args()
    destination = urlsplit(args.url)
    require(destination.scheme in ("http", "https") and destination.hostname in ("127.0.0.1", "localhost", "::1")
            and destination.username is None and destination.password is None
            and destination.path in ("", "/") and not destination.query and not destination.fragment,
            "Private demo credentials may only be sent to the local loopback gateway")
    subprocess.run([sys.executable, str(ROOT / "scripts/smoke-phase-3.py"), "--expected-commit",
                    args.expected_commit, "--url", args.url.rstrip("/"), "--wait", str(args.wait)],
                   cwd=ROOT, check=True)
    env = _phase3.private_environment()
    secrets = [value for key, value in env.items() if key.endswith(("PASSWORD", "TOKEN", "SECRET"))]
    api = _phase3.Platform(args.url, secrets)
    tokens = {username: api.login(username, env["DEV_SEED_PASSWORD"], role)
              for username, role in _phase3.DEMO_ROLES.items()}
    student = tokens["demo_student_01"]
    other = tokens["demo_student_02"]
    room_admin = tokens["demo_room_admin"]
    admin = tokens["demo_admin"]

    def call(method, path, body=None, token=student, status=200, code=None):
        value, request_id = api.api(method, path, body, token, status, code)
        api.safe(value)
        return value, request_id

    call("GET", "/api/v1/reservations", token=None, status=401, code="UNAUTHORIZED")
    call("GET", "/api/v1/reservations", token="smoke-invalid-jwt", status=401, code="UNAUTHORIZED")
    call("GET", "/api/v1/reservations", token=env["PLATFORM_RESERVATION_TOKEN"], status=401,
         code="UNAUTHORIZED")
    rooms, _ = call("GET", "/api/v1/reservation/rooms?limit=100")
    require(isinstance(rooms, list) and len(rooms) == 4 and all(row.get("enabled") is True for row in rooms),
            "Expected four enabled demo rooms from the real D catalog")
    require({row.get("type") for row in rooms} == {"STUDY_ROOM", "CLASSROOM"},
            "The M1 catalog must retain both enabled room types")
    total_seats = 0
    for room in rooms:
        rows, _ = call("GET", f"/api/v1/reservation/rooms/{room['id']}/seats?limit=100")
        require(isinstance(rows, list) and len(rows) == 10 and all(row.get("enabled") is True for row in rows),
                "Expected ten enabled seats per demo room")
        total_seats += len(rows)
    room_id = rooms[0]["id"]
    seats_by_power = {}
    for power in (True, False):
        rows, _ = call("GET", f"/api/v1/reservation/rooms/{room_id}/seats?hasPowerOutlet={str(power).lower()}&limit=100")
        require(isinstance(rows, list) and len(rows) == 5
                and all(row.get("hasPowerOutlet") is power for row in rows),
                "Power-outlet filtering did not match the real seat catalog")
        seats_by_power[power] = rows
    require(total_seats == 40, "Expected forty demo seats")
    seat = seats_by_power[True][0]
    seat_id = seat["id"]
    settings, _ = call("GET", "/api/v1/settings")
    require(settings == {"maxReservationHours": 4}, "This development smoke run requires the default four-hour setting")
    identity, _ = call("GET", "/api/v1/me")
    owner_id = identity["id"]
    zone = ZoneInfo(env["CAMPUS_BUSINESS_ZONE"])
    start = datetime.now(zone).replace(minute=0, second=0, microsecond=0) + timedelta(hours=2)
    end = start + timedelta(hours=2)
    payload = {"roomId": room_id, "seatId": seat_id,
               "startAt": start.isoformat(timespec="seconds"), "endAt": end.isoformat(timespec="seconds")}

    def availability():
        query = urlencode({"startAt": payload["startAt"], "endAt": payload["endAt"], "limit": 100})
        value, _ = call("GET", f"/api/v1/reservation/rooms/{room_id}/availability?" + query)
        require(isinstance(value, dict) and value.get("roomId") == room_id
                and isinstance(value.get("seats"), list), "Invalid availability interval response")
        require(datetime.fromisoformat(value["startAt"].replace("Z", "+00:00")) == start
                and datetime.fromisoformat(value["endAt"].replace("Z", "+00:00")) == end,
                "Availability did not represent the requested interval")
        require(all(set(row) == {"id", "code", "hasPowerOutlet", "available"}
                    and isinstance(row["available"], bool) for row in value["seats"]),
                "Availability leaked ownership or omitted a boolean result")
        matching = [row for row in value["seats"] if row.get("id") == seat_id]
        require(len(matching) == 1, "Requested seat missing from availability")
        return matching[0]["available"]

    require(availability() is True, "The selected smoke interval is already occupied; existing bookings were not changed")
    call("POST", "/api/v1/reservations", payload, token=room_admin, status=403, code="FORBIDDEN")
    too_long = dict(payload, endAt=(start + timedelta(hours=5)).isoformat(timespec="seconds"))
    call("POST", "/api/v1/reservations", too_long, status=422, code="RESERVATION_DURATION_EXCEEDED")
    print("PASS: real M1 catalog exposes both room types and forty seats; outlet filter and four-hour limit are enforced")

    created_ids = []
    first_id = rebook_id = None
    request_ids = {}
    cancelled = None
    try:
        booking, request_ids["create"] = call("POST", "/api/v1/reservations", payload, status=201)
        first_id = booking["id"]
        created_ids.append(first_id)
        require(booking.get("status") == "CREATED" and booking.get("userId") == owner_id
                and booking.get("roomId") == room_id and booking.get("seatId") == seat_id,
                "New reservation did not belong to the authenticated student and selected seat")
        call("POST", "/api/v1/reservations", payload, status=409, code="RESERVATION_TIME_CONFLICT")
        require(availability() is False, "Availability did not reflect the real active occupancy")
        for token in (other, admin):
            call("GET", f"/api/v1/reservations/{first_id}", token=token, status=404,
                 code="RESERVATION_NOT_FOUND")
            call("POST", f"/api/v1/reservations/{first_id}/cancel", token=token, status=404,
                 code="RESERVATION_NOT_FOUND")
            call("POST", f"/api/v1/reservations/{first_id}/rebook",
                 {"startAt": payload["startAt"], "endAt": payload["endAt"]}, token,
                 status=404, code="RESERVATION_NOT_FOUND")
        print("PASS: real reservation and occupancy block overlap; other students and system admin cannot act as its owner")

        cancelled, request_ids["cancel"] = call("POST", f"/api/v1/reservations/{first_id}/cancel")
        repeated, _ = call("POST", f"/api/v1/reservations/{first_id}/cancel")
        require(cancelled.get("status") == "CANCELLED" and repeated == cancelled,
                "Cancellation was not idempotent")
        require(availability() is True, "Cancellation did not release the matching occupancy")
        history, _ = call("GET", "/api/v1/reservations?view=history&limit=100")
        require(isinstance(history, list) and any(row.get("id") == first_id
                and row.get("status") == "CANCELLED" for row in history),
                "Cancelled reservation missing from the owner's history")
        rebooked, request_ids["rebook"] = call("POST", f"/api/v1/reservations/{first_id}/rebook",
                {"startAt": payload["startAt"], "endAt": payload["endAt"]}, status=201)
        rebook_id = rebooked["id"]
        created_ids.append(rebook_id)
        require(rebook_id != first_id and rebooked.get("rebookOf") == first_id
                and rebooked.get("seatId") == seat_id and rebooked.get("userId") == owner_id
                and rebooked.get("status") == "CREATED", "Rebooking did not create a new validated reservation")
        require(availability() is False, "Rebooking did not create a new active occupancy")
        call("POST", "/api/v1/reservations", payload, status=409, code="RESERVATION_TIME_CONFLICT")
        previous, _ = call("GET", f"/api/v1/reservations/{first_id}")
        require(previous == cancelled, "Rebooking changed the old history record")
        print("PASS: cancellation releases occupancy and keeps history; rebooking creates a new conflicting source ID")
    finally:
        failures = []
        for reservation_id in reversed(created_ids):
            try:
                value, _ = call("POST", f"/api/v1/reservations/{reservation_id}/cancel")
                require(value.get("status") == "CANCELLED", "Created reservation was not cancelled during cleanup")
            except Exception:
                # Try every known booking; do not print response bodies or credentials on cleanup failures.
                failures.append(reservation_id)
        require(not failures, "Failed to cancel smoke-created reservation IDs: " + ", ".join(map(str, failures)))

    require(availability() is True, "Smoke-created active occupancy remained after cancellation")
    for reservation_id in created_ids:
        value, _ = call("GET", f"/api/v1/reservations/{reservation_id}")
        require(value.get("status") == "CANCELLED", "Smoke booking was not retained as cancelled history")
    current, _ = call("GET", "/api/v1/reservations?view=current&limit=100")
    require(isinstance(current, list) and not {row.get("id") for row in current}.intersection(created_ids),
            "Smoke-created cancelled reservations remained in the current list")
    status, _, _ = api.raw("GET", "/internal/v1/rooms", token=env["PLATFORM_RESERVATION_TOKEN"])
    require(status == 404, "The public gateway exposed the private room catalog")
    print("PASS: all smoke-created bookings are cancelled; history/events remain, with no user or resource additions")
    evidence = {"verifiedAt": datetime.now(timezone.utc).isoformat(), "gitCommit": args.expected_commit,
                "roomId": room_id, "seatId": seat_id, "reservationId": first_id, "rebookId": rebook_id,
                "requestIds": request_ids, "interval": {"startAt": payload["startAt"], "endAt": payload["endAt"]},
                "catalogCounts": {"rooms": len(rooms), "seats": total_seats},
                "checks": {"realDLoginAndMetadata": True, "outletFilter": True, "defaultFourHours": True,
                           "overlapRejected": True, "ownerIsolation": True, "cancelIdempotent": True,
                           "occupancyReleased": True, "historyRebookNewId": True,
                           "allCreatedBookingsCancelled": True, "internalCatalogHidden": True}}
    if args.evidence:
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print("PASS: Phase 4 real reservation workflow completed without deleting retained history")


if __name__ == "__main__":
    try:
        main()
    except (SmokeFailure, OSError, URLError, ValueError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        print("FAIL:", str(error), file=sys.stderr)
        sys.exit(1)
