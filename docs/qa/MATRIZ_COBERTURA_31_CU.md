# Matriz de cobertura de los 31 CU — auditoría funcional (baseline `6db0d1e`; actualizada en `c1f0ce3` [CU-22] y en el corte CU-20)

> Conteo vigente tras los cortes CU-22, CU-20, CU-26, CU-11/12 (§13), CU-24 (§14) y F1 CU-04/05 (§23). **IMPLEMENTED 8 (01, 04, 05, 11, 12, 22, 24, 29) · PARTIAL 19 · INSTITUTIONALLY BLOCKED 3 · NOT IMPLEMENTED 1.** «IMPLEMENTED» = verificación técnica automatizada, NO aceptación institucional del GAMW. CU-26 y CU-20 permanecen PARTIAL (solo subconjuntos verificados, §11-12). Conteos anteriores (históricos, ya no vigentes): 5/22/3/1 tras CU-11/12 y **3/24/3/1** antes de ellos. CU-20 permanece PARTIAL: solo está verificado el subconjunto *sin fotografías* (ver §11); no es aceptación del CU completo.

**Naturaleza:** nació como auditoría de solo lectura; las secciones §10 en adelante registran cortes posteriores con implementación y pruebas. Evidencia = rutas de archivos del repositorio y resultados ya ejecutados en esta campaña.
**Distinción obligatoria:** *verificación técnica automatizada* ≠ *aceptación institucional*. Ningún CU está aceptado por el GAMW.
Fuentes: `docs/context/GAMW_BASELINE_FUNCIONAL_CU01_CU31_v1_0.md` (actores), `docs/context/04_REQUIREMENTS_TRACEABILITY_GAPS.md` (RF↔CU), código en `backend/`, `apps/web/`, `apps/mobile/`.

## 1. Criterio de clasificación (estricto)

- **IMPLEMENTED:** el flujo principal de **todos** los actores iniciadores existe en producción (backend + interfaz web/móvil), con autenticación **de producción (JWT + concesiones)**, y tiene verificación automatizada del backend **y** de la interfaz.
- **PARTIAL:** existe código, pero falta alguno de: interfaz verificada bajo JWT, actor iniciador, pruebas del backend, o regla ya definida por implementar.
- **INSTITUTIONALLY BLOCKED:** no se puede cerrar de forma segura sin decisión del GAMW (D1, PENDING-05, fuentes territoriales, política pública).
- **NOT IMPLEMENTED:** sin vertical, sin bloqueo institucional que lo impida del todo.
- **No cuentan como flujo de UI completado:** siembras SQL, pruebas solo REST, pantallas que solo pasan en modo local de desarrollo (`desarrollo-local` + `X-GAMW-Development`), dobles o mocks.

## 2. Resultado

| Estado | Cantidad | CU |
|---|---|---|
| IMPLEMENTED | **8** | 01, 04, 05, 11, 12, 22, 24, 29 (22 desde §10; 11/12 desde §13; 24 desde §14; 04/05 desde §23) |
| PARTIAL | **19** | 02, 03, 06, 07, 09, 10, 13, 14, 15, 16, 17, 18, 19, 20, 21, 23, 26, 27, 28 |
| INSTITUTIONALLY BLOCKED | **3** | 08, 25, 31 (31: código listo, acceso denegado por D1) |
| NOT IMPLEMENTED | **1** | 30 |

Total 31 (verificado fila por fila contra la tabla de la sección siguiente, corte F1 §23). **IMPLEMENTED no implica aceptación**: es verificación técnica automatizada; ningún CU está aceptado por el GAMW. Conteos históricos de esta tabla: 6 / 21 / 3 / 1 (tras §21) y 3 / 24 / 3 / 1 (antes de CU-11/12 y CU-24).

## 3. Inventario de pruebas realmente ejecutadas (esta campaña, local/CI)

Backend 199 (surefire, 1 omitida Android) + IT aislados; navegador real: `FlujoMunicipalNavegadorIT` (flujo municipal + correctivo), `AutenticacionWebIT`, `RegistroPlanificacionWebIT`; web vitest 64; Flutter CI (analyze + test); emulador Android (cadena offline, GPS **sintético** en el almacén local). Informe: `docs/qa/QA_TECNICA_GAMW_20261118.md`.
**Sin ninguna prueba automatizada encontrada** (búsqueda en `backend/src/test`, `apps/web`, `apps/mobile/test`): `PuntosCriticosController`, revisiones/relaciones/anexos de `ReclamosController` (CU-27), `RevisionGpsController`/`RevisarEvidenciaGps`, `ReferenciasEspacialesController`, `MonitoreoController` (solo comprobación de cierre en `ConsultaSeguimientoGpsIT.java:109`), `AdministracionIdentidadController`/`AdministrarIdentidad`, `PanelIncidencias` (Flutter) y `PaginaReprogramacionEmpresa` (web). *(Actualizado: el alta de inspección sin fotografías, CU-20, y `PaginaCorrectivasEmpresa`, CU-22, ya tienen pruebas; ver §10 y §11.)*

## 4. Matriz CU × RF × Actor × componentes × pruebas × estado

Leyenda de pruebas: **ITJ** = IT con JWT real; **ITD** = IT en modo `desarrollo-local`; **E2E** = Playwright real; **VT** = vitest; **FT** = Flutter test/CI; **EMU** = emulador Android; **—** = ninguna encontrada.

