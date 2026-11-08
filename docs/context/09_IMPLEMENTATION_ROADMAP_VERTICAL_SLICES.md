# Implementation Roadmap — Vertical Slices

## Principle

Do not implement:
"all backend first → all frontend later → mobile last".

Implement complete vertical slices that produce demonstrable product value.

## Phase 0 — Engineering baseline

Deliver:
- repository structure;
- environment configuration;
- coding conventions;
- database migration mechanism;
- CI basics;
- test strategy;
- context docs;
- architectural decision records where useful.

Acceptance:
Project builds; automated tests can run; no business feature yet required.

## Slice 1 — Warnes geospatial POC

Goal:
Validate the highest technical risk: map + PostGIS + route geometry.

Implement:
- React + MapLibre map;
- configurable base map provider;
- PostGIS enabled;
- create/read territorial layer;
- draw route;
- store LineString;
- reload route;
- spatial index;
- simple route version metadata.

Acceptance:
A user can draw a route in the Warnes map, save it, reload it and see the same geometry.

Evidence:
- automated API test;
- PostGIS persistence test;
- screenshot/demo;
- stored geometry validation.

## Slice 2 — Institutional baseline + operator resources

Implement:
- authentication skeleton;
- operator;
- service period;
- vehicles;
- operational responsibles;
- minimal role enforcement.

Acceptance:
Authorized operator/municipal users can work only with permitted data.

## Slice 3 — Journey planning and assignment

Implement:
- choose route version;
- create journey;
- assign vehicle;
- assign responsible;
- view programmed journey.

Acceptance:
Journey is traceably linked to service period, operator, route version, vehicle and responsible.

## Slice 4 — Mobile execution

Implement:
- login;
- assigned journey view;
- start journey;
- journey state;
- finish journey;
- incident registration.

Acceptance:
Driver can execute the full journey lifecycle.

## Slice 5 — GPS capture and offline synchronization

Implement:
- location adapter;
- local durable queue;
- capture metadata;
- connectivity-independent capture;
- batch synchronization;
- idempotency;
- server ingestion.

Acceptance:
Turn off Internet while GPS remains active; positions are preserved locally and later synchronized with original timestamps.

## Slice 6 — Near-real-time monitoring

Implement:
- active journey query;
- latest position;
- SSE updates;
- map markers;
- route overlay;
- active trajectory;
- stale tracking warning.

Acceptance:
Fiscalizador can see new backend-received positions without manual full-page refresh.

## Slice 7 — Historical trajectory and GPS review

Implement:
- historical journey selector;
- trajectory reconstruction;
- tracking gaps;
- accuracy display;
- deviation/stop analysis baseline;
- control-point/geofence matching.

Acceptance:
Fiscalizador can reconstruct and inspect a completed journey.

## Slice 8 — Field inspection and fiscalization

Implement:
- field inspection;
- evidence;
- full journey evidence view;
- fiscalization result.

Acceptance:
Fiscalizador can review planning + execution evidence and manually record a result.

## Slice 9 — Corrective actions and reprogramming

Implement:
- corrective action;
- operator response;
- municipal verification;
- journey reprogramming with historical antecedent.

Acceptance:
No historical plan is overwritten.

## Slice 10 — Public citizen report

Implement:
- public report form;
- location picker;
- optional image;
- file validation;
- anti-spam/rate limiting;
- pending validation;
- no internal data exposure.

Acceptance:
Citizen can report without an internal account.

## Slice 11 — Complaint workflow and critical points

Implement:
- institutional complaint registration;
- validate citizen report;
- link complaint to territory/journey when supported by evidence;
- operator attention;
- municipal verification;
- critical point registration;
- recurrence view.

Acceptance:
Public report remains distinct from validated institutional complaint.

## Slice 12 — History, reports, indicators and audit

Implement:
- institutional historical queries;
- report filters;
- initial validated indicators;
- audit consultation.

Acceptance:
Historical data remains consultable across service periods/operators.

## Release criteria for Baseline 1.0

A release candidate must demonstrate:

1. Route can be digitized and versioned.
2. Journey can be programmed and assigned.
3. Driver can execute journey on mobile.
4. GPS works with online/offline scenarios.
5. Fiscalizador can monitor and reconstruct trajectory.
6. Fiscalizador records human result.
7. Complaints/public reports can be managed.
8. Critical points can be preserved territorially.
9. Historical data remains traceable.
10. Relevant actions are auditable.
