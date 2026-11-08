# Risks, Open Decisions and Institutional Validation Backlog

## 1. Product risks

### RISK-01 — Operator adoption
Risk:
Operator personnel may resist a monitoring tool that increases municipal visibility.

Mitigation:
- keep driver workflow simple;
- provide operational value to supervisor;
- validate responsibilities institutionally;
- avoid unnecessary data entry.

### RISK-02 — GPS manipulation
Risk:
Driver may disable location, leave phone elsewhere, use mock location, or create tracking gaps.

Mitigation:
- integrity/anomaly evidence;
- timestamps;
- accuracy;
- mock-location signal if available;
- impossible jump detection;
- control points;
- field inspection;
- no automatic fraud accusation.

Residual risk:
A normal smartphone cannot guarantee physical vehicle identity.

Dedicated hardware is OUT OF SCOPE unless approved later.

### RISK-03 — Connectivity
Risk:
Poor mobile coverage causes delayed updates.

Mitigation:
offline queue + later sync + visible freshness state.

### RISK-04 — Map provider dependency
Risk:
External tiles/provider becomes unavailable or changes policy.

Mitigation:
provider abstraction; GAMW data in PostGIS.

### RISK-05 — Historical mutation
Risk:
Route edits or reprogramming destroy old evidence.

Mitigation:
route versions + immutable historical references.

### RISK-06 — Citizen spam/fraud
Risk:
Public report form receives spam or false reports.

Mitigation:
rate limiting, CAPTCHA/equivalent, validation, file restrictions, duplicate candidates, municipal review.

### RISK-07 — Over-scoping
Risk:
Project expands into route optimization, waste treatment, IoT, billing, maintenance or citizen portal.

Mitigation:
strict product scope control.

## 2. Open institutional decisions

### PENDING-01
Who formally authorizes route creation/update in the platform?

### PENDING-02
Does the operator supervisor create route versions directly, or does municipal staff validate before activation?

### PENDING-03
Exact journey programming responsibility.
Current development baseline: operator supervisor.

### PENDING-04
Final GPS capture interval and battery/performance policy.

### PENDING-05
Official fiscalization result categories.

### PENDING-06
Official incident categories.

### PENDING-07
Corrective-action statuses and institutional procedure.

### PENDING-08
Citizen-report contact information policy and privacy rules.

### PENDING-09
Photo retention/storage policy.

### PENDING-10
Official criteria for "critical point" / recurrence.

### PENDING-11
Final report and indicator formulas.

### PENDING-12
Exact role/permission matrix.

## 3. Questions for the next GAMW interview

Route:
- Who creates the route today?
- Who approves it?
- What route format exists?
- How often does it change?
- Are old routes preserved?
- Who changes frequency/schedule?

Journey:
- Who decides which vehicle and driver perform a route?
- Is that assignment daily?
- Can a vehicle be replaced mid-journey?
- What happens if a route cannot be completed?

GPS:
- Is phone-based tracking acceptable institutionally?
- What level of monitoring frequency is operationally useful?
- What events should trigger municipal attention?

Fiscalization:
- What evidence is currently accepted?
- What result categories are used?
- What information appears in the fiscalizador's report?
- What creates a corrective action?

Complaints:
- What channels currently receive complaints?
- What minimum data make a complaint actionable?
- Is a public form acceptable?
- Can photos be stored?
- Can the citizen remain anonymous?
- Is a tracking code desirable?

Critical points:
- What makes a location a recognized microbasural?
- Who validates it?
- When is it considered resolved/inactive?

Reports:
- Which reports are actually used by management?
- Which indicators are contractually relevant?
- Which filters are essential?

## 4. Change classification

Every new idea must be classified:

A. Inside scope.
B. Minor change.
C. New function with impact.
D. Out of scope.

A change must state:
- problem solved;
- actor;
- value;
- priority;
- dependency;
- risk;
- data;
- rules;
- acceptance/test;
- affected artifacts.
