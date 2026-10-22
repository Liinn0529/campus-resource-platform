#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -e .env ]; then
    printf '%s\n' '.env already exists; left unchanged.'
    exit 0
fi
umask 077
# Refuse overwriting an environment created concurrently.
set -C
password=$(openssl rand -hex 24)
printf 'POSTGRES_PASSWORD=%s\nWEB_PORT=18080\n' "$password" > .env
printf '%s\n' 'Created private .env with a generated local database password.'
