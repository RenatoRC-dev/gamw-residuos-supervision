# Dictamen de auditoría estática del código GAMW

**Fecha del inventario:** 2026-11-09 13:08 (hora Bolivia)
**Alcance:** ZIP `AUDITORIA_GAMW_CODIGO_20261109-130810.zip`, con `01_ESTRUCTURA_GIT_TRAZABILIDAD.txt`, `02_BACKEND_CODIGO_INTEGRO.txt`, `03_WEB_MOVIL_CODIGO_INTEGRO.txt`. Contrastado con `GAMW_BASELINE_FUNCIONAL_CU01_CU31_v1_0.md` (versión aprobada por el PO).
**Método:** inspección estática de los listados fuente entregados; **sin ejecutar** Maven, Flutter, React, PostgreSQL, Git ni Android.
**Limitación:** el exportador sustituyó algunos fragmentos potencialmente sensibles por `[REDACTADO]` y el ZIP no conserva el diff completo ni artefactos binarios. No es posible demostrar compilación, reproducibilidad o resultado de pruebas con esta captura. El hash de fuente consignado corresponde al original, no al texto redactado.

## 1. Resultado ejecutivo

**Dictamen general:** arquitectura modular coherente con el producto y las ocho áreas del catálogo; buena conservación histórica y controles de autenticación para el núcleo móvil; casos de uso institucionales de fiscalización, reclamos y monitoreo aún sin flujo vertical completo. **No hay prueba de cierre integral de los 31 CU.** No se identificó en el código productivo exportado un mecanismo que sustituya automáticamente el juicio del fiscalizador por GPS. No se han ejecutado nuevas pruebas.

**Estado Git del inventario:** `main`, HEAD `fbcf850ea979369fc9fd2dc7e3a2916b43ca17d7`; 27 archivos modificados y 45 entradas no rastreadas. Incluyen la nueva baseline Markdown y `V9__posiciones_gps.sql`. El informe consigna sin diferencias en migraciones publicadas y `git diff --check` sin errores; esta auditoría no reprodujo esos comandos.

**Cobertura de la captura:** 146 archivos backend (Java/SQL/XML/configuración/pruebas) y 97 web/móvil (React/Flutter/Kotlin/configuración/pruebas), total 243. El informe de estructura es adicional.

## 2. Arquitectura de paquetes y correspondencia con CU

| Subsistema real | Responsabilidad funcional y CU | Dictamen |
|---|---|---|
| `modules/administracion/identidad` + React `administracion/identidad` | CU-01; fundamento de CU-02/03 | Conforme como separación de identidad y autorización pendiente |
| `modules/administracion/registrooperativo` + React `registro-operativo` | CU-04 a CU-07 | Conforme: contexto de empresa/periodo/personas/vehículos unificado, sin CRUD global genérico |
| `modules/planificacion/rutas` | CU-09 | Conforme: Ruta/RutaVersion y mapa MapLibre/Terra Draw |
| `modules/planificacion/jornadas` | CU-11, CU-12, núcleo CU-24 | Conforme; no introduce estados institucionales nuevos |
| `modules/ejecucion/jornadas` y `modules/ejecucion/gps` + Flutter | CU-13 | Conforme en separación de planificación vs ejecución, pero vertical incompleto |
| `modules/monitoreo/seguimiento` | CU-15/16, repositorio consulta PostGIS | Parcial; lectura interna sin API ni UI |
| Fiscalización, incidencias, reclamos, historial general | CU-14, 17–23, 25–31 salvo fundamentos compartidos | Funcionalidades no desarrolladas en la captura |

**Sobre carpetas por CU:** no hay carpetas `cu01_...` individuales, pero sí servicios de aplicación con responsabilidad identificable (`GestionarJornadas`, `GestionarPersonalEmpresa`, `ConsultarSeguimientoGps`, etc.). **No lo considero violación de arquitectura** si se conserva el mapeo CU→método/componente/prueba. Sería perjudicial duplicar entidades compartidas o crear capas vacías únicamente por numeración de CU.