| CU | RF | Actor iniciador | Backend | Web | Móvil | Pruebas | Estado y brecha exacta |
|---|---|---|---|---|---|---|---|
| 01 Autenticar | RF-01 | todos | `modules/administracion/identidad/api/AutenticacionController`, `shared/security/*` | `PaginaAcceso.tsx` | `shared/auth/sesion.dart`, `pagina_operacion.dart` | ITJ `ContratoHttpAutenticacionTests`, `AutenticacionWebIT` (E2E 2/2); VT `PaginaAcceso.test.tsx`; FT `test/auth/*`, `sesion_almacen_ilegible_test.dart`; EMU | **IMPLEMENTED** (técnico). Pendiente: aceptación; política institucional de sesión |
| 02 Gestionar usuarios | RF-02 | A1 | `identidad/gestion/AdministrarIdentidad`, `AdministracionIdentidadController` | `PaginaAdministracionUsuarios.tsx` | — | **—** (sin IT/VT/E2E); solo SQL de actuaciones en `BitacoraEventosIT` | **PARTIAL**: código sin pruebas; provisión/reactivación/administradores bloqueados por D1 |
| 03 Roles y permisos | RF-02 | A1 | `AutorizacionMunicipal/Operador`, CLI `ProvisionarPermisoMunicipal`, V10/V15/V17/V19 | `PaginaAdministracionUsuarios.tsx` | — | ITJ parcial `AutorizacionOperativaV19IT` (concesiones/revocación efectivas); gestión HTTP/UI: **—** | **PARTIAL**: catálogo de roles y límites A1 = D1 |
| 04 Empresas | RF-03 | A1 | `EmpresasOperadorasController` | `PaginaOperadores.tsx` | — | ITD `RegistroOperativoIT`; E2E JWT `flujo-empresas-periodos.test.mjs` vía `EmpresasPeriodosNavegadorIT` (§23); E2E dev-local `RegistroPlanificacionWebIT`; VT `PaginaOperadores.test.tsx` | **IMPLEMENTED** (técnico, §23): A1 bajo JWT + `EMPRESAS_*`; D1 (qué actores en producción) y CA-04-07 (fallo de persistencia) pendientes/no demostrados |
| 05 Periodos | RF-03 | A1 | `EmpresasOperadorasController`, `PeriodosPlanificacionController` | `PaginaOperadores.tsx` | — | ídem CU-04 | **IMPLEMENTED** (técnico, §23): A1 bajo JWT + `PERIODOS_*`; corrección incompatible con jornadas = 409; solapamiento sin política (permitido); D1 y CA-05-07 pendientes/no demostrados |
| 06 Vehículos | RF-04 | A1 / A4 según ámbito | `VehiculosController`, `VinculosVehiculoEmpresaController` | `PaginaOperadores.tsx` (vínculos) | — | ITD `VehiculosIT`; E2E `vehiculos.test.mjs` (dev-local); VT `vehiculos.test.ts` | **PARTIAL**: escritura A4 sin alcance definido (D1); UI bajo JWT sin verificar |
| 07 Responsables | RF-04 | A1 / A4 según ámbito | `PersonasController`, vínculos/funciones | `PaginaOperadores.tsx` | — | ITD `RegistroOperativoIT`; E2E dev-local | **PARTIAL**: ídem CU-06 |
| 08 Estructura territorial | RF-05 | A1 | — | — | — | — | **INSTITUTIONALLY BLOCKED**: jerarquía y fuentes oficiales inexistentes (CU-08). No inventar |
| 09 Rutas | RF-06 | **A4** (baseline) | `RutasReferenciaController`, V2/V3 versionado | `PaginaRutasReferencia.tsx` | — | ITD `RutasReferenciaIT`; E2E `rutas-referencia`; VT | **PARTIAL**: el código restringe a A1; escritura A4 sin alcance aprobado; UI bajo JWT sin verificar |
| 10 Puntos de control / geocercas | RF-06 | A1 / A3 | `ReferenciasEspacialesController`, V12 | `PaginaReferenciasEspaciales.tsx` | — | **—** | **PARTIAL**: sin pruebas; fuentes/vigencia de geocercas por definir |
| 11 Programar jornada | RF-07 | **A4** | `GestionarJornadas`, `PlanificarJornadasOperador`, `JornadasOperadorController` | `PaginaReprogramacionEmpresa.tsx` (`/empresa/jornadas`) ✔ UI A4; `PaginaJornadas.tsx` (municipal) | — | E2E `flujo-planificacion.test.mjs` vía `PlanificacionOperadorNavegadorIT` (UI A4, JWT, concesión de periodo) + REST; ITD `JornadasIT` (11); ITJ `AutorizacionOperativaV19IT` (6) | **IMPLEMENTED** (técnico, §13). Pendiente institucional: calendarios/horarios oficiales y D1 (no inventados); la creación de empresas/periodos/rutas se siembra por SQL |
| 12 Asignar recursos | RF-07 | **A4** | ídem + `ReferenciasJornadas` | ídem | — | ídem (asignar, reasignar, historial, una abierta, recursos elegibles) | **IMPLEMENTED** (técnico, §13); alta de vínculos de recursos = CU-06/07 (A1), fuera de este corte |
| 13 Ejecutar jornada | RF-08/09/10/11 | A5 | `EjecucionJornadasController`, `PosicionesGpsController`, `MisJornadasController`, V7–V9 | — | `pagina_operacion.dart`, SQLite, `ServicioCapturaGps.kt`, cola/sync | ITJ `EjecucionAutenticadaIT`, `EventosEjecucionIT`, `PosicionesGpsIT`, `CadenaOperativaFiscalizacionIT`; FT `test/auth/pagina_operacion_test.dart`; EMU `qa_emulador/cadena_offline_emulador_test.dart` (**GPS sintético**, no UI) | **PARTIAL**: GPS nativo, batería, segundo plano, fabricantes y CA-4A-21 sin QA física; el emulador no maneja la UI |
| 14 Incidencia | RF-16 | A3/A4/A5 según ámbito | `ActuacionesController`, `GestionarActuaciones` | `PaginaCorrectivas.tsx` (lectura) | `panel_incidencias.dart` | REST en `FlujoMunicipalNavegadorIT` (alta, duplicado, tardía 409); móvil/web alta: **—** | **PARTIAL**: móvil sin pruebas; A4 y captura tardía/offline sin política |
| 15 Monitorear posición | RF-12 | A3 / A4 | `MonitoreoController` | `PaginaMonitoreo.tsx` | — | E2E `flujo-monitoreo.test.mjs` vía `MonitoreoMunicipalNavegadorIT` (UI A3 + REST), §15 | **PARTIAL** (A3 verificado técnicamente, §15): A4 sigue bloqueado por D1 (sin permiso; 403) y falta QA de continuidad con ejecución real |
| 16 Trayectoria GPS | RF-12 | A3 / **A4** | `TrayectoriasController`, `ConsultarTrayectoriasAutorizadas` | `PaginaTrayectorias.tsx`, `MapaTrayectoria.tsx` | — | ITJ `CadenaOperativaFiscalizacionIT`; E2E `flujo-municipal.test.mjs` (tabla, orden, hueco, «inferidos»); E2E `flujo-trayectoria.test.mjs` vía `TrayectoriaMunicipalNavegadorIT` (§16) | **PARTIAL**: A3 verificado técnicamente (§16); A4 sin acceso (D1 pendiente); el lienzo del mapa/teselas y los marcadores **no** se verifican |
| 17 Continuidad/integridad GPS | RF-13 | A3 | `RevisionGpsController`, `RevisarEvidenciaGps` | `PaginaRevisionGps.tsx` | — | E2E `flujo-integridad-gps.test.mjs` vía `IntegridadGpsNavegadorIT` (§17) | **PARTIAL**: A3 verificado técnicamente con umbrales provisionales explícitos (§17); pendientes umbrales institucionales (PENDING-04) y validación con GPS físico |
| 18 Coincidencias | RF-14 | A3 | ídem | ídem | — | E2E `flujo-coincidencias-gps.test.mjs` vía `CoincidenciasEspacialesNavegadorIT` (§18) | **PARTIAL**: A3 verificado técnicamente con radios y tolerancias provisionales explícitos (§18); pendientes reglas institucionales de puntos de control y validación con GPS físico |
| 19 Desvíos y detenciones | RF-15 | A3 | ídem | ídem | — | E2E `flujo-desvios-gps.test.mjs` vía `DesviosDetencionesNavegadorIT` (§19) | **PARTIAL**: A3 verificado técnicamente con umbrales provisionales explícitos (§19); pendientes criterios institucionales, validación con GPS físico y contexto de incidencias asociadas (no implementado) |
| 20 Inspección y evidencias | RF-17 | A3 | `FiscalizacionController` (`POST …/inspecciones`, JSON y multipart), `GestionarFiscalizacion.inspeccionar`, `PoliticaEvidenciasCampo` | `PaginaFiscalizacion.tsx` + `FormularioInspeccion.tsx` | — | ITJ `InspeccionCampoIT` (4); E2E `flujo-inspeccion.test.mjs` (UI, JWT) vía `FlujoMunicipalNavegadorIT` | **PARTIAL** (subconjunto sin fotografías verificado, §11): fotos/archivos (CA-20-03/04), FA-02/FA-03 y política PENDING-09 sin verificar o bloqueados |
| 21 Fiscalizar y resultado | RF-18 | A3 | `GestionarFiscalizacion` | `PaginaFiscalizacion.tsx` | — | ITJ `CadenaOperativaFiscalizacionIT`; E2E expediente (lectura) | **PARTIAL / emisión bloqueada**: catálogo oficial PENDING-05 vacío; emisión en UI no probada; rectificación no definida |
| 22 Acciones correctivas | RF-19 | A3 (+A4 responde) | `ActuacionesController`, `CorrectivasEmpresaController`, `AtenderCorrectivasEmpresa` | `PaginaCorrectivas.tsx` ✔ solicitud; `PaginaCorrectivasEmpresa.tsx` | — | E2E `flujo-correctivo.test.mjs` fases 1 y 1b (solicitud A3 y **respuesta A4 por la UI**, JWT) + REST en `FlujoMunicipalNavegadorIT` | **IMPLEMENTED** (técnico, ver §10); estados/plazos/adjuntos = institucional |
| 23 Verificar atención | RF-19 | A3 | `GestionarActuaciones.verificar` | `PaginaCorrectivas.tsx` | — | E2E fase 2 (UI) + REST (409/403) con categoría **sintética** | **PARTIAL / bloqueado en producción**: catálogo oficial inexistente |
| 24 Reprogramar | RF-20 | **A4** | `ReprogramarJornadasOperador`, V16 | `PaginaReprogramacionEmpresa.tsx` | — | ITJ REST `AutorizacionOperativaV19IT`; ITD `JornadasIT`; E2E `RegistroPlanificacionWebIT` **dev-local por vía municipal** (cerrada en producción: 403) | **IMPLEMENTED** (técnico, §14): UI A4 con JWT verificada; jornadas ejecutadas/fiscalizadas siguen **bloqueadas** (procedimiento institucional pendiente, no inventado) |
| 25 Reporte ciudadano | RF-21 (GAP-01) | A6 externo | — | — | — | — | **INSTITUTIONALLY BLOCKED**: formulario público, privacidad y controles sin aprobar; RF por ampliar |
| 26 Registrar reclamo | RF-21 | A3 | `ReclamosController.registrar`, `GestionarReclamos`, V18 | `PaginaReclamosPuntos.tsx` (formulario «Registrar reclamo recibido») | — | ITJ `ReclamoInstitucionalIT` (4); E2E `flujo-reclamo.test.mjs` (UI, JWT) vía `FlujoMunicipalNavegadorIT` | **PARTIAL** (registro textual verificado, §12): anexos, privacidad (datos personales), catálogo de canales y duplicados entre canales sin definir/bloqueados; CU-27 y CU-25 fuera de alcance |
| 27 Validar/gestionar reclamos | RF-21 | A3 | `ReclamosController` (revisiones/relaciones) | ídem | — | **—** | **PARTIAL**: sin pruebas; catálogo de valoración vacío (D1/PENDING) |
| 28 Punto crítico | RF-22 | A3 | `PuntosCriticosController`, V18 | `PaginaReclamosPuntos.tsx` | — | **—** | **PARTIAL**: sin pruebas; criterios PENDING-10 (apagado por defecto) |
| 29 Historial | RF-23 | A1/A2/A3 | `modules/historial/*` | `PaginaHistorialServicio.tsx` | — | ITJ `HistorialServicioIT` (5); E2E `flujo-municipal`; VT (2) | **IMPLEMENTED** (técnico, JWT). Pendiente: D1, snapshots nominales, ejecución sin permiso aprobado |
| 30 Reportes e indicadores | RF-24 | usuario autorizado | — | — | — | — | **NOT IMPLEMENTED**: fórmulas/entregables sin definir; solo cabría conteo descriptivo sin inferir cumplimiento (propuesta, no hecho) |
| 31 Bitácora | RF-25 | A1 | `modules/bitacora/*` | `PaginaBitacora.tsx` | — | ITJ `BitacoraEventosIT` (doble de autorización), `BitacoraAutorizacionIT` (denegación real); VT (5) | **INSTITUTIONALLY BLOCKED**: `BITACORA_CONSULTAR` no existe en catálogo (D1); fuentes parciales; retención sin definir |

