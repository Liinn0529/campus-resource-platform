#!/usr/bin/env python3
"""Verify the real local platform APIs while restoring existing demo resource values."""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent.parent
DEMO_ROLES = {
    "demo_admin": "SYSTEM_ADMIN",
    "demo_student_01": "STUDENT",
    "demo_student_02": "STUDENT",
    "demo_room_admin": "ROOM_ADMIN",
    "demo_property_staff": "PROPERTY_STAFF",
}


class SmokeFailure(RuntimeError):
    """A verification error whose message never contains response bodies or credentials."""


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def require(condition, message):
    if not condition:
        raise SmokeFailure(message)


def private_environment():
    values = {}
    for raw in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)", line)
        require(match is not None, "Private .env contains an unsupported assignment")
        key, value = match.groups()
        value = value.strip()
        if value.startswith(("'", '"')):
            require(len(value) >= 2 and value[-1] == value[0], "Private .env has an incomplete quoted value")
            value = value[1:-1]
        # Values are literal strings: never execute substitutions or evaluate shell/Python code.
        values[key] = value
    required = ("DEV_SEED_PASSWORD", "PLATFORM_RESERVATION_TOKEN",
                "PLATFORM_CLASSROOM_TOKEN", "PLATFORM_MAINTENANCE_TOKEN", "CAMPUS_BUSINESS_ZONE")
    require(all(values.get(key) for key in required), "Run ./scripts/init-env.sh before API verification")
    return values


