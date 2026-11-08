# Repository Structure and Coding Rules

## 1. Architectural organization rule

The source code must reflect the thesis/product decomposition:

**Package/Sub-system → Use Case → Components**

Traceability target:

**Package UML → Subsystem → CU → Code → Tests**

## 2. Recommended monorepo

```text
gamw-platform/
├── apps/
│   ├── web/
│   └── mobile/
├── backend/
├── docs/
│   ├── context/
│   ├── architecture/
│   └── evidence/
└── scripts/
```

Exact build tooling may vary.

## 3. Backend recommended structure

```text
backend/
└── src/main/java/.../
    ├── shared/
    │   ├── security/
    │   ├── errors/
    │   ├── time/
    │   └── geo/
    │
    └── modules/
        ├── administration/
        │   ├── authentication/
        │   ├── users/
        │   ├── roles/
        │   ├── operators/
        │   ├── service_periods/
        │   ├── vehicles/
        │   └── operational_responsibles/
        │
        ├── planning/
        │   ├── territorial_structure/
        │   ├── routes/
        │   ├── control_points/
        │   ├── journey_programming/
        │   └── journey_assignment/
        │
        ├── execution/
        │   ├── execute_journey/
        │   └── operational_incidents/
        │
        ├── monitoring/
        │   ├── gps_ingestion/
        │   ├── live_monitoring/
        │   ├── trajectory/
        │   ├── integrity_review/
        │   ├── control_point_review/
        │   └── deviation_review/
        │
        ├── fiscalization/
        │   ├── field_inspection/
        │   └── journey_fiscalization/
        │
        ├── corrective_actions/
        │   ├── manage_action/
        │   ├── verify_action/
        │   └── journey_reprogramming/
        │
        ├── complaints/
        │   ├── public_report/
        │   ├── institutional_complaint/
        │   ├── complaint_validation/
        │   └── critical_points/
        │
        └── history_reporting/
            ├── institutional_history/
            ├── reports_indicators/
            └── audit_log/
```

Use-case folder names do not need literal `cu09_` prefixes in code if names are stable and traceability metadata exists, but adding CU identifiers to documentation/tests is encouraged.

## 4. Internal module organization

A use-case module may contain:

```text
journey_programming/
├── api/
├── application/
├── domain/
├── persistence/
└── tests/
```

Not every folder must exist if unnecessary.

Avoid ceremonial architecture with empty layers.

## 5. Application layer

Contains use-case orchestration.

Examples:
- `ProgramJourneyUseCase`
- `AssignJourneyResourcesUseCase`
- `StartJourneyUseCase`
- `FinishJourneyUseCase`

Do not put unrelated workflows in `GeneralService`.

## 6. Domain layer

Contains:
- domain entities/value objects;
- invariant rules;
- domain policies.

Examples:
- route version cannot mutate historical journey references;
- journey must be in allowed state to start;
- fiscalization result requires authorized actor.

## 7. Persistence

Repositories should be owned by the module/domain that needs them.

Shared data does not mean one global `RepositoryService`.

## 8. Frontend web structure

Recommended:

```text
apps/web/src/
├── app/
├── shared/
│   ├── ui/
│   ├── auth/
│   ├── api/
│   ├── map/
│   └── errors/
└── modules/
    ├── administration/
    ├── planning/
    ├── execution/
    ├── monitoring/
    ├── fiscalization/
    ├── corrective-actions/
    ├── complaints/
    └── history-reporting/
```

Within each module:

```text
routes/
├── pages/
├── components/
├── api/
├── hooks/
├── model/
└── tests/
```

## 9. Flutter structure

Recommended:

```text
apps/mobile/lib/
├── app/
├── shared/
│   ├── auth/
│   ├── location/
│   ├── local_storage/
│   ├── synchronization/
│   └── map/
└── modules/
    ├── assigned_journey/
    ├── journey_execution/
    ├── incidents/
    └── gps_tracking/
```

The driver UI must not own synchronization logic.

## 10. Public citizen form

Keep the public form separated logically from internal authenticated UI.

Example:
- public route `/reportar`
- no internal navigation
- no internal API data exposure
- dedicated rate limits/validation

## 11. Coding constraints

- No business logic in React components.
- No business logic in REST controllers.
- No raw SQL scattered across controllers/services.
- No direct external map provider calls from domain logic.
- No direct mobile GPS plugin usage inside presentation widgets.
- No silent mutation of historical route/GPS/audit data.
- No "admin can do everything" shortcut without permission checks.
- No giant `Util` or `Helper` classes for business rules.

## 12. Naming

Use domain terminology consistently.

Prefer:
- `Journey`
- `RouteVersion`
- `GPSPosition`
- `Fiscalization`
- `CorrectiveAction`

Avoid inconsistent synonyms across modules.

## 13. Testing location

Tests should mirror module structure.

Every important use case requires:
- application/use-case tests;
- persistence/integration tests where needed;
- API contract tests;
- frontend interaction tests for critical paths.

High-risk geospatial logic requires explicit test fixtures.
