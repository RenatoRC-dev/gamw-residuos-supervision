# QA física Android — Redmi 15C · CU-13 / CU-14

Lista ejecutable por el PO (los comandos Flutter/ADB están reservados al PO en este entorno). **Ningún resultado de esta lista se da por
cumplido sin ejecutarla en el dispositivo**: las pruebas de widgets, el emulador (`android-emulator-qa.yml`, GPS sintético) y las IT REST no
sustituyen la validación de GPS físico. Registrar cada punto como PASS / FAIL / BLOCKED con fecha, versión de HEAD y observación breve.
No subir capturas, bases SQLite, logs con credenciales ni evidencia local al repositorio.

## 0. Preparación (una vez)

| # | Paso | Criterio |
|---|---|---|
| 0.1 | Redmi 15C con depuración USB activa; `adb devices -l` (SDK `%LOCALAPPDATA%\Android\Sdk\platform-tools`) | aparece el número de serie como `device` (no `unauthorized`) |
| 0.2 | Anotar modelo, versión de Android/HyperOS y nivel de batería inicial | registro de contexto |
| 0.3 | Ajustes → Batería: «Sin restricciones» para el paquete de prueba `bo.gob.warnes.gamw.gamw_mobile.it`; Inicio automático permitido | se registra si HyperOS lo permitió (sin esto, el segundo plano puede detenerse) |
| 0.4 | Al aire libre o junto a una ventana, ubicación del sistema activada en modo de alta precisión | requisito de GPS real |
| 0.5 | `git rev-parse --short HEAD` y CI verde de ese HEAD | trazabilidad de la versión probada |

El arnés instala únicamente el APK aislado `.it` (verificado con `aapt` antes de instalar); **no** reinstala ni limpia la app normal.

## 1. Corrida automatizada con GPS real (arnés existente `FlujoMovilIT`)

Desde `backend/` (servidor y PostgreSQL/PostGIS efímeros propios de la prueba; ninguna base de desarrollo o producción):

```powershell
.\mvnw.cmd test "-Dtest=FlujoMovilIT" "-Dgamw.mobile.e2e=true" "-Dgamw.mobile.gps.real=true" `
  "-Dgamw.mobile.gps.install-approved=true" "-Dgamw.mobile.device=<SERIE_REDMI>" "-Dgamw.mobile.gps.wait-seconds=180"
```

| # | Verifica | Criterio de PASS |
|---|---|---|
| 1.1 | Compilación `flutter build apk --debug` (arm64) | sin error. Riesgo conocido: el SDK Flutter local exigió Gradle ≥ 9.3.1 (§21); si falla aquí, registrar **BLOCKED (toolchain)**, no FAIL de la app |
| 1.2 | Dos arranques (fase 1 y 2): sesión en KeyStore, SQLite nativo, `adb reverse` | la prueba termina con `Tests run: 1, Failures: 0` |
| 1.3 | CA-13-02: INICIO/FIN de la cuenta A | 2 eventos de la jornada A con `usuario_id` = A; ninguno en la jornada de B |
| 1.4 | CA-13-04/05: posiciones GPS reales recibidas | `posicion_gps` de la jornada A > 0, todas de A, SRID 4326, `POINT` |
| 1.5 | Evento histórico sin actor | sigue con `usuario_id` nulo y `capturadoEn`/`recibidoEn` originales |

## 2. Observación manual en el dispositivo (CU-13)

Con la app de prueba en una jornada asignada propia y backend local del PO (nunca producción):

| # | Acción | Criterio de PASS | CA |
|---|---|---|---|
| 2.1 | Registrar INICIO y conceder ubicación **precisa** y notificaciones | notificación de servicio en primer plano visible; contador «Posiciones pendientes» crece | CA-13-02, CA-13-04 |
| 2.2 | Repetir con **solo ubicación aproximada** | no inicia captura y lo explica; no muestra «captura activa» | E4, H-04 |
| 2.3 | Denegar notificaciones (ubicación precisa concedida) | la captura inicia con advertencia | H-02 |
| 2.4 | Modo avión 5 min con GPS activo, caminar | las posiciones siguen aumentando como pendientes; ninguna se pierde | CA-13-05, FA-01, E7 |
| 2.5 | Salir del modo avión y «Sincronizar pendientes» | pendientes pasan a sincronizadas sin duplicados; `capturado_en` ≠ `recibido_en` en la base | CA-13-06, RN-CU-13-03 |
| 2.6 | Pantalla apagada 10 min durante la captura | al volver hay posiciones del intervalo (si HyperOS lo detuvo: **FAIL** con nota de ajustes de batería) | segundo plano |
| 2.7 | Forzar detención de la app desde Ajustes y reabrir | la evidencia local y la cola permanecen; la captura puede reanudarse con «Reanudar GPS de jornada» | CA-13-09, FA-05 |
| 2.8 | Cerrar sesión e ingresar con otra cuenta | no envía pendientes de la primera cuenta ni le reasigna GPS | CA-13-07, E6 |
| 2.9 | Registrar FIN con posiciones pendientes | la captura se detiene; las pendientes se conservan y sincronizan después | CA-13-08 |
| 2.10 | Batería: anotar % inicial y final de una corrida de 60 min | registro informativo (no hay umbral institucional aprobado) | PENDING |
| 2.11 | Monitoreo/trayectoria web municipal de la jornada | muestra la evidencia como tal; ningún texto declara cumplimiento | CA-13-10 |

## 3. Observación manual CU-14 (incidencia en línea, A5)

| # | Acción | Criterio de PASS | CA |
|---|---|---|---|
| 3.1 | Seleccionar la asignación **antes** de INICIO; registrar INICIO; luego abrir «Incidencias de mi asignación» | la fecha sugerida corresponde a la apertura del formulario (posterior al INICIO), no a la selección | CA-14-02 |
| 3.2 | Registrar una incidencia con descripción, sin coordenadas | «Incidencia confirmada por el servidor»; visible en «Consultar incidencias registradas» | CA-14-01, CA-14-07, FA-02 |
| 3.3 | Intentar registrar sin descripción | no se envía; se pide completar | CA-14-04 |
| 3.4 | Modo avión y registrar | «Sin confirmación…»; el botón pasa a «Reintentar misma solicitud»; al volver la red, una sola incidencia | CA-14-06 |
| 3.5 | Tras FIN, intentar registrar | rechazo (registro tardío no habilitado); ninguna confirmación | política pendiente |
| 3.6 | Expediente municipal (A3, `CORRECTIVA_CONSULTAR`) | la incidencia aparece como antecedente; no se creó correctiva ni dictamen | CA-14-05 |

**Fuera de esta QA (pendiente institucional, no se prueba ni se simula):** incidencias offline durables, registro por A4 y consulta por
supervisor (D1), categorías oficiales (PENDING-06), cadencia GPS institucional.

## 4. Cierre

CU-13 solo puede reclasificarse si 1.x y 2.1–2.9 resultan PASS en el dispositivo y el PO lo registra en la matriz (§25). Cualquier FAIL se
reporta con el número de punto y la observación; se corrige solo el defecto demostrado.
