# DICTAMEN DE AUDITORÍA INTEGRAL DEL PROTOTIPO GAMW — CORTE V18

**Proyecto:** Plataforma Web y Móvil para la Supervisión, Fiscalización y Trazabilidad del Servicio de Recolección de Residuos Urbanos mediante Georreferenciación y Monitoreo GPS para el Gobierno Autónomo Municipal de Warnes.

**Corte de evidencia:** 9 de noviembre de 2026, captura de fuentes 16:57–16:59, rama `feat/gamw-checkpoint-fiscalizacion-20261109`, HEAD `a9b247f`. **Tipo:** auditoría de fuentes exportadas, configuración y registros de ejecución; no incluye sesiones HTTP reales con roles A1–A5 ni pruebas Android nuevas.

**Reglas:** PUDS; baseline funcional 31 CU v1.0; UML 2.5; IEEE 830 para alineación documental. Se mantienen RF históricos como antecedentes, no como nueva numeración autoritativa. GPS es evidencia, nunca prueba automática de recolección; el fiscalizador municipal registra resultados humanos.

## 1. Alcance, método y nivel de evidencia

Se revisaron los **11 informes adjuntos** (inventario Git, fuentes Java, web/móvil, documentación, indicadores estáticos, APIs, SQL, pruebas, CU, diff y exclusiones) y el log independiente de verificación. Se contrastaron los archivos fuente reconstruidos a partir de la exportación. El exportador redacta automáticamente ciertas cadenas, por lo que **no se atribuyen errores de sintaxis al código redaccionado**. No se usó el inventario de coincidencias de texto para declarar funcionalidades completas.

**Clases de certeza:**
- **VT:** comprobado por ejecución independiente reportada.
- **CC:** comprobado por lectura de código/SQL; algunas consecuencias requieren petición real.
- **RV:** riesgo con indicios, no defecto reproducido.
- **BI:** restricción institucional deliberada, no defecto de implementación.
- **PE:** pendiente de evidencia.

**Prioridad:** P0 = bloqueante transversal; P1 = bloqueante de flujo o seguridad; P2 = importante no bloqueante; P3 = mantenimiento diferible. Prioridad de revisión distinta de prioridad formal del RF/CU.

## 2. Resultado ejecutivo

| Dimensión | Resultado |
|---|---|
| Backend | **VT:** Java 21 compiló 143 fuentes; Maven `BUILD SUCCESS`. |
| Pruebas del comando ejecutado | **VT:** 5 unitarias + 10 integración = **15/15**, ninguna fallida u omitida. No son pruebas exhaustivas de CU nuevos. |
| Base de datos | **VT:** Flyway V1–V18 aplicadas en base `gamw_v8_clean`, PostgreSQL 16.6 + PostGIS 3.6.2 aislados. **No acredita upgrade completo sobre información institucional existente.** |
| Web | **VT:** `tsc --noEmit && vite build`, 82 módulos transformados; advertencia de chunk MapLibre grande, sin error. |
| Móvil | **VT:** `flutter analyze --no-pub`, 0 incidencias. Sin ejecución física nueva de CU-13. |
| Cobertura de baseline | **CC:** hay código asociado a aproximadamente **26/31 CU**; **5 CU no tienen vertical funcional propio**. Esto NO es 26 CU terminados ni aceptados. |
| Autorización | **CC/RV:** existe deny-by-default y permisos explícitos; incompatibilidad probable CSRF vs clientes Bearer y brechas de acceso institucional a CRUD operativo anterior. |
| Fiscalización | **CC/BI:** registro humano diseñado; emisión, catálogos y fotografías dependen de aprobaciones/configuración explícitas. |
| Git | **VT:** HEAD remoto `a9b247f`; 15 archivos tracked modificados, numerosos untracked, entre ellos V14–V18. No se hace commit ni push como parte de esta auditoría. |

**Dictamen:** **NO LISTO PARA DEMOSTRACIÓN INSTITUCIONAL INTEGRAL**. Sí dispone de una base compilable y módulos funcionales en código que justifican una fase concentrada de corrección e integración. Se recomienda suspender nuevos CU hasta resolver P0/P1.

## 3. Hallazgos prioritarios y correcciones

