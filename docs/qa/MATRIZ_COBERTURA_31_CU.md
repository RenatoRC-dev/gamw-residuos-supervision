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
| IMPLEMENTED | **10** | 01, 04, 05, 06, 07, 11, 12, 22, 24, 29 (22 desde §10; 11/12 desde §13; 24 desde §14; 04/05 desde §23; 06/07 desde §24) |
| PARTIAL | **18** | 02, 03, 09, 10, 13, 14, 15, 16, 17, 18, 19, 20, 21, 23, 26, 27, 28, 30 (30 desde §26) |
| INSTITUTIONALLY BLOCKED | **3** | 08, 25, 31 (31: código listo, acceso denegado por D1) |
| NOT IMPLEMENTED | **0** | — |

Total 31 (verificado fila por fila contra la tabla de la sección siguiente, corte §26). **IMPLEMENTED no implica aceptación**: es verificación técnica automatizada; ningún CU está aceptado por el GAMW. Conteos históricos de esta tabla: 10 / 17 / 3 / 1 (tras §24/§25), 8 / 19 / 3 / 1 (tras §23), 6 / 21 / 3 / 1 (tras §21) y 3 / 24 / 3 / 1 (antes de CU-11/12 y CU-24).

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
| 06 Vehículos | RF-04 | A1 / A4 según ámbito | `VehiculosController`, `VinculosVehiculoEmpresaController` | `PaginaOperadores.tsx` (vínculos) | — | ITD `VehiculosIT`; E2E JWT `flujo-vehiculos-personal.test.mjs` vía `VehiculosPersonalNavegadorIT` (§24); ITJ `FalloPersistenciaRegistroOperativoIT`, `ConcurrenciaPeriodoJornadaIT`; E2E dev-local `vehiculos.test.mjs`; VT `vehiculos.test.ts` | **IMPLEMENTED** (técnico, §24): A1 bajo JWT + `VEHICULOS_*`; cierre incompatible con asignaciones = 409. Pendiente: D1 (escritura A4 en su ámbito, sin conceder), catálogo real de tipos/marcas |
| 07 Responsables | RF-04 | A1 / A4 según ámbito | `PersonasController`, vínculos/funciones | `PaginaOperadores.tsx` | — | ITD `RegistroOperativoIT`; E2E JWT `flujo-vehiculos-personal.test.mjs` vía `VehiculosPersonalNavegadorIT` (§24); ITJ `FalloPersistenciaRegistroOperativoIT`, `ConcurrenciaPeriodoJornadaIT`; E2E dev-local | **IMPLEMENTED** (técnico, §24): A1 bajo JWT + `PERSONAS_*`; cierre incompatible con asignaciones = 409. Pendiente: D1 (A4), criterios de conducción y obligatoriedad de cuenta (no inventados) |
| 08 Estructura territorial | RF-05 | A1 | — | — | — | — | **INSTITUTIONALLY BLOCKED**: jerarquía y fuentes oficiales inexistentes (CU-08). No inventar |
| 09 Rutas | RF-06 | **A4** (baseline) | `RutasReferenciaController`, V2/V3 versionado | `PaginaRutasReferencia.tsx` | — | ITD `RutasReferenciaIT`; E2E `rutas-referencia`; VT | **PARTIAL**: el código restringe a A1; escritura A4 sin alcance aprobado; UI bajo JWT sin verificar |
| 10 Puntos de control / geocercas | RF-06 | A1 / A3 | `ReferenciasEspacialesController`, V12 | `PaginaReferenciasEspaciales.tsx` | — | ITJ `ReferenciasEspacialesIT` 6 (+ no propietaria 6); E2E `flujo-referencias.test.mjs` vía `ReferenciasEspacialesNavegadorIT` (dibujo y render reales en el lienzo) (§27) | **PARTIAL** (§27): registro, versionado, cierre y consulta verificados técnicamente con concesiones explícitas. Pendiente: autoridad A1/A3 y procedimiento de validación (D1), tipos/radios/áreas institucionales, fuentes de puntos reales, validación en campo |
| 11 Programar jornada | RF-07 | **A4** | `GestionarJornadas`, `PlanificarJornadasOperador`, `JornadasOperadorController` | `PaginaReprogramacionEmpresa.tsx` (`/empresa/jornadas`) ✔ UI A4; `PaginaJornadas.tsx` (municipal) | — | E2E `flujo-planificacion.test.mjs` vía `PlanificacionOperadorNavegadorIT` (UI A4, JWT, concesión de periodo) + REST; ITD `JornadasIT` (11); ITJ `AutorizacionOperativaV19IT` (6) | **IMPLEMENTED** (técnico, §13). Pendiente institucional: calendarios/horarios oficiales y D1 (no inventados); la creación de empresas/periodos/rutas se siembra por SQL |
| 12 Asignar recursos | RF-07 | **A4** | ídem + `ReferenciasJornadas` | ídem | — | ídem (asignar, reasignar, historial, una abierta, recursos elegibles) | **IMPLEMENTED** (técnico, §13); alta de vínculos de recursos = CU-06/07 (A1, §24) |
| 13 Ejecutar jornada | RF-08/09/10/11 | A5 | `EjecucionJornadasController`, `PosicionesGpsController`, `MisJornadasController`, V7–V9 | — | `pagina_operacion.dart`, SQLite, `ServicioCapturaGps.kt`, cola/sync | ITJ `EjecucionAutenticadaIT`, `EventosEjecucionIT`, `PosicionesGpsIT`, `CadenaOperativaFiscalizacionIT`; FT `test/auth/pagina_operacion_test.dart`; EMU `qa_emulador/cadena_offline_emulador_test.dart` (**GPS sintético**, no UI); FT `test/gps/*`, `test/auth/sesion_offline_test.dart` (§25) | **PARTIAL** (§25): CA-13-01…10 con evidencia automatizada; GPS nativo real, batería, segundo plano, HyperOS/fabricantes y CA-4A-21 **sin QA física** (lista `QA_FISICA_REDMI_15C_CU13_CU14.md`) |
| 14 Incidencia | RF-16 | A3/A4/A5 según ámbito | `ActuacionesController`, `GestionarActuaciones` | `PaginaCorrectivas.tsx` (lectura) | `panel_incidencias.dart` | ITJ `IncidenciasOperativasIT` 5/5 (+ no propietaria 5/5); REST en `FlujoMunicipalNavegadorIT`; FT `test/incidencias/panel_incidencias_test.dart` 7 (CI) (§25) | **PARTIAL** (§25): flujo A5 en línea verificado técnicamente; A4 (registro y consulta, D1), incidencias offline/tardías y categorías (PENDING-06) sin política; sin QA en dispositivo |
| 15 Monitorear posición | RF-12 | A3 / A4 | `MonitoreoController` | `PaginaMonitoreo.tsx` | — | E2E `flujo-monitoreo.test.mjs` vía `MonitoreoMunicipalNavegadorIT` (UI A3 + REST), §15 | **PARTIAL** (A3 verificado técnicamente, §15): A4 sigue bloqueado por D1 (sin permiso; 403) y falta QA de continuidad con ejecución real |
| 16 Trayectoria GPS | RF-12 | A3 / **A4** | `TrayectoriasController`, `ConsultarTrayectoriasAutorizadas` | `PaginaTrayectorias.tsx`, `MapaTrayectoria.tsx` | — | ITJ `CadenaOperativaFiscalizacionIT`; E2E `flujo-municipal.test.mjs` (tabla, orden, hueco, «inferidos»); E2E `flujo-trayectoria.test.mjs` vía `TrayectoriaMunicipalNavegadorIT` (§16) | **PARTIAL**: A3 verificado técnicamente (§16); A4 sin acceso (D1 pendiente); el lienzo del mapa/teselas y los marcadores **no** se verifican |
| 17 Continuidad/integridad GPS | RF-13 | A3 | `RevisionGpsController`, `RevisarEvidenciaGps` | `PaginaRevisionGps.tsx` | — | E2E `flujo-integridad-gps.test.mjs` vía `IntegridadGpsNavegadorIT` (§17) | **PARTIAL**: A3 verificado técnicamente con umbrales provisionales explícitos (§17); pendientes umbrales institucionales (PENDING-04) y validación con GPS físico |
| 18 Coincidencias | RF-14 | A3 | ídem | ídem | — | E2E `flujo-coincidencias-gps.test.mjs` vía `CoincidenciasEspacialesNavegadorIT` (§18) | **PARTIAL**: A3 verificado técnicamente con radios y tolerancias provisionales explícitos (§18); pendientes reglas institucionales de puntos de control y validación con GPS físico |
| 19 Desvíos y detenciones | RF-15 | A3 | ídem | ídem | — | E2E `flujo-desvios-gps.test.mjs` vía `DesviosDetencionesNavegadorIT` (§19) | **PARTIAL**: A3 verificado técnicamente con umbrales provisionales explícitos (§19); pendientes criterios institucionales, validación con GPS físico y contexto de incidencias asociadas (no implementado) |
| 20 Inspección y evidencias | RF-17 | A3 | `FiscalizacionController` (`POST …/inspecciones`, JSON y multipart), `GestionarFiscalizacion.inspeccionar`, `PoliticaEvidenciasCampo` | `PaginaFiscalizacion.tsx` + `FormularioInspeccion.tsx` | — | ITJ `InspeccionCampoIT` (8) + no propietario (8), `InspeccionEvidenciasSinteticasIT` (3, fotos solo con configuración sintética aislada); E2E `flujo-inspeccion.test.mjs` vía `FlujoMunicipalNavegadorIT` y `flujo-inspeccion-campo.test.mjs` vía `InspeccionCampoNavegadorIT` | **PARTIAL** (§11, §31): registro sin fotografías verificado; canal de fotos verificado técnicamente pero deshabilitado en configuración normal (PENDING-09); FA-02/FA-03 y datos mínimos aprobados pendientes |
| 21 Fiscalizar y resultado | RF-18 | A3 | `FiscalizacionController`, `GestionarFiscalizacion`, `ManejadorErroresFiscalizacion`, V13 | `PaginaFiscalizacion.tsx` (expediente, GPS, «Revisión sin dictamen») | — | ITJ `CadenaOperativaFiscalizacionIT`, `FiscalizacionMunicipalIT` (4) + no propietario (4); E2E `flujo-fiscalizacion-municipal.test.mjs` vía `FiscalizacionMunicipalNavegadorIT` | **PARTIAL / emisión bloqueada** (§30): revisión municipal verificada; catálogo oficial PENDING-05 vacío (sin emisión en producción); rectificación no definida |
| 22 Acciones correctivas | RF-19 | A3 (+A4 responde) | `ActuacionesController`, `CorrectivasEmpresaController`, `AtenderCorrectivasEmpresa` | `PaginaCorrectivas.tsx` ✔ solicitud; `PaginaCorrectivasEmpresa.tsx` | — | E2E `flujo-correctivo.test.mjs` fases 1 y 1b (solicitud A3 y **respuesta A4 por la UI**, JWT) + REST en `FlujoMunicipalNavegadorIT` | **IMPLEMENTED** (técnico, ver §10); estados/plazos/adjuntos = institucional |
| 23 Verificar atención | RF-19 | A3 | `GestionarActuaciones.verificar` | `PaginaCorrectivas.tsx` | — | E2E fase 2 (UI) + REST (409/403) con categoría **sintética** | **PARTIAL / bloqueado en producción**: catálogo oficial inexistente |
| 24 Reprogramar | RF-20 | **A4** | `ReprogramarJornadasOperador`, V16 | `PaginaReprogramacionEmpresa.tsx` | — | ITJ REST `AutorizacionOperativaV19IT`; ITD `JornadasIT`; E2E `RegistroPlanificacionWebIT` **dev-local por vía municipal** (cerrada en producción: 403) | **IMPLEMENTED** (técnico, §14): UI A4 con JWT verificada; jornadas ejecutadas/fiscalizadas siguen **bloqueadas** (procedimiento institucional pendiente, no inventado) |
| 25 Reporte ciudadano | RF-21 (GAP-01) | A6 externo | — | — | — | — | **INSTITUTIONALLY BLOCKED**: formulario público, privacidad y controles sin aprobar; RF por ampliar |
| 26 Registrar reclamo | RF-21 | A3 | `ReclamosController.registrar`, `GestionarReclamos`, V18 | `PaginaReclamosPuntos.tsx` (formulario «Registrar reclamo recibido») | — | ITJ `ReclamoInstitucionalIT` (6, concurrencia y 503 desde §12) + no propietario (6); E2E `flujo-reclamo.test.mjs` (UI, JWT) vía `FlujoMunicipalNavegadorIT` | **PARTIAL** (registro textual verificado, §12): anexos, privacidad (datos personales), catálogo de canales y duplicados entre canales sin definir/bloqueados; CU-27 y CU-25 fuera de alcance |
| 27 Validar/gestionar reclamos | RF-21 | A3 | `ReclamosController` (revisiones/relaciones), `GestionarReclamos`, `ManejadorErroresReclamos`, V18 | `PaginaReclamosPuntos.tsx` («Agregar revisión humana», «Vincular antecedente comprobable», historia) | — | ITJ `RevisionReclamosIT` (6) + no propietario (6); E2E `flujo-revision-reclamo.test.mjs` vía `RevisionReclamosNavegadorIT` | **PARTIAL** (§28): revisión con observaciones y vínculos justificados verificada; catálogo de valoración vacío (D1/PENDING), A4 (CA-27-05/06), seguimiento, cierre y SLA sin definir |
| 28 Punto crítico | RF-22 | A3 | `PuntosCriticosController`, `GestionarReclamos`, `RepositorioReclamosPostgres`, V18 | `PaginaReclamosPuntos.tsx` («Registrar ubicación para revisión municipal», «Agregar observación sin borrar antecedentes», historia) | — | ITJ `PuntosCriticosIT` (6) + no propietario (6); E2E `flujo-punto-critico.test.mjs` vía `PuntosCriticosNavegadorIT` | **PARTIAL** (§29): registro de candidatos, observaciones, vínculos, consulta e historia verificados; validación municipal deshabilitada (PENDING-10), sin criterios de recurrencia, actualización ni retiro |
| 29 Historial | RF-23 | A1/A2/A3 | `modules/historial/*` | `PaginaHistorialServicio.tsx` | — | ITJ `HistorialServicioIT` (5); E2E `flujo-municipal`; VT (2) | **IMPLEMENTED** (técnico, JWT). Pendiente: D1, snapshots nominales, ejecución sin permiso aprobado |
| 30 Reportes e indicadores | RF-24 | usuario autorizado | `historial/api/ReportesServicioController`, `ConsultarReportesServicio`, `RepositorioReportesServicio` | `PaginaReportesServicio.tsx` (`/reportes-servicio`) | — | ITJ `ReportesServicioIT` 6 (+ no propietaria 6); E2E `flujo-reportes.test.mjs` vía `ReportesServicioNavegadorIT`; VT `reportesServicio.test.ts` (§26) | **PARTIAL** (§26): recuentos descriptivos verificados técnicamente (municipal, `JORNADAS_CONSULTAR` + permiso propio por fuente). Pendiente: reportes/fórmulas/unidades oficiales (PENDING-11), formato de exportación, A2/A4 (D1) |
| 31 Bitácora | RF-25 | A1 | `modules/bitacora/*` | `PaginaBitacora.tsx` | — | ITJ `BitacoraEventosIT` (doble de autorización), `BitacoraAutorizacionIT` (denegación real); VT (5) | **INSTITUTIONALLY BLOCKED**: `BITACORA_CONSULTAR` no existe en catálogo (D1); fuentes parciales; retención sin definir |

