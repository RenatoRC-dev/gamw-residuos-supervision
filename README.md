# GAMW — Plataforma de supervisión y trazabilidad

Plataforma Web y Móvil para la Supervisión, Fiscalización y Trazabilidad del
Servicio de Recolección de Residuos Urbanos mediante Georreferenciación y
Monitoreo GPS para el Gobierno Autónomo Municipal de Warnes.

**Current status: PHASE 0 — ENGINEERING FOUNDATION**

GPS es evidencia, no prueba automática de recolección. El fiscalizador municipal
determina el resultado. La información histórica debe sobrevivir a los cambios
de operador. No hay sanciones automáticas.

## Estructura

- `backend/`: Java/Spring Boot, monolito modular; solo infraestructura técnica.
- `apps/web/`: React/TypeScript, shell técnico y configuración HTTP centralizada.
- `apps/mobile/`: Flutter, shell técnico Android/iOS sin plugins adicionales.
- [docs/context](docs/context/00_README.md): Baseline 1.0; lectura obligatoria.
- [docs/architecture](docs/architecture/adr/0001-engineering-baseline.md): ADR,
  estrategia de pruebas y trazabilidad.
- `docs/evidence/`: resultados verificables por fase/slice.
- `scripts/`: verificaciones reproducibles.

## Requisitos

Java JDK 21 (`JAVA_HOME`), Node.js 22.18.0 y npm 10.9.3, Flutter stable 3.35.4
con Dart 3.9.2 (SDK local utilizado), PostgreSQL accesible y credenciales de una
base **dedicada al proyecto** para ejecutar el backend completo. Maven 3.9.16
se descarga mediante Wrapper; no requiere instalación global. Primeras
resoluciones de dependencias requieren Internet. PostGIS será requisito de
Slice 1 y no se instala automáticamente.

Android requiere su SDK y dispositivo/emulador; iOS requiere macOS y Xcode.
No se verificó un binario móvil de plataforma en Phase 0.

## Backend

Desde `backend/`, exportar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` en el entorno
del proceso (ver [ejemplo sin credenciales](backend/.env.example)). Spring Boot
**no carga `.env` automáticamente**. No guardar secretos en archivos versionados.

```powershell
./mvnw.cmd spring-boot:run
./mvnw.cmd --batch-mode --no-transfer-progress test
./mvnw.cmd --batch-mode --no-transfer-progress package
```

En Linux/macOS usar `sh ./mvnw` en lugar de `./mvnw.cmd`.
`GET /api/technical/health` es liveness del proceso, no readiness de la BD.
Por defecto escucha solo en `127.0.0.1:8080`. La aplicación normal exige
PostgreSQL y ejecuta Flyway al arrancar. Hibernate valida, no crea el esquema.
Solo los tests `smoke` excluyen persistencia; no emplean H2.

La seguridad permite exclusivamente ese GET y deniega el resto. No hay login,
usuarios generados ni matriz de permisos definitiva. CSRF permanece activo.
El despliegue HTTPS y autenticación se diseñarán antes de exponer datos internos.

Para verificar Flyway contra una BD PostgreSQL dedicada ya configurada:

```powershell
./mvnw.cmd --batch-mode --no-transfer-progress -Ppostgres-integration verify
```

Este comando aplica migraciones: **no apuntarlo a una BD ajena al proyecto**.
Alternativamente, en Windows con binarios PostgreSQL locales, el script
[Test-Postgres.ps1](scripts/Test-Postgres.ps1) crea un cluster de prueba aislado
en `.local/`, ejecuta la integración y lo detiene. No usa el servicio existente.

## Web

Desde `apps/web/`:

```powershell
npm ci
npm test
npm run build
npm run dev
```

Abrir la URL que indique Vite (normalmente `http://127.0.0.1:5173`).
La configuración pública usa `VITE_API_BASE_URL` (predeterminado `/api`). El
proxy de desarrollo usa `BACKEND_PROXY_TARGET`; ambos se documentan en
[apps/web/.env.example](apps/web/.env.example). Exportar variables o configurar
el entorno local sin versionar secretos. `VITE_*` se incrusta en el cliente.
Producción necesita servir el frontend y configurar su propio proxy `/api`.
`vite preview` solo previsualiza el build y no es un servidor de producción.

## Mobile

Desde `apps/mobile/`, usando una operación Flutter a la vez:

```powershell
flutter pub get
flutter analyze
flutter test
flutter run
```

`flutter run` necesita un dispositivo disponible. No cambiar de canal ni
actualizar el SDK para ejecutar esta base. `pubspec.lock` se versiona.

## Pruebas y estado

[Estrategia](docs/architecture/testing-strategy.md) ·
[Trazabilidad](docs/architecture/traceability.md) ·
[Evidencia de Phase 0](docs/evidence/phase-0.md).

Se incluyen comandos de verificación secuencial en `scripts/verify.ps1` y CI
básica en `.github/workflows/verify.yml`; esta última se ejecutará si el proyecto
se aloja en GitHub. No se ha creado remoto, commit ni publicación.

No están implementados Slice 1, mapas, entidades de negocio, rutas operativas,
jornadas, GPS, persistencia offline móvil, sincronización, fiscalización,
reclamos ni reportes. Sin Docker, microservicios, IA, IoT u optimización avanzada.
Las decisiones institucionales `PENDING` de la Baseline permanecen abiertas.
