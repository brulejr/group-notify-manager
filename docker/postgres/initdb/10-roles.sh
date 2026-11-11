#!/usr/bin/env bash
# Runs once, when the PostgreSQL container starts on an empty data volume, as the
# instance's superuser. Creates the application's own role and schema. The tables
# themselves are created by the application's migrations.
#
# The application owns its schema rather than running as the superuser, so the
# database can later move to a shared instance without granting it more than its own
# tables.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_user="$POSTGRES_APP_USERNAME" \
  -v app_password="$POSTGRES_APP_PASSWORD" \
  -v schema=group_notify <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
CREATE SCHEMA :"schema" AUTHORIZATION :"app_user";
SQL