## 5. Comportamiento de producción faltante (no solo pruebas)

1. **A4 sin acceso a consulta de trayectoria/monitoreo** (CU-15/16, D1) y **sin escritura de rutas/recursos compartidos** (CU-06/07/09): el baseline los nombra como iniciadores; el código los limita a municipal o a recursos elegibles.
2. **Emisión de resultados (CU-21/23/27) imposible en producción** hasta que exista un catálogo aprobado (PENDING-05); solo hay fixtures sintéticas de prueba.
3. **Fotos/anexos (CU-20/26), validación CU-28, provisión CU-02**: apagados por configuración hasta aprobar política (por diseño correcto, pero sin vertical alcanzable).
4. **Sin nombres históricos inmutables** (CU-29): razón social/placa/persona muestran el maestro vigente.
5. **Sin vertical** de CU-08, CU-25, CU-30.
6. **Móvil:** incidencias (CU-14) sin pruebas; sin UI de reclamos/inspección (previsto web).

## 6. Desconocidos explícitos

- Resultado de la última ejecución de GitHub Actions de `6db0d1e` (no consultado; el usuario lo reporta verde).
- Roles A2 (autoridad municipal) como concepto de permiso: no existe una concesión específica; se asume el mismo permiso de consulta (decisión D1).
- Alcance real de A4 para consultas y escrituras compartidas (D1).
- Umbrales de CU-17–19 aprobados por el GAMW; si los valores provisionales coinciden con los institucionales: **desconocido**.
- Comportamiento de producción de las pantallas A4 (`/empresa/*`) bajo navegador: **no probado**.
- Rendimiento/volumen (CU-16/29/31): sin mediciones.

## 7. Priorización de CU incompletos (valor × dependencia × riesgo × esfuerzo)

| # | CU | Valor institucional | Dependencias | Riesgo si se deja | Esfuerzo | Bloqueo institucional |
|---|---|---|---|---|---|---|
| 1 | 22 (respuesta A4 en UI) | Alto: cierra la cadena correctiva con el actor real | CU-14, 22, 23 ya verificados | Medio | **S** | No |
| 2 | 20 (alta de inspección sin fotos) | Alto: antecedente de CU-21/22/23 | CU-21 | Alto (alta sin ninguna prueba) | **S–M** | Fotos: sí (se excluyen) |
| 3 | 26 (+27 sin validación oficial) | Medio-alto | CU-29 (conteo) | Alto (módulo sin pruebas) | **M** | Catálogo de valoración / anexos: sí (se excluyen) |
| 4 | 11/12/24 (UI A4 `/empresa/jornadas` bajo JWT) | Alto: actor real de la planificación | CU-11/12/24 | Alto (única vía de producción sin prueba de UI) | **M** | No |
| 5 | 17–19 (revisión GPS con fixtures) | Medio | CU-16 | Medio (sin pruebas, umbrales) | **M** | Umbrales: parcial |
| 6 | 04/05 (UI bajo JWT) | Medio | CU-11 | Medio | **S–M** | No |
| 7 | 15 (monitoreo) | Medio | CU-13 | Medio | **S** | A4: D1 |
| 8 | 02/03 | Medio | D1 | Alto | M | D1 |
| 9 | 14 móvil | Medio | CU-13 | Medio | S–M | No |
| — | 08, 25, 30, 31, 21-emisión, 23-catálogo | — | — | — | — | **Bloqueados / sin definición** |

## 8. Próximos 3 cortes verticales completos (mínimos, sin ampliar alcance)

1. **Respuesta del operador A4 a la acción correctiva, de extremo a extremo en el navegador (CU-22).**
   Alcance: `PaginaCorrectivasEmpresa` con JWT y concesión de periodo; E2E que complete incidencia → solicitud → **respuesta por la UI** → verificación (catálogo sintético de prueba); denegación de otro periodo en la UI. Sin código nuevo salvo defectos confirmados. Criterio: E2E verde en `browser-e2e.yml`.
2. **Alta de inspección de campo sin fotografías (CU-20) hasta el expediente (CU-21).**
   Alcance: `POST …/inspecciones` (JSON, sin archivos, política de fotos apagada) con prueba HTTP (idempotencia por `solicitudId`, 400/403/409) y E2E en `PaginaFiscalizacion`; la inspección aparece en el expediente y en CU-29. No emite resultado oficial.
3. **Registro y consulta de reclamo institucional sin validación oficial (CU-26) con su conteo en CU-29.**
   Alcance: registro textual (sin anexos), idempotencia, autorización `RECLAMO_REGISTRAR/CONSULTAR`, relación a jornada; IT HTTP + E2E; sin categorías de valoración (CU-27 queda bloqueado por catálogo).

**Siguiente CU recomendado: CU-22** (cierre del flujo correctivo con el actor A4 en la interfaz real; esfuerzo S, sin bloqueo institucional). Fuera de alcance explícito: CU-08, CU-25, CU-30, emisión oficial CU-21/23, habilitar CU-31.

## 10. Actualización — corte CU-22 (respuesta A4 por la UI)

`FlujoMunicipalNavegadorIT` (1/1, local) + `apps/web/e2e/flujo-correctivo.test.mjs` fase `1b`. Cadena verificada con JWT y concesiones de periodo (RF-19):

| Criterio (baseline CU-22 / reglas) | Prueba |
|---|---|
| A3 solicita la acción desde un antecedente (UI) | E2E fase 1 |
| A4 ve **solo** acciones de su periodo concedido; otro operador/periodo ve lista vacía y 403 por REST | E2E fase 1b (2 opciones vs 1) + `FlujoMunicipalNavegadorIT` |
| A4 inspecciona y responde **por la UI**; campos obligatorios sin envío; hora de Bolivia | E2E fase 1b (`checkValidity`, 0 peticiones con formulario incompleto) |
| Envío fallido + reintento = **misma solicitud**, una sola respuesta | E2E fase 1b (cuerpos idénticos) + DB `count=1` |
| Solicitud original, autor y fecha conservados; autor de la respuesta = operador | DB: `accion_correctiva` sin cambios; `usuario_id`; `registrada_en ≥ comunicada_en` |
| Contenido distinto con la misma solicitud → 409; fecha futura / observaciones vacías / campo de identidad → 400, sin registros | REST en la IT |
| La respuesta **no** es verificación ni cumplimiento; A4 no puede verificar (403) ni ve formulario de verificación | E2E (sin botón ni encabezado de verificación) + REST 403 + DB `verificacion_correctiva=0` |
| A3 verifica luego en la UI con categoría **sintética** | E2E fase 2 |

No hay defectos de producción confirmados en este corte. **No** se inventaron plazos, estados, adjuntos ni el procedimiento PENDING-07; la categoría de verificación es fixture de prueba. Pendiente institucional: estados/plazos de CU-22, adjuntos nuevos, rectificación, catálogo oficial (CU-23). Acuerdo GAMW: **PENDING** (solo verificación técnica).

## 11. Actualización — corte CU-20 (inspección sin fotografías) · RF-17

Verificado (local): `InspeccionCampoIT` 4/4 (JWT real, PostgreSQL/PostGIS aislado) + `FlujoMunicipalNavegadorIT` 1/1 con `apps/web/e2e/flujo-inspeccion.test.mjs` (Chromium real, REST real, planificación sembrada por SQL — **no** hecha por la UI).