## 5. Comportamiento de producción faltante (no solo pruebas)

1. **A4 sin acceso a consulta de trayectoria/monitoreo** (CU-15/16, D1) y **sin escritura de rutas/recursos compartidos** (CU-06/07/09): el baseline los nombra como iniciadores; el código los limita a municipal o a recursos elegibles.
2. **Emisión de resultados (CU-21/23/27) imposible en producción** hasta que exista un catálogo aprobado (PENDING-05); solo hay fixtures sintéticas de prueba.
3. **Fotos/anexos (CU-20/26), validación CU-28, provisión CU-02**: apagados por configuración hasta aprobar política (por diseño correcto, pero sin vertical alcanzable).
4. **Sin nombres históricos inmutables** (CU-29): razón social/placa/persona muestran el maestro vigente.
5. **Sin vertical** de CU-08 y CU-25. CU-30 solo tiene recuentos descriptivos; los indicadores oficiales siguen sin definir (PENDING-11).
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

**Endurecimiento de concurrencia (2026-11-20).** Defecto reproducido con PostgreSQL real y JWT: 4 réplicas idénticas simultáneas (misma `solicitudId`, actor y contenido; la primera inserción retenida dentro de la transacción para superponerlas de forma determinista) respondían 1×200 y 3×409, porque la segunda inserción chocaba con el índice único. Corrección (mismo patrón que CU-28, §29): `INSERT … ON CONFLICT (solicitud_id) DO NOTHING` y relectura de la solicitud confirmada; el servicio devuelve el reclamo original o 409 si la solicitud es de otro actor o contenido.

