# Matriz de cobertura de los 31 CU — auditoría funcional (baseline `6db0d1e`; actualizada en `c1f0ce3` [CU-22] y en el corte CU-20)

> Conteo vigente tras los cortes CU-22 y CU-20: **IMPLEMENTED 3 · PARTIAL 24 · INSTITUTIONALLY BLOCKED 3 · NOT IMPLEMENTED 1**. CU-20 permanece PARTIAL: solo está verificado el subconjunto *sin fotografías* (ver §11); no es aceptación del CU completo.

**Naturaleza:** auditoría de solo lectura (no se implementó nada). Evidencia = rutas de archivos del repositorio y resultados ya ejecutados en esta campaña.
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
| IMPLEMENTED | **3** | 01, 22, 29 (22 desde el corte `CU-22 A4`, ver §10) |
| PARTIAL | **24** | 02, 03, 04, 05, 06, 07, 09, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 23, 24, 26, 27, 28 |
| INSTITUTIONALLY BLOCKED | **3** | 08, 25, 31 (31: código listo, acceso denegado por D1) |
| NOT IMPLEMENTED | **1** | 30 |

Total 31. **IMPLEMENTED no implica aceptación** (01 y 29 siguen sujetos a D1/aceptación GAMW).

## 3. Inventario de pruebas realmente ejecutadas (esta campaña, local/CI)

Backend 199 (surefire, 1 omitida Android) + IT aislados; navegador real: `FlujoMunicipalNavegadorIT` (flujo municipal + correctivo), `AutenticacionWebIT`, `RegistroPlanificacionWebIT`; web vitest 64; Flutter CI (analyze + test); emulador Android (cadena offline, GPS **sintético** en el almacén local). Informe: `docs/qa/QA_TECNICA_GAMW_20261118.md`.
**Sin ninguna prueba automatizada encontrada** (búsqueda en `backend/src/test`, `apps/web`, `apps/mobile/test`): `ReclamosController`, `PuntosCriticosController`, `RevisionGpsController`/`RevisarEvidenciaGps`, `ReferenciasEspacialesController`, `MonitoreoController` (solo comprobación de cierre en `ConsultaSeguimientoGpsIT.java:109`), `AdministracionIdentidadController`/`AdministrarIdentidad`, `PanelIncidencias` (Flutter) y `PaginaReprogramacionEmpresa` (web). *(Actualizado: el alta de inspección sin fotografías, CU-20, y `PaginaCorrectivasEmpresa`, CU-22, ya tienen pruebas; ver §10 y §11.)*

## 4. Matriz CU × RF × Actor × componentes × pruebas × estado

Leyenda de pruebas: **ITJ** = IT con JWT real; **ITD** = IT en modo `desarrollo-local`; **E2E** = Playwright real; **VT** = vitest; **FT** = Flutter test/CI; **EMU** = emulador Android; **—** = ninguna encontrada.

