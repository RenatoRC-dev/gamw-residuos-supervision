# End-to-End Operational Flow

This document explains how all actors and use cases work together as one product.

## 1. Institutional configuration

The Administrador municipal establishes the institutional baseline: users, roles, operators, service periods, vehicles, and operational responsibles.

Each company is associated with the service period in which it operated.

Historical records are preserved when the operator changes.

## 2. Territorial structure

The municipality configures the territorial references used by the service:
- districts;
- sectors;
- quadrants;
- other validated territorial units.

These are stored as institutional geospatial data.

The base map may use OpenStreetMap, but GAMW territory, routes, critical points, complaints and operational records are first-class data owned by the platform.

## 3. Route registration

The operator already has operational routes.

The Supervisor de empresa operadora uses the web map to:
- select the relevant territory;
- digitize an existing route;
- edit its geometry;
- associate sector(s);
- record known frequency/schedule information;
- save the route.

The route is not just a visual polyline. It is a versioned geospatial entity.

If the route changes later, create a new route version.

Old journeys keep the route version that was assigned at the time.

Future route optimization may use accumulated historical data, but automatic optimization is outside Baseline 1.0.

## 4. Control points and geofences

Authorized staff define control points/geofences that help compare execution against planned reference areas.

They are supporting evidence.

Reaching a point/geofence does NOT prove that waste was collected.

## 5. Journey programming

The Supervisor de empresa operadora programs a journey.

The journey references:
- service period;
- operator;
- route version;
- date;
- relevant schedule/reference information.

The journey becomes the central traceability object.

## 6. Resource assignment

The supervisor assigns:
- vehicle;
- operational responsible / driver.

The platform checks that the selected resources belong to or are authorized for the relevant operator/service period.

## 7. Driver receives the journey

The Conductor / responsable móvil authenticates in Flutter.

The mobile application shows:
- assigned journey;
- vehicle;
- route/reference;
- date;
- other operational information.

The driver does not manually reconstruct the route.

## 8. Journey starts

The driver starts the journey.

The system records:
- original start timestamp;
- authenticated user;
- assigned journey;
- assigned vehicle.

GPS tracking becomes active according to configured capture policy.

## 9. GPS collection during execution

Each position should preserve, when available:
- journey ID;
- vehicle ID;
- responsible ID;
- latitude;
- longitude;
- captured timestamp;
- server received timestamp;
- accuracy;
- synchronization state;
- optional integrity metadata supported by the device.

The platform never replaces the original capture timestamp with the synchronization timestamp.

## 10. Offline mode

If GPS works but Internet does not:
- continue capturing GPS;
- persist records locally;
- mark them pending synchronization.

When connectivity returns:
- send pending records;
- preserve original metadata;
- avoid duplicates;
- mark successfully synchronized records.

Internet failure is NOT GPS failure.

## 11. Operational incidents

During the journey the driver may record an operational incident.

Examples may include:
- road inaccessible;
- vehicle problem;
- route interruption;
- other validated incident categories.

Do not invent final official categories until GAMW validates them.

Supervisor can consult incidents relevant to their operator.

## 12. Near-real-time monitoring

The Fiscalizador municipal opens the monitoring map.

For each active journey, authorized information may include:
- vehicle/journey;
- route reference;
- last position;
- timestamp of last position;
- current trajectory;
- GPS accuracy indicator;
- synchronization/tracking status;
- incidents.

The supervisor may also receive an operational monitoring view limited to their own company.

Municipal fiscalization remains separate.

## 13. Trajectory review

For active or historical journeys, the fiscalizador can reconstruct the trajectory.

He/she can compare:
- reference route;
- actual positions;
- control-point/geofence coincidences;
- interruptions;
- deviations;
- long stops;
- incidents.

These are evidence inputs, not automatic compliance conclusions.

## 14. GPS integrity review

The platform can surface suspicious or incomplete tracking conditions:
- tracking gaps;
- stale last position;
- low GPS accuracy;
- impossible speed/jump;
- mock-location signal when technically available;
- device/location permission interruption when detectable;
- late synchronization.

The platform labels these as events/anomalies requiring review.

It never automatically declares fraud.

## 15. Field inspection

The Fiscalizador municipal can perform a field inspection.

The inspection can include:
- date/time;
- location;
- observations;
- photos/evidence;
- linked journey;
- related complaint/incident if applicable.

## 16. Fiscalization result

The fiscalizador consults the complete context:
- programmed journey;
- operator;
- route version;
- vehicle;
- responsible;
- GPS trajectory;
- GPS anomalies;
- control points;
- operational incidents;
- field inspections/evidence;
- complaints.

Then the fiscalizador records the municipal result.

The platform does not calculate the institutional decision automatically.

## 17. Corrective actions

If the fiscalizador identifies a matter requiring correction:
- a corrective action is registered;
- the operator supervisor can register attention/response;
- the fiscalizador verifies the attention;
- the action remains open or is considered verified according to the result.

No automatic sanction.

## 18. Reprogramming

If a journey must be reprogrammed:
- preserve the original plan;
- create the new scheduling state/version;
- preserve reason and traceability.

Never overwrite history.

## 19. Public citizen report

The Citizen / reporter accesses a public web form.

Possible information:
- report type;
- description;
- location/map pin;
- optional photograph;
- optional contact information if institutionally approved.

Controls:
- anti-spam / CAPTCHA or equivalent;
- file validation;
- rate limiting;
- duplicate-candidate detection;
- explicit "pending validation" initial status.

The citizen does not need an internal GAMW account.

The public form does not expose private operational data.

## 20. Institutional complaint received by other channels

Complaints may also arrive through:
- phone;
- WhatsApp;
- in-person attention;
- another institutional channel.

The fiscalizador can register them manually in the platform.

This preserves current workflows while centralizing the resulting information.

## 21. Complaint validation

The fiscalizador reviews:
- complaint data;
- location;
- evidence/photo;
- related sector;
- planned journeys;
- GPS trajectories;
- route;
- prior complaints.

If the complaint corresponds to the service, it is managed with the operator.

The supervisor records attention/response where applicable.

The fiscalizador validates the outcome.

A public report is not automatically a valid complaint or proof of non-compliance.

## 22. Critical point / microbasural

When a recurrent location is municipally identified or validated, the fiscalizador may register it as a critical point/microbasural.

Purpose:
- preserve recurrent location history;
- associate complaints;
- associate inspections;
- associate nearby routes/journeys;
- prioritize municipal follow-up;
- identify spatial recurrence.

Possible visualizations:
- validated critical-point markers;
- recurrence count;
- last validation date;
- heatmap of validated/relevant events.

Do not automatically call a location a "red zone" just because many public forms were submitted.

## 23. Historical consultation

GAMW users can later reconstruct historical service by:
- operator;
- period;
- sector;
- route;
- vehicle;
- responsible;
- journey;
- complaint;
- fiscalization.

This is one of the project's strongest institutional values.

## 24. Reports and indicators

Authorized users can generate consolidated information from institutional data.

Possible dimensions:
- journeys programmed/executed;
- incidents;
- tracking interruptions;
- deviations;
- complaints received/validated;
- critical points;
- fiscalizations;
- corrective actions;
- route/sector/date/operator filters.

Exact contractual indicators and formulas remain pending institutional validation.

## 25. Audit trail

Relevant user/system actions are automatically recorded.

Authorized administrators can consult the audit log.

The user goal is "consult audit log"; automatic recording is system behavior.
