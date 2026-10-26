#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
umask 077
temporary=$(mktemp .env.tmp.XXXXXX)
trap 'rm -f "$temporary" "$temporary.next"' EXIT HUP INT TERM
if [ -f .env ]; then
    cat .env > "$temporary"
    if [ -s "$temporary" ] && [ -n "$(tail -c 1 "$temporary")" ]; then
        printf '\n' >> "$temporary"
    fi
else
    printf 'WEB_PORT=18080\n' > "$temporary"
fi
for name in POSTGRES_PASSWORD \
    PLATFORM_DB_PASSWORD PLATFORM_FLYWAY_PASSWORD \
    RESERVATION_DB_PASSWORD RESERVATION_FLYWAY_PASSWORD \
    CLASSROOM_DB_PASSWORD CLASSROOM_FLYWAY_PASSWORD \
    MAINTENANCE_DB_PASSWORD MAINTENANCE_FLYWAY_PASSWORD DEV_SEED_PASSWORD \
    PLATFORM_JWT_SECRET PLATFORM_RESERVATION_TOKEN PLATFORM_CLASSROOM_TOKEN PLATFORM_MAINTENANCE_TOKEN M1_CLASSROOM_TOKEN; do
    value=$(awk -v name="$name" '
        $0 ~ "^(export[[:space:]]+)?" name "=" {
            value=$0; sub("^(export[[:space:]]+)?" name "=", "", value)
        }
        END { gsub(/^[[:space:]]+|[[:space:]]+$/, "", value); print value }
    ' "$temporary")
    case "$value" in
        ''|'""'|"''")
            awk -v name="$name" '$0 !~ "^(export[[:space:]]+)?" name "="' "$temporary" > "$temporary.next"
            mv "$temporary.next" "$temporary"
            printf '%s=%s\n' "$name" "$(openssl rand -hex 24)" >> "$temporary"
            ;;
    esac
done
if ! awk '/^(export[[:space:]]+)?CAMPUS_BUSINESS_ZONE=./ { found=1 } END { exit !found }' "$temporary"; then
    awk '$0 !~ /^(export[[:space:]]+)?CAMPUS_BUSINESS_ZONE=/' "$temporary" > "$temporary.next"
    mv "$temporary.next" "$temporary"
    printf 'CAMPUS_BUSINESS_ZONE=Asia/Shanghai\n' >> "$temporary"
fi
chmod 600 "$temporary"
mv "$temporary" .env
printf '%s\n' 'Private .env ready; existing nonempty credentials preserved, missing secrets generated.'