### H-01 — P0 — CSRF de Spring Security posiblemente bloquea todos los POST/PUT/PATCH Bearer (**CC, reproducción HTTP pendiente**)

- **Fuentes:** `backend/.../shared/security/SecurityConfiguration.java`, líneas 26–64: reglas `.authenticated()` para escrituras, pero `.csrf(csrf -> csrf.ignoringRequestMatchers("/api/autenticacion/login"))`. `apps/web/src/modules/reclamos/api/reclamos.ts`, líneas 26–32, y otros clientes Bearer no incorporan token CSRF. `apps/web/src/shared/api/http.ts`, líneas 6–10, usa cabecera local de desarrollo.
- **Mecanismo:** con CSRF habilitado por defecto, una solicitud HTTP no segura (`POST`, `PUT`, `PATCH`) desde React/Flutter mediante solo `Authorization: Bearer` puede recibir **403 antes de llegar al controlador**, incluso con permiso válido. Pruebas de seguridad preexistentes utilizan `.with(csrf())`, por lo que no demuestran este flujo de cliente.
- **Impacto:** CU-02/03, CU-10/13/14, CU-20/21, CU-22/23/24 y CU-26/27/28; registro GPS y eventos móviles también pueden afectarse.
- **Acción:** reproducir con un usuario de prueba autorizado en infraestructura aislada, enviando el mismo POST con y sin CSRF; confirmar causa. Si todas estas APIs son exclusivamente Bearer y sin sesión/cookie autenticadora, definir política CSRF explícita para rutas estateless pertinentes, preservando seguridad de cualquier mecanismo basado en cookies. Agregar 1–2 comprobaciones HTTP focalizadas. **No deshabilitar seguridad indiscriminadamente.**
- **Criterio de cierre:** POST autorizado con Bearer funciona sin CSRF cuando el contrato es stateless; POST no autorizado sigue 401/403; no hay acceso cross-site por cookie.

### H-02 — P1 — CU operativos antiguos dependen de perfil de desarrollo y no de roles institucionales (**CC**)

- **Fuentes:** `SecurityConfiguration.java`, reglas autorizadas y `anyRequest().denyAll()` al final; `SeguridadDesarrolloLocal.java`, `@Profile("desarrollo-local")`, endpoints empresariales/personas/vehículos/jornadas/rutas con cabecera `X-GAMW-Development` solo loopback; `apps/web/src/shared/api/http.ts` siempre envía dicha cabecera en el cliente tradicional.
- **Impacto:** CU-04,05,06,07,09,11,12 tienen operaciones programadas y pantallas en código, **pero no un contrato A1/A4 productivo habilitado con JWT/permisos explícitos**. Crear `PersonaNatural` y otros datos base desde la administración institucional también puede quedar bloqueado; D1-CU02 presupone persona ya existente. La reprogramación A4 crea sucesoras sin asignaciones y carece del mecanismo autorizado para asignarlas.
- **Acción:** resolver mediante un slice pequeño de **autorización funcional del registro y planificación**, separando A1 municipal y A4 por periodo, sin eliminar `denyAll` ni publicar permisos generales a cualquier JWT. Modificar cliente antiguo para Bearer y eliminar dependencia del header temporal solo en flujos homologados. No migrar automáticamente facultades empresariales a roles de seguridad.
- **Cierre:** una cuenta A1 preparada puede crear/consultar datos permitidos y una A4 solo opera su periodo; el acceso cruzado, anónimo y sin concesión queda denegado.

### H-03 — P1 — Ausencia de pruebas HTTP y negocio focalizadas para la mayoría de slices V10–V18 (**CC**)

- **Fuentes:** informe `08_PRUEBAS_INVENTARIO_Y_LAGUNAS.txt`; backend `src/test` mantiene 44 fuentes, la mayoría anteriores a los nuevos módulos. El último comando ejecutó `PosicionGpsTests`, `ConsultarSeguimientoGpsTests`, `PosicionesGpsIT` y `ConsultaSeguimientoGpsIT` (15 casos).
- **Impacto:** los 15 éxitos no prueban concesión/revocación, fiscalización, autorización interempresa, edición de geocercas, incidencias, reprogramación, usuarios/roles, reclamos ni anexos.
- **Acción:** nosotros, no Codex por defecto, debemos crear/ejecutar **una batería mínima transversal**, no cientos de tests: autenticación/autorización, permisos revocados, límites A4 por periodo, una escritura/relectura inmutable por módulo, cadenas CU-13/24 y CU-22/23, V18 reclamo→revisión→punto. Usar `PostgresV8Aislado` y usuarios/datos sintéticos; no tocar bases ni móviles físicos.
- **Cierre:** contrato HTTP con principal real, SQL real y escenarios denegados demostrados para cada frontera crítica.