class Platform:
    def __init__(self, base, secrets):
        self.base = base.rstrip("/")
        self.http = build_opener(ProxyHandler({}), NoRedirect())
        self.secrets = list(secrets)

    def raw(self, method, path, body=None, token=None):
        headers = {"Accept": "application/json"}
        payload = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            payload = json.dumps(body).encode("utf-8")
        if token is not None:
            headers["Authorization"] = "Bearer " + token
        request = Request(self.base + path, data=payload, method=method, headers=headers)
        try:
            response = self.http.open(request, timeout=20)
        except HTTPError as error:
            response = error
        with response:
            return response.status, response.read(), response.headers

    def api(self, method, path, body=None, token=None, status=200, code=None):
        actual, raw, headers = self.raw(method, path, body, token)
        require(actual == status, f"{method} {path}: expected HTTP {status}, received {actual}")
        try:
            envelope = json.loads(raw)
        except (ValueError, UnicodeDecodeError):
            raise SmokeFailure(f"{method} {path}: expected a JSON envelope") from None
        require(isinstance(envelope, dict), f"{method} {path}: expected an object envelope")
        request_id = envelope.get("requestId")
        require(isinstance(request_id, str) and bool(request_id)
                and request_id == headers.get("X-Request-ID"), f"{method} {path}: request ID mismatch")
        if status >= 400:
            require(set(envelope) == {"code", "message", "requestId"}, f"{method} {path}: invalid error envelope")
            if code is not None:
                require(envelope["code"] == code, f"{method} {path}: unexpected error code")
            self.safe(envelope)
            return envelope, request_id
        require(set(envelope) == {"data", "requestId"}, f"{method} {path}: invalid success envelope")
        return envelope["data"], request_id

    def safe(self, value):
        serialized = json.dumps(value, ensure_ascii=False)
        require(not any(secret and secret in serialized for secret in self.secrets),
                "A response exposed a credential or check-in code")
        def check_fields(item):
            if isinstance(item, dict):
                require(not {str(key).lower() for key in item}.intersection(
                    {"password", "passwordhash", "password_hash", "authversion", "auth_version"}),
                    "A response exposed a private identity field")
                for child in item.values():
                    check_fields(child)
            elif isinstance(item, list):
                for child in item:
                    check_fields(child)
        check_fields(value)

    def login(self, username, password, role):
        result, _ = self.api("POST", "/api/v1/auth/login", {"username": username, "password": password})
        require(isinstance(result, dict), "Login response must contain token and identity")
        token = result.get("accessToken")
        require(isinstance(token, str) and len(token.split(".")) == 3, "Login did not issue a JWT")
        require(result.get("tokenType") == "Bearer" and isinstance(result.get("expiresAt"), str),
                "Login token metadata is incomplete")
        self.secrets.append(token)
        identity = result.get("user")
        require(isinstance(identity, dict) and identity.get("username") == username
                and identity.get("roles") == [role], "Unexpected demo identity or roles")
        self.safe(identity)
        current, _ = self.api("GET", "/api/v1/me", token=token)
        require(current == identity, "Current identity differs from login response")
        self.safe(current)
        return token


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--url", default="http://127.0.0.1:18080")
    parser.add_argument("--wait", type=int, default=180)
    parser.add_argument("--evidence", type=Path, help="Save nonsensitive verification results")
    args = parser.parse_args()
    destination = urlsplit(args.url)
    require(destination.scheme in ("http", "https") and destination.hostname in ("127.0.0.1", "localhost", "::1")
            and destination.username is None and destination.password is None
            and destination.path in ("", "/") and not destination.query and not destination.fragment,
            "This development smoke script sends private demo credentials only to a loopback gateway")
    subprocess.run([sys.executable, str(ROOT / "scripts/smoke-phase-1.py"), "--platform-api",
                    "--url", args.url.rstrip("/"), "--expected-commit", args.expected_commit,
                    "--wait", str(args.wait)], cwd=ROOT, check=True)
    env = private_environment()
    business_zone = ZoneInfo(env["CAMPUS_BUSINESS_ZONE"])
    secrets = [value for key, value in env.items() if key.endswith(("PASSWORD", "TOKEN", "SECRET"))]
    platform = Platform(args.url, secrets)
    password = env["DEV_SEED_PASSWORD"]
    tokens = {username: platform.login(username, password, role) for username, role in DEMO_ROLES.items()}
    admin, student, room_admin = (tokens[name] for name in ("demo_admin", "demo_student_01", "demo_room_admin"))
    platform.api("POST", "/api/v1/auth/login", {"username": "demo_admin", "password": "smoke-invalid-credential"},
                 status=401, code="AUTHENTICATION_FAILED")
    platform.api("GET", "/api/v1/users", status=401, code="UNAUTHORIZED")
    print("PASS: five seeded identities log in using real passwords; missing and wrong credentials return 401")

    users, _ = platform.api("GET", "/api/v1/users?limit=100", token=admin)
    roles, _ = platform.api("GET", "/api/v1/roles", token=admin)
    permissions, _ = platform.api("GET", "/api/v1/permissions", token=admin)
    require(isinstance(users, list) and len(users) == 5, "Expected only five demo users")
    require({row.get("username") for row in users} == set(DEMO_ROLES), "Unexpected demo user set")
    require(isinstance(roles, list) and len(roles) == 7, "Expected seven role definitions")
    require(isinstance(permissions, list) and len(permissions) == 14, "Expected fourteen reference permissions")
    platform.safe(users)
    platform.safe(roles)
    platform.safe(permissions)
    platform.api("GET", "/api/v1/users", token=student, status=403, code="FORBIDDEN")
    platform.api("POST", "/api/v1/buildings", {"code": "SMOKE_DENIED", "name": "Must not be created"},
                 student, status=403, code="FORBIDDEN")
    buildings, _ = platform.api("GET", "/api/v1/buildings?limit=100", token=student)
    rooms, _ = platform.api("GET", "/api/v1/rooms?limit=100", token=student)
    require(isinstance(buildings, list) and len(buildings) == 2, "Expected two demo buildings")
    require(isinstance(rooms, list) and len(rooms) == 4, "Expected four enabled demo rooms")
    seats_by_room = {}
    for room in rooms:
        seats, _ = platform.api("GET", f"/api/v1/rooms/{room['id']}/seats?limit=100", token=student)
        require(isinstance(seats, list) and len(seats) == 10, "Expected ten seats per demo room")
        seats_by_room[room["id"]] = seats
    print("PASS: admin can read identity/role catalogs; student has read access to 2 buildings, 4 rooms and 40 seats")

    room, other_room = rooms[:2]
    room_id, other_id = room["id"], other_room["id"]
    seat = seats_by_room[room_id][0]
    room_admin_user = next(user for user in users if user["username"] == "demo_room_admin")
    room_admin_id = room_admin_user["id"]
    original_scope = room_admin_user["roomIds"]
    require(original_scope == [], "The demo room administrator must have no pre-existing scope for this smoke run")
    settings, _ = platform.api("GET", "/api/v1/settings", token=student)
    original_hours = settings["maxReservationHours"]
    require(original_hours == 4, "The development seed setting must be four hours before this smoke run")
    room_path = f"/api/v1/rooms/{room_id}"
    seat_path = f"/api/v1/seats/{seat['id']}"
    scope_path = f"/api/v1/users/{room_admin_id}/room-scopes"
    code_path = room_path + "/checkin-code"
    flags = {"room": False, "seat": False, "settings": False, "scope": False}
    audit_request_id = None
    try:
        flags["room"] = True
        updated_room, audit_request_id = platform.api("PATCH", room_path,
                {"name": "Phase 3 smoke verification"}, admin)
        require(updated_room.get("name") == "Phase 3 smoke verification", "Room update did not persist")
        observed_room, _ = platform.api("GET", room_path, token=student)
        require(observed_room.get("name") == updated_room["name"], "Student catalog did not reflect room maintenance")
        flags["seat"] = True
        changed_seat, _ = platform.api("PATCH", seat_path,
                {"hasPowerOutlet": not seat["hasPowerOutlet"]}, admin)
        require(changed_seat.get("hasPowerOutlet") is (not seat["hasPowerOutlet"]), "Seat update did not persist")
        flags["settings"] = True
        changed_settings, _ = platform.api("PUT", "/api/v1/settings", {"maxReservationHours": 5}, admin)
        require(changed_settings == {"maxReservationHours": 5}, "Setting update did not persist")
        shared_settings, _ = platform.api("GET", "/api/v1/settings", token=student)
        require(shared_settings == changed_settings, "Current setting was not readable by a student")

        before, _ = platform.api("GET", "/api/v1/managed-rooms", token=room_admin)
        require(before == [], "An unassigned room administrator unexpectedly manages a room")
        flags["scope"] = True
        platform.api("PUT", scope_path, {"roomIds": [room_id]}, admin)
        managed, _ = platform.api("GET", "/api/v1/managed-rooms?limit=100", token=room_admin)
        require([item["id"] for item in managed] == [room_id], "Room administrator scope was not enforced")
        platform.api("PATCH", f"/api/v1/rooms/{other_id}", {"name": "Must not change"}, room_admin,
                     status=403, code="FORBIDDEN")
        scoped_room, _ = platform.api("PATCH", room_path, {"name": "Phase 3 smoke verification"}, room_admin)
        require(scoped_room.get("id") == room_id, "Scoped room maintenance did not complete")
        print("PASS: real room/seat/settings updates persist; the existing room-admin JWT sees only its assigned management scope")

        platform.api("POST", code_path, token=student, status=403, code="FORBIDDEN")
        platform.api("GET", code_path, token=student, status=403, code="FORBIDDEN")
        first_code, _ = platform.api("POST", code_path, token=admin)
        repeat_code, _ = platform.api("POST", code_path, token=admin)
        require(first_code == repeat_code and isinstance(first_code.get("code"), str)
                and re.fullmatch(r"[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}", first_code["code"]) is not None,
                "Repeated daily-code requests did not return the same valid record")
        require(first_code.get("date") == datetime.now(business_zone).date().isoformat(),
                "Daily code does not belong to the current configured business date")
        platform.secrets.append(first_code["code"])
        scoped_code, _ = platform.api("GET", code_path, token=room_admin)
        require(scoped_code == first_code, "Scoped code access did not return today's record")
        events, _ = platform.api("GET", "/api/v1/audit-events?targetType=ROOM&limit=100", token=admin)
        require(isinstance(events, list), "Audit query must return an array")
        matching = [event for event in events if event.get("requestId") == audit_request_id]
        require(len(matching) == 1 and matching[0].get("action") == "ROOM_UPDATE"
                and matching[0].get("targetId") == str(room_id), "Room mutation was not linked to exactly one audit record")
        platform.safe(events)
        print("PASS: daily code is stable and restricted; audit links the real mutation request without credentials or code")
    finally:
        restore_errors = []
        restorations = (
            ("scope", "PUT", scope_path, {"roomIds": original_scope}),
            ("settings", "PUT", "/api/v1/settings", {"maxReservationHours": original_hours}),
            ("seat", "PATCH", seat_path, {"hasPowerOutlet": seat["hasPowerOutlet"]}),
            ("room", "PATCH", room_path, {"name": room["name"]}),
        )
        for flag, method, path, body in restorations:
            if flags[flag]:
                try:
                    platform.api(method, path, body, admin)
                except Exception:
                    # Attempt every independent restoration; never include body/token values in an error.
                    restore_errors.append(flag)
        require(not restore_errors, "Failed to restore smoke changes: " + ", ".join(restore_errors))

    restored_room, _ = platform.api("GET", room_path, token=student)
    restored_seats, _ = platform.api("GET", room_path + "/seats?limit=100", token=student)
    restored_seat = next(item for item in restored_seats if item["id"] == seat["id"])
    restored_settings, _ = platform.api("GET", "/api/v1/settings", token=student)
    restored_scope, _ = platform.api("GET", "/api/v1/managed-rooms", token=room_admin)
    require(restored_room.get("name") == room["name"] and restored_seat["hasPowerOutlet"] == seat["hasPowerOutlet"]
            and restored_settings["maxReservationHours"] == original_hours and restored_scope == [],
            "Smoke changes were not fully restored")
    platform.api("PATCH", room_path, {"name": "Must not change"}, room_admin, status=403, code="FORBIDDEN")
    print("PASS: resource values and empty demo room scope were restored; revoked scope immediately rejects the old JWT")

    for method, path, body in (
            ("GET", "/internal/v1/settings", None),
            ("GET", f"/internal/v1/rooms/{room_id}", None),
            ("GET", f"/internal/v1/rooms/{room_id}/seats", None),
            ("POST", "/internal/v1/checkin-codes/verify", {"roomId": room_id, "code": "AAAAAAAA"})):
        status, _, _ = platform.raw(method, path, body, env["PLATFORM_RESERVATION_TOKEN"])
        require(status == 404, "The public gateway exposed an internal route")
    for key in ("PLATFORM_RESERVATION_TOKEN", "PLATFORM_CLASSROOM_TOKEN", "PLATFORM_MAINTENANCE_TOKEN"):
        platform.api("GET", "/api/v1/me", token=env[key], status=401, code="UNAUTHORIZED")
    print("PASS: internal routes remain hidden at the gateway and service credentials cannot authenticate as users")

    evidence = {"verifiedAt": datetime.now(timezone.utc).isoformat(), "gitCommit": args.expected_commit,
                "counts": {"users": 5, "roles": 7, "permissions": 14, "buildings": 2, "rooms": 4, "seats": 40},
                "roomId": room_id, "seatId": seat["id"], "auditRequestId": audit_request_id,
                "checks": {"realLogin": True, "studentForbidden": True, "scopedManagement": True,
                           "dailyCodeStable": True, "auditLinked": True, "internalHidden": True,
                           "oldJwtScopeRevoked": True, "originalValuesRestored": True}}
    if args.evidence:
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print("PASS: Phase 3 platform API verification completed without adding users or resources")


if __name__ == "__main__":
    try:
        main()
    except (SmokeFailure, OSError, URLError, ValueError, subprocess.CalledProcessError) as error:
        print("FAIL:", str(error), file=sys.stderr)
        sys.exit(1)
