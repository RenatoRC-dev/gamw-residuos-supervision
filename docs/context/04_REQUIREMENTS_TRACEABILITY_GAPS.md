# Requirements Baseline, Traceability and Gaps

## 1. Current official RF baseline

The thesis baseline currently contains 25 functional requirements:

| RF | Name |
|---|---|
| RF-01 | Autenticar usuarios |
| RF-02 | Gestionar usuarios, roles y permisos |
| RF-03 | Gestionar empresas operadoras y periodos de servicio |
| RF-04 | Gestionar vehículos y responsables operativos |
| RF-05 | Gestionar estructura territorial del servicio |
| RF-06 | Gestionar rutas, frecuencias y puntos de control |
| RF-07 | Programar y asignar jornadas |
| RF-08 | Consultar jornada asignada |
| RF-09 | Iniciar y finalizar jornada |
| RF-10 | Capturar posiciones GPS durante la jornada |
| RF-11 | Almacenar y sincronizar información sin conectividad |
| RF-12 | Monitorear posición y trayectoria de la jornada |
| RF-13 | Detectar interrupciones del seguimiento |
| RF-14 | Detectar coincidencia espacial con puntos de control y geocercas |
| RF-15 | Identificar desvíos y detenciones prolongadas |
| RF-16 | Registrar incidencias operativas |
| RF-17 | Registrar inspecciones y evidencias de campo |
| RF-18 | Fiscalizar jornada y registrar resultado |
| RF-19 | Gestionar acciones correctivas |
| RF-20 | Reprogramar jornada conservando el antecedente |
| RF-21 | Registrar y gestionar reclamos |
| RF-22 | Registrar puntos críticos y microbasurales |
| RF-23 | Consultar historial institucional del servicio |
| RF-24 | Generar reportes e indicadores |
| RF-25 | Registrar bitácora de acciones relevantes |

## 2. High-priority existing RFs
- RF-07
- RF-08
- RF-09
- RF-10
- RF-11
- RF-12
- RF-18

These already had activity diagrams in the thesis baseline and will need review after the use-case restructuring.

## 3. Current alignment with the 31-use-case baseline

Most of the 31 use cases are conceptually supported by current RFs.

Examples:
- CU-09 routes ↔ RF-06
- CU-11/CU-12 ↔ RF-07
- CU-13 ↔ RF-08 + RF-09 + RF-10 + RF-11
- CU-15/CU-16 ↔ RF-12
- CU-17 ↔ RF-13 plus new integrity refinements
- CU-18 ↔ RF-14
- CU-19 ↔ RF-15
- CU-14 ↔ RF-16
- CU-20 ↔ RF-17
- CU-21 ↔ RF-18
- CU-22/CU-23 ↔ RF-19
- CU-24 ↔ RF-20
- CU-26/CU-27 ↔ RF-21
- CU-28 ↔ RF-22
- CU-29 ↔ RF-23
- CU-30 ↔ RF-24
- CU-31 ↔ RF-25 consultation behavior

## 4. Mandatory requirement updates before final thesis freeze

### GAP-01 — Public citizen report
Current RF-21 assumes municipal authorized users register complaints and explicitly excludes citizen direct interaction.

Baseline 1.0 now proposes:
**CU-25 Presentar reporte ciudadano**

Required action:
- either create a new RF;
- or formally expand RF-21.

Recommended approach:
Create a dedicated requirement such as:
**Permitir la presentación pública de reportes ciudadanos**

Keep internal complaint validation/management separate.

### GAP-02 — Route authority and validation
RF-06 currently avoids assigning final responsibility for route definition.

Institutional interview indicates:
- the operator manages the routes;
- route/frequency data are provided to municipal inspection.

Required refinement:
Define:
- who may create/digitize;
- who may update;
- whether municipal validation is required;
- whether a route version can be used immediately in journey programming.

### GAP-03 — GPS integrity / anti-manipulation evidence
Existing RFs cover interruptions, geospatial coincidence, deviations and stops.

They do not fully specify:
- mock-location indicators;
- impossible spatial/temporal jumps;
- accuracy thresholds;
- device/location status events;
- integrity evidence retention.

Required action:
Refine RF-13 or add a dedicated integrity requirement.

Do not promise perfect fraud prevention.
The objective is anomaly/integrity evidence for fiscalization.

### GAP-04 — Route versioning
The thesis already requires historical preservation, but implementation must make route versioning explicit.

Recommended rule:
A journey must reference an immutable route version.

### GAP-05 — Reports/indicators
RF-24 already combines reports and indicators.
The use-case baseline correctly consolidates them into CU-30.

### GAP-06 — Audit log
RF-25 records audit events automatically.
CU-31 represents authorized consultation of those events.

Do not model "register audit event" as a normal manual use case.

## 5. Traceability rule

Every implementation item must be traceable through:

**Institutional finding → Need → RF → Business rule → Actor → CU → Domain model → Component → Test → Evidence**

Example:

Institutional need:
"Fiscalizador needs real-time evidence of route execution."

→ RF-12 Monitorear posición y trayectoria

→ A3 Fiscalizador

→ CU-15 Monitorear posición de jornada

→ Journey + GPSPosition + RouteVersion

→ Monitoring application use case + geospatial query

→ integration tests + browser demo + stored GPS evidence

## 6. Change control rule

When a requirement changes, assess at minimum:
- RF text;
- priority;
- activity diagram if High;
- actor permissions;
- use cases;
- use-case detail;
- communication/analysis diagram;
- domain model;
- class/design model;
- API contract;
- UI;
- tests;
- traceability matrix;
- thesis figures/tables.

Do not silently implement a changed requirement.
