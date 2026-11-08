# ADR 0002 — Persistencia geoespacial institucional

Status: Proposed

Fecha: 2026-11-08

## Propuesta para Slice 1

PostGIS almacenará geometrías institucionales bajo control GAMW. OpenStreetMap
se utilizará solo como fuente/proveedor de mapa base, nunca como base de datos
institucional. MapLibre GL JS está previsto para el cliente React de Slice 1;
no está instalado ni implementado en Phase 0.

Las geometrías de territorio y versiones de ruta deben persistirse en PostGIS,
con migraciones, validación y pruebas espaciales reales. Las futuras jornadas
referenciarán la versión histórica asignada; una edición no debe reescribirla.
No hay entidades ni tablas de negocio creadas en esta fase.

Un proveedor configurable de tiles/estilos evitará URLs dispersas por la UI y
permitirá sustituir el servicio cartográfico. La disponibilidad del proveedor
no determina propiedad ni conservación de los datos institucionales.

## Pendientes

- Finalizar CRS/SRID en Slice 1 y definir orden de coordenadas/intercambio GeoJSON.
- Validar procedencia y clasificación oficial/referencial/de prueba de capas.
- Seleccionar herramienta de dibujo compatible con MapLibre.
- Confirmar disponibilidad de PostGIS en la base dedicada y sus permisos.
- Mantener PENDING la autoridad institucional de creación/aprobación de rutas.

El versionado preserva historia; no decide quién autoriza una ruta. GPS,
coincidencias y geocercas son evidencia y no prueban recolección automáticamente.