### H-04 — P1/BI — Catálogos y políticas institucionales bloquean recorridos finales (**CC/BI**)

- **Fuentes:** `PoliticaEvidenciasCampo.java`; `GestionarActuaciones.java`; `GestionarReclamos.java`; `AdministrarIdentidad.java`; `.codex-local/CURRENT_SLICE.md`.
- **Alcance:** resultado fiscal CU-21 sin catálogo oficial; verificación correctiva CU-23 sin catálogo; revisión final de reclamos CU-27 requiere catálogo; validación CU-28 bloqueada por `gamw.puntos-criticos.validacion-habilitada=false` y referencia de política; fotografías/anexos desactivados por defecto; creación/entrega de cuentas y reactivación parcialmente bloqueadas.
- **Diagnóstico:** **no es error**: el software falla de forma segura cuando el GAMW aún no definió reglas. Pero impide demostrar los ciclos completos si se deja todo vacío.
- **Acción:** obtener decisiones firmadas o documentar **catálogos de prueba claramente rotulados como PROVISIONALES**, activados solo en BD y configuración de pruebas aislada, nunca presentarlos como resultado oficial.

### H-05 — P1 — CU-24 permite sucesora sin recursos, mientras CU-12 carece de acceso productivo A4 (**CC**)

- **Fuentes:** `ReprogramarJornadasOperador.java` líneas 39–67, deja las asignaciones antiguas en su jornada; `.codex-local/CURRENT_SLICE.md`; `SecurityConfiguration.java`; cliente anterior `apiJornadas` con header de desarrollo.
- **Impacto:** sucesora histórica correcta pero posiblemente **no ejecutable**, si no existe flujo autorizado de asignar vehículo/responsable a la nueva jornada. No se debe solucionar copiando asignaciones sin autorización.
- **Acción:** integrar CU-12 autorizado a nueva sucesora y mostrar explícitamente `SIN_ASIGNACIONES`; bloquear iniciar hasta asignación válida; preservar ruta/periodo/antecedente.

### H-06 — P1 — CU-14 aún no cubre el actor A4 ni posibles capturas tardías (**CC/BI**)

- **Fuentes:** baseline CU-14 actores A4/A5 y FA-01 condicional; `GestionarActuaciones.java` líneas 62–79 usa `RegistrarEventosEjecucion.autorizarCaptura` ligada al responsable de la asignación y exige INICIO sin FIN.
- **Impacto:** A5 puede registrar su incidencia conectada; A4 no dispone de un registro autorizado propio, pese a estar en la baseline. Registro offline es **opcional condicionado a aprobación**, no incumplimiento automático.
- **Acción:** decidir A4 por periodo y ubicación de la funcionalidad; implementar solo si se ratifica el actor y evento. Offline de incidencias es decisión separada; no mezclarlo con GPS offline ya existente.

### H-07 — P1/RV — Relaciones de reclamos no comprueban coherencia cruzada de jornada (**CC, regla de negocio pendiente**)

- **Fuentes:** `GestionarReclamos.java`, método `relacionar`: verifica que el destino exista, pero no comprueba que `incidencia`, `acción` o `inspección` correspondan a la `jornada_id` de un reclamo que ya tenga una; `V18__reclamos_y_puntos_criticos.sql` líneas 55–67 define FKs simples para cada referencia.
- **Impacto:** se podrían construir expedientes que conecten un reclamo vinculado a la jornada J1 con actuaciones de J2, sin que exista validación semántica. En algunos escenarios la relación interjornada puede ser legítima; NO se debe prohibir sin regla institucional.
- **Acción:** definir si el enlace exige misma jornada o admite varias relacionadas, guardando fundamento; después implementar validación coherente con esa decisión y test de asociación cruzada.

