#!/usr/bin/env bash
# QA Android en emulador (bajo demanda). Se ejecuta DENTRO de reactivecircus/android-emulator-runner (el emulador ya está activo).
# Backend y PostgreSQL/PostGIS efímeros los crea la propia prueba (AndroidEmuladorQaIT); no hay bases ni credenciales externas.
set -u
SALIDA="${GITHUB_WORKSPACE}/qa-emulador"
mkdir -p "$SALIDA/capturas"
export GAMW_PG_BIN=/usr/lib/postgresql/16/bin GAMW_PG_VERSION_PREFIX='16.'

adb devices
adb shell getprop ro.build.version.release > "$SALIDA/android-version.txt" || true

# Registro del dispositivo (sin secretos: la prueba usa credenciales sintéticas aleatorias, aun así se filtran líneas sensibles).
adb logcat -c || true
( adb logcat -v time 2>&1 | grep -viE 'clave|contrasena|password|authorization|bearer|eyJ' > "$SALIDA/logcat-sanitizado.txt" ) &
LOGCAT_PID=$!
# Capturas de pantalla periódicas (evidencia visual del estado de la app en cada fase).
( while true; do adb exec-out screencap -p > "$SALIDA/capturas/$(date +%H%M%S).png" 2>/dev/null; sleep 15; done ) &
CAPTURAS_PID=$!

cd "$GITHUB_WORKSPACE/backend"
sh ./mvnw --batch-mode --no-transfer-progress \
  -Dgamw.mobile.qa.emulador=true -Dgamw.mobile.device=emulator-5554 \
  -Dtest=AndroidEmuladorQaIT -Dsurefire.failIfNoSpecifiedTests=false test > "$SALIDA/maven-qa-emulador.log" 2>&1
CODIGO=$?

kill "$CAPTURAS_PID" "$LOGCAT_PID" 2>/dev/null || true
adb exec-out screencap -p > "$SALIDA/capturas/final.png" 2>/dev/null || true
# Informe de pruebas y registro de Maven sin líneas sensibles.
mkdir -p "$SALIDA/surefire"
cp -r target/surefire-reports/. "$SALIDA/surefire/" 2>/dev/null || true
grep -viE 'GAMW_QA_CLAVE|dart-defines=|authorization|bearer|eyJ' "$SALIDA/maven-qa-emulador.log" > "$SALIDA/maven-qa-emulador-sanitizado.log" || true
rm -f "$SALIDA/maven-qa-emulador.log"
tail -n 60 "$SALIDA/maven-qa-emulador-sanitizado.log"
exit "$CODIGO"