**Dependencias entre módulos:** `planificacion/jornadas` importa contratos de dominio de registro y rutas; `ejecucion/jornadas` depende de planificación y registro; `ejecucion/gps` reutiliza autorización de ejecución; `monitoreo` consulta el tipo `PosicionGps` de ejecución. No hay ciclos evidentes en esas importaciones. Conviene formalizar contratos de lectura entre subsistemas al crecer, no refactorizar preventivamente todo.

**Observación de mantenibilidad:** `apps/mobile/lib/modules/ejecucion/jornadas/presentacion/pagina_operacion.dart` (~277 líneas) contiene inicialización de cliente/SQLite/sesión, recuperación GPS, ciclo de vida, registro/sincronización y UI. No es un error demostrado, pero concentra responsabilidades y puede volverse frágil con CU-14 y nuevos estados.

## 3. Hallazgos priorizados y evidencia exacta

### H-01 — ALTA — CU-13 no presenta datos operativos completos de la jornada
- **Evidencia:** `backend/.../modules/planificacion/jornadas/api/MisJornadasController.java:18-25` devuelve solo `jornadaId`, `asignacionJornadaId`, `inicioProgramado`, `finProgramado`, `vigente`, `vigenteDesde`, `vigenteHasta`. `apps/mobile/lib/modules/ejecucion/jornadas/presentacion/pagina_operacion.dart:241-248` presenta fecha y vigencia en la selección, sin ruta, vehículo o periodo.
- **Desviación de baseline:** CU-13 paso principal 3 exige revisar periodo, ruta, vehículo y contexto disponible.
- **Acción:** definir DTO de lectura autenticada con información operativa autorizada, reutilizando relaciones existentes; incluir prueba móvil/backend sin inventar función de conductor ni cambiar registros históricos.

### H-02 — ALTA — CU-15 no puede ofrecer actualizaciones cercanas al tiempo real
- **Evidencia:** `apps/mobile/lib/modules/ejecucion/jornadas/presentacion/pagina_operacion.dart:203-213,255` inicia `_cola.sincronizar()` desde botón; `apps/mobile/lib/modules/ejecucion/gps/aplicacion/sincronizar_posiciones_gps.dart:11` declara sincronización no automática. La consulta del backend solo refleja posiciones ya recibidas.
- **Impacto:** sin transmisión online automática, el mapa del fiscalizador podría permanecer desactualizado aunque el teléfono esté capturando GPS.
- **Acción:** diseñar política de envío cuando hay Internet y retención local offline, sin forzar cadencia institucional no aprobada; pruebas de pérdida y retorno de red, batería, identidad y FIN. SSE/polling es decisión posterior de actualización *navegador*, no solución del envío móvil.

### H-03 — ALTA — CU-15/CU-16 carecen de recorrido vertical autorizado
- **Evidencia:** `modules/monitoreo/seguimiento/aplicacion/ConsultarSeguimientoGps.java:21-41` y su repo PostGIS implementan lecturas; no existe `api/` dentro de ese módulo ni rutas React de monitoreo en `apps/web/src/app/router.tsx`; `shared/security/SecurityConfiguration.java:30-37` cierra el resto de APIs.
- **Impacto:** componentes correctos internamente, sin usuario A3/A4 capaz de ejecutar el CU.
- **Acción:** resolver D1 y exponer endpoints filtrados por jornada/empresa/periodo, pruebas de 401/403/no filtración; vista cartográfica y trayectoria histórica con RutaVersion.

### H-04 — MEDIA — Señal de ubicación simulada no preservada para CU-17
- **Evidencia:** `apps/mobile/android/app/src/main/kotlin/bo/gob/warnes/gamw/gamw_mobile/ServicioCapturaGps.kt:106-107` rechaza muestras marcadas `mock` y coloca únicamente estado textual en memoria.
- **Impacto:** no se conserva un antecedente consultable sobre una anomalía técnicamente observable. No prueba fraude ni que el vehículo no estuvo en la ruta.
- **Acción:** definir, con alcance CU-17, si se registra evento de integridad independiente y su semántica; no convertir coordenadas simuladas en prueba de recolección.