### H-08 — P1 — Gestión de enlaces duplicados de reclamos contradice índice único (**CC**)

- **Fuentes:** `GestionarReclamos.java`, método `relacionar`: reintenta sin cambio únicamente cuando coinciden tipo, ID, fundamento y usuario. `V18` líneas 64–67: único por `(reclamo_id, referencia_id)` de cada tipo, sin distinguir fundamento/actor.
- **Impacto:** volver a enlazar el mismo destino con otro fundamento/usuario pasa la comprobación de aplicación y provoca violación SQL `UNIQUE`; el error puede retornar 500 en vez de conflicto de negocio controlado. También impide incorporar varias justificaciones como historia separada.
- **Acción:** optar entre **una relación única + observaciones append-only** o **múltiples justificaciones históricas**; ajustar código/índice solo mediante una futura migración aditiva si se cambia esquema publicado. Mínimo inmediato: detectar el destino existente y responder 409 claro sin SQL exception inesperada.

### H-09 — P1/PE — V18 aplicada en base vacía, pero no certifica actualización de base con historia (**VT/PE**)

- **Fuentes:** log `Texto pegado(20261109-205939).txt`: Flyway instala 18 migraciones en `gamw_v8_clean`; esquema `gamw_v8_upgrade` aparece inicialmente migrado hasta V7; las pruebas ejecutadas no son el ensayo completo de upgrade con datos históricos complejos.
- **Impacto:** cambios V14–V18 podrían afectar una BD real con filas de V7–V13, aunque el camino de instalación limpia pasa. No asumir que `upgrade` fue probado en la misma medida que `clean`.
- **Acción:** ensayo en **copia totalmente aislada y anonimizada** de un dataset representativo, snapshots checksum de historial, claves y cantidades antes/después. Sin tocar la BD de desarrollo habitual.

### H-10 — P2 — Tests de migración históricos esperan nueve versiones y pueden fallar por desactualización (**CC**)

- **Fuentes:** `IdentidadPostgresIT.java` líneas 42–47 `hasSize(9)`, líneas 63–74 `migrationsExecuted == 1` en un paso cuyo Flyway actual podría aplicar V9–V18. El test no fue incluido en el comando exitoso.
- **Impacto:** suite completa fallará por expectativa antigua aunque V18 sea correcta; dificulta diagnosticar regresiones reales.
- **Acción:** diferenciar prueba de versión puntual (target=9) de estado de esquema actual (18), sin reescribir V1–V18. Corregir exclusivamente expectativas del test, no migraciones históricas.

### H-11 — P2 — CU-17/18/19 constituyen análisis técnico parcial, no dictamen (**CC/BI**)

- **Fuentes:** `RevisarEvidenciaGps.java`: límite 5.000 muestras y 100 referencias, parámetros de precisión/detención/hueco configurables, salidas `POR_REVISAR`, `EVIDENCIA_INSUFICIENTE`, `REVISION_PARCIAL`.
- **Impacto:** un operador podría interpretar una coincidencia, un desvío o ausencia de hallazgo como certificado. La rama `coincidencias` puede etiquetar `COINCIDENCIA_OBSERVADA` aunque ninguna muestra sea precisa, si la coordenada reportada cae dentro; se adjunta precisión pero conviene hacer prominente la limitación.
- **Acción:** fortalecer texto y metadatos de evidencia; testear precisión baja, vacíos, límite y cambio de ruta. No introducir cumplimiento automático. Revisar qué semántica oficial se acepta para geocercas.

### H-12 — P2 — GPS físico y ciclo Android aún no cierran CA-4A-21 (**PE**)

- **Fuentes:** `.codex-local/CURRENT_SLICE.md`, antecedentes de 0061, `ServicioCapturaGps.kt`, `MainActivity.kt`, scripts `integration_test/slice4a_*` excluidos del git checkpoint.
- **Alcance:** existe código de captura nativa/cola y evidencia anterior, pero no prueba automatizada final en dispositivo de captura y transmisión continuas con el proceso Flutter terminado, sincronización y recuperación. No hay que afirmar que 30 s de captura sostenida esté garantizado.
- **Acción:** conservar pruebas físicas separadas, con autorización expresa antes de instalar APK y sin tocar SQLite real. CA-4A-21 permanece parcial.

