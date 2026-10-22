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
    args = parser.parse_args()

    expected_roles = sorted(schema + suffix for schema in SCHEMAS for suffix in ("_app", "_migrator"))
    rows = query("SELECT rolname || '|' || rolsuper || '|' || rolcreatedb || '|' || rolcreaterole || '|' "
                 "|| rolreplication || '|' || rolbypassrls || '|' || rolinherit FROM pg_roles "
                 "WHERE rolname ~ '^(platform|reservation|classroom|maintenance)_(app|migrator)$' ORDER BY rolname")
    assert rows.splitlines() == [role + "|false|false|false|false|false|false" for role in expected_roles], rows
    assert query("SELECT count(*) FROM pg_auth_members WHERE member IN (SELECT oid FROM pg_roles "
                 "WHERE rolname ~ '^(platform|reservation|classroom|maintenance)_(app|migrator)$')") == "0"

    for schema in SCHEMAS:
        assert query("SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname='" + schema + "'") == schema + "_migrator"
        assert query("SELECT version || '|' || success FROM " + schema
                     + ".flyway_schema_history WHERE version IS NOT NULL") == "1|true"
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
        print("PASS:", schema, "migration v1, TCP credentials, schema isolation, app DDL/history restrictions")

    expected = {"user": 5, "role": 7, "permission": 0, "user_role": 5,
                "role_permission": 0, "building": 2, "room": 4, "seat": 40}
    counts = {table: int(query('SELECT count(*) FROM platform."' + table + '"', "platform"))
              for table in expected}
    assert counts == expected, counts
    passwords = query('SELECT username || \':\' || password_hash FROM platform."user" ORDER BY username', "platform")
    fingerprint = hashlib.sha256(passwords.encode()).hexdigest()
    evidence = {"counts": counts, "passwordFingerprint": fingerprint,
                "schemas": list(SCHEMAS), "version": "1"}
    if args.compare:
        assert json.loads(args.compare.read_text()) == evidence, "Data or existing passwords changed on restart"
        print("PASS: restart retained exact counts, migration versions and existing password hashes")
    if args.snapshot:
        args.snapshot.write_text(json.dumps(evidence, indent=2) + "\n")
    print("PASS: minimum seed counts", json.dumps(counts, sort_keys=True))


if __name__ == "__main__":
    main()
