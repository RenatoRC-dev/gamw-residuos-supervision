# Product Vision, Institutional Context and Scope

## 1. Product vision

The product is an institutional web and mobile platform for the **supervision, inspection, and traceability** of the outsourced urban waste collection service of GAMW.

The product is not primarily a route optimizer.

Its central value is to allow GAMW to answer, with persistent evidence:

- What service was planned?
- Which operator was responsible?
- Which vehicle and operational responsible were assigned?
- Which route/reference area was expected?
- When did the journey begin and end?
- Where did the GPS-enabled mobile device report being?
- Were there interruptions, deviations or long stops?
- What incidents were recorded?
- What did the municipal fiscalizador observe?
- What corrective action was requested and how was it attended?
- What citizen complaints or recurring critical points were associated?
- What final inspection/fiscalization result was recorded?
- Can this history still be consulted after the operator changes?

The product must preserve the chain:

**Empresa → Periodo → Ruta → Jornada → Vehículo → Responsable → Trayectoria GPS → Incidencias → Evidencias → Fiscalización → Acción correctiva → Historial**

## 2. Institutional facts from the interview

### CONFIRMED
- The collection service is outsourced.
- The operator manages operational collection routes and frequencies.
- GAMW performs municipal inspection/fiscalization.
- The company provides route/frequency information to the municipal fiscalizador.
- The municipality currently relies heavily on reports, WhatsApp and spreadsheets.
- Municipal staff perform field inspection of routes.
- Complaints reach Ornato Público and a fiscalizador validates them before managing them with the operator.
- Existing routes differ by districts/sectors and collection frequency.
- Operational problems may include vehicle failure, full truck requiring unloading, road access problems, and service deviations.
- GAMW wants better real-time control of routes, stops and deviations.
- The institutional history must remain useful across operator changes.

### PRELIMINARY / TO VALIDATE
- Exact institutional office/department naming for every role.
- Final permission matrix.
- Final contractual obligation of the operator to use the platform.
- Official route/frequency documents and formats.
- Official fiscalization criteria and result categories.

## 3. Product boundaries

### IN SCOPE
- Users, roles and permissions.
- Operators and service periods.
- Vehicles and operational responsibles.
- Territorial structure.
- Existing operational routes as reference geometries.
- Frequencies, schedules and control points when validated.
- Journey programming and resource assignment.
- Mobile journey execution.
- GPS capture.
- Offline local persistence and later synchronization.
- Near-real-time monitoring.
- Historical trajectory consultation.
- Follow-up interruptions and GPS integrity observations.
- Control points / geofences.
- Deviations and long stops.
- Operational incidents.
- Field inspection and evidence.
- Human fiscalization and recorded result.
- Corrective actions and verification.
- Reprogramming while preserving the original antecedent.
- Citizen public report form.
- Institutional complaint registration and management.
- Critical points / microbasurales.
- Institutional history.
- Reports and indicators.
- Audit log.

### OUT OF SCOPE unless explicitly approved
- Recycling workflows.
- Industrial waste treatment.
- Internal landfill operation.
- Billing.
- Payments.
- Mechanical maintenance management.
- Complete procurement/public contracting workflow.
- Automatic sanctions.
- Automatic legal/contractual compliance decisions.
- Advanced AI route optimization.
- IoT sensor network.
- Dedicated GPS trackers.
- Smart containers.
- Full citizen account/portal.
- Citizen education management.
- Artificial intelligence features.

## 4. Product philosophy

The system is an evidence and control platform.

**Wrong interpretation:**
"GPS says the truck passed, therefore service was completed."

**Correct interpretation:**
"GPS, route reference, timestamps, incidents, field inspections, complaints and other evidence are available to the fiscalizador, who records the institutional result."

## 5. Product evolution

### Baseline 1.0
Focus on:
- reference route registration;
- journey programming;
- mobile execution;
- GPS traceability;
- monitoring;
- inspection/fiscalization;
- complaints;
- critical points;
- institutional history.

### Possible future evolution
Historical data may later support:
- route redesign studies;
- capacity planning;
- route optimization;
- broader performance analysis.

These future capabilities must not contaminate the initial scope.
