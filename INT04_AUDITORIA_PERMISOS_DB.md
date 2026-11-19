# INT-04 — Auditoría de permisos de base de datos (solo lectura)

**Fecha:** 19/11/2026 · **Base:** `7e92aaa` (rama `feat/gamw-checkpoint-fiscalizacion-20261109`) · **Modo:** revisión estática; no se conectó a ninguna base, no se leyeron secretos (`.env`, `backend/dev/` excluidos) ni se ejecutó SQL mutante. No se modificó código, migraciones, workflows ni Git.

**Semántica:** CONFIRMADO = demostrado en archivos del repositorio · RIESGO = consecuencia técnica plausible · PENDIENTE = requiere verificación en la instalación real del GAMW. **La configuración de desarrollo y de pruebas NO representa producción**; la identidad y privilegios de producción son desconocidos.

## 1. Identidades de base de datos encontradas

| Identidad | Origen (evidencia) | Propósito | Privilegios efectivos según evidencia |
|---|---|---|---|
| `${DB_USERNAME}` | `backend/src/main/resources/application.yml:8-11` | conexión de **ejecución** de Spring Boot **y** de Flyway (no hay `spring.flyway.user/url/password`) | desconocidos en producción (PENDIENTE) |
| `postgres` | `backend/compose.dev.yml:8` (imagen `postgis/postgis:16-3.7`) | superusuario de inicialización **solo desarrollo**; crea PostGIS (`10_postgis.sh` de la imagen) | superusuario |
| `gamw_app_dev` | `backend/docker/postgres/init/20-gamw-app-role.sh:12-19` | rol de aplicación **de desarrollo** | `LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS`; **dueño de la base y del esquema `public`** |
| `gamw_bootstrap_it` | `backend/src/test/java/.../shared/config/PostgresV8Aislado.java:70-79` | superusuario del clúster **efímero de pruebas** (initdb); crea PostGIS | superusuario, solo en pruebas |
| `gamw_app_dev` (pruebas) | `PostgresV8Aislado.java:80-93` | rol de pruebas: `CREATE ROLE ... NOSUPERUSER`, dueño de las bases `gamw_v8_*` y del esquema `public` | dueño del esquema; ejecuta Flyway, por tanto **dueño de todas las tablas, funciones y triggers** |

No se encontraron `GRANT`, `REVOKE`, `ALTER DEFAULT PRIVILEGES`, `SECURITY DEFINER`, `OWNER TO` ni manipulación de triggers en V1–V21 (búsqueda en `backend/src/main/resources/db/migration`). V2 ejecuta `CREATE EXTENSION IF NOT EXISTS postgis` (requiere privilegio de creación de extensiones si no existe).

## 2. Hallazgos

### P-01 — Ejecución y migración comparten credenciales — CONFIRMADO (código)
`application.yml` define un único `spring.datasource` y Flyway lo reutiliza; `spring.flyway.clean-disabled: true` y `validate-on-migrate: true` (bien), pero Flyway corre **al arrancar la aplicación con la misma identidad**. Consecuencia: la identidad de ejecución necesita DDL (crear tablas, triggers, funciones, índices, restricciones) y, en la práctica, ser **propietaria** del esquema/tablas.

### P-02 — El rol de aplicación es propietario del esquema (desarrollo y pruebas) — CONFIRMADO (configuración dev/test); producción PENDIENTE
`20-gamw-app-role.sh:18-19` y `PostgresV8Aislado.java:82,93` asignan la base y `public` a `gamw_app_dev`, que luego crea todos los objetos vía Flyway. Un propietario puede, **sin superusuario**: `ALTER TABLE ... DISABLE TRIGGER`, `DROP TRIGGER`, `ALTER TABLE ... DROP CONSTRAINT`, `TRUNCATE`, `DROP TABLE`, `CREATE OR REPLACE FUNCTION` sobre las funciones protectoras. **No** puede `SET session_replication_role` (requiere superusuario o privilegio `SET` concedido).