| Criterio CU-20 | Estado | Prueba |
|---|---|---|
| CA-20-01 registro con jornada válida | **Verificado (subconjunto sin fotos)** | `InspeccionCampoIT.registraObservacion…`; E2E UI |
| CA-20-02 fecha, autor y observación originales conservados | **Verificado** | autor desde la sesión, `realizada_en` (Bolivia UTC−04:00) y `registrada_en` del servidor, observación y coordenadas [lon,lat] SRID 4326; inmutable (UPDATE/DELETE rechazados) |
| CA-20-03 archivos permitidos asociados | **No verificado / bloqueado** | fotografías deshabilitadas (política PENDING-09 sin aprobar); no se inventan retención ni privacidad |
| CA-20-04 archivo rechazado sin evidencia ficticia | **Verificado solo el rechazo** | con fotos deshabilitadas, el multipart con archivo → 409 sin inspección ni evidencia (`fotografiasPermanecenDeshabilitadas…`); validación de formatos/tamaño con política habilitada: sin prueba |
| CA-20-05 inspección sin fotografías con datos mínimos | **Verificado** | mínima = fecha + observación (coordenadas opcionales) |
| CA-20-06 no se reemplaza evidencia | **Verificado** | reintento idéntico = misma inspección (200, `registradaEn` igual); mismo `solicitudId` con otro contenido u otro actor = 409; UI: envío fallido + reintento = una fila |
| CA-20-07 sin resultado de fiscalización automático | **Verificado** | `fiscalizacion`/`accion_correctiva` = 0; UI sin «Resultado humano registrado» ni veredicto |
| CA-20-08 solo el fiscalizador autorizado | **Verificado** | sin `INSPECCION_REGISTRAR` (solo consulta / conductor) → 403; sin identidad 401/403; jornada inexistente 404 con permiso y 403 sin él |
| E1 jornada inexistente · E3 datos incompletos | **Verificado** | 404; 400 por latitud/longitud fuera de rango, solo una coordenada, sin observación/fecha/solicitud, precisión de fecha, campo de identidad ajeno, adjunto sin archivo; UI: formulario inválido no emite petición |
| Visible en expediente (CU-21) e historial (CU-29) | **Verificado** | expediente `inspecciones=1`, `fiscalizacion=null`; historial «Con registros (1)» con permiso propio (API y UI) |
| FA-02 vínculo con incidencia/reclamo · FA-03 anexo posterior · retención/privacidad | **No implementado / PENDING** | sin regla aprobada (PENDING-09); no se implementó |

No se detectaron defectos de producción. Se mantiene sin definir si una fecha de inspección futura debe rechazarse (no hay regla aprobada; no se inventó). Acuerdo GAMW: **PENDING**.

## 12. Actualización — corte CU-26 (reclamo institucional) · RF-21

Verificado (local): `ReclamoInstitucionalIT` 4/4 (JWT real, PostgreSQL/PostGIS aislado) + `FlujoMunicipalNavegadorIT` 1/1 con `apps/web/e2e/flujo-reclamo.test.mjs` (Chromium real, REST real; jornada y concesiones sembradas por SQL, no por la UI). Sin defectos de producción detectados.

| Criterio CU-26 / regla | Estado | Prueba |
|---|---|---|
| Canal declarado, recepción original, descripción, autor y registro conservados | **Verificado** | autor desde la sesión; `recibido_en` exacto; `registrado_en` del servidor posterior; ubicación opcional [lon,lat] SRID 4326; inmutable (UPDATE/DELETE rechazados) |
| Reclamo **sin** jornada identificada | **Verificado** | `jornadaId` nulo; no aparece en el conteo CU-29 de ninguna jornada |
| Con jornada: referencia existente + origen documentado | **Verificado** | sin referencias de origen → 400; jornada inexistente → 400 (API y UI: mensaje, sin registro); válido → 200 y cuenta 1 en CU-29 (con `RECLAMO_CONSULTAR`) |
| Campos obligatorios, fechas y coordenadas inválidas | **Verificado** | 400: falta solicitud/canal/fecha/descripción, canal vacío, fecha futura, nanosegundos, latitud/longitud fuera de rango, una sola coordenada, autor ajeno, campo de validación; UI: formulario vacío sin petición |
| Idempotencia, conflicto, inmutabilidad | **Verificado** | réplica idéntica = mismo reclamo y `registradoEn`; otro contenido u otro actor = 409; UI: envío fallido + reintento = misma `solicitudId`, un solo registro |
| Autorización | **Verificado** | solo consulta o sin permisos → 403; sin identidad 401/403; opciones: consultor no puede registrar ni revisar |
| Registrar **no** valida, no imputa incumplimiento ni crea acción correctiva/dictamen | **Verificado** | `validadoMunicipalmente=false`; 0 revisiones/relaciones; `accion_correctiva` sin variación; `fiscalizacion`=0; la UI no muestra validación |
| Anexos / datos personales / catálogo oficial de canales / duplicados entre canales / SLA | **No implementado / PENDING** | anexos deshabilitados sin política (`anexosHabilitados=false`); canal es texto declarado, no catálogo; sin portal ciudadano (CU-25) ni validación (CU-27) |

Acuerdo GAMW: **PENDING** (solo verificación técnica del registro textual).

## 13. Actualización — corte CU-11/CU-12 (planificación por el operador A4) · RF-07

Verificado (local): `PlanificacionOperadorNavegadorIT` 1/1 (Chromium real + REST real + PostgreSQL/PostGIS aislado), con `apps/web/e2e/flujo-planificacion.test.mjs`; regresión `JornadasIT` 11, `AutorizacionOperativaV19IT` 6, `CadenaOperativaFiscalizacionIT` 2, `HistorialServicioIT` 5. Empresas, periodos, ruta, vínculos y concesiones sembrados por SQL (no por la UI). Sin defectos de producción detectados.

| Criterio | Estado | Prueba |
|---|---|---|
| A4 ve y programa **solo** en periodos con concesión `JORNADA_PROGRAMAR`; otro operador ve solo el suyo y ninguna jornada ajena | **Verificado** | UI: selector con 1 periodo propio; otro operador: periodo propio y 0 jornadas; REST: programar en periodo ajeno/inexistente = 403 |
| Empresa derivada del periodo (no viaja en el cuerpo) | **Verificado** | cuerpo sin empresa; DB `periodo→empresa`; campo `empresaOperadoraId` = 400 |
| Versión de ruta válida y fechas programadas (Bolivia UTC−04:00) | **Verificado** | `ruta_version_id` y `inicio_programado` exactos; fuera de periodo = 400; fin ≤ inicio = 400; fecha anterior a la concesión sin histórico = 403; versión inexistente 400/404 |
| Referencias inmutables | **Verificado** | UPDATE de periodo/inicio/vínculos rechazado |
| Reintento tras fallo de transporte incierto | **Verificado** | UI: envío fallido + reintento = misma solicitud, UNA jornada (programar) y UNA asignación (asignar); conflicto: misma solicitud con otro contenido = 409 |
| Asignación a recursos **elegibles** de la empresa derivada | **Verificado** | UI ofrece solo vínculos de la empresa del periodo; excluye otra empresa, vehículo inactivo y vínculo vencido |
| Recursos incompatibles / duplicados | **Verificado** | otra empresa (vehículo o persona), inactivo, vencido, inexistente = 400/404; asignar con una abierta = 409; reasignar sin motivo = 400; el historial no cambió |
| Una sola asignación abierta, historial conservado, autoría | **Verificado** | tras reasignar: 2 filas, 1 abierta, la anterior cerrada al iniciar la nueva, motivo; 3 actuaciones (PROGRAMAR/ASIGNAR/REASIGNAR) con la cuenta autenticada |
| Acceso entre operadores | **Verificado** | otro operador: GET jornada, recursos y asignar = 403 |
| Programar/asignar no implica ejecución ni cumplimiento | **Verificado** | 0 eventos INICIO/FIN, 0 GPS, 0 fiscalización, 0 acciones correctivas; la UI no muestra veredicto ni «en ejecución» |
| Calendarios de disponibilidad, horarios oficiales, D1, contratos | **No implementado / PENDING** | no se inventaron |
| CU-24 (reprogramación) | **Cubierto en el corte siguiente** | ver §14 |

Nota CI (CU-26, Linux): el clic en «Registrar» fallaba en CI (runs `1631d79`, `8da3fb7`, `d7e4561`; el `DIAG` mostró el formulario con botones habilitados, o sea no era un estado deshabilitado). El mapa de reclamos (`adaptadorReferenciasEspaciales.ts`) no importaba `maplibre-gl.css` y las páginas se cargan de forma diferida, de modo que la hoja de estilo solo llegaba si otra página con mapa se había cargado antes. Se movió la importación al adaptador y la prueba comprueba que cada mapa quede contenido (`position: relative`, `overflow: hidden`). **Causa: hipótesis respaldada por código, NO reproducida en Linux**; queda por confirmar con el Browser E2E posterior al arreglo. Un estilo de mapa local sin red se mantiene en la prueba por aislamiento.

## 14. Actualización — corte CU-24 (reprogramación por el operador A4) · RF-20

Verificado (local): `ReprogramacionOperadorNavegadorIT` 1/1 (Chromium real + REST real + PostgreSQL/PostGIS aislado) con `apps/web/e2e/flujo-reprogramacion.test.mjs`. Sin cambios de código de producción: no se detectó ningún defecto. Empresas, periodos, ruta, vínculos, concesiones y las jornadas de partida (incluida una con evento INICIO) se siembran por SQL.

