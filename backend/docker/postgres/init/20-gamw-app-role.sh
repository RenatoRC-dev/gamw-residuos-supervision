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
