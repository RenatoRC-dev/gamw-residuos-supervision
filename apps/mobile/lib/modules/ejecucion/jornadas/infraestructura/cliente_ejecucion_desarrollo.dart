import 'dart:convert';
import 'dart:io';
import '../aplicacion/cola_ejecucion.dart';
import '../dominio/evento_ejecucion_local.dart';

// Adaptador técnico local, sin identidad de usuario/persona ni autenticación simulada.
class ClienteEjecucionDesarrollo implements ClienteEventosEjecucion {
  ClienteEjecucionDesarrollo(this.base, {HttpClient? cliente})
    : _cliente = cliente ?? HttpClient() {
    if (!['127.0.0.1', 'localhost', '::1'].contains(base.host) ||
        !['http', 'https'].contains(base.scheme) ||
        base.userInfo.isNotEmpty) {
      throw ArgumentError('El backend de desarrollo debe ser loopback.');
    }
    _cliente.connectionTimeout = const Duration(seconds: 10);
  }
  final Uri base;
  final HttpClient _cliente;

  @override
  Future<ResultadoEnvio> enviar(EventoEjecucionLocal evento) async {
    // Un evento institucional nunca puede degradarse al transporte técnico sin JWT.
    if (evento.usuarioIdCaptura != null) return ResultadoEnvio.pendiente;
    final url = Uri.parse(
      '${base.toString().replaceAll(RegExp(r"/+$"), "")}/jornadas/${evento.jornadaId}/ejecucion/${evento.tipoEvento == TipoEventoEjecucion.inicio ? "inicio" : "fin"}',
    );
    final peticion = await _cliente
        .postUrl(url)
        .timeout(const Duration(seconds: 10));
    peticion.headers.contentType = ContentType.json;
    peticion.headers.set('X-GAMW-Development', 'gamw-desarrollo');
    peticion.write(jsonEncode(evento.cuerpo));
    final respuesta = await peticion.close().timeout(
      const Duration(seconds: 10),
    );
    final contenido = await utf8.decoder
        .bind(respuesta)
        .join()
        .timeout(const Duration(seconds: 10));
    if (respuesta.statusCode == 409 ||
        respuesta.statusCode == 400 ||
        respuesta.statusCode == 404) {
      return ResultadoEnvio.conflicto;
    }
    if (respuesta.statusCode != 200 && respuesta.statusCode != 201) {
      return ResultadoEnvio.pendiente;
    }
    final datos = jsonDecode(contenido) as Map<String, dynamic>;
    if (!_mismoUuid(datos['eventoClienteId'], evento.eventoClienteId) ||
        !_mismoUuid(datos['jornadaId'], evento.jornadaId) ||
        !_mismoUuid(datos['asignacionJornadaId'], evento.asignacionJornadaId) ||
        datos['tipoEvento'] != evento.tipoEvento.codigo ||
        DateTime.parse(datos['capturadoEn'] as String).toUtc() !=
            evento.capturadoEn) {
      throw const FormatException(
        'La respuesta no confirma la misma evidencia enviada.',
      );
    }
    return ResultadoEnvio.sincronizado;
  }

  void cerrar() => _cliente.close(force: true);

  bool _mismoUuid(Object? recibido, String esperado) =>
      recibido is String && recibido.toLowerCase() == esperado.toLowerCase();
}
