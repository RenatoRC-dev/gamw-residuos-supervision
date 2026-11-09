import 'package:flutter_secure_storage/flutter_secure_storage.dart';

abstract interface class AlmacenSesion {
  Future<String?> leer();
  Future<void> guardar(String contenido);
  Future<void> borrar();
}

/// Solo token y respuesta de identidad del servidor; nunca contraseñas.
class AlmacenSesionSeguro implements AlmacenSesion {
  // Fallar sin borrar claves/caché existentes si Android no puede descifrarlas.
  static const opcionesAndroid = AndroidOptions(resetOnError: false);
  AlmacenSesionSeguro({FlutterSecureStorage? storage, String clave = 'gamw.sesion.autenticada'})
    : _storage = storage ?? const FlutterSecureStorage(aOptions: opcionesAndroid), _clave = clave;
  final FlutterSecureStorage _storage;
  final String _clave;
  @override
  Future<String?> leer() => _storage.read(key: _clave);
  @override
  Future<void> guardar(String contenido) => _storage.write(key: _clave, value: contenido);
  @override
  Future<void> borrar() => _storage.delete(key: _clave);
}
