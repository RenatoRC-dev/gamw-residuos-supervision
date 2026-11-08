# Estrategia de pruebas — Phase 0

## Backend

- Pruebas unitarias para reglas reales cuando existan; no inventar negocio para
  justificar tests ni clases vacías.
- JUnit/AssertJ y Spring Boot Test para arranque de contexto.
- MockMvc con filtros Security reales para liveness, denegación de acceso
  y protección de métodos inseguros. El perfil `smoke` excluye JDBC/JPA/Flyway
  solo en tests: no verifica persistencia ni certifica arranque con BD.
- Errores MVC/Bean Validation mediante ProblemDetail; las pruebas de transporte
  pueden usar controladores exclusivos de test, nunca endpoints ficticios de negocio.
- `mvnw test` y `mvnw package` ejecutan la suite sin PostgreSQL.
- `mvnw -Ppostgres-integration verify` usa Failsafe y `FlywayBaselineIT` contra
  PostgreSQL real configurado por `DB_*`. Aplica V1 y verifica validación e
  idempotencia de migraciones; requiere una BD dedicada y permisos DDL.
- En Windows, `scripts/Test-Postgres.ps1` permite esa misma integración con los
  binarios PostgreSQL ya instalados y un cluster aislado temporal, sin tocar
  el servicio local existente. No descarga ni instala motores.

## Web

Vitest + jsdom + React Testing Library: smoke de aplicación con router y
respuesta del estado técnico; fallo de red visible. Se simula únicamente HTTP
para probar componentes. `npm run build` incluye comprobación TypeScript.
No se consideran estas pruebas evidencia de backend o PostGIS.

## Mobile

`flutter analyze` y `flutter test`: arranque del widget principal sin permisos
GPS, red ni plugins. Operaciones Flutter siempre secuenciales. Pruebas nativas
en dispositivo y builds Android/iOS se incorporarán cuando proceda.

## Slice 1 y siguientes

PostGIS integration tests contra extensión real: tipo/validez de geometría,
SRID, índice, persistencia/recuperación y conservación de versiones. No H2 como
sustituto espacial. No asumir Docker o Testcontainers.

Cada caso de uso importante añadirá camino feliz, autorización, precondiciones,
persistencia y casos de trazabilidad. GPS añadirá captura online/offline,
sincronización tardía, reintentos duplicados y preservación de `capturedAt`.
Los resultados de fiscalización seguirán siendo decisiones humanas autorizadas.

## Evidencia y CI

Guardar resultados resumidos en `docs/evidence/`, sin secretos. Logs y salidas
generadas quedan ignorados. CI ejecuta backend test/package, web ci/test/build
y mobile pub get/analyze/test en ese orden. La verificación DB dedicada es
separada; CI no afirma haber verificado PostgreSQL/PostGIS.
