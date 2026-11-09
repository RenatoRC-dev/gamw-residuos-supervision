import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../../../../shared/auth/almacen_sesion_seguro.dart';

abstract interface class CacheJornadas {
  Future<List<Map<String,dynamic>>> leer(String usuarioId);
  Future<void> guardar(String usuarioId,List<Map<String,dynamic>> jornadas);
}

/// Contexto de captura offline por cuenta. Nunca concede autorización en servidor.
class CacheJornadasSeguro implements CacheJornadas {
  CacheJornadasSeguro({FlutterSecureStorage? storage}) : _storage=storage??const FlutterSecureStorage(aOptions:AlmacenSesionSeguro.opcionesAndroid);
  final FlutterSecureStorage _storage;
  @override
  Future<List<Map<String,dynamic>>> leer(String usuarioId) async {
    final texto=await _storage.read(key:'gamw.jornadas.$usuarioId');
    if(texto==null) return [];
    try { return (jsonDecode(texto) as List<dynamic>).cast<Map<String,dynamic>>(); }
    on FormatException { return []; }
    on TypeError { return []; }
  }
  @override
  Future<void> guardar(String usuarioId,List<Map<String,dynamic>> jornadas) =>
    _storage.write(key:'gamw.jornadas.$usuarioId',value:jsonEncode(jornadas));
}