| Escenario | Estado | Prueba |
|---|---|---|
| Réplicas concurrentes idénticas | **Verificado** | 4 simultáneas: todas 200, mismo `id`, un solo registro; autor y `recibido_en` originales |
| Misma solicitud simultánea con otro actor u otro contenido | **Verificado** | exactamente una 200 y dos 409; el registro guardado es el del ganador (autor y descripción no reemplazados) |
| Fallo de persistencia | **Verificado** | fallo inyectado: 503 (manejador de §28), inserción revertida; la misma solicitud se confirma después con el autor de la sesión |
| Ejecución no propietaria | **Verificado** | `ReclamoInstitucionalEjecucionNoPropietariaIT` 6/6 (hereda los escenarios) |

Local: `ReclamoInstitucionalIT` 6/6 (antes 4) y no propietario 6/6; regresión `RevisionReclamosIT` 6/6, `PuntosCriticosIT` 6/6, `HistorialServicioIT` 5/5, `FlujoMunicipalNavegadorIT` 1/1 (registro por la UI). `ReclamoInstitucionalIT` se añadió a `-Dit.test` de Engineering foundation (antes solo corría en CI su variante no propietaria). Sin cambios en V1–V21, canales, privacidad ni duplicados entre canales. CU-26 sigue **PARTIAL**.

CI `cb90db2`: Engineering foundation (run 44698499595) **PASS**; Browser E2E (44698499557) **PASS**.

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

## 24. Cierre funcional F1 — CU-06 vehículos y CU-07 responsables operativos (A1, JWT) · RF-04

Verificado (local): `VehiculosPersonalNavegadorIT` 1/1 (Chromium real + REST real + PostgreSQL/PostGIS aislado, JWT y concesiones municipales explícitas `VEHICULOS_*`/`PERSONAS_*`/`EMPRESAS_CONSULTAR`) con `apps/web/e2e/flujo-vehiculos-personal.test.mjs`; `ConcurrenciaPeriodoJornadaIT` 7/7 y su variante con rol no propietario 7/7; `FalloPersistenciaRegistroOperativoIT` 5/5; unitarias 99/99; fase `verify` del CI con integración 148/148 (25 clases); regresión `RegistroOperativoIT` 11, `VehiculosIT` 8, `JornadasIT` 11, navegador de empresas/planificación/reprogramación.