### H-05 — MEDIA — Consulta de trayectorias sin estrategia de volumen
- **Evidencia:** `RepositorioConsultaGpsPostgres.java:15-26` realiza `SELECT ... WHERE jornada_id=? ORDER BY capturado_en,captura_id,id` sin límite; `ConsultarSeguimientoGps.java:23` materializa todas las muestras y sus DTO.
- **Impacto:** jornadas con mucho GPS pueden incrementar memoria/latencia. No es un defecto reproducido en los volúmenes actuales.
- **Acción:** definir criterios de paginación/consulta por rango/segmentos al implementar el endpoint, preservando orden y coordenadas originales.

### H-06 — MEDIA — Mantenibilidad de Flutter y contratos entre subsistemas
- **Evidencia:** `PaginaOperacion` combina autenticación, GPS, cola, UI; `RegistrarPosicionesGps` depende directamente de `RegistrarEventosEjecucion`, `RepositorioEventosEjecucion` y `RepositorioJornadas`; `ConsultarSeguimientoGps` reutiliza `PosicionGps` de ejecución.
- **Impacto:** mayor acoplamiento futuro; **no es una ruptura demostrada** ni justifica reescritura completa.
- **Acción:** extraer orquestación de presentación solo cuando se amplíe CU-13/CU-14; conservar los contratos de dominio y sus pruebas.

### H-07 — BAJA — Documentación técnica interna desactualizada
- **Evidencia:** `GestionarPersonalEmpresa.java:13` aún comenta `CU-07 / RF-04 parcial`; `modules/package-info.java` solo cita CU-09 como ejemplo. Los RF antiguos se mencionan también en clases/migraciones históricas.
- **Impacto:** confusión del estado/documentación, no fallo del sistema. No alterar migraciones para corregir comentarios.
- **Acción:** actualizar únicamente trazabilidad vigente y comentarios editables en futuras modificaciones justificadas.

### H-08 — PENDIENTE DE PRUEBA — Permisos Android y detención GPS
- **Evidencia:** `MainActivity.kt:49-52` considera suficiente el permiso de ubicación fina **o** aproximada; el servicio intenta `GPS_PROVIDER` en `ServicioCapturaGps.kt:84-90` y maneja excepción de permisos. El método nativo de inicio devuelve el texto «Esperando ubicación» antes de verificar que exista una nueva medición válida.
- **Riesgo:** en dispositivos con solo ubicación aproximada puede no iniciarse la captura satelital; la UI debe mostrar el estado real y no interpretar inicio del servicio como evidencia GPS obtenida.
- **Acción:** cubrir escenario Android 12+ de ubicación aproximada y permisos revocados; conservar el CA-4A-21 como parcial hasta E2E físico.

### H-09 — PENDIENTE INSTITUCIONAL — D1 y pantallas web de desarrollo
- **Evidencia:** `SecurityConfiguration.java` denyAll para negocio no autorizado; `apps/web/src/shared/api/http.ts:3-9` sigue usando la cabecera `X-GAMW-Development` para registro/rutas/jornadas y `apps/web/src/app/TechnicalHome.tsx` enlaza esas pantallas sin matriz de roles.
- **Impacto:** la web operativa actual se ejecuta en modalidad técnica local; un login web correcto **no habilita** el resto de las operaciones bajo seguridad productiva.
- **Acción:** especificar D1, implementar autorización por recurso y adaptar clientes web; no sustituirla por la cabecera de desarrollo.

### H-10 — CONTROL DE CAMBIOS — Gran cantidad de trabajo no publicado
- **Evidencia:** inventario Git: 27 modificaciones y 45 entradas no rastreadas, incluyendo V9, servicio GPS Android, pruebas y baseline Markdown.
- **Impacto:** una copia del commit HEAD no reproduce el incremento local.
- **Acción:** mantener respaldo verificable y preparar revisión Git acotada; nunca `reset/clean/restore/stash` automático ni staging indiscriminado.

## 4. Controles favorables respaldados por código

