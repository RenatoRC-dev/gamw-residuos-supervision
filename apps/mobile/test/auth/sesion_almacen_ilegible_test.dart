import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gamw_mobile/shared/api/cliente_backend.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';
import 'package:gamw_mobile/shared/auth/sesion.dart';

class _Almacen implements AlmacenSesion {
  _Almacen({this.lectura});
  final Future<String?> Function()? lectura;
  int borrados = 0;
  String? guardado;
  @override
  Future<String?> leer() => lectura != null ? lectura!() : Future<String?>.value(guardado);
  @override
  Future<void> guardar(String contenido) async { guardado = contenido; }
  @override
  Future<void> borrar() async { borrados++; guardado = null; }
}

class _Api extends ClienteBackend {
  _Api() : super(Uri.parse('http://127.0.0.1:1/api'));
  @override
  Future<Object?> solicitar(String metodo, String ruta, {Object? cuerpo, String? token}) async => {
    'tokenAcceso': 'token-sintetico', 'usuarioId': 'u1', 'personaNaturalId': 'p1', 'nombreUsuario': 'cuenta',
    'expiraEn': DateTime.now().add(const Duration(hours: 1)).toUtc().toIso8601String(),
  };
}

PlatformException get _descifradoFallido => PlatformException(code: 'Exception encountered', message: 'javax.crypto.BadPaddingException');

void main() {
  late _Api api;
  setUp(() { api = _Api(); });
  tearDown(() { api.cerrar(); });

  test('copia confirmada como criptográficamente ilegible: se descarta solo esa copia y se puede iniciar sesión de nuevo', () async {
    final almacen = _Almacen(lectura: () async => throw _descifradoFallido);
    final sesiones = GestorSesion(api, almacen);
    await sesiones.restaurar();
    expect(sesiones.actual, isNull);
    expect(almacen.borrados, 1);
    await sesiones.iniciar('cuenta', 'clave-sintetica');
    expect(sesiones.actual?.usuarioId, 'u1');
    expect(almacen.guardado, isNotNull);
    expect(almacen.guardado, isNot(contains('clave-sintetica'))); // nunca se persiste la contraseña
    sesiones.dispose();
  });

  test('un error transitorio de lectura NO borra la sesión guardada', () async {
    for (final error in <Object>[
      PlatformException(code: 'timeout', message: 'almacenamiento ocupado'),
      PlatformException(code: 'unavailable'),
      StateError('fallo genérico'),
      TimeoutException('lento'),
    ]) {
      final almacen = _Almacen(lectura: () async => throw error)..guardado = 'copia-intacta';
      final sesiones = GestorSesion(api, almacen);
      await sesiones.restaurar();
      expect(sesiones.actual, isNull, reason: '$error');
      expect(almacen.borrados, 0, reason: 'No se borra por $error');
      expect(almacen.guardado, 'copia-intacta');
      sesiones.dispose();
    }
  });

  test('una restauración obsoleta con error criptográfico no borra la sesión autenticada más reciente', () async {
    final lectura = Completer<String?>();
    final almacen = _Almacen(lectura: () => lectura.future);
    final sesiones = GestorSesion(api, almacen);
    final restauracion = sesiones.restaurar(); // queda esperando la lectura
    await sesiones.iniciar('cuenta', 'clave-sintetica'); // inicio de sesión posterior
    expect(sesiones.actual?.usuarioId, 'u1');
    lectura.completeError(_descifradoFallido); // la lectura antigua falla DESPUÉS
    await restauracion;
    expect(almacen.borrados, 0, reason: 'Una lectura obsoleta no puede borrar la sesión nueva');
    expect(almacen.guardado, isNotNull);
    expect(sesiones.actual?.usuarioId, 'u1');
    sesiones.dispose();
  });

  test('una restauración obsoleta con copia válida antigua no sustituye la sesión nueva', () async {
    final lectura = Completer<String?>();
    final almacen = _Almacen(lectura: () => lectura.future);
    final sesiones = GestorSesion(api, almacen);
    final restauracion = sesiones.restaurar();
    await sesiones.iniciar('cuenta', 'clave-sintetica');
    lectura.complete(jsonEncode({'tokenAcceso': 'token-viejo', 'usuarioId': 'viejo', 'personaNaturalId': 'pv', 'nombreUsuario': 'antigua',
      'expiraEn': DateTime.now().add(const Duration(hours: 1)).toUtc().toIso8601String()}));
    await restauracion;
    expect(sesiones.actual?.usuarioId, 'u1');
    expect(almacen.borrados, 0);
    sesiones.dispose();
  });

  test('restauración e inicio de sesión simultáneos: gana el inicio de sesión y nada se borra', () async {
    final almacen = _Almacen(lectura: () async => throw _descifradoFallido);
    final sesiones = GestorSesion(api, almacen);
    final restauracion = sesiones.restaurar();
    final inicio = sesiones.iniciar('cuenta', 'clave-sintetica'); // sin esperar: concurrentes
    await Future.wait([restauracion, inicio]);
    expect(sesiones.actual?.usuarioId, 'u1');
    expect(almacen.borrados, 0);
    expect(almacen.guardado, isNotNull);
    sesiones.dispose();
  });

  test('copiaCifradaIlegible solo reconoce excepciones criptográficas confirmadas', () {
    expect(copiaCifradaIlegible(_descifradoFallido), isTrue);
    expect(copiaCifradaIlegible(PlatformException(code: 'x', details: 'AEADBadTagException: bad tag')), isTrue);
    expect(copiaCifradaIlegible(PlatformException(code: 'timeout')), isFalse);
    expect(copiaCifradaIlegible(StateError('BadPaddingException')), isFalse); // no es PlatformException
  });
}