**Defecto corregido (probado):** cerrar un vínculo vehicular o de personal podía dejar fuera del vínculo jornadas ya asignadas con él (CU-06 E4, CU-07 E5, CA-06-04, CA-07-08). Ahora responde **409** con la misma regla de fecha de servicio America/La_Paz que la asignación; reabrir o cerrar en una fecha compatible sigue permitido. Concurrencia: el cierre bloquea la empresa (FOR UPDATE) y la asignación la lee con FOR SHARE (§23 H1), así que una asignación en curso hace que el cierre incompatible se rechace, y un cierre en curso hace que la asignación se rechace. Reproducido como fallo sin la regla (E2E y 2 casos de concurrencia).

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-06-01 placa única | **Verificado** | UI rechaza el duplicado; REST 409; una sola fila |
| CA-06-02 vehículo sin empresa obligatoria | **Verificado** | se registra y consulta sin vínculo |
| CA-06-03 vínculo histórico con empresa existente | **Verificado** | UI; empresa o vehículo inexistentes 404; fechas incoherentes 400 |
| CA-06-04 cierre no modifica asignaciones pasadas | **Verificado** | asignación intacta; cierre que la dejaría fuera = 409 (límite exacto aceptado) |
| CA-06-05 / CA-07-07 sin intervención fuera de alcance | **Verificado** | vínculo de otra empresa 404; A4 403 en lectura y escritura administrativas; lector 403 al escribir |
| CA-06-06 sin referencias inexistentes | **Verificado** | REST 404 |
| CA-06-07 regreso a empresa anterior conserva historia | **Verificado** | segmentos A (cerrado), B y nuevo A |
| CU-06 E5 / CU-07 E6 error transaccional | **Verificado** (inyección) | 503; vehículo, vínculo y persona intactos; ningún vínculo sin funciones |
| CA-07-01 persona sin Usuario | **Verificado** | ningún `usuario` creado |
| CA-07-02 / 03 vínculo con al menos una función válida | **Verificado** | UI no envía sin función; Conductor / Supervisor del catálogo existente |
| CA-07-04 funciones inactivas, duplicadas o inexistentes | **Verificado** | REST 400; ningún vínculo creado |
| CA-07-05 cambio de función conserva el segmento | **Verificado** | segmento cerrado conserva CONDUCTOR; nuevo con SUPERVISOR |
| CA-07-06 misma persona en otra empresa | **Verificado** | mismo `persona_id` en A y B |
| CA-07-08 jornadas conservan el vínculo histórico | **Verificado** | asignación intacta; cierre incompatible 409, también sobre datos creados por la UI y asignados por A4 |
| Revocación | **Verificado** | `VEHICULOS_GESTIONAR` revocado: el mismo token pasa a 403 al escribir y conserva la consulta |
| Flujo A4 existente | **Verificado** (sin ampliar permisos) | con concesión por periodo ve los vínculos creados por A1 como recursos elegibles y asigna |
| D1 (A4 sobre sus vehículos/personas), catálogo real de tipos, criterio de conducción, cuenta obligatoria | **PENDING** | no se inventaron |

## 25. F3 móvil — CU-13 ejecutar jornada y CU-14 incidencia en línea (A5)

CI: `146ca8c` — Engineering foundation (run 44648958801) **PASS**: `flutter analyze`, `flutter test` (incluye las 7 pruebas nuevas del panel), backend unitario y `backend-integration`. `5b142b9` — Browser E2E (44648359013) **PASS**; Engineering foundation (44648359083) **FAIL solo en `flutter test`** porque el fixture nuevo dejaba pendiente el temporizador de `GestorSesion` (se corrigió en `146ca8c`; `backend-integration` del mismo run **PASS**). Local: integración `verify` 158/158 (27 clases), unitarias 99/99. Flutter no se ejecuta localmente (guardián del entorno); su evidencia es la de CI.

**Defectos corregidos (CU-14):**
1. El panel de incidencias se monta al seleccionar la asignación (antes del INICIO) y fijaba ahí la fecha sugerida del hecho; sin editarla, se declaraba esa hora: 409 si era anterior al INICIO, o una ocurrencia distinta del hecho (CA-14-02). Ahora una fecha sugerida y no editada se renueva al abrir el formulario; una fecha editada no se toca.
2. Un fallo de almacenamiento llegaba a `/error` (cerrado por la seguridad) y el móvil mostraba «sin autorización». Ahora responde **503** («no hay confirmación; reintente la misma solicitud») y la escritura se revierte.

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-13-01…03 identidad, INICIO único, FIN no anterior | **Verificado (automatizado)** | `EjecucionAutenticadaIT`, `EventosEjecucionIT`, `pagina_operacion_test` |
| CA-13-04/05 captura con precisión y offline | **Verificado con datos sintéticos** | `posiciones_gps_test`, `PosicionesGpsIT`, emulador; **GPS físico PENDING** |
| CA-13-06/07 idempotencia, cuenta B | **Verificado (automatizado)** | `sesion_offline_test`, `posiciones_gps_test`, `PosicionesGpsIT` |
| CA-13-08/09 FIN detiene captura, recuperación | **Verificado (automatizado)** | `pagina_operacion_test`, migraciones SQLite; **segundo plano/HyperOS PENDING** |
| CA-13-10 sin cumplimiento automático | **Verificado** | `FlujoMunicipalNavegadorIT`: INICIO, GPS, incidencia y FIN no crean dictamen ni modifican evidencia |
| CA-14-01 / 02 jornada válida, descripción y fecha declarada | **Verificado** | REST (registrante = principal, fecha declarada ≠ recepción); panel (UTC exacto, fecha sugerida al abrir) |
| CA-14-03 sin acceso de otro actor | **Verificado** | otro conductor 403; A4 sin acceso al expediente (403); jornada inexistente 403 sin enumeración |
| CA-14-04 datos mínimos | **Verificado** | REST 400 (descripción, fecha, asignación, campo de identidad, fecha futura); panel no envía |
| CA-14-05 sin sanción ni dictamen | **Verificado** | sin acción correctiva, inspección ni fiscalización creadas |
| CA-14-06 sin confirmación falsa | **Verificado** | REST 503 con reversión (inyección); panel: sin red/503 no confirma y reintenta la misma `solicitudId` |
| CA-14-07 antecedente consultable | **Verificado** | consulta propia del conductor y expediente municipal (`CORRECTIVA_CONSULTAR`) |
| Idempotencia / fuera de ejecución | **Verificado** | mismo contenido → misma incidencia y recepción; otro contenido 409; antes del INICIO o tras FIN 409 |
| GPS físico, batería, segundo plano, HyperOS (CU-13) | **PENDING** | `QA_FISICA_REDMI_15C_CU13_CU14.md`; no se afirma desde pruebas sintéticas ni de widgets |
| A4 en CU-14, incidencias offline/tardías, categorías | **PENDING** | D1, decisión institucional, PENDING-06 |

Conteos sin cambio: CU-13 y CU-14 siguen **PARTIAL**.

## 26. CU-30 reportes descriptivos del servicio (corte mínimo, municipal) · RF-24

Vertical de solo lectura `GET /api/reportes-servicio/resumen?desde&hasta[&empresaId][&periodoId]` y página `/reportes-servicio`. Alcance: el mismo de CU-29 (fecha de servicio = inicio programado en America/La_Paz, ambos extremos incluidos). Autorización: `JORNADAS_CONSULTAR` para el reporte (igual que CU-29) y, para cada fuente adicional, el permiso propio de su módulo (`TRAYECTORIA_JORNADA_CONSULTAR`, `CORRECTIVA_CONSULTAR`, `FISCALIZACION_CONSULTAR`, `RECLAMO_CONSULTAR`); sin permisos nuevos. Recuentos: jornadas, sustituidas, con asignación, posiciones GPS, jornadas con GPS, incidencias, inspecciones, reclamos vinculados y reclamos recibidos (este último solo sin filtro de empresa/periodo). Eventos INICIO/FIN: «no consultado» (sin permiso municipal aprobado, igual que CU-29).

