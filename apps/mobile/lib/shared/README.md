# Límites de infraestructura móvil

Futuras responsabilidades, sin implementación ni plugins en Phase 0:

- `auth/`: sesión según el diseño aprobado en su slice.
- `location/`: adaptador de ubicación; los widgets no invocan plugins GPS.
- `local_storage/`: persistencia durable independiente de la presentación.
- `synchronization/`: reintentos, confirmación e idempotencia fuera de widgets.
- `map/`: adaptador cartográfico configurable.

GPS disponible e Internet disponible son estados distintos. La captura original
se preservará separada de la recepción. Ninguna lectura GPS prueba recolección.
No se crean interfaces vacías que aparenten funcionalidades implementadas.