### P-03 — V20/V21 se pueden eludir con los permisos ordinarios del propietario — CONFIRMADO en dev/test; RIESGO en producción
Las protecciones de V9, V13–V21 son triggers `BEFORE UPDATE/DELETE/TRUNCATE` y claves foráneas. Contra un rol **no propietario** sin `TRIGGER`/DDL son efectivas (un `DELETE`/`TRUNCATE` concedido sigue bloqueado por el trigger). Contra el **propietario** (situación actual de desarrollo y pruebas, y de producción si se replica el patrón) son solo una barrera de errores, no de seguridad: basta `DISABLE TRIGGER` + `DELETE`. Las pruebas actuales (`IntegridadHistoricaIT`, `EventosEjecucionIT`) se ejecutan como propietario y **no** demuestran resistencia a un rol de ejecución hostil o comprometido.

### P-04 — Sin privilegios por defecto ni concesiones explícitas — CONFIRMADO
No hay `ALTER DEFAULT PRIVILEGES` ni `GRANT` en migraciones. Separar roles exige conceder permisos a cada tabla existente y a las futuras (V22+); si no, la ejecución fallará con `permission denied` al primer uso de una tabla nueva. **Impacto en compatibilidad, no en seguridad.**

### P-05 — Necesidades reales del backend (mínimo privilegio) — CONFIRMADO (código)
- **SELECT/INSERT:** todas las tablas de negocio (repositorios `*Postgres.java` con `INSERT INTO` en identidad, registro operativo, rutas, jornadas, ejecución, GPS, fiscalización, correctivas, reclamos, referencias).
- **UPDATE** (únicas tablas actualizadas): `vehiculo`, `vinculo_vehiculo_empresa`, `persona_natural`, `empresa_operadora`, `periodo_servicio`, `vinculo_persona_empresa`, `ruta`, `ruta_version`, `referencia_espacial_version`, `usuario`, `facultad_delegacion`, `concesion_periodo_operador`, `concesion_permiso`, `jornada`, `asignacion_jornada` (grep `UPDATE \w+ SET` en `backend/src/main/java`).
- **`SELECT ... FOR UPDATE`** requiere privilegio `UPDATE` (al menos de una columna) en: `reclamo_institucional`, `punto_critico`, `vehiculo`, `persona_natural`, `empresa_operadora`, `ruta`, `jornada`, `periodo_servicio`, `referencia_espacial`, `usuario`. Además el trigger de V21 hace `SELECT ... FROM ruta FOR UPDATE` como **invocador**: el rol de ejecución necesita `UPDATE` (columna) en `ruta` para insertar/cerrar versiones.
- **DELETE / TRUNCATE:** **ninguno** (no hay `DELETE FROM` ni `TRUNCATE` en `backend/src/main/java`).
- **Secuencias:** ninguna (identificadores `uuid`; sin `serial`/`identity`).
- **Funciones:** solo PostGIS (`ST_*`, `EXECUTE` a `PUBLIC` por defecto) y las funciones de trigger (se ejecutan como invocador; no requieren `GRANT` adicional para dispararse).
- **JPA:** `ddl-auto: validate` con starter JPA pero **sin entidades** (`@Entity` ausente): no crea ni altera esquema.

### P-06 — PUBLIC y PostGIS — RIESGO / PENDIENTE
En PostgreSQL 15+ `PUBLIC` ya no tiene `CREATE` en `public`, pero el esquema pertenece al rol de aplicación. Las funciones PostGIS pertenecen al superusuario de creación y son ejecutables por `PUBLIC` (necesario). No se encontró `REVOKE ALL ... FROM PUBLIC` sobre tablas/funciones propias: por defecto `PUBLIC` no tiene privilegios de tabla, pero sí `EXECUTE` sobre las funciones de trigger (inocuo: no son invocables directamente como función normal con efecto). Verificar en instalación (consultas §4).

### P-07 — Las pruebas no verifican el modelo de privilegios — CONFIRMADO
Todo el arnés (`PostgresV8Aislado`) migra y ejecuta con el propietario. No existe prueba que arranque la aplicación con un rol no propietario. Riesgo de regresión silenciosa al separar roles.

