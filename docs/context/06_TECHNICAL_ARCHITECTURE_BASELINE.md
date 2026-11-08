# Technical Architecture Baseline 1.0

## 1. Architecture goals

The architecture must:
- remain maintainable after the thesis;
- preserve domain traceability;
- avoid vendor lock-in;
- support geospatial history;
- support offline mobile operation;
- support near-real-time monitoring;
- preserve historical evidence;
- allow later infrastructure changes without rewriting domain logic.

## 2. Technology baseline

### Web frontend
- React
- TypeScript
- Recommended mapping engine: MapLibre GL JS
- HTTP client layer isolated from UI
- feature/module organization aligned to packages/use cases

### Mobile
- Flutter
- local persistent storage for pending GPS/events
- mapping library behind an abstraction layer
- GPS/location adapter behind an abstraction layer
- synchronization worker separated from UI

### Backend
- Java
- Spring Boot
- REST API for commands/queries
- SSE recommended for browser monitoring updates in Baseline 1.0
- WebSocket can replace SSE later if requirements demand bidirectional real-time communication

### Database
- PostgreSQL
- PostGIS

### Security
- HTTPS
- role/permission enforcement
- token/session approach defined in design
- no business authorization enforced only in frontend

## 3. Logical architecture

Recommended **modular monolith** for Baseline 1.0.

Why:
- project scope is one institutional system;
- team size is small;
- deployment should remain simple;
- business boundaries still remain explicit;
- avoids premature microservices.

Packages/subsystems:
1. Administration
2. Planning
3. Execution
4. Monitoring/Traceability
5. Fiscalization
6. Corrective Actions
7. Complaints/Critical Points
8. History/Reporting

Each subsystem contains application use cases and domain logic.

## 4. No generic layered dumping ground

Avoid a global structure such as:

```text
controllers/
services/
repositories/
entities/
```

when it mixes unrelated business modules.

Prefer feature-first / bounded-module organization.

## 5. API style

REST for:
- authentication;
- route management;
- journey programming;
- resource assignment;
- mobile execution commands;
- GPS batch upload;
- incidents;
- fiscalization;
- complaints;
- reports.

SSE for:
- pushing active journey/position updates to monitoring web clients.

GPS ingestion must work even if the web monitoring page is closed.

## 6. Geospatial ownership

OpenStreetMap is the visual base map source.

Institutional geometries are stored in PostGIS:
- territorial units;
- routes;
- route versions;
- control points;
- geofences;
- GPS positions;
- critical points;
- citizen report locations.

Never depend on external map rendering for historical data ownership.

## 7. Map provider abstraction

Do not hardcode map tile/provider URLs throughout the code.

Use configuration/adapters such as:
- `MapStyleProvider`
- `TileProviderConfig`
- geocoding provider only if later approved

This allows provider replacement without rewriting business logic.

## 8. Persistence principles

### Route
Versioned.

### GPS
Append-oriented.
Do not update historical GPS points to "correct" them silently.

### Audit
Append-oriented.

### Fiscalization result
Changes require explicit traceability.

## 9. Scalability strategy

Do not overengineer.

Initial operational scale is modest.

PostgreSQL/PostGIS is sufficient.

Prepare for:
- spatial indexes;
- indexes by journey/time;
- optional time-based partitioning of GPS positions later;
- GPS batch ingestion instead of one HTTP request per point if useful.

Do not add Kafka/RabbitMQ/Redis unless a measured need appears.

## 10. Deployment baseline

Vendor-neutral:
- web client;
- mobile client;
- Internet;
- application server;
- PostgreSQL/PostGIS server;
- external cartographic service/provider.

Do not assume AWS/Azure/GCP/Docker/Kubernetes unless later approved.
