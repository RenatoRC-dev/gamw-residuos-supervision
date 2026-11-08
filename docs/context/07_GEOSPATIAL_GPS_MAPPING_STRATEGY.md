# Warnes Geospatial, Mapping and GPS Strategy

## 1. Key principle

OpenStreetMap is a **base map**, not the institutional source of truth.

GAMW-owned/reference data must be stored in PostGIS.

## 2. Geospatial data types

Recommended model:

### Territorial unit
- geometry: `Polygon` / `MultiPolygon`

### Route version
- geometry: `LineString` / `MultiLineString` when needed
- CRS stored consistently
- version metadata

### Control point
- geometry: `Point`

### Geofence
- geometry: polygon or derived radius around a point

### GPS position
- geometry: `Point`
- timestamp + accuracy + journey metadata

### Critical point
- geometry: `Point` or polygon if later needed

### Citizen report
- geometry: `Point`

## 3. Warnes map modes

The same map engine should support several product modes.

### Mode A — Planning / reference routes
Used by operator supervisor and authorized municipal users.

Capabilities:
- view official/reference territorial layers;
- draw route;
- edit route geometry;
- save route version;
- display control points/geofences.

### Mode B — Live monitoring
Used by fiscalizador and operator supervisor within permission scope.

Capabilities:
- active journeys;
- last known positions;
- live/near-live route trace;
- reference route overlay;
- tracking freshness;
- incidents;
- status.

### Mode C — Historical/fiscalization
Capabilities:
- select date/journey;
- load route version used by that journey;
- reconstruct GPS trajectory;
- show control-point hits;
- show interruptions;
- show deviations/stops;
- show evidence/complaints when relevant.

### Mode D — Territorial complaints/critical points
Capabilities:
- public report pin selection;
- internal complaint review;
- validated critical points;
- recurrence visualization;
- optional heatmap.

## 4. Route digitization

User flow proposal:
1. Select territory/sector.
2. Create route.
3. Draw polyline over the map.
4. Adjust vertices.
5. Enter name/reference metadata.
6. Associate validated frequency/schedule.
7. Save as route version.

Do not require raw coordinates.

## 5. Route versioning

Recommended fields:
- `route_id`
- `version_number`
- `valid_from`
- `valid_to` nullable
- `geometry`
- `created_by`
- `created_at`
- `change_reason` when applicable

A journey stores `route_version_id`.

## 6. GPS record model

Minimum desired fields:
- `id`
- `journey_id`
- `vehicle_id`
- `responsible_id`
- `captured_at`
- `received_at`
- `latitude`
- `longitude`
- `accuracy_m`
- `source`
- `sync_state`
- `integrity_flags` or normalized integrity-event relationship

Optional if platform/device supports:
- speed
- heading
- altitude
- mock-location indicator

Do not require unsupported mobile metadata.

## 7. GPS capture policy

PENDING:
Final capture interval must be tested.

Do not hardcode a thesis claim such as "every 5 seconds" without performance/battery/pilot evidence.

Configuration should allow tuning.

## 8. Offline synchronization

Local queue requirements:
- durable storage;
- ordered original timestamps;
- retry-safe;
- idempotency key/client UUID;
- batch sync support;
- duplicate prevention.

Server should acknowledge accepted records.

## 9. Near-real-time monitoring

Definition:
"Near-real-time" means the monitoring map receives updates shortly after the backend receives new GPS positions.

Do not promise strict hard real-time.

Suggested flow:
Flutter → REST GPS ingestion → database → monitoring event → SSE → React map.

## 10. Integrity/anomaly review

Potential technical rules:
- time gap longer than configured threshold;
- distance jump incompatible with elapsed time;
- accuracy above configured warning threshold;
- position outside plausible service area;
- mock-location signal if available;
- stale tracking;
- unusual stop duration.

These generate **reviewable flags**, not sanctions or automatic fiscalization results.

## 11. Control points/geofences

Possible checks:
- distance from GPS point to control point;
- entry/exit into geofence;
- first/last timestamp within geofence.

Use PostGIS spatial queries.

Again:
**geofence match ≠ collection proof**.

## 12. Public report map

Public user should:
- select location on map;
- optionally use browser geolocation with consent;
- manually correct the pin;
- attach optional photo.

Do not reveal active truck positions or internal routes on the public form unless explicitly approved.

## 13. Critical points / microbasurales

A critical point becomes useful when linked to history.

Suggested data:
- location;
- description;
- first detected;
- last validated;
- status;
- evidence;
- recurrence count;
- associated complaints;
- nearby route/sector;
- validation history.

Heatmaps should be based on validated/relevant institutional events, not raw unverified submissions.

## 14. Base-map operational warning

Do not hardcode the public OpenStreetMap tile server as a production dependency.

Use a configurable tile/style provider.

GAMW data remains usable even if the external cartographic provider is unavailable.
