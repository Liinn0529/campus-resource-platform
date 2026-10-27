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
    phase.add_argument("--phase5", action="store_true", help="Check platform V4, reservation V3 and check-in/no-show/reminders")
    phase.add_argument("--phase7", action="store_true", help="Also check maintenance V2 work orders, events and truthful sync")
    phase.add_argument("--phase6", action="store_true", help="Also check classroom V2 and approval/synchronization consistency")
    args = parser.parse_args()
    platform_v3 = args.phase3 or args.phase4 or args.phase5 or args.phase6 or args.phase7

    expected_roles = sorted(schema + suffix for schema in SCHEMAS for suffix in ("_app", "_migrator"))
    rows = query("SELECT rolname || '|' || rolsuper || '|' || rolcreatedb || '|' || rolcreaterole || '|' "
                 "|| rolreplication || '|' || rolbypassrls || '|' || rolinherit FROM pg_roles "
                 "WHERE rolname ~ '^(platform|reservation|classroom|maintenance)_(app|migrator)$' ORDER BY rolname")
    assert rows.splitlines() == [role + "|false|false|false|false|false|false" for role in expected_roles], rows
    assert query("SELECT count(*) FROM pg_auth_members WHERE member IN (SELECT oid FROM pg_roles "
                 "WHERE rolname ~ '^(platform|reservation|classroom|maintenance)_(app|migrator)$')") == "0"

    versions = {schema: (["1", "2", "3"] if platform_v3 and schema == "platform" else ["1"])
                for schema in SCHEMAS}
    if args.phase4 or args.phase5 or args.phase6 or args.phase7:
        versions["reservation"] = ["1", "2"]
    if args.phase5 or args.phase6 or args.phase7:
        versions["platform"] = ["1", "2", "3", "4"]
        versions["reservation"] = ["1", "2", "3"]
    if args.phase6 or args.phase7:
        versions["classroom"] = ["1", "2"]
    if args.phase7:
        versions["maintenance"] = ["1", "2"]
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
    if args.phase4 or args.phase5 or args.phase6 or args.phase7:
        check_reservation_workflow(args.phase5 or args.phase6 or args.phase7)
    if args.phase5 or args.phase6 or args.phase7:
        check_lifecycle()
    if args.phase6 or args.phase7:
        check_classroom()
    if args.phase7:
        check_maintenance()
    if args.compare:
        assert json.loads(args.compare.read_text()) == evidence, "Data or existing passwords changed on restart"
        print("PASS: restart retained exact counts, migration versions and existing password hashes")
    if args.snapshot:
        args.snapshot.write_text(json.dumps(evidence, indent=2) + "\n")
    print("PASS: minimum seed counts", json.dumps(counts, sort_keys=True))


