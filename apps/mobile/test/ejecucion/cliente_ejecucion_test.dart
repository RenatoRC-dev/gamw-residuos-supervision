import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/dominio/evento_ejecucion_local.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/aplicacion/cola_ejecucion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/infraestructura/cliente_ejecucion_desarrollo.dart';

void main() {
  test(
    'adaptador local valida 201/200, cabecera y contenido idéntico; 409 conserva conflicto',
    () async {
      final servidor = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      var peticiones = 0;
      final e = EventoEjecucionLocal(
        eventoClienteId: '11111111-1111-4111-8111-111111111111',
        jornadaId: '22222222-2222-4222-8222-222222222222',
        asignacionJornadaId: '33333333-3333-4333-8333-333333333333',
        tipoEvento: TipoEventoEjecucion.inicio,
        capturadoEn: DateTime.utc(2026, 11, 10, 11, 2, 0, 123, 456),
      );
      final escucha = servidor.listen((solicitud) async {
        peticiones++;
        expect(
          solicitud.headers.value('X-GAMW-Development'),
          'gamw-desarrollo',
        );
        expect(solicitud.headers.value('X-Persona-Id'), isNull);
        expect(
          solicitud.uri.path,
          '/api/jornadas/${e.jornadaId}/ejecucion/inicio',
        );
        expect(jsonDecode(await utf8.decoder.bind(solicitud).join()), e.cuerpo);
        solicitud.response.statusCode = peticiones == 1
            ? 201
            : peticiones == 2
            ? 200
            : 409;
        solicitud.response.headers.contentType = ContentType.json;
        solicitud.response.write(
          jsonEncode({
            ...e.cuerpo,
            'jornadaId': e.jornadaId,
            'tipoEvento': 'INICIO',
            'id': '44444444-4444-4444-8444-444444444444',
            'recibidoEn': '2026-11-10T13:47:00Z',
          }),
        );
        await solicitud.response.close();
      });
      final cliente = ClienteEjecucionDesarrollo(
        Uri.parse('http://127.0.0.1:${servidor.port}/api'),
      );
      try {
        expect(await cliente.enviar(e), ResultadoEnvio.sincronizado);
        expect(await cliente.enviar(e), ResultadoEnvio.sincronizado);
        expect(await cliente.enviar(e), ResultadoEnvio.conflicto);
      } finally {
        cliente.cerrar();
        await escucha.cancel();
        await servidor.close(force: true);
      }
    },
  );
  test('el adaptador de desarrollo rechaza destinos externos', () {
    expect(
      () =>
          ClienteEjecucionDesarrollo(Uri.parse('https://externo.example/api')),
      throwsArgumentError,
    );
  });
}
