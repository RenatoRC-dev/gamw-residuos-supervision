# Domain Model and Business Rules Baseline

## 1. Central aggregate concept: Jornada

`Jornada` is the central traceability concept.

It connects:
- service period;
- operator;
- route version;
- vehicle;
- operational responsible;
- schedule;
- execution state;
- GPS positions;
- trajectory;
- incidents;
- inspections;
- evidence;
- complaints when related;
- fiscalization result;
- corrective actions.

## 2. Main domain concepts

### Institutional
- Government / GAMW
- User
- Role
- Permission
- OperatorCompany
- ServicePeriod

### Operational resources
- Vehicle
- OperationalResponsible

### Territory / route
- TerritorialUnit
- Sector
- Route
- RouteVersion
- RouteGeometry
- ControlPoint
- Geofence

### Execution
- Journey
- JourneyAssignment
- JourneyStatus
- OperationalIncident

### GPS / traceability
- GPSPosition
- GPSTrajectory
- TrackingInterruption
- GPSIntegrityEvent
- GeofenceMatch
- RouteDeviation
- LongStop

### Fiscalization
- FieldInspection
- Evidence
- Fiscalization
- FiscalizationResult
- CorrectiveAction
- CorrectiveActionAttention
- CorrectiveActionVerification

### Citizen / territorial follow-up
- CitizenReport
- Complaint
- ComplaintAttention
- CriticalPoint
- CriticalPointValidation / history

### Institutional history
- AuditEvent
- ReportDefinition if needed
- IndicatorResult as derived information when possible

## 3. Core business rules

### BR-01 — GPS evidence
A GPS position or trajectory is evidence, not automatic proof of collection.

### BR-02 — Fiscalization authority
Only an authorized municipal fiscalizador records the official fiscalization result.

### BR-03 — Historical continuity
Operator change must not remove or orphan historical data.

### BR-04 — Route version preservation
A journey references the route version assigned when it was programmed.
Later route edits do not mutate old journey history.

### BR-05 — Original GPS timestamp
`capturedAt` is preserved.
`receivedAt` / `syncedAt` is stored separately.

### BR-06 — Offline mode
No Internet with available GPS must not stop location capture.

### BR-07 — No automatic sanction
System anomalies, complaints or route differences do not directly trigger sanctions.

### BR-08 — Complaint validation
A public citizen report enters a pending-review state.
It is not automatically an institutional complaint or non-compliance finding.

### BR-09 — Critical point validation
A critical point/microbasural must be municipally identified/validated before being treated as an institutional territorial reference.

### BR-10 — Reprogramming
Reprogramming must preserve the previous plan and reason/history.

### BR-11 — Audit immutability
Audit-relevant events must not be retroactively altered as if they never occurred.

### BR-12 — Operator data isolation
Operator users only access data within authorized operator/service-period scope.

### BR-13 — Municipal historical access
Authorized GAMW users can consult historical records across past operators.

### BR-14 — Public/private separation
The public citizen form must not expose internal monitoring, personal, operator or fiscalization information.

## 4. Proposed journey states

PROPOSAL, subject to implementation refinement:

- `PROGRAMADA`
- `ASIGNADA`
- `EN_EJECUCION`
- `FINALIZADA`
- `CANCELADA` only if institutionally required

Reprogramming should be represented by history/version/relationship, not by deleting old states.

## 5. Proposed complaint/report states

### CitizenReport
- `PENDIENTE_REVISION`
- `VALIDADO`
- `DESCARTADO`
- `DUPLICADO_PROBABLE` may be a flag rather than a terminal state

### Complaint
- `REGISTRADO`
- `EN_GESTION`
- `PENDIENTE_RESPUESTA_OPERADOR`
- `PENDIENTE_VERIFICACION`
- `CERRADO`

Final names remain pending institutional validation.

## 6. Critical point classification

Do not implement a "red zone" as an automatic legal/institutional label.

Possible technical attributes:
- occurrence count;
- validated report count;
- last observed date;
- active/inactive;
- recurrence score as derived information;
- severity/priority only if municipal criteria are defined.

A heatmap may visualize recurrence, but it must not replace municipal validation.
