# Evidencia — Phase 0

Fecha: 2026-11-08 (America/La_Paz).

Estado: **PHASE 0 — ENGINEERING FOUNDATION** implementada y verificada localmente.
No se inició Slice 1 ni se implementaron CU/RF de negocio.

## Alcance e integridad

Se releen los 13 documentos de contexto antes de modificar archivos. Sus hashes
SHA-256 previos y posteriores coinciden; snapshot en `context-sha256.json`.
CONFIRMED, PRELIMINARY, PROPOSAL, PENDING y OUT OF SCOPE mantienen su significado.
No se decide la aprobación de rutas, permisos finales, categorías de fiscalización,
frecuencia GPS ni política de datos ciudadanos.

Los archivos nuevos versionables se enumeran en `phase-0-files.md`. No se borraron
archivos del usuario. Los artefactos de compilación, logs y clusters de prueba
quedan en ubicaciones ignoradas; no hay `.env` real ni credenciales versionables.

## Versiones utilizadas

| Componente | Versión |
|---|---|
| Java / javac | 21.0.3 |
| Spring Boot | 4.1.2 |
| Maven / Wrapper | 3.9.16 / 3.3.4 (only-script) |
| Node / npm | 22.18.0 / 10.9.3 |
| React / React DOM | 19.3.1 |
| TypeScript / Vite | 5.9.3 / 8.3.6 |
| React Router DOM | 7.18.5 |
| Vitest / jsdom | 5.1.0 / 26.1.0 |
| Flutter / Dart | 3.35.4 stable / 3.9.2 |
| PostgreSQL cliente y motor de integración | 16.6 |
| Flyway | 12.4.0 |
| PostgreSQL JDBC | 42.7.14 |
| Hibernate ORM | 7.4.13.Final |
| Spring Security | 7.1.2 |

Versiones del backend adicionalmente verificadas dentro del JAR empaquetado.
Web fijada en manifiesto y package-lock; mobile en pubspec.lock y SDK local.

## Comandos y resultados

Comandos desde su componente, salvo scripts desde la raíz. Todos los checks
finales se ejecutaron secuencialmente; nunca dos operaciones Flutter a la vez.

| Comando | Resultado |
|---|---|
| `git init -b main` | Repositorio local creado |
| `flutter create --platforms=android,ios --org bo.gob.warnes.gamw --project-name gamw_mobile --no-pub --empty apps/mobile` | Shell nativo generado; luego personalizado |
| `backend/mvnw.cmd --version` | Maven 3.9.16 sobre Java 21.0.3 |
| `mvnw.cmd --batch-mode --no-transfer-progress test` | 5 pruebas; 0 fallos/errores/omitidas |
| `mvnw.cmd --batch-mode --no-transfer-progress package` | 5 pruebas y JAR ejecutable; éxito |
| `npm install` | Instalación inicial y package-lock; 0 vulnerabilidades informadas |
| `npm test` | 2 pruebas correctas |
| `npm run build` | Comprobación TypeScript y build Vite correctos |
| `flutter pub get` | Dependencias resueltas |
| `flutter analyze` | Sin incidencias |
| `flutter test` | 1 prueba correcta |
| `scripts/Test-Postgres.ps1` | PostgreSQL real aislado; verify con 5 pruebas + 1 integración correctas |
| `scripts/verify.ps1` | Repite secuencialmente backend test/package, web ci/test/build y mobile pub get/analyze/test; éxito |
| `git status`, `git remote -v`, `git ls-files --stage`, `git check-ignore` | Sin commits/remotos/archivos staged; Wrapper, locks, migración y ejemplos no ignorados |

Los logs locales detallados están ignorados en `.local/`: backend-test.log,
backend-package.log, web-install.log, web-test.log, web-build.log,
mobile-pub-get.log, mobile-analyze.log, mobile-test.log,
postgres-integration-final.log y verify-final.log. No constituyen archivos
versionables ni se presentan como evidencia de pruebas espaciales.