### H-13 — P2 — Documentos contextuales acumulan estados incompatibles con el corte V18 (**CC**)

- **Fuentes:** `.codex-local/CURRENT_SLICE.md` **1.086 líneas**; `.codex-local/TRACEABILITY.md` **793 líneas**; `.codex-local/ARCHITECTURE.md` **567 líneas**. Se alternan secciones recientes V18 y antecedentes que todavía indican V1–V7, V8 siguiente, CU nuevos bloqueados, etc.
- **Impacto:** contradicciones en auditorías y contexto excesivo consumido por Codex, contra modo FAST acordado. El orden de prioridad textual no evita que búsquedas `rg` encuentren filas históricas obsoletas.
- **Acción:** conservar copia histórica, generar `CURRENT_SLICE.md` compacto (≤100–150 líneas) como estado único vigente; mantener historial durable separado/archivado; TRACEABILITY con matriz única de 31 CU; actualizar ARCHITECTURE en un apartado de revisión V18 sin borrar decisiones históricas.

### H-14 — P2 — Riesgo de publicar accidentalmente evidencia física y utilidades locales (**CC**)

- **Fuentes:** informe Git: 15 tracked modificados y aproximadamente 54 untracked, entre ellos `EVIDENCIA*`, `VALIDACION*`, `backend/dev/*`, pruebas Android físicas y V14–V18. `git diff` por sí solo excluye untracked.
- **Impacto:** un `git add .` podría subir archivos técnicos/locales de evidencia o información confidencial y dejar migraciones fuera si se stagea solo el diff tracked.
- **Acción:** al crear nuevo checkpoint usar allowlist explícita de código/documentación/migraciones y verificación de secretos; nunca stagear `backend/dev`, evidencias ni datos SQLite. No reset/clean; no commit en auditoría.

### H-15 — P2 — Preparación de APK de distribución incompleta (**CC**)

- **Fuentes:** `apps/mobile/android/app/build.gradle.kts` líneas 42–47: `release` utiliza firma `debug` y existen comentarios de configuración de ID.
- **Impacto:** válido para iteración local, **no aceptable como distribución formal o institucional**.
- **Acción:** postergar a transición: política de claves, firma release, distribución y credenciales seguras; no meter aquí Docker, nube o nuevo IoT.

### H-16 — P2 — Consultas intensivas y UX necesitan medición, no refactor preventivo (**RV**)

- **Fuentes:** `RepositorioRevisionGpsPostgres.java`: `ST_Distance` contra ruta versionada por muestra, combinaciones control-muestra con límite; `RepositorioMonitoreoPostgres.java`: LATERAL última posición; `PaginaOperacion` concentra varias responsabilidades.
- **Impacto:** el costo crecerá con jornadas, muestras y geocercas; sin evidencia de fallo ahora. La paginación y límites actuales reducen riesgo pero acotan revisión.
- **Acción:** después de QA funcional usar `EXPLAIN (ANALYZE,BUFFERS)` en BD aislada con dataset sintético y medir; refactor solo si la medición lo justifica.

### H-17 — P2/BI — Datos personales, adjuntos y conservación pendientes de política (**CC/BI**)

- **Fuentes:** V13 `evidencia_inspeccion` y V18 `anexo_reclamo` guardan originales en `bytea` con metadatos y hash; `PoliticaEvidenciasCampo` valida formatos/medidas; `GestionarReclamos.anexar` exige habilitación explícita.
- **Impacto:** contenido y documentos pueden ser sensibles; la retención, acceso, respaldo y tratamiento deben definirse antes de subir registros reales. SHA-256 demuestra correspondencia de bytes, no autenticidad material de la inspección.
- **Acción:** clasificar datos y aprobar procedimiento local de archivos, retención, permisos y rectificación; demostrar descarga restringida (usuario sin concesión obtiene rechazo).

### H-18 — P3 — Mantenibilidad de archivos densos y cadenas de responsabilidad (**CC**)

- **Fuentes:** varios services/repositorios mezclan records DTO y lógica en líneas compactas; interfaz móvil `PaginaOperacion` concentra controles, sincronización e interfaz. Modularidad de paquetes **sí existe**, por lo que NO se exige `cuXX` por carpeta.
- **Acción:** refactor por necesidad medible, después de resolver comportamiento y seguridad; no adelantarlo a los slices con tokens limitados.

