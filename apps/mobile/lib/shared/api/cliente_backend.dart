import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';

class ErrorBackend implements Exception {
  const ErrorBackend(this.estado);
  final int estado;
  @override
  String toString() => 'Solicitud rechazada por el servidor ($estado).';
}

class ClienteBackend {
  ClienteBackend(this.base, {HttpClient? transporte}) : _http = transporte ?? HttpClient() {
    if (base.userInfo.isNotEmpty || base.hasQuery || base.hasFragment ||
        !(base.scheme == 'https' || (kDebugMode && base.scheme == 'http' &&
          ['127.0.0.1', 'localhost', '::1'].contains(base.host)))) {
      throw ArgumentError('Use HTTPS; HTTP solo está permitido para loopback en debug.');
    }
    _http.connectionTimeout = const Duration(seconds: 10);
  }
  final Uri base;
  final HttpClient _http;

  Future<Object?> solicitar(String metodo, String ruta, {Object? cuerpo, String? token}) async {
    final uri = Uri.parse('${base.toString().replaceAll(RegExp(r'/+$'), '')}/$ruta');
    final peticion = await _http.openUrl(metodo, uri).timeout(const Duration(seconds: 15));
    peticion.followRedirects = false;
    peticion.headers.set(HttpHeaders.acceptHeader, 'application/json');
    if (token != null) peticion.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
    if (cuerpo != null) {
      peticion.headers.contentType = ContentType.json;
      peticion.write(jsonEncode(cuerpo));
    }
    final respuesta = await peticion.close().timeout(const Duration(seconds: 15));
    final texto = await utf8.decoder.bind(respuesta).join().timeout(const Duration(seconds: 15));
    if (respuesta.statusCode < 200 || respuesta.statusCode >= 300) throw ErrorBackend(respuesta.statusCode);
    return texto.isEmpty ? null : jsonDecode(texto);
  }

  void cerrar() => _http.close(force: true);
}