## 3. Modelo mínimo seguro propuesto (NO aplicado — decisión de despliegue)

| Rol | Atributos | Propiedad | Uso |
|---|---|---|---|
| `gamw_migrador` | `LOGIN NOSUPERUSER NOCREATEROLE NOCREATEDB` | dueño del esquema `gamw` (o `public`), tablas, funciones, triggers y `flyway_schema_history` | **solo** Flyway, como paso de despliegue o con `spring.flyway.user/password` separados; credenciales fuera del runtime |
| `gamw_app` | `LOGIN NOSUPERUSER NOINHERIT` | **ninguna** | Spring Boot: `USAGE` en esquema; `SELECT, INSERT` en tablas de negocio; `UPDATE` solo en las 15 tablas de P-05 (idealmente por columna) y `UPDATE` de columna para los `FOR UPDATE`; **sin** `DELETE`, `TRUNCATE`, `TRIGGER`, `REFERENCES`, DDL ni `CREATE` en esquema |
| (opcional) `gamw_lectura` | `LOGIN NOSUPERUSER` | ninguna | auditoría/reportes: `SELECT` |

Complementos: `REVOKE CREATE ON SCHEMA ... FROM PUBLIC`; `ALTER DEFAULT PRIVILEGES FOR ROLE gamw_migrador IN SCHEMA ... GRANT SELECT, INSERT ON TABLES TO gamw_app` (los `UPDATE` se conceden explícitamente en la migración que crea la tabla); PostGIS creado por un administrador antes de V2.

**Orden de transición sugerido:** (1) ejecutar consultas de §4 en la instalación objetivo y registrar resultados; (2) crear roles (administrador); (3) transferir propiedad a `gamw_migrador` (`REASSIGN OWNED BY` o `ALTER ... OWNER`) en ventana controlada; (4) concesiones a `gamw_app`; (5) separar credenciales de Flyway en configuración (`spring.flyway.user/password` o Flyway como paso previo con `spring.flyway.enabled=false` en runtime); (6) arrancar y ejecutar pruebas de humo; (7) revocar al antiguo rol la propiedad/privilegios sobrantes.
**Compatibilidad:** Flyway al arranque necesita credenciales de migrador; con roles separados, el runtime debe poder leer `flyway_schema_history` solo si se mantiene `validate`. Cada migración futura debe incluir sus `GRANT` (o depender de privilegios por defecto).
**Rollback:** conservar las credenciales y propiedad originales hasta validar; la reversión es `REASSIGN OWNED BY gamw_migrador TO <rol anterior>` y restaurar la configuración previa; ninguna operación del plan modifica datos.

## 4. Consultas de verificación (SOLO LECTURA, para ejecución autorizada con la cuenta real de ejecución)

```sql
SELECT current_user, session_user, r.rolsuper, r.rolcreaterole, r.rolcreatedb, r.rolbypassrls
FROM pg_roles r WHERE r.rolname = current_user;
SELECT n.nspname, pg_get_userbyid(n.nspowner) AS dueno, has_schema_privilege(current_user, n.oid, 'CREATE') AS puede_crear
FROM pg_namespace n WHERE n.nspname = 'public';
SELECT pg_get_userbyid(d.datdba) AS dueno_base FROM pg_database d WHERE d.datname = current_database();
SELECT c.relname, pg_get_userbyid(c.relowner) AS dueno,
       has_table_privilege(current_user, c.oid, 'DELETE')   AS borrar,
       has_table_privilege(current_user, c.oid, 'TRUNCATE') AS vaciar,
       has_table_privilege(current_user, c.oid, 'TRIGGER')  AS triggers,
       pg_has_role(current_user, c.relowner, 'MEMBER')      AS es_dueno_o_miembro
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'r' ORDER BY c.relname;
SELECT tgrelid::regclass AS tabla, tgname, tgenabled FROM pg_trigger WHERE NOT tgisinternal AND tgenabled <> 'O';
SELECT p.proname, pg_get_userbyid(p.proowner) AS dueno, p.prosecdef AS security_definer
FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public' AND p.prorettype = 'trigger'::regtype;
SELECT defaclrole::regrole, defaclobjtype, defaclacl FROM pg_default_acl;
SELECT grantee, table_name, privilege_type FROM information_schema.role_table_grants
WHERE table_schema = 'public' AND grantee = 'PUBLIC';
SELECT version, installed_rank, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
```

