#!/usr/bin/env python3
"""Check the real development database without exposing credentials or hashes."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess

SCHEMAS = ("platform", "reservation", "classroom", "maintenance")


def query(sql, schema=None, migrator=False, denied=False):
    if schema is None:
        command = "exec psql -U postgres -d campus_resource -At -v ON_ERROR_STOP=1 -v VERBOSITY=sqlstate -c \"$1\""
    else:
        suffix = "FLYWAY_PASSWORD" if migrator else "DB_PASSWORD"
        role = schema + ("_migrator" if migrator else "_app")
        variable = schema.upper() + "_" + suffix
        command = ('export PGPASSWORD="$' + variable + '"; exec psql -h 127.0.0.1 -U '
                   + role + ' -d campus_resource -At -v ON_ERROR_STOP=1 -v VERBOSITY=sqlstate -c "$1"')
    result = subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "sh", "-ec", command, "check-database", sql],
        text=True, capture_output=True, check=False)
    if denied:
        assert result.returncode != 0 and "42501" in result.stderr, (
            "Expected insufficient_privilege", schema, migrator, result.returncode, result.stderr)
        return None
    assert result.returncode == 0, (schema, result.stderr)
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, help="Write nonsensitive counts and password fingerprint")
    parser.add_argument("--compare", type=Path, help="Compare with a snapshot from before a persistent restart")
    phase = parser.add_mutually_exclusive_group()
    phase.add_argument("--phase3", action="store_true", help="Check platform V3, reference permissions and append-only audit")
    phase.add_argument("--phase4", action="store_true", help="Also check reservation V2 and workflow ledger consistency")
    args = parser.parse_args()
    platform_v3 = args.phase3 or args.phase4

    expected_roles = sorted(schema + suffix for schema in SCHEMAS for suffix in ("_app", "_migrator"))
    rows = query("SELECT rolname || '|' || rolsuper || '|' || rolcreatedb || '|' || rolcreaterole || '|' "
                 "|| rolreplication || '|' || rolbypassrls || '|' || rolinherit FROM pg_roles "
                 "WHERE rolname ~ '^(platform|reservation|classroom|maintenance)_(app|migrator)$' ORDER BY rolname")
    assert rows.splitlines() == [role + "|false|false|false|false|false|false" for role in expected_roles], rows
    assert query("SELECT count(*) FROM pg_auth_members WHERE member IN (SELECT oid FROM pg_roles "
                 "WHERE rolname ~ '^(platform|reservation|classroom|maintenance)_(app|migrator)$')") == "0"

    versions = {schema: (["1", "2", "3"] if platform_v3 and schema == "platform" else ["1"])
                for schema in SCHEMAS}
    if args.phase4:
        versions["reservation"] = ["1", "2"]
    for schema in SCHEMAS:
        assert query("SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname='" + schema + "'") == schema + "_migrator"
        assert query("SELECT version || '|' || success FROM " + schema
                     + ".flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank").splitlines() == [
                         version + "|true" for version in versions[schema]], (schema, "Unexpected migration history")
        for migrator in (False, True):
            role = schema + ("_migrator" if migrator else "_app")
            assert query("SELECT current_user || '|' || current_schema()", schema, migrator) == role + "|" + schema
            for other in SCHEMAS:
                if other != schema:
                    query("SELECT * FROM " + other + ".flyway_schema_history", schema, migrator, denied=True)
                    query("UPDATE " + other + ".flyway_schema_history SET description=description WHERE FALSE",
                          schema, migrator, denied=True)
        query("BEGIN; CREATE TABLE " + schema + ".forbidden_ddl_probe (id BIGINT); ROLLBACK",
              schema, denied=True)
        query("SET ROLE " + schema + "_migrator", schema, denied=True)
        query("SELECT * FROM " + schema + ".flyway_schema_history", schema, denied=True)
        query("UPDATE " + schema + ".flyway_schema_history SET description=description WHERE FALSE",
              schema, denied=True)
        print("PASS:", schema, "migration versions " + ",".join(versions[schema]),
              "TCP credentials, schema isolation, app DDL/history restrictions")

    if platform_v3:
        query("UPDATE platform.audit_log SET action=action WHERE FALSE", "platform", denied=True)
        query("DELETE FROM platform.audit_log WHERE FALSE", "platform", denied=True)
        # Roll back even if a regression unexpectedly grants TRUNCATE, so verification cannot erase audit.
        query("BEGIN; TRUNCATE TABLE platform.audit_log; ROLLBACK", "platform", denied=True)
        print("PASS: platform app cannot update, delete or truncate audit history")

    expected = {"user": 5, "role": 7, "permission": 0, "user_role": 5,
                "role_permission": 0, "building": 2, "room": 4, "seat": 40}
    if platform_v3:
        expected.update(permission=14, role_permission=35, user_room_scope=0)
    counts = {table: int(query('SELECT count(*) FROM platform."' + table + '"', "platform"))
              for table in expected}
    assert counts == expected, counts
    passwords = query('SELECT username || \':\' || password_hash FROM platform."user" ORDER BY username', "platform")
    fingerprint = hashlib.sha256(passwords.encode()).hexdigest()
    evidence = {"counts": counts, "passwordFingerprint": fingerprint,
                "schemas": list(SCHEMAS), "version": "1"}
    if platform_v3:
        evidence.pop("version")
        evidence["versions"] = versions
    if args.phase4:
        check_reservation_workflow()
    if args.compare:
        assert json.loads(args.compare.read_text()) == evidence, "Data or existing passwords changed on restart"
        print("PASS: restart retained exact counts, migration versions and existing password hashes")
    if args.snapshot:
        args.snapshot.write_text(json.dumps(evidence, indent=2) + "\n")
    print("PASS: minimum seed counts", json.dumps(counts, sort_keys=True))


def check_reservation_workflow():
    tables = query("SELECT tablename FROM pg_tables WHERE schemaname='reservation' AND tablename IN "
                   "('room_gate','seat_reservation','seat_occupancy','reservation_event') ORDER BY tablename",
                   "reservation").splitlines()
    assert tables == ["reservation_event", "room_gate", "seat_occupancy", "seat_reservation"], tables
    assert query("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                 "WHERE conrelid='reservation.seat_occupancy'::regclass AND conname='occupancy_source_unique'",
                 "reservation") == "UNIQUE (source_module, source_ref_id, source_type)"
    assert query("SELECT count(*) FROM reservation.seat_reservation r WHERE NOT EXISTS "
                 "(SELECT 1 FROM reservation.room_gate g WHERE g.room_id=r.room_id)", "reservation") == "0"
    assert query("SELECT count(*) FROM reservation.seat_occupancy o LEFT JOIN reservation.seat_reservation r "
                 "ON o.source_ref_id=r.id::text WHERE o.source_module='M1' AND "
                 "(o.source_type<>'RESERVATION' OR r.id IS NULL OR o.room_id<>r.room_id "
                 "OR o.seat_id IS DISTINCT FROM r.seat_id OR o.start_at<>r.start_at "
                 "OR o.end_at IS DISTINCT FROM r.end_at)", "reservation") == "0"
    assert query("SELECT count(*) FROM reservation.seat_reservation r WHERE "
                 "(SELECT count(*) FROM reservation.seat_occupancy o WHERE o.source_module='M1' "
                 "AND o.source_type='RESERVATION' AND o.source_ref_id=r.id::text)<>1",
                 "reservation") == "0"
    assert query("SELECT count(*) FROM reservation.seat_reservation r JOIN reservation.seat_occupancy o "
                 "ON o.source_module='M1' AND o.source_type='RESERVATION' AND o.source_ref_id=r.id::text "
                 "WHERE (r.status='CANCELLED' AND (o.status<>'RELEASED' OR o.released_at IS NULL)) "
                 "OR (r.status='CREATED' AND (o.status<>'ACTIVE' OR o.released_at IS NOT NULL))",
                 "reservation") == "0"
    assert query("SELECT count(*) FROM reservation.seat_reservation r WHERE NOT EXISTS "
                 "(SELECT 1 FROM reservation.reservation_event e WHERE e.reservation_id=r.id "
                 "AND e.action IN ('CREATED','REBOOKED'))", "reservation") == "0"
    assert query("SELECT count(*) FROM reservation.seat_reservation r WHERE r.status='CANCELLED' AND "
                 "(SELECT count(*) FROM reservation.reservation_event e WHERE e.reservation_id=r.id "
                 "AND e.action='CANCELLED')<>1", "reservation") == "0"
    rows = {table: int(query("SELECT count(*) FROM reservation." + table, "reservation"))
            for table in ("room_gate", "seat_reservation", "seat_occupancy", "reservation_event")}
    rows["cancelledReservations"] = int(query(
        "SELECT count(*) FROM reservation.seat_reservation WHERE status='CANCELLED'", "reservation"))
    rows["activeOccupancies"] = int(query(
        "SELECT count(*) FROM reservation.seat_occupancy WHERE status='ACTIVE'", "reservation"))
    rows["cancelledActiveOccupancies"] = int(query(
        "SELECT count(*) FROM reservation.seat_reservation r JOIN reservation.seat_occupancy o "
        "ON o.source_module='M1' AND o.source_type='RESERVATION' AND o.source_ref_id=r.id::text "
        "WHERE r.status='CANCELLED' AND o.status='ACTIVE'", "reservation"))
    # Workflow history is intentionally retained and can grow after another real smoke run.
    # Keep it outside the stable seed/password/version snapshot used for restart comparison.
    print("PASS: reservation V2 gates, unique sources, ledger/state/event consistency", json.dumps(rows, sort_keys=True))


if __name__ == "__main__":
    main()
