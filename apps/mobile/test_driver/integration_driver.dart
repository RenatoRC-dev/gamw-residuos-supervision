import 'package:integration_test/integration_test_driver.dart';

// Los resultados se devuelven al proceso de pruebas, sin crear informes adicionales.
Future<void> main() => integrationDriver(responseDataCallback: (_) async {});
