#!/usr/bin/env bash
set -eo pipefail

# Runs after the image's 10_postgis.sh on a fresh development data directory.
# Mount only this file so the image's PostGIS initialization remains available.
: "${APP_DB_PASSWORD:?APP_DB_PASSWORD must be configured}"

psql -X --set=ON_ERROR_STOP=1 --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" <<'SQL'
\getenv app_password APP_DB_PASSWORD
\getenv app_database POSTGRES_DB

SELECT 'CREATE ROLE gamw_app_dev'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'gamw_app_dev')
\gexec

ALTER ROLE gamw_app_dev WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    NOREPLICATION NOBYPASSRLS PASSWORD :'app_password';
ALTER DATABASE :"app_database" OWNER TO gamw_app_dev;
ALTER SCHEMA public OWNER TO gamw_app_dev;
SQL

# INT-04 (solo desarrollo): gamw_app_dev queda como identidad de MIGRACIÓN propietaria. Si se define APP_RUNTIME_DB_PASSWORD
# se crea además la identidad de EJECUCIÓN no propietaria gamw_app_ejecucion_dev; sus concesiones las aplica Flyway con
# GAMW_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/privilegios y GAMW_ROL_EJECUCION=gamw_app_ejecucion_dev.
if [ -n "${APP_RUNTIME_DB_PASSWORD:-}" ]; then
psql -X --set=ON_ERROR_STOP=1 --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" <<'SQL'
\getenv runtime_password APP_RUNTIME_DB_PASSWORD
SELECT 'CREATE ROLE gamw_app_ejecucion_dev'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'gamw_app_ejecucion_dev')
\gexec
ALTER ROLE gamw_app_ejecucion_dev WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE
    NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD :'runtime_password';
SQL
else
  echo "INT-04: APP_RUNTIME_DB_PASSWORD no definida; no se crea gamw_app_ejecucion_dev (modo compatible: una sola identidad)."
fi
