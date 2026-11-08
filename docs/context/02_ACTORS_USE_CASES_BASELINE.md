# Actors and Use Case Baseline 1.0

## 1. Actors

### A1 — Administrador municipal
Internal GAMW actor.

Responsibilities:
- Manage institutional users and access.
- Register operators/service periods.
- Maintain institutional reference data.
- Manage or authorize territorial configuration according to permissions.
- Consult audit information.

### A2 — Responsable municipal / autoridad
Internal GAMW actor.

Responsibilities:
- Institutional consultation.
- Historical consultation.
- Reports and indicators.
- Management-level oversight.

### A3 — Fiscalizador municipal / Ornato Público
Internal GAMW actor.

Responsibilities:
- Monitor journeys.
- Review GPS evidence and anomalies.
- Perform/record field inspection.
- Determine fiscalization result.
- Validate citizen/institutional complaints.
- Create/verify corrective actions.
- Register/validate critical points.
- Consult history and reports.

### A4 — Supervisor de empresa operadora
Authorized operator-company actor.

Responsibilities:
- Maintain operator-owned operational resources within permission limits.
- Register/manage reference routes used by the operator.
- Program journeys.
- Assign vehicle and operational responsible.
- Respond to incidents, complaints and corrective actions.
- Monitor only the operator's authorized operational information.

### A5 — Conductor / responsable móvil
Authorized mobile operational actor.

Responsibilities:
- View assigned journey.
- Start and finish execution.
- Follow assigned route/reference information.
- Allow GPS capture during execution.
- Record operational incidents.

### A6 — Ciudadano / reportante
External public actor.

Important:
- No authenticated access to the private institutional platform.
- Uses a public web form only.
- May submit a service-related report with location, description and optional photo.
- Does not validate complaints.
- Does not determine compliance.
- Does not access internal GAMW information.

## 2. Proposed 31-use-case baseline

This is the current **development baseline**. IDs can be renumbered later if thesis restructuring requires it.

### Package 1 — Administración institucional

| ID | Use Case | Main actor(s) | Priority |
|---|---|---|---|
| CU-01 | Autenticar usuario | A1, A2, A3, A4, A5 | Medium |
| CU-02 | Gestionar usuarios | A1 | Medium |
| CU-03 | Gestionar roles y permisos | A1 | Medium |
| CU-04 | Gestionar empresas operadoras | A1 | Medium |
| CU-05 | Gestionar periodos de servicio | A1 | Medium |
| CU-06 | Gestionar vehículos | A1, A4 | Medium |
| CU-07 | Gestionar responsables operativos | A1, A4 | Medium |

### Package 2 — Planificación territorial y programación

| ID | Use Case | Main actor(s) | Priority |
|---|---|---|---|
| CU-08 | Gestionar estructura territorial del servicio | A1 | Medium |
| CU-09 | Gestionar rutas operativas de referencia | A4, authorized municipal reviewer if defined | Medium |
| CU-10 | Gestionar puntos de control y geocercas | A1/A3 depending final permissions | Medium |
| CU-11 | Programar jornada | A4 | High |
| CU-12 | Asignar recursos a jornada | A4 | High |

### Package 3 — Ejecución operativa

| ID | Use Case | Main actor(s) | Priority |
|---|---|---|---|
| CU-13 | Ejecutar jornada asignada | A5 | High |
| CU-14 | Registrar incidencia operativa | A5, A4 | Medium |

CU-13 consolidates the previous separate goals:
- consult assigned journey;
- start journey;
- finish journey.

GPS capture, offline persistence and synchronization are essential functional behaviors inside execution, but do not need to remain independent user-goal use cases.

### Package 4 — Monitoreo GPS y trazabilidad

| ID | Use Case | Main actor(s) | Priority |
|---|---|---|---|
| CU-15 | Monitorear posición de jornada | A3; A4 with operator-limited scope | High |
| CU-16 | Consultar trayectoria GPS de jornada | A3; A4 with operator-limited scope | High |
| CU-17 | Revisar continuidad e integridad del seguimiento GPS | A3 | Medium |
| CU-18 | Revisar coincidencias con puntos de control y geocercas | A3 | Medium |
| CU-19 | Revisar desvíos y detenciones prolongadas | A3 | Medium |

### Package 5 — Fiscalización

| ID | Use Case | Main actor(s) | Priority |
|---|---|---|---|
| CU-20 | Registrar inspección y evidencias de campo | A3 | Medium |
| CU-21 | Fiscalizar jornada y registrar resultado | A3 | High |

### Package 6 — Incidencias y acciones correctivas

| ID | Use Case | Main actor(s) | Priority |
|---|---|---|---|
| CU-22 | Gestionar acciones correctivas | A3, A4 | Medium |
| CU-23 | Verificar atención de acciones correctivas | A3 | Medium |
| CU-24 | Reprogramar jornada conservando el antecedente | A4; permissions pending | Medium |

### Package 7 — Reclamos y puntos críticos

| ID | Use Case | Main actor(s) | Priority |
|---|---|---|---|
| CU-25 | Presentar reporte ciudadano | A6 | Medium |
| CU-26 | Registrar reclamo recibido por canal institucional | A3 | Medium |
| CU-27 | Validar y gestionar reclamos | A3, A4 | Medium |
| CU-28 | Registrar punto crítico o microbasural identificado | A3 | Low/Medium |

### Package 8 — Historial, reportes e indicadores

| ID | Use Case | Main actor(s) | Priority |
|---|---|---|---|
| CU-29 | Consultar historial institucional del servicio | A2, A3, A1 as permitted | Medium |
| CU-30 | Generar reportes e indicadores | A1, A2, A3; A4 limited if authorized | Medium |
| CU-31 | Consultar bitácora de acciones relevantes | A1 | Medium |

## 3. Important use-case decisions

### CU-09 — Routes
The platform does not primarily optimize routes.

The operator can register/digitize existing operational routes on a map and update them as new versions.

The system must preserve historical route versions used by old journeys.

### CU-13 — Execute assigned journey
One cohesive mobile use case:
1. View assigned journey.
2. Review route/vehicle information.
3. Start.
4. GPS capture begins.
5. Offline persistence if needed.
6. Incident registration.
7. Finish.
8. Pending GPS records continue syncing until resolved.

### CU-15 / CU-16 — Monitoring
Fiscalizador:
- live/near-live view;
- last known position;
- active trajectory;
- historical trajectory by date/journey.

Supervisor:
- may have operational monitoring for their own company only.

This does not grant fiscalization authority.

### CU-17 — GPS integrity
Review:
- tracking interruptions;
- location permission loss if observable;
- low-accuracy records;
- impossible spatial/temporal jumps;
- mock-location indicators if available from device/platform;
- synchronization gaps.

No anomaly is an automatic finding of non-compliance.

### CU-25 — Public report
Public web form, not a full citizen portal.

### CU-28 — Critical point
A critical point is not simply "many reports on the map".
It is a municipally identified/validated location with recurring service/environmental relevance.

### CU-30 — Reports and indicators
Merge the old separate "report" and "indicator" use cases because the same product function consumes consolidated institutional information.

### CU-31 — Audit log
Manual user goal is consultation.
Automatic event recording remains a system functional requirement.
