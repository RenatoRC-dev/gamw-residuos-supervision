import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import '../api/cliente_backend.dart';
import 'almacen_sesion_seguro.dart';

class SesionUsuario {
  const SesionUsuario(this.token, this.expiraEn, this.usuarioId, this.personaNaturalId, this.nombreUsuario);
  final String token, usuarioId, personaNaturalId, nombreUsuario;
  final DateTime expiraEn;
  Map<String, Object> get datos => {'tokenAcceso': token, 'expiraEn': expiraEn.toUtc().toIso8601String(),
    'usuarioId': usuarioId, 'personaNaturalId': personaNaturalId, 'nombreUsuario': nombreUsuario};
  factory SesionUsuario.desde(Map<String, dynamic> d) => SesionUsuario(d['tokenAcceso'] as String,
    DateTime.parse(d['expiraEn'] as String).toUtc(), d['usuarioId'] as String, d['personaNaturalId'] as String, d['nombreUsuario'] as String);
}

class GestorSesion extends ChangeNotifier {
  GestorSesion(this.api, this.almacen, {DateTime Function()? ahora}) : _ahora = ahora ?? DateTime.now;
  final ClienteBackend api;
  final AlmacenSesion almacen;
  final DateTime Function() _ahora;
  SesionUsuario? _sesion;
  Timer? _vence;
  int _revision = 0;
  bool _terminado = false;
  Future<void> _escritura = Future.value();
  SesionUsuario? get actual => _sesion;

  Future<void> _persistir(Future<void> Function() accion) {
    final resultado = _escritura.then((_) => accion());
    _escritura = resultado.then<void>((_) {}, onError: (Object e, StackTrace s) {});
    return resultado;
  }

  void _activar(SesionUsuario sesion) {
    if (_terminado) return;
    _vence?.cancel();
    _sesion = sesion;
    final demora = sesion.expiraEn.difference(_ahora().toUtc());
    _vence = Timer(demora.isNegative ? Duration.zero : demora, () {
      unawaited(invalidar(sesion.token).catchError((Object _) { /* El token vencido ya no se usa en memoria. */ }));
    });
    if (!_terminado) notifyListeners();
  }

  Future<void> iniciar(String nombre, String contrasena) async {
    final revision = ++_revision;
    final d = await api.solicitar('POST', 'autenticacion/login', cuerpo: {'nombreUsuario': nombre, 'contrasena': contrasena});
    if (revision != _revision) return;
    final sesion = SesionUsuario.desde(d as Map<String, dynamic>);
    if (!sesion.expiraEn.isAfter(_ahora().toUtc())) throw const ErrorBackend(401);
    await _persistir(() => almacen.guardar(jsonEncode(sesion.datos)));
    if (revision == _revision) _activar(sesion);
  }

  Future<void> restaurar() async {
    final revision = ++_revision;
    final guardado = await almacen.leer();
    if (revision != _revision || guardado == null) return;
    SesionUsuario sesion;
    try { sesion = SesionUsuario.desde(jsonDecode(guardado) as Map<String, dynamic>); }
    on Object { await cerrar(); return; }
    if (!sesion.expiraEn.isAfter(_ahora().toUtc())) { await cerrar(); return; }
    _activar(sesion);
    try { await identidadActual(); }
    on SocketException { /* Conservar identidad previamente verificada para captura offline. */ }
    on TimeoutException { /* La autorización sigue siendo responsabilidad del backend. */ }
  }

  Future<SesionUsuario> vigente() async {
    final sesion = _sesion;
    if (sesion == null) throw const ErrorBackend(401);
    if (!sesion.expiraEn.isAfter(_ahora().toUtc())) {
      await invalidar(sesion.token);
      throw const ErrorBackend(401);
    }
    return sesion;
  }

  Future<Object?> solicitar(String metodo, String ruta, {Object? cuerpo}) async {
    final sesion = await vigente();
    try { return await api.solicitar(metodo, ruta, cuerpo: cuerpo, token: sesion.token); }
    on ErrorBackend catch (e) { if (e.estado == 401) await invalidar(sesion.token); rethrow; }
  }

  Future<void> identidadActual() async {
    final anterior = await vigente();
    final revision = _revision;
    final d = await solicitar('GET', 'autenticacion/actual') as Map<String, dynamic>;
    if (revision != _revision || _sesion?.token != anterior.token) return;
    if (d['usuarioId'] != anterior.usuarioId) { await invalidar(anterior.token); throw const ErrorBackend(401); }
    final actualizada = SesionUsuario(anterior.token, anterior.expiraEn, d['usuarioId'] as String,
      d['personaNaturalId'] as String, d['nombreUsuario'] as String);
    await _persistir(() => almacen.guardar(jsonEncode(actualizada.datos)));
    if (revision == _revision) _activar(actualizada);
  }

  Future<void> invalidar(String token) async { if (_sesion?.token == token) await cerrar(); }
  Future<void> cerrar() async {
    ++_revision;
    _vence?.cancel();
    _sesion = null;
    if (!_terminado) notifyListeners();
    await _persistir(almacen.borrar);
  }
  @override
  void dispose() { ++_revision; _terminado = true; _vence?.cancel(); _sesion = null; super.dispose(); }
}
