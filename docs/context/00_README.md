# GAMW Graduation Project — Codex Context Pack
## Baseline 1.0 — Product, Requirements, Architecture, Implementation and Traceability

### Project
**Plataforma Web y Móvil para la Supervisión, Fiscalización y Trazabilidad del Servicio de Recolección de Residuos Urbanos mediante Georreferenciación y Monitoreo GPS para el Gobierno Autónomo Municipal de Warnes**

Case study: **Gobierno Autónomo Municipal de Warnes (GAMW), Santa Cruz, Bolivia**

### Purpose of this context pack
This folder is the authoritative working context for a coding agent (Codex/OpenCode or equivalent) that will help implement the project.

The agent must NOT treat every statement as equally authoritative. Every relevant decision is classified as:

- **CONFIRMED**: supported by the institutional interview, current project baseline, or an explicit user decision.
- **PRELIMINARY**: currently used but still subject to institutional validation.
- **PROPOSAL**: a design/product decision adopted for implementation baseline, but not yet confirmed by GAMW.
- **PENDING**: information still missing or requiring validation.
- **OUT OF SCOPE**: explicitly excluded unless the Product Owner authorizes a change.

### Mandatory methodological rule
Do not jump straight from an idea to code.

Use this chain:

**Business → Requirements → Use Cases → Analysis → Design → Implementation → Tests → Evidence**

For implementation work, a practical iterative loop is allowed:

**Baseline requirement/use case → vertical slice implementation → test/evidence → refine UML/documentation → maintain traceability**

This is compatible with PUDS as long as the baseline is explicit and changes are controlled.

### Mandatory standards
- PUDS
- UML 2.5 / 2.5.1
- IEEE 830-1998
- APA 7 for thesis text

### Core product principles
1. GPS is evidence, not automatic proof of waste collection.
2. The municipal fiscalizador determines compliance.
3. Technology supports municipal control; it does not replace human judgment.
4. Information must remain under GAMW control even if the operator changes.
5. Do not invent contractual duties.
6. Do not add functions without institutional or product justification.
7. Do not turn the product into a generic CRUD system.
8. GPS availability, Internet connectivity, and synchronization are different states.
9. If GPS is available but Internet is not, capture locally and sync later while preserving original capture metadata.
10. No automatic sanctions.

### How to use this folder
Recommended reading order:

1. `01_PRODUCT_VISION_SCOPE.md`
2. `02_ACTORS_USE_CASES_BASELINE.md`
3. `03_END_TO_END_OPERATIONAL_FLOW.md`
4. `04_REQUIREMENTS_TRACEABILITY_GAPS.md`
5. `05_DOMAIN_MODEL_BUSINESS_RULES.md`
6. `06_TECHNICAL_ARCHITECTURE_BASELINE.md`
7. `07_GEOSPATIAL_GPS_MAPPING_STRATEGY.md`
8. `08_REPOSITORY_STRUCTURE_AND_CODING_RULES.md`
9. `09_IMPLEMENTATION_ROADMAP_VERTICAL_SLICES.md`
10. `10_PUDS_DOCUMENTATION_ALIGNMENT.md`
11. `11_RISKS_OPEN_DECISIONS_VALIDATION.md`
12. `12_CODEX_AGENT_OPERATING_INSTRUCTIONS.md`

### Important warning
This pack intentionally contains a **development baseline** that is more refined than the current thesis document in some areas. When implementation reveals better details, update requirements and documentation through controlled traceability. Do not silently rewrite the business meaning of the project.