Verificado (local): `ReportesServicioIT` 6/6 y variante con rol no propietario 6/6; `ReportesServicioNavegadorIT` 1/1 (Chromium real); web `tsc`, vitest 75/75, build; integración `verify` 170/170 (29 clases), unitarias 99/99.

CI: `ea3c997` Browser E2E (run 44657906186) **PASS**, incluye `ReportesServicioNavegadorIT`. `backend-integration` falló dos veces antes de ejecutar pruebas: Maven Central respondió **429 Too Many Requests** (44657906261) y luego, con una caché propia vacía, también rechazó el POM padre (44659120496). Corrección de CI: caché Maven propia del job, con respaldo en la caché de setup-java y esperas de reintento mayores; las pruebas no se omiten. `637203a` Engineering foundation (44660223744) **PASS** (`verify` + `backend-integration`, con `ReportesServicioIT` y su variante no propietaria).

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-30-01 filtro por periodo y empresa | **Verificado** | empresa y periodo restringen el alcance; periodo de otra empresa 400; empresa/periodo inexistentes 404 (no «cero») |
| CA-30-02 agregados según filtros | **Verificado** | recuentos exactos sobre fixture propio, incluidos los bordes de día en America/La_Paz |
| CA-30-03 definición de cada recuento | **Verificado** | definición textual y fuente (tabla) por recuento; naturaleza «no es indicador oficial» |
| CA-30-04 faltante no se sustituye por cero | **Verificado** | `SIN_REGISTROS` (0 consultado) frente a `ACCESO_NO_PERMITIDO` (sin cifra) y `NO_CONSULTADO`/`NO_APLICA` |
| CA-30-05 sin cumplimiento oficial | **Verificado** | solo recuentos enteros; sin porcentajes, KPIs, cumplimiento ni sanciones |
| CA-30-06 supervisor sin agregados de otra empresa | **Verificado** (por denegación) | A4 con concesión de periodo: 403. FA-03 (A4 sobre su empresa) no habilitado: D1 |
| CA-30-07 la generación no altera registros | **Verificado** | huella de tablas fuente idéntica antes/después (REST e interfaz); transacción de solo lectura; la interfaz no envía escrituras |
| CA-30-08 referencia temporal y fuente | **Verificado** | filtros, zona horaria, `generadoEn` y «último registro» por fuente |
| CA-30-09 exportación no autorizada | **Verificado** (por ausencia) | no hay exportación (formato no aprobado); rutas de exportación 403 |
| Reportes, fórmulas y unidades oficiales (PENDING-11); formato de exportación; A2/A4 (D1) | **PENDING** | no inventados |

## 27. F2 CU-10 puntos de control y geocercas · RF-06

Verificado (local): `ReferenciasEspacialesIT` 6/6 y variante con rol no propietario 6/6 (JWT, PostgreSQL/PostGIS aislado); `ReferenciasEspacialesNavegadorIT` 1/1: Chromium real con MapLibre + Terra Draw sobre WebGL (SwiftShader), estilo local sin teselas externas. El punto y la geocerca se **dibujan con el ratón sobre el lienzo**, se guardan, se recuperan de PostGIS con la misma geometría y su capa persistida se verifica **leyendo los píxeles del lienzo** (color del círculo y del relleno). Por la base compartida, la respuesta real del listado se restringe a la RutaVersion propia de la prueba. Web `tsc`, vitest 75/75, build. Coordenadas, radios y rutas sintéticos; ninguna ubicación ni radio institucional. Regresión local: integración 183/183 (32 clases, incluida `CoincidenciasEspacialesNavegadorIT` de CU-18), unitarias 99/99.

CI `aaa9706`: Browser E2E (run 44667135176) **PASS**, incluye la verificación del lienzo WebGL en CI; Engineering foundation (44667135151) **PASS**.

**Defectos corregidos (probados):**
1. GeoJSON malformado (p. ej. `coordinates` no es un arreglo): PostGIS responde `XX000` y la excepción llegaba a `/error` (cerrado por la seguridad), lo que daba un 403 engañoso. Ahora responde **400** y no persiste nada.
2. Fallo de persistencia sin manejador: llegaba a `/error` (403 «permiso requerido»). Ahora responde **503**, se revierte y la versión anterior sigue abierta (`ManejadorErroresReferenciasEspaciales`).
3. Interfaz: tras guardar, el borrador de Terra Draw quedaba sobre el mapa y **tapaba por completo la capa persistida**; «Nuevo registro» tampoco lo retiraba. Ahora se retira tras guardar y al iniciar otro registro.
4. Interfaz: una cuenta con solo `REFERENCIA_ESPACIAL_CONSULTAR` veía un error general porque el selector de versiones de ruta exige `GESTIONAR`. Ahora la consulta funciona sin alerta y se informa «solo consulta» (detectado por análisis de código; cubierto por la E2E).

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-10-01 registrar y recuperar un punto válido | **Verificado** | dibujado en el lienzo; SRID 4326, `POINT`, 2D, radio explícito; GET devuelve la misma geometría |
| CA-10-02 geometría inválida rechazada | **Verificado** | polígono que se cruza, anillo sin cerrar, 3D, fuera de rango, CRS alternativo, tipo no admitido o incoherente, radio ausente/excesivo, geocerca con radio, GeoJSON malformado, nombre vacío: 400 sin persistir |
| CA-10-03 vínculo con RutaVersion | **Verificado** | asociación conservada; RutaVersion inexistente 400 |
| CA-10-04 actualización sin descontextualizar la historia | **Verificado** | nueva versión cierra la anterior en su inicio; la anterior conserva geometría y radio; UPDATE/DELETE en SQL rechazados; CU-18 evalúa cada posición con la versión vigente al capturarla |
| CA-10-05 consulta con geometría y nombre sin cumplimiento | **Verificado** | listado y mapa (lector incluido) sin campos de evaluación |
| CA-10-06 sin autorización no modifica | **Verificado** | lector 403 al escribir; cuentas sin permiso espacial, A4 y revisor GPS 403; revocación efectiva con el mismo token |
| CA-10-07 utilizable por consulta espacial posterior | **Verificado** | `/api/revision-gps/.../coincidencias` usa la referencia (2 muestras observadas en v1, 0 reasignadas a v2) |
| Cierre / reversión | **Verificado** | cierre 204; repetir o versionar una referencia cerrada o inexistente 409; fallo inyectado al guardar v2: 503, v1 sigue abierta |
| Autoridad A1/A3 y validación (D1), tipos/radios/áreas institucionales, fuentes de puntos reales, validación en campo | **PENDING** | no inventados; el radio sigue siendo técnico y explícito |

## 28. F4 CU-27 revisión municipal de reclamos · RF-21