## 4. Fortalezas observadas (no modificar por rutina)

- Paquetes de backend `administracion`, `planificacion`, `ejecucion`, `monitoreo`, `fiscalizacion`, `correctivas`, `reclamos`; separación aplicación/dominio/persistencia razonable para monolito modular.
- Registro de permisos municipales y empresariales **sin herencia automática desde función laboral**, concesiones revocables por cuenta/periodo, principal JWT derivado desde cuenta.
- Ruta y RutaVersion independientes; Jornada referencia periodo y versión, AsignacionJornada preserva vehículos y personas históricos.
- GPS con tiempos captura/recepción distintos; SQLite duradero para evidencias INICIO/FIN y GPS; sincronización idempotente; sin interpretar GNSS como recolección.
- PostGIS con geometrías SRID 4326, índices GiST, triggers de evidencia inmutable, sin `ON DELETE CASCADE` evidente en migraciones nuevas.
- Reclamos institucionales son distintos de ciudadanos autenticados. Puntos críticos candidatos se distinguen de los validados. No se inventan ubicaciones municipales ni categorías de cumplimiento.
- Constructor de catálogos vacío y bloqueos seguros por política. No sustituir esto por datos «oficiales» ficticios.

## 5. Matriz actual de 31 casos de uso

La columna **código** señala existencia de componentes y rutas; **PARCIAL** significa que existe código pero flujos, permisos, políticas o pruebas relevantes no están completos. **NINGUNO está institucionalmente aceptado por estos reportes**. La cobertura registrada anterior no se debe reinterpretar como cierre.

| CU | Caso de uso | Código al corte V18 | Brecha clave para aceptación |
|---|---|---|---|
| 01 | Autenticar usuario | Sí | Verificar integración y sesión global |
| 02 | Gestionar usuarios | Parcial | Provisión, reactivación, personas y QA de administración |
| 03 | Gestionar roles y permisos | Parcial | Roles institucionales reales, delegación y pruebas de revocación |
| 04 | Gestionar empresas operadoras | Sí, desarrollo local | Habilitar autorización institucional A1 |
| 05 | Gestionar periodos de servicio | Sí, desarrollo local | Habilitar autorización institucional A1 |
| 06 | Gestionar vehículos | Sí, desarrollo local | Habilitar autorización institucional A1/A4 pertinente |
| 07 | Gestionar responsables operativos | Parcial | Perfil productivo y acceso al registro histórico |
| 08 | Gestionar estructura territorial del servicio | **No** | Definir fuentes/jerarquía territorial oficiales y después construir |
| 09 | Gestionar rutas operativas de referencia | Sí, desarrollo local | Autorizar gestión y distinguir referencia/versionado |
| 10 | Gestionar puntos de control y geocercas | Parcial | Autoridad, fuentes y pruebas espaciales |
| 11 | Programar jornada | Sí, desarrollo local | Autorizar programación en producción |
| 12 | Asignar recursos a jornada | Sí, desarrollo local | Enlazar con sucesora CU-24 y autorización por periodo |
| 13 | Ejecutar jornada asignada | Parcial | FIN tras reasignación, offline real, CA-4A-21, CSRF |
| 14 | Registrar incidencia operativa | Parcial | A4, reglas de captura tardía y pruebas |
| 15 | Monitorear posición de jornada | Parcial | Transmisión online real, acceso y actualización de mapa |
| 16 | Consultar trayectoria GPS de jornada | Parcial | Contrato HTTP, acceso municipal y mapa histórico E2E |
| 17 | Revisar continuidad e integridad del seguimiento GPS | Parcial | Datos límites/precisión y pruebas con GNSS irregular |
| 18 | Revisar coincidencias con puntos de control y geocercas | Parcial | Semántica de precisión/vigencia y casos de geometría |
| 19 | Revisar desvíos y detenciones prolongadas | Parcial | Umbrales técnicos aprobados para uso interno y casos límite |
| 20 | Registrar inspección y evidencias de campo | Parcial | Política de fotos y flujo HTTP de inspección |
| 21 | Fiscalizar jornada y registrar resultado | Parcial | Catálogo humano aprobado, rectificación y prueba integral |
| 22 | Gestionar acciones correctivas | Parcial | Respuesta A4 probada, estados y políticas de atención |
| 23 | Verificar atención de acciones correctivas | Parcial | Catálogo y comprobación de respuesta previa |
| 24 | Reprogramar jornada conservando el antecedente | Parcial | Asignación de sucesora, historial y QA de permisos |
| 25 | Presentar reporte ciudadano | **No** | Aprobar formulario público y mitigaciones básicas |
| 26 | Registrar reclamo recibido por canal institucional | Parcial | Prueba HTTP y privacidad/origen |
| 27 | Validar y gestionar reclamos | Parcial | Catálogo oficial y consistencia relacional |
| 28 | Registrar punto crítico o microbasural identificado | Parcial | Validación municipal/PENDING-10 y QA geoespacial |
| 29 | Consultar historial institucional del servicio | **No** | Lectura transversal y continuidad interempresa |
| 30 | Generar reportes e indicadores | **No** | Indicadores definidos sin confundir GPS con cumplimiento |
| 31 | Consultar bitácora de acciones relevantes | **No** | Bitácora transversal, accesos y auditabilidad |

