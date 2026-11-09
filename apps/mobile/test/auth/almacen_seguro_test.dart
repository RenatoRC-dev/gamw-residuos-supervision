import 'package:flutter_test/flutter_test.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';

void main() {
  test('un error criptográfico no habilita borrado automático de claves Android',() {
    expect(AlmacenSesionSeguro.opcionesAndroid.toMap()['resetOnError'],'false');
  });
}
