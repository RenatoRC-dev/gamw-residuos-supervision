# GAMW Mobile — Phase 0

Shell técnico Android/iOS con Flutter stable 3.35.4 y Dart 3.9.2 del SDK local.
Ejecutar una operación Flutter a la vez desde este directorio:

```text
flutter pub get
flutter analyze
flutter test
flutter run
```

El último comando requiere dispositivo/emulador; iOS requiere macOS/Xcode.
No hay GPS, mapas, plugins adicionales, persistencia local, sincronización ni
casos de uso de negocio. Los límites futuros se documentan en `lib/shared/`.
Consultar el README raíz y `docs/context/` antes de añadir funcionalidad.