**Indicador conservador:** 26 CU tienen algún componente/flujo de código y 5 siguen sin vertical específico. **No se computa porcentaje de conclusión funcional ni número de CU aceptados** a partir de estos datos.

## 6. Auditoría transversal por capas

### 6.1 Negocio / alcance

**Adecuado:** no se incorporan sanciones automáticas, pagos, botadero, IA, IoT ni optimización avanzada. Registro municipal, operador y móvil mantienen responsabilidades distintas. **Pendiente:** A1–A4, catálogos, PENDING-06/07/09/10, reprogramación de jornadas ejecutadas, privacidad de reclamos, políticas de imágenes; estas decisiones no pueden «resolverse» por intuición técnica.

### 6.2 Backend / REST / seguridad

Revisar primero **H-01 y H-02**. `SecurityConfiguration` protege muchos endpoints nuevos con `.authenticated()` y la lógica de permisos en servicio; buena intención, pero se requiere prueba de integración. `AutorizacionMunicipal` se consulta contra BD y cuenta activa; `AutorizacionOperador` limita por `PeriodoServicio`. **Verificar** que toda descarga de archivo y todo POST pasan por permiso efectivo y que revocar de verdad afecta la siguiente solicitud. No exponer lista general por UUID sin filtros.

### 6.3 PostgreSQL/PostGIS

V1–V18 instalada limpiamente (VT). Las V13–V18 contienen muchas FKs, índices y triggers para impedir borrado/edición de historia. **No reescribirlas después del checkpoint y uso con datos; preferir V19 si hay cambios**. Ensayar acceso entre periodos, colisiones de `solicitud_id`, migraciones desde dataset poblado y caso de relaciones de reclamos (H-07/08). Guardar query plans de consultas espaciales únicamente si hay lentitud real.

### 6.4 Flutter/Android

`flutter analyze` pasó, pero es verificación estática. Mantener `ServicioCapturaGps.kt` y pruebas físicas aisladas intactos. El sistema de captura GPS nativo y el transmisor Flutter son procesos con garantías diferentes: un servicio Android puede capturar sin que la UI esté viva, pero **no se debe prometer transmisión activa con Flutter terminado**. FIN, bloqueo por cuenta, almacenamiento antes de sincronizar y logout requieren ensayo independiente en entorno aislado. CA-4A-21 sigue abierto.

### 6.5 React/MapLibre

Build pasó. Hay páginas en `/monitoreo`, `/trayectorias`, `/revision-gps`, `/referencias`, `/fiscalizacion`, `/correctivas`, `/empresa/correctivas`, `/empresa/jornadas`, `/administracion/usuarios` y `/reclamos`. La existencia de rutas no certifica navegación con JWT, filtros, permisos ni persistencia. Páginas antiguas `/operadores`, `/jornadas`, `/rutas` siguen usando cliente de desarrollo. Separar visualmente candidato/validado y hallazgo/veredicto.

### 6.6 Pruebas y trazabilidad