| Criterio | Estado | Prueba |
|---|---|---|
| Sucesora conserva periodo y versión; enlaza antecedente; registra motivo y fechas (Bolivia UTC−04:00) | **Verificado** | DB: J1/J2 con mismo periodo y versión, `jornada_antecedente_id`, motivo exacto e inicio exacto; el cuerpo UI no envía periodo/versión/empresa; REST rechaza esos campos (400) |
| Original, asignaciones, eventos y GPS no cambian | **Verificado** | fila de J0 idéntica salvo dejar de ser vigente; asignaciones de J0 idénticas; evento de la jornada con evidencia idéntico |
| Sucesora no hereda recursos, eventos, GPS, fiscalización ni acciones correctivas | **Verificado** | 0 filas en J1/J2; UI: «Sin asignaciones…» y `SIN_ASIGNACIONES`; sin veredicto ni «en ejecución» |
| Segunda reprogramación: cadena histórica completa | **Verificado** | UI recorre J2→J1→J0 con motivos; DB: 2 enlaces únicos y solo la última vigente; el sustituido no admite otra sucesora (409) |
| Motivo faltante / fechas inválidas | **Verificado** | UI: sin motivo no hay petición; fin anterior al inicio = 400; REST: sin motivo/en blanco/>10000, sin solicitud, sin inicio, fuera de periodo, precisión >µs = 400; antes de la concesión = 403 |
| Actores no autorizados | **Verificado** | otro operador y operador solo con consulta = 403 (la UI no ofrece el formulario); sin autenticar = 403 (denegado por defecto) |
| Duplicados, fallo de transporte, conflicto | **Verificado** | UI: envío fallido + reintento = misma solicitud y una sucesora; REST: duplicado = 200 con la misma sucesora; mismo ID con otro contenido o fecha = 409; solicitud ajena = 403 |
| Sucesoras concurrentes | **Verificado** | dos solicitudes distintas sobre el mismo antecedente: 200 y 409, una sola sucesora; misma solicitud concurrente: sin 5xx, una sucesora y reintento idempotente |
| Jornadas ejecutadas o fiscalizadas | **Bloqueadas (según reglas vigentes)** | con evento: UI sin formulario y REST 409; sin sucesora ni traslado de evidencia |
| Política oficial de reprogramación, aprobación automática, D1 | **PENDING / no implementado** | no se inventaron; el procedimiento institucional para jornadas con evidencia sigue pendiente |

Observación: la prueba de GPS/fiscalización sembrada como evidencia usa un evento de ejecución; el bloqueo por `posicion_gps`/`fiscalizacion` está en la misma consulta (`tieneEvidencia`) pero no se sembró cada tipo por separado.

## 15. Actualización — corte CU-15 (monitoreo municipal de últimas posiciones) · RF-12

Verificado (local): `MonitoreoMunicipalNavegadorIT` 1/1 (Chromium real + REST real + PostgreSQL/PostGIS aislado, `gamw.monitoreo.antiguedad-segundos=600`, consulta cada 5 s). Sin cambios de código de producción: no se detectó ningún defecto. Datos sintéticos sembrados por SQL. Conteos de la matriz sin cambio (CU-15 permanece PARTIAL).

| Criterio | Estado | Prueba |
|---|---|---|
| A3 ve solo jornadas con INICIO y sin FIN | **Verificado** | las 5 jornadas sembradas con INICIO y sin FIN (por identificador exacto) aparecen una sola vez; la cerrada y la no iniciada no aparecen (REST y UI). La base de la JVM es compartida con otras pruebas, que pueden dejar jornadas activas legítimas: la prueba no asume el total y recorre todas las páginas (corrección tras el fallo de CI 44450526391, que esperaba exactamente 5 y recibió 6) |
| Última posición por `capturado_en` (no `recibido_en`), incluida sincronización tardía | **Verificado** | muestra recibida después pero capturada antes no gana (REST y UI) |
| Desempate estable | **Verificado** | empate de captura: gana `captura_id` mayor (los `id` de fila no se probaron como segundo desempate) |
| Captura, recepción, coordenadas, precisión y antigüedad | **Verificado** | valores exactos en REST y en la fila; jornada antigua rotulada «Sin lectura reciente» |
| Jornada sin GPS / antigua / cerrada | **Verificado** | «Sin posiciones recibidas», «Ubicación no disponible», «Sin dato»; sin texto de detención, omisión, fraude o incumplimiento en la tabla |
| Consulta periódica sin seguimiento instantáneo | **Verificado** | UI rotula «No es seguimiento instantáneo»; la segunda petición ocurre sola; no se fabrican posiciones |
| Red no disponible | **Verificado** | navegador sin red: mensaje y evidencia conservada; al volver la red, se limpia |
| Sesión vencida | **Verificado (simulado)** | 401 simulado en la UI cierra la sesión y borra la evidencia; la expiración real del JWT no se probó aquí |
| Sin acceso / sin exposición entre operadores | **Verificado** | sin token y token inválido 401/403; cuenta sin permiso y A4 = 403 sin datos; permiso revocado = 403; consultar no modifica evidencia |
| D1: A4 sin permisos de monitoreo | **Preservado** | A4 no tiene concesión de monitoreo; no se concedió ninguno |
| Mapa: contención, referencia histórica seleccionable y respaldo tabular | **Verificado parcialmente** | región del mapa contenida y selector de ruta; los **marcadores WebGL no se afirman por DOM** (no verificados) |
| Continuidad con ejecución móvil real (CU-13) | **PENDING** | no cubierto en este corte |

## 16. Actualización — corte CU-16 (trayectoria GPS municipal) · RF-12

Verificado (local): `TrayectoriaMunicipalNavegadorIT` 1/1 (Chromium real + REST real + PostgreSQL/PostGIS aislado) con `apps/web/e2e/flujo-trayectoria.test.mjs`. Sin cambios de código de producción: no se detectó ningún defecto. Conteos de la matriz sin cambio (CU-16 permanece PARTIAL). Datos sintéticos sembrados por SQL.

| Criterio | Estado | Prueba |
|---|---|---|
| Cero, una y varias muestras | **Verificado** | UI: mensaje sin fabricar ubicación ni concluir incumplimiento; «Una muestra aislada no representa un recorrido continuo»; 4 filas |
| Orden determinista por captura, recepción tardía, empate | **Verificado** | UI y REST: orden por `capturado_en`, desempate `captura_id`; la capturada primero y recibida última conserva su hora de recepción; muestra tardía posterior se ordena por su captura |
| [longitud, latitud], marcas de tiempo, precisión y asignación original | **Verificado** | REST (GeoJSON) y columnas de la tabla |
| RutaVersion histórica tras cambios posteriores de ruta | **Verificado** | v1 cerrada y v2 abierta después; las jornadas anteriores devuelven v1 (2 puntos) y la nueva v2 (3 puntos), UI y REST |
| Paginación, corte de recepción estable, límites | **Verificado** | UI con 505 muestras: 500 + 5, «Vista paginada…», sin repetidas, última página con «siguiente» deshabilitado; REST: una recepción posterior al corte no desplaza las páginas; límites/corte futuro/formato inválido = 400 |
| Intervalos sin muestras no se muestran como recorrido | **Verificado (rotulado)** | texto «no acreditan recorrido»; no se unen puntos por defecto |
| Línea inferida | **Verificado** | apagada por defecto; al activarla, rótulo «conexiones visuales inferidas, no recorrido físico confirmado» |
| Acceso, inexistentes y revocación | **Verificado** | sin token 401/403; A4 y cuenta sin permiso = 403 (incluso antes de revelar existencia) sin datos; jornada inexistente 404; UUID inválido 400; permiso revocado = 403 |
| Consulta no edita ni dictamina | **Verificado** | solo peticiones GET desde la UI; recuento de GPS/eventos intacto; evidencia inmutable; sin veredicto |
| D1: A4 sin permisos de trayectoria | **Preservado** | no se concedió ninguno |
| Mapa | **Parcial** | contención del mapa verificada; **marcadores/líneas WebGL no verificados** sin evidencia visual |

## 17. Actualización — corte CU-17 (continuidad e integridad GPS) · RF-13

Verificado (local): `IntegridadGpsNavegadorIT` 1/1 (Chromium real + REST real + PostgreSQL/PostGIS aislado) con `apps/web/e2e/flujo-integridad-gps.test.mjs`. Sin cambios de código de producción: no se detectó ningún defecto. Conteos sin cambio (CU-17 permanece PARTIAL). Los umbrales son **provisionales y explícitos en la prueba** (separación 60 s, precisión 50 m, velocidad 120 km/h); no se inventó ninguna política (PENDING-04). CU-18/19 no se tocaron.

