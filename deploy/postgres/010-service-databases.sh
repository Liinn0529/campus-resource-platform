#!/bin/sh
set -eu

# The official PostgreSQL entrypoint runs this only when PGDATA is empty.
: "${PLATFORM_DB_PASSWORD:?PLATFORM_DB_PASSWORD is required}"
: "${PLATFORM_FLYWAY_PASSWORD:?PLATFORM_FLYWAY_PASSWORD is required}"
: "${RESERVATION_DB_PASSWORD:?RESERVATION_DB_PASSWORD is required}"
: "${RESERVATION_FLYWAY_PASSWORD:?RESERVATION_FLYWAY_PASSWORD is required}"
: "${CLASSROOM_DB_PASSWORD:?CLASSROOM_DB_PASSWORD is required}"
: "${CLASSROOM_FLYWAY_PASSWORD:?CLASSROOM_FLYWAY_PASSWORD is required}"
: "${MAINTENANCE_DB_PASSWORD:?MAINTENANCE_DB_PASSWORD is required}"
: "${MAINTENANCE_FLYWAY_PASSWORD:?MAINTENANCE_FLYWAY_PASSWORD is required}"

if [ "${POSTGRES_USER:-}" != postgres ] || [ "${POSTGRES_DB:-}" != campus_resource ]; then
    printf '%s\n' 'POSTGRES_USER must be postgres and POSTGRES_DB must be campus_resource' >&2
    exit 1
fi

# Read secrets inside psql instead of including them in arguments or shell SQL.
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q <<'SQL'
\getenv platform_app_password PLATFORM_DB_PASSWORD
\getenv platform_migrator_password PLATFORM_FLYWAY_PASSWORD
\getenv reservation_app_password RESERVATION_DB_PASSWORD
\getenv reservation_migrator_password RESERVATION_FLYWAY_PASSWORD
\getenv classroom_app_password CLASSROOM_DB_PASSWORD
\getenv classroom_migrator_password CLASSROOM_FLYWAY_PASSWORD
\getenv maintenance_app_password MAINTENANCE_DB_PASSWORD
\getenv maintenance_migrator_password MAINTENANCE_FLYWAY_PASSWORD

BEGIN;

SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT NOBYPASSRLS PASSWORD %L',
              'platform_app', :'platform_app_password') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT NOBYPASSRLS PASSWORD %L',
              'platform_migrator', :'platform_migrator_password') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT NOBYPASSRLS PASSWORD %L',
              'reservation_app', :'reservation_app_password') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT NOBYPASSRLS PASSWORD %L',
              'reservation_migrator', :'reservation_migrator_password') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT NOBYPASSRLS PASSWORD %L',
              'classroom_app', :'classroom_app_password') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT NOBYPASSRLS PASSWORD %L',
              'classroom_migrator', :'classroom_migrator_password') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT NOBYPASSRLS PASSWORD %L',
              'maintenance_app', :'maintenance_app_password') \gexec
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOINHERIT NOBYPASSRLS PASSWORD %L',
              'maintenance_migrator', :'maintenance_migrator_password') \gexec

REVOKE ALL ON DATABASE campus_resource FROM PUBLIC;
GRANT CONNECT ON DATABASE campus_resource TO
    platform_app, platform_migrator,
    reservation_app, reservation_migrator,
    classroom_app, classroom_migrator,
    maintenance_app, maintenance_migrator;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA platform AUTHORIZATION platform_migrator;
CREATE SCHEMA reservation AUTHORIZATION reservation_migrator;
CREATE SCHEMA classroom AUTHORIZATION classroom_migrator;
CREATE SCHEMA maintenance AUTHORIZATION maintenance_migrator;
REVOKE ALL ON SCHEMA platform, reservation, classroom, maintenance FROM PUBLIC;
GRANT USAGE ON SCHEMA platform TO platform_app;
GRANT USAGE ON SCHEMA reservation TO reservation_app;
GRANT USAGE ON SCHEMA classroom TO classroom_app;
GRANT USAGE ON SCHEMA maintenance TO maintenance_app;

ALTER DEFAULT PRIVILEGES FOR ROLE platform_migrator IN SCHEMA platform
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO platform_app;
ALTER DEFAULT PRIVILEGES FOR ROLE platform_migrator IN SCHEMA platform
    GRANT USAGE, SELECT ON SEQUENCES TO platform_app;
ALTER DEFAULT PRIVILEGES FOR ROLE reservation_migrator IN SCHEMA reservation
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO reservation_app;
ALTER DEFAULT PRIVILEGES FOR ROLE reservation_migrator IN SCHEMA reservation
    GRANT USAGE, SELECT ON SEQUENCES TO reservation_app;
ALTER DEFAULT PRIVILEGES FOR ROLE classroom_migrator IN SCHEMA classroom
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO classroom_app;
ALTER DEFAULT PRIVILEGES FOR ROLE classroom_migrator IN SCHEMA classroom
    GRANT USAGE, SELECT ON SEQUENCES TO classroom_app;
ALTER DEFAULT PRIVILEGES FOR ROLE maintenance_migrator IN SCHEMA maintenance
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO maintenance_app;
ALTER DEFAULT PRIVILEGES FOR ROLE maintenance_migrator IN SCHEMA maintenance
    GRANT USAGE, SELECT ON SEQUENCES TO maintenance_app;

ALTER ROLE platform_app IN DATABASE campus_resource SET search_path TO platform, pg_catalog;
ALTER ROLE platform_migrator IN DATABASE campus_resource SET search_path TO platform, pg_catalog;
ALTER ROLE reservation_app IN DATABASE campus_resource SET search_path TO reservation, pg_catalog;
ALTER ROLE reservation_migrator IN DATABASE campus_resource SET search_path TO reservation, pg_catalog;
ALTER ROLE classroom_app IN DATABASE campus_resource SET search_path TO classroom, pg_catalog;
ALTER ROLE classroom_migrator IN DATABASE campus_resource SET search_path TO classroom, pg_catalog;
ALTER ROLE maintenance_app IN DATABASE campus_resource SET search_path TO maintenance, pg_catalog;
ALTER ROLE maintenance_migrator IN DATABASE campus_resource SET search_path TO maintenance, pg_catalog;

COMMIT;
SQL