## Qué verifican las pruebas

- Contexto Spring: arranque del shell, explícitamente sin datasource en perfil smoke.
- HTTP/Security: GET público mínimo, acceso privado denegado y CSRF preservado.
- Bean Validation: ProblemDetail sin valor rechazado; fixture exclusivo de test,
  no incluido en el JAR de producción.
- Flyway/PostgreSQL: arranque completo con datasource/JPA/Flyway, aplicación de V1,
  validación y nueva ejecución sin migraciones pendientes. No usa H2.
- Web: router y disponibilidad del backend; representación de fallo de red.
- Mobile: widget principal sin permisos ni dependencias externas.

No se han verificado UI en navegador real, build nativo Android/iOS ni ejecución
de GitHub Actions. CI básica está configurada, pero no existe remoto donde ejecutarla.

## PostgreSQL y PostGIS

El cliente es PostgreSQL 16.6. `pg_isready -h localhost -p 5432 -t 3` devuelve
`accepting connections`; el servicio `postgresql-x64-16` está en ejecución.
No hay DB_* configuradas. Una consulta read-only con `psql -X -w` al servicio
existente no pudo autenticar: no se suministró contraseña. No se modificó ese
servicio ni ninguna base suya y no se buscaron credenciales privadas.

Para verificar migraciones se usó un cluster nuevo bajo `.local/postgres-phase0/`,
con los binarios PostgreSQL instalados, puerto loopback libre y contraseña aleatoria
solo de prueba. No se creó una base en el servicio existente. El cluster aislado
ejecutó V1 sobre su base predeterminada; Flyway solo añadió su tabla técnica de
historial. Al terminar quedó detenido y se eliminó su archivo de contraseña generado.
Los datos/logs de prueba quedan ignorados y no son la BD operativa del proyecto.

No se encontraron archivos `*postgis*.control` en el directorio de extensiones de
la instalación PostgreSQL 16. No puede certificarse la extensión en otras instalaciones
o bases sin acceso. No se instaló ni simuló PostGIS.

## Ajustes realizados durante la verificación

- Maven Central no ofrecía el checksum `.sha256` consultado para la distribución:
  se verificó el ZIP contra el `.sha512` publicado y se fijó su SHA-256 calculado
  en las propiedades del Wrapper.
- Se corrigió la espera del script PostgreSQL: `Start-Process -Wait` esperaba
  también al servidor descendiente; se usa `WaitForExit` del proceso pg_ctl.
  El cluster de ese intento se detuvo de forma controlada antes de repetir.
- Se corrigió el manejo de stderr nativo en Windows PowerShell 5: los avisos
  no sustituyen al código de salida como criterio de éxito. La limpieza y
  restauración del entorno se ejecutan en finally.
- No hubo conflictos de dependencias que requirieran cambiar la baseline o usar
  flags de fuerza. jsdom 26.1.0 evita exigir un Node posterior al disponible.

Avisos no bloqueantes: dependencia transitiva whatwg-encoding obsoleta en jsdom;
auto-attachment de Mockito/Byte Buddy con Java 21; paquetes Flutter más recientes
fuera de restricciones del SDK local. No se actualizó el SDK ni se forzaron versiones.

## Git y siguientes requisitos

Rama `main`, sin HEAD/commits, sin staged, remotos ni push. Los archivos de
contexto existentes siguen sin seguimiento junto con los nuevos archivos: no
había un commit previo. Wrapper y locks quedan listos para el futuro primer commit.

Phase 0 está verificada. Para ejecutar la POC de Slice 1 faltan una BD dedicada
configurada y PostGIS disponible/habilitado, además de finalizar CRS/SRID y origen
de capas durante ese slice. No se inicia ese trabajo en esta fase. No se implementan
mapas, CRUD, rutas, jornadas, GPS, fiscalización, reclamos, reportes, IA, IoT,
sanciones automáticas, Docker ni microservicios.