def check_reservation_workflow(phase5=False):
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
    released = "('CANCELLED','NO_SHOW')" if phase5 else "('CANCELLED')"
    active = "('CREATED','CHECKED_IN')" if phase5 else "('CREATED')"
    assert query("SELECT count(*) FROM reservation.seat_reservation r JOIN reservation.seat_occupancy o "
                 "ON o.source_module='M1' AND o.source_type='RESERVATION' AND o.source_ref_id=r.id::text "
                 "WHERE (r.status IN " + released + " AND (o.status<>'RELEASED' OR o.released_at IS NULL)) "
                 "OR (r.status IN " + active + " AND (o.status<>'ACTIVE' OR o.released_at IS NOT NULL))",
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


def check_lifecycle():
    assert query("SELECT count(*) FROM reservation.seat_reservation r WHERE "
                 "(r.status='CHECKED_IN') <> EXISTS(SELECT 1 FROM reservation.checkin c WHERE c.reservation_id=r.id "
                 "AND c.user_id=r.user_id)", "reservation") == "0"
    assert query("SELECT count(*) FROM reservation.seat_reservation r WHERE "
                 "(r.status='NO_SHOW') <> EXISTS(SELECT 1 FROM reservation.violation v WHERE v.reservation_id=r.id "
                 "AND v.user_id=r.user_id AND v.reason='NO_SHOW')", "reservation") == "0"
    for status in ("CHECKED_IN", "NO_SHOW"):
        assert query("SELECT count(*) FROM reservation.seat_reservation r WHERE r.status='" + status + "' AND "
                     "(SELECT count(*) FROM reservation.reservation_event e WHERE e.reservation_id=r.id "
                     "AND e.action='" + status + "')<>1", "reservation") == "0"
    assert query("SELECT count(*) FROM reservation.reservation_event WHERE "
                 "(action='NO_SHOW' AND actor_user_id IS NOT NULL) "
                 "OR (action<>'NO_SHOW' AND actor_user_id IS NULL)", "reservation") == "0"
    assert query("SELECT count(*) FROM reservation.reminder m JOIN reservation.seat_reservation r "
                 "ON r.id=m.reservation_id WHERE (m.kind='CHECKIN_FIRST' AND "
                 "(m.due_at<>r.start_at-INTERVAL '15 minutes' OR m.expires_at<>r.start_at+INTERVAL '10 minutes')) "
                 "OR (m.kind='CHECKIN_SECOND' AND (m.due_at<>r.start_at+INTERVAL '10 minutes' "
                 "OR m.expires_at<>r.start_at+INTERVAL '15 minutes'))", "reservation") == "0"
    # This verifier may inspect both schemas as the operator; production services never do so.
    assert query("SELECT count(*) FROM reservation.reminder m JOIN reservation.seat_reservation r "
                 "ON r.id=m.reservation_id LEFT JOIN platform.notification n ON n.id=m.notification_id "
                 "WHERE m.status='ACCEPTED' AND (n.id IS NULL OR n.source_module<>'M1' "
                 "OR n.source_ref_id<>'reservation-reminder:' || m.id::text OR n.user_id<>r.user_id "
                 "OR n.reservation_id<>r.id OR n.room_id<>r.room_id OR n.start_at<>r.start_at "
                 "OR n.kind<>m.kind OR n.due_at<>m.due_at OR n.expires_at<>m.expires_at)") == "0"
    summary = {table: int(query("SELECT count(*) FROM reservation." + table, "reservation"))
               for table in ("checkin", "violation", "reminder")}
    summary["notifications"] = int(query("SELECT count(*) FROM platform.notification", "platform"))
    print("PASS: check-in/no-show uniqueness, automated event actors, reminder windows and actual D receipts",
          json.dumps(summary, sort_keys=True))


def check_classroom():
    assert query("SELECT count(*) FROM classroom.classroom_request r WHERE "
                 "(SELECT count(*) FROM classroom.approval_step s WHERE s.request_id=r.id)<>2", "classroom") == "0"
    assert query("SELECT count(*) FROM classroom.classroom_request r WHERE r.status IN ('SUBMITTED','APPROVING') "
                 "AND ((SELECT count(*) FROM classroom.approval_step s WHERE s.request_id=r.id AND s.status='PENDING')<>1 "
                 "OR NOT EXISTS(SELECT 1 FROM classroom.approval_step s WHERE s.request_id=r.id AND s.status='PENDING' "
                 "AND s.due_at=r.approval_due_at AND s.step_number=CASE WHEN r.status='SUBMITTED' THEN 1 ELSE 2 END))",
                 "classroom") == "0"
    assert query("SELECT count(*) FROM classroom.approval_action a JOIN classroom.classroom_request r ON r.id=a.request_id "
                 "WHERE a.actor_id=r.applicant_id", "classroom") == "0"
    assert query("SELECT count(*) FROM classroom.approval_action a JOIN classroom.approval_action b "
                 "ON a.request_id=b.request_id AND a.step_number=1 AND b.step_number=2 "
                 "WHERE a.action='APPROVE' AND b.action='APPROVE' AND a.actor_id=b.actor_id", "classroom") == "0"
    assert query("SELECT count(*) FROM classroom.approval_action a LEFT JOIN classroom.approval_step s "
                 "ON s.request_id=a.request_id AND s.step_number=a.step_number WHERE s.id IS NULL "
                 "OR s.actor_id IS DISTINCT FROM a.actor_id OR s.status<>CASE WHEN a.action='APPROVE' THEN 'APPROVED' ELSE 'REJECTED' END",
                 "classroom") == "0"
    assert query("SELECT count(*) FROM classroom.classroom_request r WHERE r.status IN "
                 "('APPROVED_PENDING_SYNC','SYNC_FAILED','APPROVED') AND "
                 "(SELECT count(*) FROM classroom.approval_step s WHERE s.request_id=r.id AND s.status='APPROVED')<>2",
                 "classroom") == "0"
    assert query("SELECT count(*) FROM classroom.classroom_request r WHERE NOT EXISTS "
                 "(SELECT 1 FROM classroom.classroom_event e WHERE e.request_id=r.id AND e.action='SUBMITTED')",
                 "classroom") == "0"
    for status, event in (("CANCELLED","CANCELLED"),("AUTO_CANCELLED","AUTO_CANCELLED"),("SYNC_FAILED","SYNC_FAILED"),("APPROVED","OCCUPANCY_CONFIRMED")):
        assert query("SELECT count(*) FROM classroom.classroom_request r WHERE r.status='"+status+"' AND NOT EXISTS "
                     "(SELECT 1 FROM classroom.classroom_event e WHERE e.request_id=r.id AND e.action='"+event+"')", "classroom") == "0"
    assert query("SELECT count(*) FROM classroom.classroom_request WHERE status='APPROVED'", "classroom") == "0", (
        "Phase 6 baseline has no real Phase 8 receipt; investigate any claimed approval")
    assert query("SELECT count(*) FROM reservation.seat_occupancy WHERE source_module='M2'", "reservation") == "0", (
        "Phase 6 baseline must not fabricate M2 occupancies")
    assert query("SELECT count(*) FROM classroom.classroom_request WHERE status IN ('CANCELLED','AUTO_CANCELLED') "
                 "AND (sync_attempts<>0 OR occupancy_id IS NOT NULL)", "classroom") == "0"
    counts={table:int(query("SELECT count(*) FROM classroom."+table,"classroom")) for table in
            ("classroom_request","approval_step","approval_action","classroom_event")}
    print("PASS: classroom V2 stages, owner separation, decisions/events and truthful Phase 8 dependency",json.dumps(counts,sort_keys=True))



def check_maintenance():
    assert query("SELECT count(*) FROM maintenance.repair_ticket t WHERE "
                 "(SELECT count(*) FROM maintenance.ticket_event e WHERE e.ticket_id=t.id AND e.action='REPORTED')<>1", "maintenance") == "0"
    assert query("SELECT count(*) FROM maintenance.repair_ticket t WHERE "
                 "(t.status NOT IN ('REPORTED','CANCELLED') AND (t.assignee_id IS NULL OR t.due_at IS NULL)) "
                 "OR t.assignee_id=t.reporter_id", "maintenance") == "0"
    assert query("SELECT count(*) FROM maintenance.ticket_event e JOIN maintenance.repair_ticket t ON t.id=e.ticket_id "
                 "WHERE e.action IN ('REPORTED','ACCEPT_REQUESTED','ACCEPTANCE_REJECTED','CANCELLED','REVIEWED') "
                 "AND e.actor_user_id IS DISTINCT FROM t.reporter_id", "maintenance") == "0"
    assert query("SELECT count(*) FROM maintenance.ticket_event e JOIN maintenance.repair_ticket t ON t.id=e.ticket_id "
                 "WHERE e.action IN ('START_REQUESTED','FINISHED') AND e.actor_user_id IS DISTINCT FROM t.assignee_id", "maintenance") == "0"
    assert query("SELECT count(*) FROM maintenance.repair_ticket t WHERE t.escalated AND NOT EXISTS "
                 "(SELECT 1 FROM maintenance.ticket_event e WHERE e.ticket_id=t.id AND e.repair_cycle=t.repair_cycle AND e.action='ESCALATED')", "maintenance") == "0"
    assert query("SELECT count(*) FROM maintenance.ticket_review r JOIN maintenance.repair_ticket t ON t.id=r.ticket_id "
                 "WHERE t.status<>'COMPLETED' OR r.reviewer_id<>t.reporter_id", "maintenance") == "0"
    # Phase7 live M1 has no room-block endpoint. A fixture must never produce live repair/receipt rows.
    assert query("SELECT count(*) FROM maintenance.repair_ticket WHERE status IN ('IN_PROGRESS','WAITING_ACCEPTANCE','COMPLETED') OR occupancy_id IS NOT NULL", "maintenance") == "0"
    assert query("SELECT count(*) FROM maintenance.repair_ticket WHERE status='SYNC_FAILED' "
                 "AND (sync_operation<>'BLOCK' OR sync_error<>'M1_HTTP_403' OR sync_attempts<>1)", "maintenance") == "0"
    assert query("SELECT count(*) FROM reservation.seat_occupancy WHERE source_module='M3'", "reservation") == "0"
    counts={table:int(query("SELECT count(*) FROM maintenance."+table,"maintenance"))
            for table in ("repair_ticket","ticket_event","ticket_review")}
    print("PASS: maintenance V2 owner/staff decisions, escalation/review consistency and truthful Phase8 dependency", json.dumps(counts, sort_keys=True))

if __name__ == "__main__":
    main()