1. **Seguridad:** `SecurityConfiguration.java` limita HTTP explícitamente y termina con `denyAll`; JWT resuelve Usuario activo desde PostgreSQL (`ConvertidorJwtUsuario.java`), usando BCrypt y secret configurado por entorno.
2. **CU-07:** `GestionarPersonalEmpresa.java:31-58` exige funciones iniciales activas y no permite cambiar la fecha histórica de inicio; `RepositorioRegistroOperativoPostgres.java:69-86` actualiza fecha final/activo e inserta funciones, sin DELETE destructivo de funciones.
3. **CU-09:** `GestionarRutasReferencia.java:19-41` versiona y cierra anteriores; geometría PostGIS y editor MapLibre/Terra Draw implementados.
4. **CU-24:** `GestionarJornadas.java:38-48` crea sucesora, conserva antecedente/motivo, no copia asignación antigua; transacción y locks existentes.
5. **Evidencia INICIO/FIN:** `RegistrarEventosEjecucion.java:38-75` valida asignación/tiempos, resuelve actor servidor, rechaza conflictos y acepta reintentos idempotentes.
6. **Evidencia GPS:** `RegistrarPosicionesGps.java:37-66` valida lote, asociación, intervalo y duplicados; `V9__posiciones_gps.sql:2-26` fija FK, SRID 4326, coordenadas, índices e inmutabilidad de UPDATE/DELETE.
7. **Temporalidad:** timestamps `capturado_en` y `recibido_en` separados; reconstrucción `ORDER BY capturado_en ASC,captura_id ASC,id ASC` en lectura de trayectoria.
8. **Offline:** `AlmacenEventosSqlite` persiste evidencia antes de sincronizar, contiene esquema local v3 con disparador de inmutabilidad; Android almacena mediciones nuevas directamente en SQLite; hay separación conceptual entre GPS e Internet.
9. **Sin afirmaciones automáticas:** los servicios de GPS, ejecución y consulta no asignan cumplimiento, sanción o fraude.

## 5. Matriz de cobertura de los 31 CU (evidencia estática)

**Leyenda:** V = componente vertical técnico existente con pruebas fuente; P = parcial; N = sin implementación funcional identificada; D = dependencia institucional que afecta acceso/aceptación. *V no significa aceptación formal ni pruebas ejecutadas en esta auditoría.*

| CU | Caso abreviado | Evidencia identificable | Estado |
|---|---|---|---|
| CU-01 | Autenticar usuario | Backend identidad/JWT; React acceso; Flutter sesión | V, D |
| CU-02 | Gestionar usuarios | Modelo de Usuario, sin administración de cuentas | N, D |
| CU-03 | Gestionar roles y permisos | No hay módulo/CRUD de rol/permiso institucional | N, D |
| CU-04 | Empresas operadoras | Backend + web + pruebas | V, D |
| CU-05 | Periodos de servicio | Backend + web + pruebas | V, D |
| CU-06 | Vehículos | Backend + web + pruebas | V, D |
| CU-07 | Responsables operativos | Personas, vínculos, funciones + web + pruebas | V, D |
| CU-08 | Estructura territorial | No hay módulo/capas territoriales institucionales persistidas | N |
| CU-09 | Rutas de referencia | Versionado, editor web, PostGIS + pruebas | V, D |
| CU-10 | Puntos/geocercas | No hay módulo geocercas/puntos de control | N |
| CU-11 | Programar jornada | Backend + web + pruebas | V, D |
| CU-12 | Asignar recursos | Backend + web + pruebas | V, D |
| CU-13 | Ejecutar jornada | Flutter/Android, eventos, GPS, offline, PostgreSQL; falta contexto móvil completo/E2E final | P |
| CU-14 | Incidencias | No hay módulo específico | N |
| CU-15 | Monitorear posición | Consulta interna de última captura, sin API/web/online | P, D |
| CU-16 | Consultar trayectoria | Consulta interna ordenada, sin API/web autorizada | P, D |
| CU-17 | Integridad GPS | Señales técnicas parciales; sin caso de revisión del fiscalizador | N |
| CU-18 | Coincidencias geocerca | No hay geocercas ni análisis vinculado | N |
| CU-19 | Desvíos/detenciones | No hay módulo de análisis y umbrales aprobados | N |
| CU-20 | Inspección/evidencias | No hay módulo de inspecciones | N |
| CU-21 | Fiscalización | No hay resultado municipal persistido/UI | N |
| CU-22 | Acciones correctivas | No hay módulo | N |
| CU-23 | Verificar atención correctiva | No hay módulo | N |
| CU-24 | Reprogramar jornada | Sucesión histórica backend/web, falta autorización institucional | P, D |
| CU-25 | Reporte ciudadano | No hay formulario público ni API | N |
| CU-26 | Reclamo institucional | No hay módulo de entrada institucional | N |
| CU-27 | Gestionar reclamos | No hay flujo de validación/atención | N |
| CU-28 | Puntos críticos | No hay validación municipal/registro geoespacial | N |
| CU-29 | Historial institucional | Existen datos históricos en módulos particulares; no consulta institucional integrada | N, D |
| CU-30 | Reportes e indicadores | No hay módulo de reportes/indicadores | N |
| CU-31 | Consultar bitácora | No hay bitácora general consultable | N |

