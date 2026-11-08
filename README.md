# GAMW — Plataforma de supervisión y trazabilidad

Plataforma web y móvil para la supervisión, fiscalización y trazabilidad del
servicio de recolección de residuos urbanos del Gobierno Autónomo Municipal de Warnes.

**GPS es evidencia, no prueba automática de recolección.**
El fiscalizador municipal determina el resultado.

## Estructura

```text
apps/
├── web/
└── mobile/
backend/
docs/
└── context/
.github/
└── workflows/
```

## Tecnologías

Java 21, Spring Boot 4.1.2, Maven Wrapper, PostgreSQL y Flyway;
React 19.3, TypeScript, Vite 8, MapLibre GL JS, Terra Draw y npm;
Flutter stable 3.35.4 y Dart 3.9.2.
Node.js 22.18.0. Docker Compose únicamente para PostgreSQL/PostGIS de desarrollo.

## Ejecución

Backend: desde `backend/`, crear `.env` a partir del [ejemplo](backend/.env.example)
y establecer contraseñas locales distintas para el administrador y la aplicación. Compose y Spring Boot cargan ese archivo;
las variables del proceso tienen prioridad. La BD usa `localhost:5433`.

```powershell
docker compose -f compose.dev.yml up -d
./mvnw.cmd spring-boot:run
```

En Linux/macOS: `sh ./mvnw spring-boot:run`.

Para la POC local de rutas, iniciar con `./mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=route-development"`.
Ese perfil permite únicamente acceso loopback con cabecera explícita y origen web
permitido; no sustituye la autenticación ni los permisos institucionales pendientes.
Sin el perfil, las APIs de negocio permanecen bloqueadas.

Web: desde `apps/web/`, con la [configuración pública](apps/web/.env.example).

```powershell
npm ci
npm run dev
```

Abrir `http://127.0.0.1:5173/rutas` para dibujar, guardar y recuperar versiones.
El mapa base es configurable mediante `VITE_MAP_STYLE_URL`. Las pruebas de navegador
se ejecutan con `npm run test:e2e`, con backend POC y Vite activos y Chromium de
Playwright instalado (`npx playwright install chromium`). No generan capturas ni informes.

Mobile: desde `apps/mobile/`, con dispositivo/emulador configurado.
iOS requiere macOS y Xcode.

```powershell
flutter pub get
flutter run
```

## Contexto

[Baseline 1.0 y reglas del proyecto](docs/context/00_README.md).

**Current status: Slice 1 — digitization, persistence and historical versioning of reference routes.**
