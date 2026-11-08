# Trazabilidad — plantilla Baseline 1.0

Cadena mínima:

**Package → Use Case → RF → Component → Test → Evidence**

| Package/subsistema | CU | RF | Regla/actor | Componente | Prueba | Evidencia | Clasificación/pendientes |
|---|---|---|---|---|---|---|---|

La tabla se completará al iniciar cada slice. No declara implementados los
31 CU propuestos. Phase 0 es infraestructura transversal y no satisface por sí
misma ningún CU funcional: no se le asignan RF ficticios.

Antes de implementar: identificar actor, datos, reglas, clasificación, criterio
de aceptación y dependencias. Después: vincular componentes/pruebas y evidencia;
evaluar cambios en RF/CU, modelos UML, diseño y tesis.

Conservar el enlace ampliado:
hallazgo → necesidad → RF → regla → actor → CU → análisis → diseño → componente
→ prueba → evidencia. Mantener visibles CONFIRMED, PRELIMINARY, PROPOSAL,
PENDING y OUT OF SCOPE. El cambio de 37 a 31 CU y las brechas de RF documentadas
no se resuelven silenciosamente mediante implementación.
