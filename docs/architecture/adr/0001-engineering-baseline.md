# ADR 0001 — Engineering baseline

Status: Accepted — Baseline 1.0

Fecha: 2026-11-08

## Contexto y autoridad

La autorización explícita de Phase 0 fija decisiones de ingeniería. No convierte
propuestas ni pendientes institucionales en hechos confirmados por GAMW.
Se preservan los 13 documentos de `docs/context/`.

## Decisión

- Java 21; Spring Boot 4.1.2; Spring MVC, Validation, Security y Data JPA.
- Maven Wrapper 3.3.4, distribución Maven 3.9.16 con SHA-256 fijado.
  Los scripts y propiedades deben incluirse en el futuro primer commit.
- React 19.3 (19.3.1), TypeScript 5.9.3, Vite 8.x (8.3.6), React Router 7.18.5.
- npm 10.9.3, Node 22.18.0 y `package-lock.json` versionable. Dependencias
  directas fijadas para evitar cambios accidentales en la base.
- Flutter stable local 3.35.4 y Dart 3.9.2. No actualizar SDK ni añadir plugins.
  `pubspec.lock` de la aplicación debe versionarse.
- PostgreSQL; Flyway gestiona evolución. Hibernate `ddl-auto=validate`.
- PostGIS previsto para persistencia geoespacial en Slice 1; no se instala en
  Phase 0. No usar H2 para aparentar integración espacial.
- Monolito modular. Organización: paquete/subsistema → funcionalidad/CU →
  componentes API/aplicación/dominio/persistencia → pruebas equivalentes.
- No Docker ni microservicios como baseline; sin Spring Cloud, Kafka,
  RabbitMQ, Redis ni WebFlux. Los BOM transitivos del parent no implican
  incorporación de esos productos al runtime.

## Límites implementados

El backend solo expone liveness técnico. Security deniega todos los demás
accesos y no proporciona usuarios ni autenticación de negocio. CSRF se conserva;
no se decide aún JWT, sesiones ni roles institucionales. Configuración DB por
variables obligatorias; binding local por defecto. HTTPS del despliegue pendiente.

Los tests smoke excluyen auto-configuración JDBC/JPA/Flyway únicamente en sus
recursos de prueba. El runtime normal requiere PostgreSQL. Integración opt-in
con PostgreSQL real, sin contenedores ni base sustituta.

No se crean paquetes vacíos por cada CU. Los README de límites y `package-info`
reservan la organización sin simular módulos implementados. Las capas de negocio
se incorporarán al comenzar cada slice.

## Consecuencias

La base es pequeña y ejecutable por componente. Cambiar las versiones o incorporar
infraestructura adicional requiere una decisión explícita documentada.
Los locks, migraciones y Wrapper no se ignoran; no se realiza commit en esta fase.

Se fija jsdom 26.1.0 porque es compatible con Node 22.18.0; jsdom 31 exige una
versión posterior. TypeScript 5.9.3 satisface esta base sin adoptar un cambio
mayor innecesario. No se fuerza ninguna resolución ni se ignoran requisitos de motor.

## Referencias técnicas

- [Spring Boot: pruebas de aplicación](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- [Maven Wrapper](https://maven.apache.org/tools/wrapper/)
- [Vite: requisitos de Node](https://vite.dev/guide/)