| Criterio | Estado | Prueba |
|---|---|---|
| Huecos antes (INICIO), entre y después (FIN) de capturas | **Verificado** | INICIO_SIN_MUESTRAS 200 s, dos INTERVALO_SIN_MUESTRAS, FIN_SIN_MUESTRAS_RECIENTES 600 s (UI y REST) |
| Recepción diferida/anterior, orden cronológico, precisión baja, mismo instante, salto improbable | **Verificado** | RECEPCION_DIFERIDA 170 s, RECEPCION_ANTERIOR_CAPTURA −5 s, orden por captura con desempate, PRECISION_LIMITADA 80 m, POSICIONES_DISTINTAS_MISMO_INSTANTE, SALTO_POR_REVISAR > 120 km/h |
| Cero o insuficientes muestras | **Verificado** | EVIDENCIA_INSUFICIENTE (0 y 1 muestra), sin veredicto |
| Límites exactos | **Verificado** | hueco = 60 s y precisión = 50 m no son señal; con 59,9 / 49,9 sí |
| Metadatos de dispositivo | **Verificado (informado como no disponible)** | el texto es «NO DISPONIBLES; no se inventan» (no existe un valor UNKNOWN literal); no se presenta GPS como auténtico |
| Captura tardía | **Verificado** | el recálculo cambia señales (el intervalo de 70 s se divide; la tardía aparece como diferida) y la evidencia original queda idéntica |
| Límite de 5000 muestras | **Verificado (REST)** | 5001 disponibles, 5000 analizadas, `parcial` y REVISION_PARCIAL; no se probó en la UI |
| Parámetros inválidos / tipo / inexistente | **Verificado** | 0, negativos, NaN, Infinity, >1 000 000, texto = 400; tipo no admitido 400; jornada inexistente 404; UI sin petición con umbral 0 |
| Acceso y revocación | **Verificado** | sin token 401/403; A4, cuenta sin permiso y permiso de OTRA revisión = 403 sin datos; revocado = 403 |
| Huecos no se atribuyen a Internet, detención, fraude o incumplimiento | **Verificado** | todas las señales POR_REVISAR; textos «no se conoce la causa», «no equivale a GPS desactivado…»; sin veredictos |
| Solo lectura | **Verificado** | UI solo GET; recuento de GPS igual; evidencia inmutable |
| Umbrales institucionales (PENDING-04) y GPS físico | **PENDING** | no resueltos |

## 18. Actualización — corte CU-18 (coincidencias espaciales) · RF-14

Verificado (local): `CoincidenciasEspacialesNavegadorIT` 1/1 (Chromium real + REST real + PostgreSQL/PostGIS aislado) con `apps/web/e2e/flujo-coincidencias-gps.test.mjs`. Sin cambios de código de producción: no se detectó ningún defecto. Conteos sin cambio (CU-18 permanece PARTIAL). Radios (50 y 100 m), geocerca y tolerancia de precisión (50 m; 3 m en una variante) son **provisionales y explícitos en la prueba**; no se inventó política del GAMW. CU-19 no se tocó. La base de la JVM es compartida: la prueba solo verifica los identificadores sembrados y no asume totales globales.

| Criterio | Estado | Prueba |
|---|---|---|
| Punto con radio explícito (ST_DWithin, geography, metros) | **Verificado** | coincidencia observada, sin coincidencia y radio exacto: radio = distancia calculada → observada (límite inclusivo); 0,01 m por fuera → insuficiente por precisión; 10 m por fuera → sin coincidencia |
| Geocerca (ST_Covers) | **Verificado** | interior y borde exacto del polígono = observada; exterior = no; «Geocerca poligonal sin radio» |
| EPSG:4326, distancias en metros, precisión reportada | **Verificado** | `distanciaMinimaMetros` igual a la distancia geography calculada; muestra de 60 m de precisión hace incierto un control a 100 m con radio 50 m |
| Cuatro resultados distintos | **Verificado** | COINCIDENCIA_OBSERVADA, SIN_COINCIDENCIA_OBSERVADA, EVIDENCIA_INSUFICIENTE (sin muestras aplicables, sin muestras precisas o proximidad incierta) |
| RutaVersion correcta y versiones históricas de la referencia | **Verificado** | un control de otra RutaVersion y uno con vigencia futura no aparecen ni afectan; dos versiones de una misma referencia se evalúan por separado |
| Vigencia por `capturado_en` (no por recepción) | **Verificado** | una muestra capturada antes de `vigente_desde` pero recibida después no cuenta; `vigente_desde` inclusivo y `vigente_hasta` exclusivo en la captura exacta |
| Sincronización tardía y recálculo | **Verificado** | una captura tardía cambia conteos (6 muestras, 3 observadas) sin alterar la evidencia original; las ventanas posteriores no cambian |
| Cero muestras / cero referencias / límites | **Verificado** | **Corregido en el corte CU-19:** antes ambos casos devolvían EVIDENCIA_INSUFICIENTE_SIN_REFERENCIAS_APLICABLES. Ahora, si no hay referencias, se mantiene EVIDENCIA_INSUFICIENTE_SIN_REFERENCIAS_APLICABLES con «Sin referencias aplicables…»; si hay referencias pero ninguna muestra aplicable (cero muestras o fuera de vigencia), EVIDENCIA_INSUFICIENTE_SIN_MUESTRAS_APLICABLES. 101 controles → 100 mostrados, LIMITE_REFERENCIAS y REVISION_PARCIAL; 5001 muestras → 5000 analizadas, parcial |
| Trazabilidad | **Verificado** | marcas de captura/recepción originales, precisión, identificador de la versión de referencia, primera/última coincidencia |
| Acceso, revocación, inexistente, solo lectura | **Verificado** | sin token 401/403; A4, cuenta sin permiso y permiso de OTRA revisión = 403 sin datos; revocado = 403; jornada inexistente 404; parámetros inválidos = 400; GPS y referencias intactos; referencia histórica inmutable |
| No convierte coincidencia en recolección, presencia ni cumplimiento | **Verificado** | la UI y la política lo declaran; sin esos términos en la pantalla |
| Observación | **Anotada** | una coincidencia observada con baja precisión sigue clasificándose como observada (la precisión se informa en la limitación, no cambia el estado); no se modificó: sería una decisión de política |
| Reglas institucionales de puntos y GPS físico | **PENDING** | no resueltos |

## 19. Actualización — corte CU-19 (separaciones y detenciones candidatas) · RF-15

Verificado (local): `DesviosDetencionesNavegadorIT` 1/1 (Chromium real + REST real + PostgreSQL/PostGIS aislado) con `apps/web/e2e/flujo-desvios-gps.test.mjs`. Suite completa de `browser-e2e.yml` (10 clases) en verde en una misma JVM con base compartida. Conteos sin cambio (CU-19 y CU-18 permanecen PARTIAL). Umbrales provisionales explícitos: separación de ruta 100 m, precisión 50 m, radio de agrupación 25 m, duración 180 s, hueco 120 s; no se inventaron criterios del GAMW.

**Defectos de producción corregidos (`RevisarEvidenciaGps`) con regresión:**
1. CU-19: con 0 o 1 muestra, el estado `EVIDENCIA_INSUFICIENTE` se reemplazaba por `REVISION_PARCIAL_DETENCIONES_INDETERMINADAS`, como si se hubiese revisado algo. Ahora queda `EVIDENCIA_INSUFICIENTE`.
2. CU-18: «sin referencias» y «referencias sin muestras aplicables» compartían el estado `EVIDENCIA_INSUFICIENTE_SIN_REFERENCIAS_APLICABLES`. El caso sin referencias conserva ese valor; el otro pasa a `EVIDENCIA_INSUFICIENTE_SIN_MUESTRAS_APLICABLES` (cubierto en `CoincidenciasEspacialesNavegadorIT`, incluida una referencia con vigencia posterior a todas las muestras).

| Criterio | Estado | Prueba |
|---|---|---|
| Distancia en metros a la RutaVersion histórica | **Verificado** | valor del hallazgo igual a la distancia geography calculada (~212 m); `rutaVersionId` y geometría de referencia = versión histórica aunque la ruta tenga una versión posterior; RutaVersion inmutable |
| Umbral, límite exacto e incertidumbre por precisión | **Verificado** | distancia = umbral: no es señal; 0,001 m por dentro: SEPARACION_INDETERMINADA_POR_PRECISION; 10 m: DESVIO_CANDIDATO; muestras con precisión 50 y 80 m: indeterminadas |
| Detención candidata | **Verificado** | 4 muestras precisas a ~0,5 m, 200 s: una DETENCION_CANDIDATA con desde/hasta y capturas; duración = umbral (200 s) inclusiva, 201 s no |
| Falsos candidatos rechazados | **Verificado** | huecos de 150 s, instante duplicado, precisión baja (30 m > radio 25 m), dispersión de 33 m y 3 muestras en 170 s: ninguna candidata; con hueco de 150 s sí agrupa (el umbral es el que decide) |
| Orden, sincronización tardía y recálculo | **Verificado** | inserción desordenada y muestra recibida mucho después: orden por captura; una captura tardía convierte 170 s en 250 s → nueva candidata; evidencia original intacta |
| Cero candidatos / insuficiencia / truncamiento | **Verificado** | «Sin señales listadas…» con REVISION_TECNICA; 0 y 1 muestra: EVIDENCIA_INSUFICIENTE; 5001 muestras: 5000 analizadas, REVISION_PARCIAL y candidata acotada a la muestra 5000 |
| Parámetros inválidos, acceso, revocación | **Verificado** | 0, negativos, NaN, >1 000 000, texto = 400; UI sin petición con radio 0; inexistente 404; sin token 401/403; A4, cuenta sin permiso y permiso de OTRA revisión = 403 sin datos; revocado = 403 |
| Sin veredictos automáticos | **Verificado** | todas POR_REVISAR; detalles «no prueba camión detenido», «no tramo recorrido ni incumplimiento»; sin fraude, vehículo detenido, recolección omitida, sanción ni cumplimiento |
| Contexto de incidencias asociadas | **Actualizado en §21 (H-08)** | antes: brecha. Ahora: contexto de solo lectura de las incidencias ya registradas de la jornada, con el permiso existente CORRECTIVA_CONSULTAR, sin asociación automática ni inferencia causal; la aceptación institucional sigue pendiente |
| Criterios institucionales y GPS físico | **PENDING** | no resueltos |

