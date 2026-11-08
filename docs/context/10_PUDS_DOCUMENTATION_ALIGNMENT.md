# PUDS, Thesis and Implementation Alignment

## 1. PUDS phases
- Inicio
- Elaboración
- Construcción
- Transición

## 2. Disciplines
- Modelado del negocio
- Requisitos
- Análisis
- Diseño
- Implementación
- Pruebas
- Despliegue

## 3. Artifact separation

Never mix:

- Requirement = what the system must do.
- Use case = actor/system interaction goal.
- Domain model = business concepts.
- Analysis = responsibilities/collaborations.
- Design = technical solution.
- Implementation = code/components.
- Test = verification.
- Evidence = proof that implementation/test occurred.

## 4. Practical project strategy

The thesis document currently has a previous 37-CU structure.

Baseline 1.0 proposes 31 better-cohesive use cases.

Development may begin from the 31-CU baseline before rewriting every figure/table, provided that:
- changes are documented;
- no old requirement is silently discarded;
- traceability is maintained;
- the final thesis is reconciled with the implementation.

## 5. Chapter 7 status

Current work reached:
- 7.1 Diseño de la Arquitectura
- 7.1.1 Diseño Físico
- deployment diagram and physical deployment description

Before continuing deeply into class/data/sequence/interface design, implementation baseline is being refined.

## 6. Chapter 8 teacher guidance

The instructor explained that implementation should reflect the design/model and contains three main aspects:

1. Implementation of the system architecture.
2. Implementation of the architecture of each subsystem/package.
3. Development tools and communication protocols.

Important interpretation for this project:
- each thesis package can be treated as an implementation subsystem;
- component diagrams should later show how each subsystem is implemented;
- frontend, business/application, data components and connections should be documented;
- source-code organization should make this relationship visible.

## 7. Documentation after implementation

After each vertical slice:
1. record what changed;
2. update requirement/use-case if needed;
3. update domain/design model;
4. capture test evidence;
5. update chapter 8 component implementation description;
6. keep change history.

## 8. Diagram strategy

Do not draw diagrams only to satisfy appearance.

Each final diagram must match implemented reality:
- deployment;
- logical package architecture;
- class design;
- data model;
- sequence diagrams for principal use cases;
- component diagrams per implementation subsystem.

## 9. High-priority requirement rule

Any RF finally classified High must have a UML 2.5 Activity Diagram.

If priorities change due to restructuring, re-evaluate required diagrams.

## 10. Final traceability target

For every major feature:

`Finding → Need → RF → Rule → Actor → CU → Analysis → Design → Component → Test → Evidence`

This traceability is a product-quality mechanism, not just thesis paperwork.