| CU | RF | Actor iniciador | Backend | Web | Móvil | Pruebas | Estado y brecha exacta |
|---|---|---|---|---|---|---|---|
| 01 Autenticar | RF-01 | todos | `modules/administracion/identidad/api/AutenticacionController`, `shared/security/*` | `PaginaAcceso.tsx` | `shared/auth/sesion.dart`, `pagina_operacion.dart` | ITJ `ContratoHttpAutenticacionTests`, `AutenticacionWebIT` (E2E 2/2); VT `PaginaAcceso.test.tsx`; FT `test/auth/*`, `sesion_almacen_ilegible_test.dart`; EMU | **IMPLEMENTED** (técnico). Pendiente: aceptación; política institucional de sesión |
| 02 Gestionar usuarios | RF-02 | A1 | `identidad/gestion/AdministrarIdentidad`, `AdministracionIdentidadController` | `PaginaAdministracionUsuarios.tsx` | — | **—** (sin IT/VT/E2E); solo SQL de actuaciones en `BitacoraEventosIT` | **PARTIAL**: código sin pruebas; provisión/reactivación/administradores bloqueados por D1 |
| 03 Roles y permisos | RF-02 | A1 | `AutorizacionMunicipal/Operador`, CLI `ProvisionarPermisoMunicipal`, V10/V15/V17/V19 | `PaginaAdministracionUsuarios.tsx` | — | ITJ parcial `AutorizacionOperativaV19IT` (concesiones/revocación efectivas); gestión HTTP/UI: **—** | **PARTIAL**: catálogo de roles y límites A1 = D1 |
| 04 Empresas | RF-03 | A1 | `EmpresasOperadorasController` | `PaginaOperadores.tsx` | — | ITD `RegistroOperativoIT`; E2E dev-local `RegistroPlanificacionWebIT`; VT `PaginaOperadores.test.tsx` | **PARTIAL**: UI no verificada bajo JWT+permiso `EMPRESAS_*`; solo modo local |
| 05 Periodos | RF-03 | A1 | `EmpresasOperadorasController`, `PeriodosPlanificacionController` | `PaginaOperadores.tsx` | — | ídem CU-04 | **PARTIAL**: ídem; política de solapamiento/edición = institucional |
| 06 Vehículos | RF-04 | A1 / A4 según ámbito | `VehiculosController`, `VinculosVehiculoEmpresaController` | `PaginaOperadores.tsx` (vínculos) | — | ITD `VehiculosIT`; E2E `vehiculos.test.mjs` (dev-local); VT `vehiculos.test.ts` | **PARTIAL**: escritura A4 sin alcance definido (D1); UI bajo JWT sin verificar |
| 07 Responsables | RF-04 | A1 / A4 según ámbito | `PersonasController`, vínculos/funciones | `PaginaOperadores.tsx` | — | ITD `RegistroOperativoIT`; E2E dev-local | **PARTIAL**: ídem CU-06 |
| 08 Estructura territorial | RF-05 | A1 | — | — | — | — | **INSTITUTIONALLY BLOCKED**: jerarquía y fuentes oficiales inexistentes (CU-08). No inventar |
| 09 Rutas | RF-06 | **A4** (baseline) | `RutasReferenciaController`, V2/V3 versionado | `PaginaRutasReferencia.tsx` | — | ITD `RutasReferenciaIT`; E2E `rutas-referencia`; VT | **PARTIAL**: el código restringe a A1; escritura A4 sin alcance aprobado; UI bajo JWT sin verificar |
| 10 Puntos de control / geocercas | RF-06 | A1 / A3 | `ReferenciasEspacialesController`, V12 | `PaginaReferenciasEspaciales.tsx` | — | **—** | **PARTIAL**: sin pruebas; fuentes/vigencia de geocercas por definir |
| 11 Programar jornada | RF-07 | **A4** | `GestionarJornadas`, `PlanificarJornadasOperador`, `JornadasOperadorController` | `PaginaReprogramacionEmpresa.tsx` (`/empresa/jornadas`), `PaginaJornadas.tsx` (municipal) | — | ITD `JornadasIT` (11); ITJ REST `AutorizacionOperativaV19IT`; E2E `jornadas` **dev-local** (municipal); VT `PaginaJornadas.test.tsx` | **PARTIAL**: la UI del actor real (A4, `/empresa/jornadas`) **no tiene ninguna prueba**; sembrado por UI solo en modo local |
| 12 Asignar recursos | RF-07 | **A4** | ídem + `ReferenciasJornadas` | ídem | — | ídem; cruce de empresa 400 en `AutorizacionOperativaV19IT` (REST) | **PARTIAL**: ídem CU-11 |
| 13 Ejecutar jornada | RF-08/09/10/11 | A5 | `EjecucionJornadasController`, `PosicionesGpsController`, `MisJornadasController`, V7–V9 | — | `pagina_operacion.dart`, SQLite, `ServicioCapturaGps.kt`, cola/sync | ITJ `EjecucionAutenticadaIT`, `EventosEjecucionIT`, `PosicionesGpsIT`, `CadenaOperativaFiscalizacionIT`; FT `test/auth/pagina_operacion_test.dart`; EMU `qa_emulador/cadena_offline_emulador_test.dart` (**GPS sintético**, no UI) | **PARTIAL**: GPS nativo, batería, segundo plano, fabricantes y CA-4A-21 sin QA física; el emulador no maneja la UI |
| 14 Incidencia | RF-16 | A3/A4/A5 según ámbito | `ActuacionesController`, `GestionarActuaciones` | `PaginaCorrectivas.tsx` (lectura) | `panel_incidencias.dart` | REST en `FlujoMunicipalNavegadorIT` (alta, duplicado, tardía 409); móvil/web alta: **—** | **PARTIAL**: móvil sin pruebas; A4 y captura tardía/offline sin política |
| 15 Monitorear posición | RF-12 | A3 / A4 | `MonitoreoController` | `PaginaMonitoreo.tsx` | — | **—** (solo cierre en `ConsultaSeguimientoGpsIT:109`) | **PARTIAL**: sin pruebas; A4 = D1; continuidad en ejecución real sin QA |
| 16 Trayectoria GPS | RF-12 | A3 / **A4** | `TrayectoriasController`, `ConsultarTrayectoriasAutorizadas` | `PaginaTrayectorias.tsx`, `MapaTrayectoria.tsx` | — | ITJ `CadenaOperativaFiscalizacionIT`; E2E `flujo-municipal.test.mjs` (tabla, orden, hueco, «inferidos») | **PARTIAL**: A3 verificado; A4 sin acceso (D1); el lienzo del mapa/teselas **no** se verifica |
| 17 Continuidad/integridad GPS | RF-13 | A3 | `RevisionGpsController`, `RevisarEvidenciaGps` | `PaginaRevisionGps.tsx` | — | **—** | **PARTIAL**: sin pruebas; umbrales provisionales por aprobar |
| 18 Coincidencias | RF-14 | A3 | ídem | ídem | — | **—** | **PARTIAL**: ídem; vigencia/precisión de puntos |
| 19 Desvíos y detenciones | RF-15 | A3 | ídem | ídem | — | **—** | **PARTIAL**: ídem; candidatos técnicos sin dictamen |
| 20 Inspección y evidencias | RF-17 | A3 | `FiscalizacionController` (`POST …/inspecciones`, JSON y multipart), `GestionarFiscalizacion.inspeccionar`, `PoliticaEvidenciasCampo` | `PaginaFiscalizacion.tsx` + `FormularioInspeccion.tsx` | — | ITJ `InspeccionCampoIT` (4); E2E `flujo-inspeccion.test.mjs` (UI, JWT) vía `FlujoMunicipalNavegadorIT` | **PARTIAL** (subconjunto sin fotografías verificado, §11): fotos/archivos (CA-20-03/04), FA-02/FA-03 y política PENDING-09 sin verificar o bloqueados |
| 21 Fiscalizar y resultado | RF-18 | A3 | `GestionarFiscalizacion` | `PaginaFiscalizacion.tsx` | — | ITJ `CadenaOperativaFiscalizacionIT`; E2E expediente (lectura) | **PARTIAL / emisión bloqueada**: catálogo oficial PENDING-05 vacío; emisión en UI no probada; rectificación no definida |
| 22 Acciones correctivas | RF-19 | A3 (+A4 responde) | `ActuacionesController`, `CorrectivasEmpresaController`, `AtenderCorrectivasEmpresa` | `PaginaCorrectivas.tsx` ✔ solicitud; `PaginaCorrectivasEmpresa.tsx` | — | E2E `flujo-correctivo.test.mjs` fases 1 y 1b (solicitud A3 y **respuesta A4 por la UI**, JWT) + REST en `FlujoMunicipalNavegadorIT` | **IMPLEMENTED** (técnico, ver §10); estados/plazos/adjuntos = institucional |
| 23 Verificar atención | RF-19 | A3 | `GestionarActuaciones.verificar` | `PaginaCorrectivas.tsx` | — | E2E fase 2 (UI) + REST (409/403) con categoría **sintética** | **PARTIAL / bloqueado en producción**: catálogo oficial inexistente |
| 24 Reprogramar | RF-20 | **A4** | `ReprogramarJornadasOperador`, V16 | `PaginaReprogramacionEmpresa.tsx` | — | ITJ REST `AutorizacionOperativaV19IT`; ITD `JornadasIT`; E2E `RegistroPlanificacionWebIT` **dev-local por vía municipal** (cerrada en producción: 403) | **PARTIAL**: UI de producción (A4) sin pruebas; ejecutadas/fiscalizadas bloqueadas |
| 25 Reporte ciudadano | RF-21 (GAP-01) | A6 externo | — | — | — | — | **INSTITUTIONALLY BLOCKED**: formulario público, privacidad y controles sin aprobar; RF por ampliar |
| 26 Registrar reclamo | RF-21 | A3 | `ReclamosController`, V18 | `PaginaReclamosPuntos.tsx` | — | **—** (solo conteo en `HistorialServicioIT`) | **PARTIAL**: sin pruebas; privacidad/anexos apagados |
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

## 9. Distinción final

Los 2 CU «IMPLEMENTED» lo son en sentido **técnico**. Ningún CU tiene aceptación institucional; GPS físico, batería y comportamiento por fabricante siguen **PENDING** (QA en dispositivo real).