## 20. Intento de validación física CU-13 / CA-4A-21 (2026-11-19) — **BLOCKED**

- Comando: `adb devices -l` (SDK local `platform-tools`) → **ningún dispositivo conectado**. Solo existe el AVD `Medium_Phone` (emulador: GPS sintético, ya cubierto por `android-emulator-qa.yml`; **no** sustituye la QA física).
- Sin dispositivo no se ejecutó: permiso real de ubicación, captura/precisión/marcas reales, pérdida de Internet con GPS activo, durabilidad tras reinicio del proceso, sincronización idempotente por REST real, `capturadoEn` ≠ `recibidoEn` reales, evidencia en PostGIS/monitoreo ni FIN con posiciones pendientes. Todos esos criterios quedan **BLOCKED** (no PASS). No se modificó código ni datos del dispositivo.
- CI: el Browser E2E de `43f0ca4` falló en el paso de instalación de PostgreSQL/PostGIS por `apt` (código 100; fallo externo de paquetes, las pruebas no llegaron a ejecutarse); no es una regresión de la aplicación y no se cambió código para sortearlo.
- CU-13 sigue **PARTIAL** (sin cambio de clasificación).

## 21. Remediación de la auditoría estática 2026-11-19 (H-01…H-12)

Base `4dd9d75`. Pruebas locales: backend focal (eventos/GPS/Flyway, autenticación, revisión GPS) y E2E de navegador con base compartida; web `tsc` + build + vitest. **Flutter no se ejecutó localmente** (el guardián del entorno bloquea comandos Flutter): `flutter analyze`/`flutter test` y la compilación Kotlin quedan a cargo de CI (`verify.yml`; `android-emulator-qa.yml` ahora se dispara también con cambios en el código nativo).

| Hallazgo | Estado | Corrección | Prueba / evidencia |
|---|---|---|---|
| H-01 revisión GPS obsoleta | **FIXED** | cancelar e invalidar consultas al cambiar jornada, tipo, umbral o sesión; clave inmutable de consulta; 401 cierra sesión | `PaginaRevisionGps.test.tsx` 6/6 (respuestas demoradas, cambios rápidos, error tardío, desmontaje, 401/403); E2E CU-17/18/19 |
| H-02 notificaciones bloqueaban GPS | **FIXED (sin QA física)** | `POST_NOTIFICATIONS` ya no condiciona el servicio en primer plano; se advierte si el aviso puede no mostrarse | `captura_gps_android_test.dart` (combinaciones FINE/notificaciones); pendiente CI Flutter y dispositivo real |
| H-03 DELETE de INICIO/FIN | **FIXED** | `V20__eventos_ejecucion_sin_borrado.sql`: DELETE y TRUNCATE bloqueados (eventos) y TRUNCATE (GPS); V7 intacta | `EventosEjecucionIT` (UPDATE/DELETE/TRUNCATE fallan; FIN posterior e idempotencia siguen); Flyway limpio |
| H-04 solo ubicación aproximada | **FIXED (sin QA física)** | GPS_PROVIDER exige FINE; con solo COARSE no se inicia ni se declara captura y se informa el motivo | pruebas Dart de decisión y canal; pendiente dispositivo real |
| H-05 fuerza bruta en login | **FIXED (técnico) + decisión de despliegue pendiente** | freno por (origen, nombre) y por origen, ventana configurable, 429 + Retry-After uniforme; BCrypt también para cuentas inexistentes/inactivas. No es bloqueo de cuenta. Varios nodos/proxy: requiere control de infraestructura | `LimiteIntentosAccesoHttpTests` 3/3, `LimitadorIntentosAccesoTests` 2/2, `ServicioAutenticacionTests` 8/8 |
| H-06 mapa base de demostración | **FIXED (técnico) + proveedor pendiente** | en producción sin `VITE_MAP_STYLE_URL`: estilo local sin teselas externas y aviso; tabla intacta; `.env.example` documentado | `mapConfig.test.ts` 3/3; build OK |
| H-07 incidencias offline CU-14 | **BLOCKED (política institucional)** | sin cola durable (no aprobada); mensaje aclarado: la solicitud pendiente solo se conserva con el formulario abierto | revisión de código; sin cambio de alcance |
| H-08 contexto de incidencias CU-19 | **FIXED (técnico)** | consulta de solo lectura con endpoint y permiso existentes (CORRECTIVA_CONSULTAR); sin asociación ni causalidad | `DesviosDetencionesNavegadorIT` (sin permiso: no expone; con permiso: muestra; señales intactas) |
| H-09 matriz contradictoria | **FIXED** | §2 y conclusión alineadas con 6/21/3/1, conteos históricos rotulados | revisión de las 31 filas |
| H-10 apt 503 en CI | **RISK (infraestructura)** | sin cambio en Browser E2E (no se reemplaza PostgreSQL/PostGIS ni se usa Docker); la falla fue externa | runs posteriores en verde |
| H-11 marcadores WebGL | **GAP** | se mantiene: las pruebas verifican tablas y contención del mapa, no píxeles; no se afirma verificación visual | — |
| H-12 GPS físico | **BLOCKED** | sin dispositivo (§20); el emulador no sustituye la QA de campo | — |

**CI de `cbf97d6` (resultado real):** Browser E2E (run 44537269606) **PASS**; job `verify` (backend unitario, web y `flutter analyze` + `flutter test`, incluidas las pruebas Dart de H-02/H-04) **PASS**; `backend-integration` (run 44537269267) **FAIL antes de ejecutar pruebas**: no se resolvió `maven-failsafe-plugin:3.5.6` (versión existente en Maven Central; resuelta en ejecuciones anteriores con el mismo `pom.xml`); `emulator-qa` (run 44537269459) **FAIL** en Gradle al resolver dependencias Kotlin 2.0.21. Ambos fallos fueron de resolución remota simultánea y sin cambios de declaración de repositorios o versiones: se clasifican como **infraestructura transitoria (no reproducida localmente)**. Corrección mínima: paso previo de resolución con reintentos acotados (Maven) y compilación `flutter build apk --debug` con reintentos y `GRADLE_OPTS` de reintento (Gradle), que además compila el Kotlin nativo; las pruebas no se omiten. La compilación Kotlin local **no** fue posible (el SDK Flutter local exige Gradle ≥ 9.3.1, distinto del 3.35.4 de CI): H-02/H-04 nativo queda verificado solo cuando pase `android-emulator-qa`.

**CI posterior:** `ba279e0` — Engineering foundation (44542445640) **PASS** (incluye `backend-integration`); Android emulator QA (44542445731) **FAIL** solo en la instalación de la imagen API 34 («Error on ZipFile unknown archive»), con `app-debug.apk` ya compilado (Kotlin de H-02/H-04 compila en CI). `55f87d6` — Android emulator QA (44544582821) **PASS** con la imagen instalada y verificada antes del emulador; `backend-integration` (44544583001) **FAIL** en el paso de resolución (reintentos sobre la resolución fallida en caché, sin `-U`; pruebas no ejecutadas). Corrección siguiente: `-U`, coordenadas explícitas `maven-dependency-plugin:3.10.0` y reintento solo ante errores de descarga/red. Verificación local de la suite sin cambios (`-Ppostgres-integration verify`, mismas 11 IT): 98 pruebas unitarias + 66 de integración, 0 fallos.

Riesgo adicional registrado (no corregido, requiere decisión): las tablas `jornada`, `asignacion_jornada` y `ruta_version` protegen UPDATE pero no DELETE de filas sin evidencia dependiente, y el rol de la aplicación es dueño del esquema (podría desactivar triggers). Separar rol de migración y rol de ejecución es decisión de despliegue.

## 22. Integridad histórica (corte INT, 2026-11-19) — `V21__integridad_historica_sin_borrado.sql`

Fuente: `AUDITORIA_INTEGRIDAD_HISTORICA_GAMW_20261119.md` (revisión estática de `5e18005`). Cada hallazgo se validó contra el código y V1–V20, que no se modifican.

