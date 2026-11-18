# QA técnica GAMW — campaña de endurecimiento (18-nov-2026)

**Alcance:** verificación técnica sobre PostgreSQL/PostGIS efímero y datos sintéticos. **No** es aceptación institucional.
**Base:** `0b94795`. Fuente de hallazgos: `INFORME_AUDITORIA_GENERAL_GAMW_20261118.md` (validados contra el código antes de modificar).

## 1. Hallazgos confirmados y correcciones

| Id | Hallazgo (validado en código) | Corrección | CU / criterio |
|---|---|---|---|
| H-05 | `RedactorSensible` solo ocultaba por patrones; el `motivo` libre y campos desconocidos podían salir. | CU-31 usa **lista permitida con fallo cerrado** (`ProyeccionAuditoria`): solo campos conocidos con forma válida; texto libre (motivo) nunca se devuelve (solo existencia y longitud); lo omitido se cuenta en `camposOmitidos`. `RedactorSensible` queda `@Deprecated` sin uso. Prueba con credenciales y documentos no reconocibles por patrón, anidados y formas inválidas. | CU-31 CA-06 (reforzado) |
| H-04 | CI no ejecutaba `*IT` contra PostgreSQL/PostGIS; el arnés solo funcionaba en Windows 16.6. | Arnés portable (`GAMW_PG_BIN`, `GAMW_PG_VERSION_PREFIX`; por defecto Windows/16.6 intacto) y job `backend-integration` en `verify.yml` (apt PostgreSQL 16 + PostGIS; clúster efímero propio de la prueba). **No ejecutado en GitHub Actions.** | transversal |
| Nuevo | 12 pruebas web unitarias fallaban: usaban el modo local sin el opt-in `VITE_GAMW_DESARROLLO_LOCAL` exigido desde H-02 (CI rojo en `npm test`). | Las pruebas declaran explícitamente el opt-in (`vi.stubEnv`). | CU-04/05/06/09/11 (contratos web) |
| Nuevo | `IdentidadPostgresIT` fijaba 9 migraciones (obsoleta; fallaba con V19). | Cuenta las migraciones publicadas; verifica instalación limpia y actualización poblada V7→V19 y la historia Flyway previa. | Migraciones V1–V19 |
| Nuevo | `RegistroPlanificacionWebIT` (navegador) agotaba tiempo: el arnés Vite no activaba el opt-in local exigido por el cliente. | `NavegadorWebAislado` define `VITE_GAMW_DESARROLLO_LOCAL=true` (solo arnés de prueba). | CU-04/05/07/11/12/24 (UI) |
| H-06 | Nombres vigentes vs identidades históricas. | Sin cambio de datos: ya rotulado en UI; añadidas pruebas de interfaz (rótulos, sin acceso vs sin registros, volver conserva filtros). Snapshots requieren aprobación institucional. | CU-29 |

## 2. Pruebas ejecutadas (esta sesión, Windows, PostgreSQL 16.6 + PostGIS 3.6.2 efímeros)

- **Backend (surefire, `*Tests` + `*IT`, excluidos los 2 de navegador):** 199 ejecutadas, 0 fallos, 0 errores, **1 omitida** (`FlujoMovilIT`, requiere Android: `gamw.mobile.e2e`). Incluye Flyway V1–V19 limpio y actualización poblada V7→V19.
- **Navegador real (Playwright/Vite) :** `RegistroPlanificacionWebIT` 2/2 y `AutenticacionWebIT` 2/2 (tras corregir el arnés).
- **Web:** `npm test` 64/64 (18 archivos; +5 nuevos de bitácora/historial), `npm run build` (tsc + vite) correcto.
- Históricas (no re-ejecutadas aquí): QA física Android CA-4A-12 (VERIFIED previa), CA-4A-21 PARTIAL.

## 3. Bloqueado / no ejecutado

- **Flutter (`flutter analyze`, `flutter test`) y Android/ADB:** el guard del proyecto reserva esos comandos al PO; no se evadió. Sin verificación de cola offline durable, reinicio de app, GPS en segundo plano ni batería.
- **Job `backend-integration` de GitHub Actions:** escrito, no ejecutado (sin acceso a Actions desde esta sesión).
- QA visual de mapa (huecos GPS en navegador) sin prueba automatizada nueva: solo cobertura de contrato HTTP y de interfaz de tablas.

## 4. Defectos/brechas restantes

- Firma debug en release Android (H-08), CU-08/CU-25/CU-30 sin vertical (H-09), matriz RF completa (H-10), fuentes parciales de bitácora (H-11), rama sin merge a `main` (H-12).
- Actualización poblada **V18→V19** con datos propios de V18 no demostrada (solo V7→V19 poblada).

## 5. Dependencias institucionales (no resolubles técnicamente)

- **D1**: permiso de consulta de bitácora (`BITACORA_CONSULTAR` no existe en el catálogo; CU-31 permanece denegado), acceso histórico A1/A2/A3, A4 tras fin de periodo.
- **PENDING-05**: catálogo oficial de resultados de fiscalización, rectificación, evidencias mínimas.
- Política de retención/eventos mínimos de bitácora; snapshots nominales históricos; reglas de CU-08/25/30.

## 6. Distinción

Todo lo anterior es **verificación técnica** con datos sintéticos. Ningún CU queda **aceptado institucionalmente**.
