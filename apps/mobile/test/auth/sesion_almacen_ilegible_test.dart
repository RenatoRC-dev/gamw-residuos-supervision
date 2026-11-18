import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gamw_mobile/shared/api/cliente_backend.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';
import 'package:gamw_mobile/shared/auth/sesion.dart';

class _AlmacenIlegible implements AlmacenSesion {
  int borrados = 0;
  String? guardado;
  @override
  Future<String?> leer() async => throw PlatformException(code: 'Exception encountered', message: 'BadPaddingException');
  @override
  Future<void> guardar(String contenido) async { guardado = contenido; }
  @override
  Future<void> borrar() async { borrados++; }
}

class _Api extends ClienteBackend {
  _Api() : super(Uri.parse('http://127.0.0.1:1/api'));
  @override
  Future<Object?> solicitar(String metodo, String ruta, {Object? cuerpo, String? token}) async => {
    'tokenAcceso': 'token-sintetico', 'usuarioId': 'u1', 'personaNaturalId': 'p1', 'nombreUsuario': 'cuenta',
    'expiraEn': DateTime.now().add(const Duration(hours: 1)).toUtc().toIso8601String(),
  };
}

void main() {
  test('una copia de sesión ilegible no bloquea: se descarta y se puede iniciar sesión de nuevo', () async {
    final almacen = _AlmacenIlegible();
    final api = _Api();
    final sesiones = GestorSesion(api, almacen);
    await sesiones.restaurar(); // no lanza
    expect(sesiones.actual, isNull);
    expect(almacen.borrados, 1);
    await sesiones.iniciar('cuenta', 'clave-sintetica');
    expect(sesiones.actual?.usuarioId, 'u1');
    expect(almacen.guardado, isNotNull);
    expect(almacen.guardado, isNot(contains('clave-sintetica'))); // nunca se persiste la contraseña
    sesiones.dispose();
    api.cerrar();
  });
}