Verificado (local, 2026-11-20): `RevisionReclamosIT` 6/6 y variante con rol no propietario 6/6 (JWT, PostgreSQL/PostGIS aislado); `RevisionReclamosNavegadorIT` 1/1 (Chromium real, REST real, JWT). Regresión local: `verify -Ppostgres-integration` completo, integración 260/260 en 57 clases (2 omitidas: pruebas de dispositivo físico, activadas solo a petición), unitarias 99/99, `FlujoMunicipalNavegadorIT` 1/1 (CU-26 comparte el formulario). Web `tsc`, vitest 75/75 y build. Datos sintéticos; no se creó ninguna categoría de valoración vigente: solo se insertó en la prueba una categoría ya retirada, para verificar que se rechaza.

CI `f201089`: Engineering foundation (run 44684130570) **PASS** (`RevisionReclamosIT` en `-Dit.test`, la variante no propietaria por `*EjecucionNoPropietariaIT`); Browser E2E (44684130677) **PASS** (`RevisionReclamosNavegadorIT` en `-Dtest`).

**Defectos corregidos (probados):**
1. Fallo de persistencia al revisar o vincular: la `DataAccessException` no tenía manejador y llegaba a `/error`, cerrado por la seguridad, lo que daba un 403 engañoso. Ahora responde **503**, la transacción se revierte y no queda revisión ni vínculo (`ManejadorErroresReclamos`). Se reprodujo antes de corregir.
2. Interfaz: tras un rechazo definitivo (409, p. ej. un vínculo con otra jornada), el formulario quedaba bloqueado (`fieldset` deshabilitado) y solo ofrecía «Reintentar misma solicitud», que repetía la petición rechazada. Ahora un 400 o 409 libera los datos para corregirlos y explica el conflicto. Solo un resultado incierto (red o 5xx) conserva la solicitud para reintentarla. Se reprodujo en la E2E antes de corregir.

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-27-01 consultar la entrada | **Verificado** (institucional) | detalle e historia con `RECLAMO_CONSULTAR`; el lector ve revisiones y vínculos en el navegador sin formularios ni alertas. Entrada ciudadana: CU-25 bloqueado |
| CA-27-02 origen y fecha conservados | **Verificado** | canal, recepción, descripción, referencias, jornada original, registrante y huella idénticos tras revisar y vincular (SQL y UI); los vínculos no reescriben `jornada_id` |
| CA-27-03 valoración humana y autor | **Verificado** (observación) | observaciones fácticas con autor desde el JWT y fecha del servidor; varias observaciones de distintos revisores, en orden; no marcan «validado». **Resultado oficial: no registrable** con catálogo vacío; un resultado inexistente o retirado da 409 sin persistir |
| CA-27-04 no atribuir empresa o jornada sin justificación | **Verificado** | vínculo solo con antecedente existente (jornada, incidencia, correctiva, inspección) y fundamento obligatorio; inexistente o sin fundamento 400; tipo «EMPRESA» o campo `empresaId` 400; vínculo con otra jornada 409 (sin política interjornada); el mismo destino con otro fundamento u otro autor da 409 (no se reemplaza) |
| CA-27-05 / CA-27-06 supervisor A4 y su respuesta | **PENDING** (D1) | sin acceso A4 a reclamos (403); no se amplió |
| CA-27-07 sin incumplimiento por mera denuncia | **Verificado** | revisar o vincular no crea acción correctiva ni fiscalización; ningún estado de tratamiento |
| CA-27-08 historial conservado | **Verificado** | UPDATE/DELETE de revisiones y vínculos rechazados por PostgreSQL; historia completa visible |
| Idempotencia / duplicados | **Verificado** | misma solicitud y contenido: 200 con el original; con otro contenido, otro actor u otro reclamo: 409; 4 réplicas concurrentes: una sola revisión; reintento tras fallo de red o 503 en la UI con la misma `solicitudId` y sin duplicar |
| Autorización / revocación | **Verificado** | solo consulta, registrador, solo `RECLAMO_REVISAR` sin consultar y cuentas sin permiso de reclamos: 403 al revisar o vincular; revocar `RECLAMO_REVISAR` con el mismo token: 403 al actuar y la consulta sigue; revocar `RECLAMO_CONSULTAR`: 403 al consultar |
| Contrato | **Verificado** | campos de estado, autor o validación del cliente, observación vacía o >20 000, sin `solicitudId`, JSON malformado: 400; reclamo inexistente: 404 |
| Fallo de BD | **Verificado** | fallo inyectado: 503, sin rastro; la misma operación se confirma después |
| Catálogo de valoración aprobado (D1), seguimiento y cierre, política interjornada (H-07), A4 y CU-25, anexos y datos personales, SLA | **PENDING** | no inventados |

## 29. F4 CU-28 puntos críticos (candidatos municipales) · RF-22

Verificado (local, 2026-11-20): `PuntosCriticosIT` 6/6 y variante con rol no propietario 6/6 (JWT, REST, PostgreSQL/PostGIS aislado); `PuntosCriticosNavegadorIT` 1/1 (Chromium real, REST real, JWT). Configuración por defecto: `gamw.puntos-criticos.validacion-habilitada=false` (PENDING-10 cerrado); ninguna prueba la habilita. Coordenadas sintéticas fuera de Warnes (Atlántico): no se registran ubicaciones municipales reales. Regresión local: `verify -Ppostgres-integration` completo, integración 273/273 en 60 clases (2 omitidas: dispositivo físico, solo a petición), unitarias y de navegador en surefire 100/100 en 29 clases; web `tsc` OK (sin cambios en `src`).

CI `7066fe0`: Engineering foundation (run 44696705689) **PASS** (`PuntosCriticosIT` en `-Dit.test`, la variante no propietaria por `*EjecucionNoPropietariaIT`); Browser E2E (44696705884) **PASS** (`PuntosCriticosNavegadorIT` en `-Dtest`).

