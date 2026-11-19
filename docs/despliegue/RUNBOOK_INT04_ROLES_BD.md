# Runbook INT-04 — Identidad de migración y de ejecución (desarrollo y pruebas aisladas)

Alcance: **solo desarrollo local y pruebas aisladas**. No se aplica a ninguna base municipal ni de producción; la adopción en el GAMW requiere decisión del PO y del responsable de infraestructura (ver `INT04_AUDITORIA_PERMISOS_DB.md`, §3–§4).

## Modelo

| Identidad | Desarrollo | Pruebas aisladas | Privilegios |
|---|---|---|---|
| Migración | `gamw_app_dev` | `gamw_app_dev` (clúster efímero) | propietaria del esquema y de todos los objetos; solo la usa Flyway |
| Ejecución | `gamw_app_ejecucion_dev` (opcional) | `gamw_app_ejecucion_it` | no propietaria, sin atributos privilegiados; `SELECT, INSERT` en tablas de negocio; `UPDATE` solo donde el backend actualiza o bloquea (`FOR UPDATE`/`FOR SHARE`); **sin** `DELETE`, `TRUNCATE`, `TRIGGER`, `REFERENCES`, DDL ni acceso a `flyway_schema_history` |

Las concesiones las aplica el callback `backend/src/main/resources/db/privilegios/afterMigrate.sql` con la identidad de migración, tras cada `migrate`. Falla (no omite) si el rol no existe, es privilegiado, coincide con el migrador, es miembro de él o falta una tabla esperada.

## Desarrollo local (opt-in; sin cambios por defecto)

1. En `backend/.env` (no versionado) defina `APP_RUNTIME_DB_PASSWORD` **antes** de crear el volumen; `20-gamw-app-role.sh` creará `gamw_app_ejecucion_dev`. Con un volumen existente, créelo manualmente con el mismo `ALTER ROLE` del script.
2. Arranque el backend con:
   - `DB_USERNAME=gamw_app_ejecucion_dev`, `DB_PASSWORD=<APP_RUNTIME_DB_PASSWORD>` (ejecución)
   - `SPRING_FLYWAY_USER=gamw_app_dev`, `SPRING_FLYWAY_PASSWORD=<APP_DB_PASSWORD>` (migración)
   - `GAMW_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/privilegios`, `GAMW_ROL_EJECUCION=gamw_app_ejecucion_dev`
3. Sin estas variables el comportamiento es el anterior (una sola identidad propietaria).

## Verificación

- Automática: `MinimoPrivilegioEjecucionIT` (identidad real, sin propiedad ni privilegios destructivos, Flyway con la identidad de migración, rechazo por permiso `42501` de `DELETE`, `TRUNCATE`, `DISABLE TRIGGER`, `DROP TRIGGER`, `DROP CONSTRAINT`, `CREATE OR REPLACE FUNCTION` y `CREATE TABLE`) y las clases `*EjecucionNoPropietariaIT` (flujos existentes como rol no propietario).
- Manual de solo lectura: consultas de `INT04_AUDITORIA_PERMISOS_DB.md` §4.

## Reversión (desarrollo)

Quitar las variables del paso 2 devuelve el modo de identidad única. Las concesiones al rol de ejecución no afectan al migrador; pueden retirarse con `REVOKE ALL ON ALL TABLES IN SCHEMA public FROM gamw_app_ejecucion_dev` ejecutado por el migrador. Ninguna operación modifica datos.

## Pendiente (fuera de alcance)

Roles reales del GAMW, transferencia de propiedad en una instalación existente, gestión de secretos y privilegios por defecto para migraciones futuras en producción.