| ID | Hallazgo validado | Estado | Corrección / evidencia |
|---|---|---|---|
| INT-01 | `jornada` y `asignacion_jornada` protegían solo UPDATE (V6); un DELETE SQL directo borraba filas sin dependientes | **FIXED** | triggers `BEFORE DELETE` (V21). `IntegridadHistoricaIT`: DELETE de filas **sin dependientes** rechazado por el trigger (mensaje «no se borra»), no por clave foránea |
| INT-02 | `ruta_version` protegía solo UPDATE (V2/V3) | **FIXED** | `BEFORE DELETE` en `ruta_version` (V21); prueba con versión sin jornadas |
| INT-03 | TRUNCATE eludía los triggers de fila de 26 tablas de antecedentes inmutables | **FIXED** | `BEFORE TRUNCATE` de sentencia en esas 26 + `jornada`, `asignacion_jornada`, `ruta_version` (eventos y GPS desde V20); `TRUNCATE ... CASCADE` rechazado; verificación en `pg_trigger` de las 31 tablas |
| INT-04 | rol de ejecución y de migración sin separación (el rol de desarrollo es dueño del esquema; `application.yml` usa las mismas credenciales para Flyway) | **FIXED en desarrollo y pruebas aisladas; PENDING en producción (decisión de despliegue)** — identidad de migración separable (`SPRING_FLYWAY_USER/PASSWORD`), callback `db/privilegios/afterMigrate.sql` que concede mínimo privilegio y falla ante roles inválidos, rol de ejecución no propietario en el arnés y en `compose.dev.yml` (opt-in). `MinimoPrivilegioEjecucionIT` 3/3 (rechazo `42501` de DELETE, TRUNCATE, DISABLE/DROP TRIGGER, DROP CONSTRAINT, funciones y DDL; Flyway con la identidad de migración) y 9 clases `*EjecucionNoPropietariaIT` (56 pruebas de flujos existentes como rol no propietario). Runbook: `docs/despliegue/RUNBOOK_INT04_ROLES_BD.md`. Antes: | propuesta sin cambiar la política: rol de **migración** propietario (solo Flyway) y rol de **ejecución** no propietario con `SELECT/INSERT`, `UPDATE` solo de las columnas de cierre/sustitución/revocación, sin `DELETE`, `TRUNCATE`, `TRIGGER`, `REFERENCES` ni DDL, y `REVOKE` a `PUBLIC`. Validación con las consultas de solo lectura de la auditoría en la instalación objetivo: **no ejecutada** (sin base real). El código de producción no contiene `DELETE` ni `TRUNCATE` |
| INT-05 | sin clave compuesta jornada↔asignación en eventos, GPS e incidencias | **FIXED** | `UNIQUE(id, jornada_id)` y 3 FK compuestas; V21 ejecuta antes la consulta de anti-coincidencia de la auditoría y **se detiene** si hay inconsistencias (no reasigna ni borra); claves `convalidated`; prueba con asignación de otra jornada rechazada y de la misma aceptada |
| INT-06 | limpieza destructiva de fixtures | **FIXED (solo pruebas)** | `JornadasIT` y `RutasReferenciaIT` sin borrado (identificadores aleatorios en clúster efímero); los E2E que eligen jornada en listados paginados avanzan con la paginación real (`e2e/paginacion.mjs`) en lugar de asumir la primera página; ninguna aserción ni protección se debilita |
| INT-07 | vigencias de versiones de una ruta podían superponerse por SQL directo | **FIXED (defensa en profundidad)** | trigger `BEFORE INSERT OR UPDATE` con bloqueo de la ruta que rechaza intervalos `[desde, hasta)` superpuestos (sin extensiones nuevas); verificación previa de datos existentes; prueba: contiguas aceptadas, superpuesta rechazada |
| — | operaciones legítimas | **Preservado** | sustitución de jornada (reprogramación), primer cierre de asignación (segundo rechazado) y cierre de versión de ruta siguen funcionando |
| — | retención/depuración institucional | **PENDING (política)** | no se inventa; ninguna migración define borrado administrativo |

Pruebas locales: `IntegridadHistoricaIT` 4/4; perfil `postgres-integration` (11 IT de CI + `IntegridadHistoricaIT`): 98 unitarias + 69 de integración, 0 fallos; 20 clases de integración y E2E de navegador con base compartida (148 pruebas) + `ReclamoInstitucionalIT`, `InspeccionCampoIT`, `VehiculosIT`, `RegistroOperativoIT`, `ContextoMisJornadasIT`, `IdentidadPostgresIT` en verde. Riesgo restante: la tabla `ruta` (cabecera) admite DELETE cuando no tiene versiones (sin historia dependiente); no se cambió.

## 23. Cierre funcional F1 — CU-04 empresas y CU-05 periodos (A1, JWT) · RF-03

Verificado (local): `EmpresasPeriodosNavegadorIT` 1/1 (Chromium real + REST real + PostgreSQL/PostGIS aislado, JWT y concesiones municipales explícitas `EMPRESAS_*`/`PERIODOS_*`) con `apps/web/e2e/flujo-empresas-periodos.test.mjs`; regresión `RegistroOperativoIT` 11, `RegistroPlanificacionWebIT` 2, `VehiculosIT` 8, `JornadasIT` 11; web `tsc` + build + vitest 73.

**Defectos corregidos (probados en este corte):**
1. *Interfaz:* la página cargaba empresas, personas y funciones con `Promise.all`; un A1 sin `PERSONAS_*` (permiso independiente) no veía ninguna empresa. Ahora cada sección se carga por separado e informa su propia falta de permiso.
2. *CU-05 E3 / CA-CU-05-06:* corregir las fechas de un periodo podía dejar fuera jornadas ya registradas en él. Ahora responde **409** si alguna jornada del periodo quedaría fuera (misma regla de fecha de servicio America/La_Paz que la programación; bloqueo de la fila del periodo). No es una política de solapamiento.
3. *Remediación F1 (auditoría 2026-11-19):* (H1) la programación/reprogramación/asignación leía periodo y empresa sin bloqueo; ahora los lee con `FOR SHARE` dentro de su transacción (orden estable periodo → empresa, también en la corrección del periodo). (H2) fallo de persistencia → 503 explícito. (H3) mensaje 400 por recurso. Regresión local: 22 clases IT, 107 pruebas en verde (incluye todas las `*EjecucionNoPropietariaIT`, `MinimoPrivilegioEjecucionIT`, `IntegridadHistoricaIT`, navegador de planificación/reprogramación/empresas).

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-04-01 registrar con razón social válida; obligatoria | **Verificado** | UI (sin petición si falta) y REST 400 |
| CA-04-02 NIT único | **Verificado** | UI muestra el rechazo; REST 409; una sola fila |
| CA-04-03 varios periodos sin duplicar identidad | **Verificado** | dos periodos coexistentes de la misma empresa |
| CA-04-04 desactivación preserva periodos, jornadas y vínculos de personal | **Verificado** | empresa inactiva sigue listada; su periodo, la jornada y el vínculo de personal (fila y función) intactos; personal consultable (200) |
| CA-04-05 modificar no reescribe referencias | **Verificado** | edición de nombre comercial; id generado por el servidor (un id enviado se ignora) |
| CA-04-06 sin eliminación física | **Verificado** | sin endpoint DELETE (403/405) |
| CA-04-07 / CA-05-07 fallo de persistencia | **Verificado** (inyección en prueba) | `FalloPersistenciaRegistroOperativoIT`: fallo inyectado *después* de ejecutar la escritura real (actualizar/estado de empresa; corregir/estado/crear periodo) → HTTP 503 «se revirtió», fila anterior idéntica, ningún periodo a medias. Antes llegaba a `/error` (cerrado por seguridad) y respondía un 403 engañoso |
| Concurrencia CU-05 ↔ CU-11/CU-24 (auditoría F1 H1) | **Verificado** | `ConcurrenciaPeriodoJornadaIT` (+ variante rol no propietario INT-04), transacciones intercaladas de forma determinista: corrección en curso → la programación espera y se rechaza contra las fechas confirmadas; programación en curso → la corrección incompatible responde 409; desactivación de empresa en curso → no se programa; dos programaciones del mismo periodo no se bloquean. Reproducido como fallo con el código anterior (2 casos) |
| Mensaje 400 de JSON malformado (H3) | **Verificado** | detalle propio de empresa/periodo, sin la explicación de vínculos |
| CA-05-01 periodo vinculado a empresa registrada | **Verificado** | empresa inexistente 404; periodo de otra empresa 404 |
| CA-05-02 fechas inconsistentes | **Verificado** | UI muestra el rechazo; REST 400 |
| CA-05-03 consulta de periodos de empresa inactiva | **Verificado** | REST 200 y UI |
| CA-05-04 jornadas mantienen su periodo | **Verificado** | `periodo_servicio_id` intacto tras correcciones |
| CA-05-05 sin restricción de solapamiento inventada | **Verificado** | periodos solapados aceptados |
| CA-05-06 corrección incompatible rechazada | **Verificado** | UI y REST 409; límite exacto (fecha de servicio = fin) aceptado |
| Autorización | **Verificado** | lector (`*_CONSULTAR`) consulta y no modifica (403); cuenta sin permiso y operador A4 = 403; sin token 401/403 |
| D1 (actores autorizados en producción), obligatoriedad/formato del NIT, política de solapamiento | **PENDING** | no se inventaron |

## 9. Distinción final

Los 8 CU «IMPLEMENTED» (01, 04, 05, 11, 12, 22, 24, 29) lo son en sentido **técnico**. Ningún CU tiene aceptación institucional; GPS físico, batería y comportamiento por fabricante siguen **PENDING** (QA en dispositivo real).
