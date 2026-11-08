import 'dart:async';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/dominio/evento_ejecucion_local.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/aplicacion/cola_ejecucion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/persistencia/almacen_eventos_sqlite.dart';

class ClientePrueba implements ClienteEventosEjecucion {
  ClientePrueba(this.responder);
  Future<ResultadoEnvio> Function(EventoEjecucionLocal) responder;
  final enviados = <EventoEjecucionLocal>[];
  @override
  Future<ResultadoEnvio> enviar(EventoEjecucionLocal e) async {
    enviados.add(e);
    return responder(e);
  }
}

void main() {
  sqfliteFfiInit();
  const jornada = '11111111-1111-4111-8111-111111111111';
  const asignacion = '22222222-2222-4222-8222-222222222222';
  late Directory directorio;
  late AlmacenEventosSqlite almacen;
  late String archivo;
  var contador = 0;
  DateTime reloj() => DateTime.utc(2026, 11, 10, 11, contador++);
  setUp(() async {
    contador = 0;
    directorio = await Directory.systemTemp.createTemp('gamw_ejecucion_test_');
    archivo = '${directorio.path}/eventos.db';
    almacen = await AlmacenEventosSqlite.abrir(
      factory: databaseFactoryFfi,
      ruta: archivo,
    );
  });
  tearDown(() async {
    await almacen.cerrar();
    await directorio.delete(recursive: true);
  });

  test(
    'INICIO y FIN se conservan tras reinicio sin red ni permisos externos',
    () async {
      final cliente = ClientePrueba(
        (_) async => throw const SocketException('Sin conexión'),
      );
      final cola = ColaEjecucion(almacen, cliente, ahora: reloj);
      final inicio = await cola.registrarLocal(
        jornada,
        asignacion,
        TipoEventoEjecucion.inicio,
      );
      final fin = await cola.registrarLocal(
        jornada,
        asignacion,
        TipoEventoEjecucion.fin,
      );
      expect(
        cliente.enviados,
        isEmpty,
      ); // Persistencia anterior a cualquier envío.
      await cola.sincronizar();
      await almacen.cerrar();
      almacen = await AlmacenEventosSqlite.abrir(
        factory: databaseFactoryFfi,
        ruta: archivo,
      );
      final restaurados = await almacen.listar();
      expect(restaurados.map((e) => e.eventoClienteId), [
        inicio.eventoClienteId,
        fin.eventoClienteId,
      ]);
      expect(restaurados.map((e) => e.capturadoEn), [
        inicio.capturadoEn,
        fin.capturadoEn,
      ]);
      expect(
        restaurados.every(
          (e) => e.estadoSincronizacion == EstadoSincronizacion.pendiente,
        ),
        isTrue,
      );
    },
  );

  test(
    'respuesta perdida reintenta el mismo ID sin duplicar evidencia del servidor',
    () async {
      final servidor = <String, Map<String, Object?>>{};
      var perdida = true;
      final cliente = ClientePrueba((e) async {
        expect(
          (await almacen.listar()).any(
            (local) => local.eventoClienteId == e.eventoClienteId,
          ),
          isTrue,
        );
        if (servidor.containsKey(e.eventoClienteId)) {
          expect(servidor[e.eventoClienteId], e.cuerpo);
        } else {
          servidor[e.eventoClienteId] = Map.of(e.cuerpo);
        }
        if (perdida) {
          perdida = false;
          throw const SocketException('Respuesta perdida tras persistir');
        }
        return ResultadoEnvio.sincronizado;
      });
      final cola = ColaEjecucion(almacen, cliente, ahora: reloj);
      final original = await cola.registrarLocal(
        jornada,
        asignacion,
        TipoEventoEjecucion.inicio,
      );
      await cola.sincronizar();
      expect(
        (await almacen.listar()).single.estadoSincronizacion,
        EstadoSincronizacion.pendiente,
      );
      await cola.sincronizar();
      expect(servidor.length, 1);
      expect(cliente.enviados.map((e) => e.eventoClienteId), [
        original.eventoClienteId,
        original.eventoClienteId,
      ]);
      final local = (await almacen.listar()).single;
      expect(local.estadoSincronizacion, EstadoSincronizacion.sincronizado);
      expect(local.capturadoEn, original.capturadoEn);
    },
  );

  test(
    'FIN no adelanta a INICIO y el conflicto conserva ambos eventos',
    () async {
      final cliente = ClientePrueba((_) async => ResultadoEnvio.conflicto);
      final cola = ColaEjecucion(almacen, cliente, ahora: reloj);
      final inicio = await cola.registrarLocal(
        jornada,
        asignacion,
        TipoEventoEjecucion.inicio,
      );
      final fin = await cola.registrarLocal(
        jornada,
        asignacion,
        TipoEventoEjecucion.fin,
      );
      await cola.sincronizar();
      expect(cliente.enviados.map((e) => e.tipoEvento), [
        TipoEventoEjecucion.inicio,
      ]);
      final locales = await almacen.listar();
      expect(locales.first.eventoClienteId, inicio.eventoClienteId);
      expect(
        locales.first.estadoSincronizacion,
        EstadoSincronizacion.conflicto,
      );
      expect(locales.last.eventoClienteId, fin.eventoClienteId);
      expect(locales.last.estadoSincronizacion, EstadoSincronizacion.pendiente);
      await almacen.cerrar();
      almacen = await AlmacenEventosSqlite.abrir(
        factory: databaseFactoryFfi,
        ruta: archivo,
      );
      expect((await almacen.listar()).length, 2);
      expect(
        (await almacen.listar()).first.estadoSincronizacion,
        EstadoSincronizacion.conflicto,
      );
    },
  );

  test(
    'envío lento de INICIO no bloquea la escritura durable de FIN',
    () async {
      final recibido = Completer<void>(),
          respuesta = Completer<ResultadoEnvio>();
      final cliente = ClientePrueba((e) async {
        if (e.tipoEvento == TipoEventoEjecucion.inicio) {
          recibido.complete();
          return respuesta.future;
        }
        return ResultadoEnvio.sincronizado;
      });
      final cola = ColaEjecucion(almacen, cliente, ahora: reloj);
      await cola.registrarLocal(
        jornada,
        asignacion,
        TipoEventoEjecucion.inicio,
      );
      final envio = cola.sincronizar();
      await recibido.future;
      final fin = await cola
          .registrarLocal(jornada, asignacion, TipoEventoEjecucion.fin)
          .timeout(const Duration(seconds: 2));
      expect(
        (await almacen.listar()).last.eventoClienteId,
        fin.eventoClienteId,
      );
      respuesta.complete(ResultadoEnvio.sincronizado);
      await envio;
      await cola.sincronizar();
      expect(cliente.enviados.map((e) => e.tipoEvento), [
        TipoEventoEjecucion.inicio,
        TipoEventoEjecucion.fin,
      ]);
      expect(
        (await almacen.listar()).every(
          (e) => e.estadoSincronizacion == EstadoSincronizacion.sincronizado,
        ),
        isTrue,
      );
    },
  );

  test(
    'capturas iguales conservan INICIO antes de FIN entre varias jornadas',
    () async {
      const otraJornada = '33333333-3333-4333-8333-333333333333';
      final ids = [
        'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
      ].iterator;
      final cliente = ClientePrueba((_) async => ResultadoEnvio.sincronizado);
      final cola = ColaEjecucion(
        almacen,
        cliente,
        ahora: () => DateTime.utc(2026, 11, 10),
        generarId: () {
          ids.moveNext();
          return ids.current;
        },
      );
      for (final id in [jornada, otraJornada]) {
        await cola.registrarLocal(id, asignacion, TipoEventoEjecucion.inicio);
        await cola.registrarLocal(id, asignacion, TipoEventoEjecucion.fin);
      }
      await cola.sincronizar();
      expect(
        cliente.enviados.map((e) => '${e.jornadaId}:${e.tipoEvento.codigo}'),
        [
          '$jornada:INICIO',
          '$jornada:FIN',
          '$otraJornada:INICIO',
          '$otraJornada:FIN',
        ],
      );
    },
  );

  test(
    'no permite FIN huérfano, captura invertida ni otro INICIO local',
    () async {
      final cola = ColaEjecucion(
        almacen,
        ClientePrueba((_) async => ResultadoEnvio.pendiente),
        ahora: reloj,
      );
      await expectLater(
        cola.registrarLocal(jornada, asignacion, TipoEventoEjecucion.fin),
        throwsStateError,
      );
      await cola.registrarLocal(
        jornada,
        asignacion,
        TipoEventoEjecucion.inicio,
      );
      await expectLater(
        cola.registrarLocal(jornada, asignacion, TipoEventoEjecucion.inicio),
        throwsStateError,
      );
      final invertida = ColaEjecucion(
        almacen,
        ClientePrueba((_) async => ResultadoEnvio.pendiente),
        ahora: () => DateTime.utc(2020),
      );
      await expectLater(
        invertida.registrarLocal(jornada, asignacion, TipoEventoEjecucion.fin),
        throwsStateError,
      );
      expect((await almacen.listar()).length, 1);
    },
  );

  test(
    'SQLite protege identidad y captura al cambiar solo el estado técnico',
    () async {
      final cola = ColaEjecucion(
        almacen,
        ClientePrueba((_) async => ResultadoEnvio.pendiente),
        ahora: reloj,
      );
      final e = await cola.registrarLocal(
        jornada,
        asignacion,
        TipoEventoEjecucion.inicio,
      );
      final db = await databaseFactoryFfi.openDatabase(archivo);
      await expectLater(
        db.rawUpdate(
          "UPDATE evento_ejecucion_local SET capturado_en='2020-01-01T00:00:00Z' WHERE evento_cliente_id=?",
          [e.eventoClienteId],
        ),
        throwsException,
      );
      await almacen.cambiarEstado(
        e.eventoClienteId,
        EstadoSincronizacion.conflicto,
      );
      expect((await almacen.listar()).single.capturadoEn, e.capturadoEn);
    },
  );
}
