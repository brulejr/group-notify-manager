#!/usr/bin/env bash
# Provisions local secrets into .env: the PostgreSQL passwords (the instance's superuser
# and the application's own role). It also points Docker Compose at the development
# compose file, so plain `docker compose` commands work from the repository root. Safe to
# re-run: each secret is generated only if it is missing.
set -euo pipefail

cd "$(dirname "$0")/.."

touch .env
chmod 600 .env

# Appends KEY=value to .env unless KEY is already set there.
ensure_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" .env; then
    echo "  $key already set, left alone"
  else
    echo "${key}=${value}" >> .env
    echo "  $key written"
  fi
}

# Letters and digits only, so a password needs no quoting in a URL, in .env or in SQL.
password() {
  openssl rand -base64 24 | tr -d '/+=' | head -c 24
}

echo "Docker Compose:"
# Compose reads COMPOSE_FILE from the .env in the directory it runs in, so
# `docker compose up -d` from the repository root starts the development services.
ensure_env COMPOSE_FILE docker/dev/compose.dev.yaml

echo "PostgreSQL:"
# The role is created from these on the container's first start with an empty volume;
# see docker/postgres/initdb. Changing them later needs ALTER ROLE too. 5433, so it runs
# beside iot-pipeline's PostgreSQL on 5432.
ensure_env POSTGRES_PORT 5433
ensure_env POSTGRES_DB group_notify
ensure_env POSTGRES_ADMIN_PASSWORD "$(password)"
ensure_env POSTGRES_APP_USERNAME group_notify
ensure_env POSTGRES_APP_PASSWORD "$(password)"

echo
echo "Secrets are in .env, which is gitignored. Keep it out of version control."
echo "SMTP and other plugin settings are added to .env by hand; see docs/configuration.md."