`08_PRUEBAS...` inventaría 81 rutas de tests/soportes, **no 81 casos aprobados en la última ejecución**. Las 15 pruebas del log están concentradas en GPS. `.codex-local/TRACEABILITY.md` contiene una tabla antigua donde V18 no está reflejada como estado consolidado y fragmentos de múltiples momentos: usar una única matriz viva. En PUDS no confundir pruebas unitarias, integración, aceptación institucional ni evidencia de campo.

## 7. Plan de corrección recomendado (sin nuevos CU por ahora)

| Ola | Prioridad | Entrega de código | Verificación que hacemos nosotros | Salida |
|---|---|---|---|---|
| A | P0 | **Resolver CSRF para REST Bearer** (H-01) con configuración mínima | Un POST con permiso, sin permiso, sin JWT y revocado, MockMvc/HTTP real en PostgreSQL aislado | APIs mutables no bloqueadas artificialmente ni abiertas de más |
| B | P1 | **Integrar CU-04/05/06/07/09/11/12 bajo D1** (H-02/05) | A1 municipal, A4 por periodo, sucesora y asignaciones; pruebas negativas de cruce empresarial | Flujo institucional programar→ejecutar viable |
| C | P1 | Correcciones focalizadas reclamos (H-07/08), solo tras decidir semántica | Reclamo→relación→revisión→punto candidato→validación; rechazo de relación incoherente | Historial de reclamos consistente |
| D | P1 | **No código no autorizado:** acordar catálogos y protocolos necesarios (H-04) | Configuración local de prueba explícita con referencia PROVISIONAL | Fiscalización/acciones/reclamos demostrables sin inventar obligaciones |
| E | P1 | Ajustes solo ante fallos reales del QA; expectativas de tests V18 | Integración focalizada por módulos, upgrade aislado, permisos/revocación | Cierre técnico por escenarios críticos |
| F | P2 | Actualizar contextos y Git con allowlist después de QA | `git status`, diff, staged, secrets, V14–V18, sin evidencia física | Nuevo checkpoint de trabajo seguro |

**Recomendación:** no gastar Codex en suites interminables, pero tampoco aprobar merges institucionales con solo compilación. Por bloque, máximo 1–3 pruebas específicas con backend real + recorrido manual de UI según criticidad. Codex queda reservado para **correcciones P0/P1**; usuario/ChatGPT conducen verificaciones.

## 8. Guía de comandos independientes y seguros

**Para recuperar un estado Git de solo lectura (sin modificar):**

```powershell
$repo='D:\2-2026\Taller de Grado\Proyecto\Desarrollo'
Set-Location $repo
git status --short
git diff --check
git diff --stat
git ls-files --others --exclude-standard
```

**Lo ya ejecutado y aprobado:** backend `mvnw.cmd -Ppostgres-integration verify` **con selección específica de tests y `spring.config.import=`**; `npm run build`; `flutter analyze --no-pub`. No necesitan repetición idéntica hasta que cambie código relevante.

**Para pruebas de seguridad próximas:** preparar un test HTTP aislado con un usuario y concesión explícitos, y registrar diferencias entre requests con/sin CSRF y entre permisos otorgados/revocados. No facilitar comandos que levanten servicios sobre la base habitual hasta leer su configuración exacta.

## 9. Restricciones y transparencia

- No se ejecutó el código de este ZIP. Se evaluó fuente exportada y el log aportado; por eso fallos de runtime se presentan como riesgos hasta reproducirlos.
- Algunas líneas del exportador son heurísticamente redactadas; no son sintaxis literal confiable para recompilar.
- No se accedió a archivos `.env`, SQLite real, credenciales, GPS físico ni datos personales. No se aplicaron migraciones a la base habitual.
- No se modificó `AGENTS.md`, los MD originales, ni el árbol Git.
- Las decisiones de fiscalización, validación y protección de datos pertenecen al GAMW: propuestas no equivalen a contrato aprobado.

## 10. Conclusión PO

**No es momento de abrir CU-08/25/29/30/31 todavía**. El siguiente valor no proviene de sumar archivos sino de volver operativos, seguros y auditables los CU existentes. Orden: **CSRF → acceso institucional antiguo/roles → recorrido fiscalizador↔operador completo → reclamos coherentes → checkpoint Git → CU restantes**. La arquitectura modular y el modelo histórico se conservan; la corrección debe ser focalizada y evidenciada.