**Conteo clasificado:** 8 V; 4 P; 19 N. Se usa la clasificación técnica del catálogo, sin afirmar que los 8 V ya tienen aceptación municipal. La fase de autorización D1 puede afectar tanto los V existentes como los P.

## 6. Pruebas y nivel de certeza

- En el ZIP se recuperaron **13 archivos de integración `*IT.java`**, **26 clases con sufijo `*Tests.java`**, **22 archivos web `*.test.*`** y **12 archivos Dart terminados en `test.dart`** (incluye `integration_test`). Esta es una cuenta de archivos, **no** de pruebas ejecutadas o aprobadas.
- La instantánea **no contiene** una nueva ejecución de `mvn test`, Failsafe, `npm test`, `flutter test`, Android E2E ni consultas directas a bases existentes. Los resultados previos reportados por Codex son evidencia histórica no reproducida aquí.
- La captura redacta líneas relacionadas con token/contraseña, por lo que la compilación completa debe comprobarse sobre el repositorio original, no sobre las fuentes extraídas de esta auditoría.
- El material no prueba performance, cobertura real, tiempos de monitoreo, precisión GNSS, disponibilidad de tiles ni seguridad frente a todas las amenazas.

## 7. Orden recomendado (PO)

1. **No cambiar V1–V9, evidencia 0061 ni teléfonos/SQLite.** Mantener resguardo del trabajo local.
2. **Corregir brecha funcional acotada de CU-13:** contrato `Mis jornadas` con periodo/ruta/vehículo; pruebas backend y widget Android; no alterar ejecución GPS para hacerlo.
3. **Definir D1 mínima:** identidad administrativa, autoridad para permisos, A3 vs A4 y aislamiento por empresa/periodo. Hasta entonces mantener `denyAll` sobre consultas institucionales.
4. **Diseñar envío GPS online/offline para CU-15** (separado de SSE/actualizaciones web); pruebas por pérdida de conectividad y preservación de evidencia; sin declarar frecuencia contractual.
5. **Finalizar CU-16 y luego CU-15:** API segura, consulta acotada y vistas cartográficas, superposición RutaVersion y antigüedad de posiciones.
6. **Completar CA-4A-21 con aprobación explícita antes de cualquier prueba física**; no tocar prueba anterior ni su evidencia.
7. **Priorizar CU-20/CU-21 (fiscalización humana)** cuando estén definidas categorías institucionales mínimas; continuar luego incidencias, correctivas, reclamos e historial.
8. **Auditoría dinámica aislada antes de consolidación Git:** pruebas Maven/Postgres aislado, vitest/build, Flutter test/analyze y E2E autorizado; comparar contra criterios de aceptación del MD, no contra RF antiguos.

## Conclusión

El monolito modular no ha degenerado en un CRUD genérico, y la estructura por subsistemas es defendible para PUDS/UML y mantenimiento. El principal problema actual **no es la estructura de directorios**, sino la ausencia de ciertos recorridos verticales y de la política de acceso institucional. El mayor riesgo de producto es confundir una cola GPS de sincronización manual con monitoreo cercano al tiempo real y dar por terminado CU-13 sin visualizar su contexto de ruta y vehículo. Esta auditoría **no autoriza cambios** y no altera el repositorio del usuario.