**Defecto corregido (probado):** réplicas concurrentes idénticas del registro (misma `solicitudId`, mismo contenido y actor) respondían 1×200 y 3×409: la segunda inserción chocaba con el índice único y un reintento legítimo con resultado incierto se informaba como conflicto. Ahora la inserción usa `ON CONFLICT (solicitud_id) DO NOTHING` y el servicio devuelve el registro original (o 409 si la solicitud es de otro actor o contenido). Se reprodujo antes de corregir reteniendo la primera inserción dentro de la transacción (superposición determinista). Las observaciones ya eran seguras (bloqueo `FOR UPDATE` del punto). Observación no verificada: el registro de reclamos (CU-26) usa el mismo patrón sin bloqueo; no se probó ni se modificó.

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-28-01 solo fiscalizador autorizado registra/valida | **Verificado** (registro) · validación **PENDING-10** | registro y observación exigen `PUNTO_CRITICO_CONSULTAR` + `PUNTO_CRITICO_REGISTRAR`; solo consulta, solo registrar sin consultar, validador y cuentas sin permisos de puntos: 403 sin persistir; anónimo 401/403. Vincular reclamo o inspección exige además poder consultarlos (403). Revocar `REGISTRAR` con el mismo token: 403 al actuar y la consulta sigue; revocar `CONSULTAR`: 403. Validar: 409 aun con `PUNTO_CRITICO_VALIDAR` (política cerrada), 403 sin el permiso |
| CA-28-02 coordenada válida y descripción | **Verificado** | `geometry(Point,4326)`, `ST_X`/`ST_Y` = [longitud, latitud] exactas; límites ±180/±90 admitidos; fuera de rango, una sola coordenada, ausentes, `NaN`, `Infinity` o texto: 400; denominación vacía o >200, sustento vacío: 400. En la UI el navegador no envía una latitud fuera de rango |
| CA-28-03 reportes asociados conservan identidad y procedencia | **Verificado** | reclamo e inspección vinculados idénticos antes y después (SQL); reclamo o inspección inexistentes: 400 sin persistir |
| CA-28-04 sin punto oficial por acumulación | **Verificado** | 3 reclamos con la misma coordenada (CU-26) no crean puntos; observaciones con reclamos asociados no validan el candidato |
| CA-28-05 historial de observaciones | **Verificado** | orden por fecha observada (un antecedente anterior se intercala sin alterar lo previo), autor desde el JWT, fecha de registro del servidor, reclamo/inspección asociados; el lector ve la historia completa (API y navegador) |
| CA-28-06 no validado distinto de aprobado | **Verificado** (candidato) | `validadoMunicipalmente=false` y `validacion=null` en lista y detalle; UI: «Candidato sin validación municipal; no es referencia confirmada ni zona roja», opción «Candidato sin validación». El estado «validado» no es alcanzable (PENDING-10) |
| CA-28-07 no se borra el pasado | **Verificado** | UPDATE/DELETE de puntos y observaciones rechazados por PostgreSQL |
| CA-28-08 sin sanción ni reprogramación automática | **Verificado** | registrar u observar no crea fiscalización, acción correctiva ni jornada |
| E5 error de almacenamiento | **Verificado** | fallo inyectado en registro y observación: 503 (no 403), sin rastro; la misma solicitud se confirma después. UI: 503 sin «Registro confirmado» y reintento con la misma `solicitudId` |
| Idempotencia / concurrencia | **Verificado** | misma solicitud y contenido: 200 con el original; otro contenido, otro actor u otro punto: 409; 4 réplicas concurrentes de registro y de observación: un solo registro; reintento tras fallo de red en la UI sin duplicar |
| Contrato | **Verificado** | campos `validadoMunicipalmente`, `usuarioId`, `oficial`, `recurrencia` del cliente: 400; observación futura, con nanosegundos, sin fecha o vacía: 400; punto inexistente: 404 |
| PENDING-10 (definición, recurrencia, validación, actualización, retiro), FA-01 detección de punto preexistente, FA-03 capas de concentración, ubicaciones municipales reales, QA de campo | **PENDING** | no inventados; QA física diferida a la campaña final |

## 30. F5 CU-21 revisión municipal del expediente de fiscalización · RF-18

Verificado (local, 2026-11-20): `FiscalizacionMunicipalIT` 4/4 y variante con rol no propietario 4/4 (JWT, REST, PostgreSQL/PostGIS aislado); `FiscalizacionMunicipalNavegadorIT` 1/1 (Chromium real, REST real, JWT); regresión `CadenaOperativaFiscalizacionIT` 2/2 e `InspeccionCampoIT` 4/4; `verify -Ppostgres-integration` completo: integración 286/286 en 63 clases (2 omitidas: dispositivo físico). En la JVM aislada de navegador (como en CI) el catálogo está vacío y la UI muestra «Revisión sin dictamen»; en un `verify` local completo la misma prueba solo encuentra fixtures sintéticas de otra prueba y no emite.

CI `5fd130a`: Engineering foundation (run 44702237953) **PASS** (`FiscalizacionMunicipalIT` en `-Dit.test`, la variante no propietaria por `*EjecucionNoPropietariaIT`); Browser E2E (44702237946) **PASS** (`FiscalizacionMunicipalNavegadorIT` en `-Dtest`). El conductor ejecuta por la API real (INICIO, GPS, FIN, incidencia); la inspección se siembra como antecedente de CU-20 (su alta no se ejercita en este corte). Sin categorías oficiales: solo fixtures `prueba-sintetica` de otras pruebas; este corte inserta únicamente una categoría ya retirada para verificar el rechazo.

**Defecto corregido (probado):** un fallo de base de datos al consultar el expediente o al emitir no tenía manejador y llegaba a `/error`, cerrado por la seguridad: en producción se veía como un 403 «sin permiso». Ahora responde **503** sin registrar nada (`ManejadorErroresFiscalizacion`, mismo patrón que §28). Se reprodujo antes de corregir (`ServletException` con la `DataAccessException` inyectada).

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-21-01 evidencias disponibles de la jornada | **Verificado** | expediente: jornada, empresa, periodo, versión de ruta **histórica** (v1 aunque luego existe v2; la línea GPS de referencia es la de v1), asignaciones sucesivas en orden (cerrada con su fin, vigente con motivo), placa/vehículo/persona, INICIO/FIN con captura, recepción, autor y asignación, GPS recibido (cantidad, última captura), inspección con inspector y fechas. UI: los mismos datos en el navegador |
| CA-21-02 JWT de conductor/supervisor no emite | **Verificado** | conductor (A5), operador A4 con concesión de su periodo, cuentas con trayectoria/reclamos e inspector: 403 en lista, expediente, GPS y resultado; quien solo consulta: 403 al emitir o inspeccionar; anónimo 401/403. UI: el A4 ve «Permiso municipal específico requerido» y ninguna jornada (no «sin jornadas») |
| CA-21-03 sin resultado por INICIO/FIN/GPS | **Verificado** | tras ejecutar, revisar y consultar: 0 fiscalizaciones |
| CA-21-04 / CA-21-05 determinación vinculada; originales disponibles | **PENDING-05** (sin emisión) | ya cubiertos con fixture sintética en `CadenaOperativaFiscalizacionIT`; en este corte: GPS, eventos e inspección idénticos antes y después de los intentos (SQL) |
| CA-21-06 resultado no reconocido | **Verificado** | categoría inexistente o retirada: 409 sin persistir; la UI con permiso de emisión muestra «Revisión sin dictamen» y ningún botón de confirmación; no se puede insertar una fiscalización sin categoría existente (FK) |
| CA-21-07 ausencia de GPS = dato faltante | **Verificado** | jornada sin evidencia: eventos, asignaciones, inspecciones y GPS vacíos, sin última captura; la API y la UI no infieren datos pendientes del móvil; con `corteRecepcion` solo se presenta lo recibido hasta el corte; corte futuro o página negativa: 400 |
| CA-21-08 rectificación | **PENDING-05** | no definida |
| Fuentes con permiso propio | **Verificado** | con `RECLAMO_CONSULTAR`: reclamos «Con registros (1)»; sin `CORRECTIVA_CONSULTAR`: incidencias y acciones «Acceso no permitido» (cantidad nula, no «sin registros»); con `CORRECTIVA_CONSULTAR`: incidencia contada y acciones «Sin registros». UI: los mismos estados para revisor y lector |
| Revocación | **Verificado** | mismo token: revocar `RECLAMO_CONSULTAR` vuelve la fuente desconocida; revocar `FISCALIZACION_CONSULTAR`: 403 en lista, expediente y GPS |
| Entradas inválidas / jornada inexistente | **Verificado** | sin resultado, sin solicitud, sin inspecciones, inspecciones duplicadas, campos `estado`/`cumple`/`sancion`/`fiscalizadorUsuarioId` del cliente, JSON malformado: 400; jornada inexistente: 404 (expediente, GPS y emisión) |
| Integridad histórica | **Verificado** | UPDATE de inspección, DELETE de eventos y segundo cierre de una asignación rechazados por PostgreSQL |
| Fallo de BD | **Verificado** | expediente y emisión con fallo inyectado: 503, sin fiscalización. UI: 503 del expediente sin datos parciales; «Actualizar antecedentes» recupera el expediente y limpia el error |
| Lectura sin escritura | **Verificado** | lector (`FISCALIZACION_CONSULTAR`): expediente completo, «No dispone de permiso para emitir resultados», sin formularios de inspección ni resultado, sin alertas; ninguna petición POST de fiscalización desde la UI |
| Catálogo oficial (PENDING-05), rectificación, requisitos mínimos de evaluación, QA física | **PENDING** | no inventados |