## 5. Trazabilidad afectada

- **RF/CU:** RF-07/08 (planificación, CU-11/12/24), RF-09/10/11 (ejecución y GPS, CU-13/15/16), RF-13/14/15 (revisión GPS, CU-17/18/19), fiscalización y correctivas (CU-20/21/22), reclamos (CU-26/27/28), historial (CU-29), identidad y concesiones (CU-01, D1) y bitácora (CU-31): todos dependen de que la evidencia inmutable no pueda alterarse.
- **Migraciones:** V2 (PostGIS), V6, V7, V9, V10, V13–V21 (protecciones); toda migración futura (concesiones).
- **Configuración:** `application.yml` (datasource/Flyway), `compose.dev.yml`, `20-gamw-app-role.sh`, `PostgresV8Aislado.java`.
- **Pruebas de regresión a añadir en un corte futuro:** prueba de integración que cree un rol no propietario con las concesiones de §3, arranque el backend con él y verifique: flujos principales OK; `DELETE`/`TRUNCATE`/`DISABLE TRIGGER`/`DROP CONSTRAINT` rechazados por **permiso**; Flyway solo con el migrador.

## 6. Tabla priorizada de hallazgos

| Prioridad | ID | Hallazgo | Clasificación | Evidencia | Riesgo |
|---|---|---|---|---|---|
| 1 | P-03 | V20/V21 eludibles por el propietario (DISABLE TRIGGER, DROP CONSTRAINT) | CONFIRMADO dev/test · RIESGO prod | migraciones V9, V13–V21; `PostgresV8Aislado.java:80-93`; `20-gamw-app-role.sh:18-19` | **Alto** si producción replica el patrón |
| 2 | P-01 | Flyway y runtime con las mismas credenciales | CONFIRMADO | `application.yml:8-19` | **Alto** (obliga a un runtime con DDL) |
| 3 | P-02 | Rol de aplicación dueño de base/esquema | CONFIRMADO dev/test · PENDIENTE prod | `20-gamw-app-role.sh`, `PostgresV8Aislado.java` | Alto/condicionado |
| 4 | P-07 | Sin pruebas con rol no propietario | CONFIRMADO | arnés de pruebas | Medio (regresión al separar) |
| 5 | P-04 | Sin GRANT ni privilegios por defecto | CONFIRMADO | V1–V21 | Medio (compatibilidad) |
| 6 | P-06 | PUBLIC/PostGIS y funciones de trigger | RIESGO/PENDIENTE | V2; catálogo real no consultado | Bajo |
| 7 | P-05 | Mínimos reales: sin DELETE/TRUNCATE/secuencias; UPDATE en 15 tablas; FOR UPDATE en 10 | CONFIRMADO | `backend/src/main/java/**/persistencia`, V21 | Base para el modelo |

**Evaluación global:** en el código **no** hay borrado ni DDL desde la aplicación y la evidencia está protegida frente a un rol sin propiedad; pero el diseño actual (credencial única + propiedad del esquema) convierte esas protecciones en barreras eludibles por la propia identidad de ejecución. **No se infiere que producción esté así**: es PENDIENTE de verificación con §4.

**Siguiente corte recomendado (INT-04-IMPL, con aprobación del PO y del responsable de infraestructura):** (a) separar credenciales de Flyway en configuración sin cambiar comportamiento por defecto; (b) migración aditiva V22 con `GRANT`/`REVOKE` parametrizados por nombre de rol solo si el rol existe (sin crear roles); (c) prueba de integración con rol no propietario (§5); (d) runbook de transición y rollback (§3). Ninguna acción sobre bases reales sin autorización.