Observación de diseño, sin cambio: el GPS del expediente (`/api/fiscalizacion/jornadas/{id}/gps`) se autoriza con `FISCALIZACION_CONSULTAR`, no con `TRAYECTORIA_JORNADA_CONSULTAR`; es la evidencia propia del expediente según el diseño vigente. Cambiarlo requiere una decisión institucional.

## 31. F5 CU-20 inspección de campo · RF-17

Verificado (local, 2026-11-20): `InspeccionCampoIT` 8/8 (antes 4) y variante con rol no propietario 8/8; `InspeccionEvidenciasSinteticasIT` 3/3; `InspeccionCampoNavegadorIT` 1/1 (Chromium real, REST real, JWT); regresión `FiscalizacionMunicipalIT` 4/4 y `CadenaOperativaFiscalizacionIT` 2/2; `verify -Ppostgres-integration` completo: integración 298/298 en 65 clases (2 omitidas: dispositivo físico). Configuración normal: `gamw.evidencias.fotografias-habilitadas=false` (PENDING-09). El canal de fotografías **existente** se ejercitó solo en `InspeccionEvidenciasSinteticasIT`, con configuración de prueba aislada y la referencia «PRUEBA-SINTETICA (no institucional, PENDING-09)»; no se definieron retención, privacidad, formatos ni procedimientos oficiales.

CI `b2cc890`: Engineering foundation (run 44704796493) **PASS** (`InspeccionCampoIT` e `InspeccionEvidenciasSinteticasIT` en `-Dit.test`, la variante no propietaria por `*EjecucionNoPropietariaIT`); Browser E2E (44704796486) **PASS** (`InspeccionCampoNavegadorIT` en `-Dtest`).

**Defectos corregidos (probados antes de corregir):**
1. Una inspección con fecha de realización **futura** se aceptaba (200): quedaba registrada antes de haber ocurrido. Ahora responde 400 sin registro.
2. Con fotografías habilitadas, una fecha de captura **futura** también se aceptaba (200, con evidencia guardada). Ahora responde 400 sin inspección ni evidencia. En la configuración normal no era alcanzable (fotos deshabilitadas).

Ambos en `GestionarFiscalizacion.inspeccionar`, con el mismo criterio que ya aplican reclamos, observaciones y respuestas correctivas (fecha declarada no futura).

| Criterio | Estado | Evidencia |
|---|---|---|
| CA-20-01 inspección con jornada válida | **Verificado** | 200 con jornada existente; jornada inexistente 404 (con permiso) o 403 (sin permiso, no revela existencia) |
| CA-20-02 fecha, autor y observación originales | **Verificado** | autor desde el JWT; fecha declarada exacta (UI en Bolivia, UTC−04:00) y registro del servidor posterior; coordenadas [longitud, latitud] EPSG:4326; fecha futura 400 (API y UI, editable para corregir) |
| CA-20-03 archivos permitidos recuperables | **Verificado (solo configuración sintética)** | PNG y JPEG: SHA-256, tamaño, MIME, procedencia, captura declarada (o nula, no inventada), autor y referencia de política; descarga con los bytes originales, `nosniff`, `attachment`, `no-store`; otra jornada 404; sin `FISCALIZACION_CONSULTAR` 403 |
| CA-20-04 archivo rechazado sin evidencia ficticia | **Verificado (solo configuración sintética)** | bytes no imagen, PNG con extensión .jpg, tipo declarado distinto, GIF, nombre con ruta, metadatos incompletos o sin procedencia, captura con nanosegundos o futura: 400; vacío o >5 MiB: 413; cuatro archivos: 400; una válida y otra inválida: 400 sin guardar la válida. En todos los casos, ni inspección ni evidencia. Configuración normal: cualquier archivo 409 sin registro |
| CA-20-05 inspección sin fotografías | **Verificado** (datos mínimos aprobados: PENDING) | fecha y observación bastan; UI sin selector de archivos |
| CA-20-06 sin reemplazo destructivo | **Verificado** | UPDATE/DELETE de inspección y evidencia rechazados; misma solicitud con otro contenido u otro archivo: 409 |
| CA-20-07 sin resultado automático | **Verificado** | ninguna fiscalización tras registrar (API, UI y SQL) |
| CA-20-08 solo fiscalizador autorizado | **Verificado** | consulta, conductor y anónimo: 403/401; revocar `INSPECCION_REGISTRAR` sobre el mismo token: 403 inmediato y la consulta sigue; UI del lector sin formulario |
| Idempotencia / concurrencia | **Verificado** | 4 réplicas simultáneas idénticas: 200 con la misma inspección, un registro; misma solicitud simultánea con otro actor o contenido: una 200 y dos 409; UI: tras 400 y 503 se reutiliza la misma solicitud y queda una sola inspección |
| Reversión / sin falso éxito | **Verificado** | fallo inyectado al insertar la inspección: 503 sin registro y el reintento confirma; fallo al guardar el 2.º archivo: 503 sin inspección ni primer archivo; UI: 503 sin «Registro confirmado» |
| PENDING-09 (formatos, tamaño, retención, almacenamiento y acceso a fotografías), datos mínimos aprobados, FA-02/FA-03, QA física | **PENDING** | no inventados |

## 9. Distinción final

Los 10 CU «IMPLEMENTED» (01, 04, 05, 06, 07, 11, 12, 22, 24, 29) lo son en sentido **técnico**. Ningún CU tiene aceptación institucional; GPS físico, batería y comportamiento por fabricante siguen **PENDING** (QA en dispositivo real).
