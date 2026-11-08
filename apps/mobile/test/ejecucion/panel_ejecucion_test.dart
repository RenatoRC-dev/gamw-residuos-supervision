import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/dominio/evento_ejecucion_local.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/aplicacion/cola_ejecucion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/presentacion/panel_ejecucion_tecnica.dart';

class ClientePanel implements ClienteEventosEjecucion {
  bool conectado = false;
  @override
  Future<ResultadoEnvio> enviar(EventoEjecucionLocal e) async {
    if (!conectado) {
      throw const SocketException('Sin conexión');
    }
    return ResultadoEnvio.sincronizado;
  }
}

// La durabilidad se comprueba con SQLite real en cola_ejecucion_test.dart.
// Este doble mantiene la prueba de presentación dentro del reloj virtual de Flutter.
class AlmacenPanel implements AlmacenEventosEjecucion {
  final eventos = <EventoEjecucionLocal>[];
  @override
  Future<void> registrar(EventoEjecucionLocal evento) async {
    eventos.add(evento);
  }

  @override
  Future<List<EventoEjecucionLocal>> listar() async => List.of(eventos);
  @override
  Future<void> cambiarEstado(String id, EstadoSincronizacion estado) async {
    final indice = eventos.indexWhere((e) => e.eventoClienteId == id);
    final e = eventos[indice];
    eventos[indice] = EventoEjecucionLocal(
      eventoClienteId: e.eventoClienteId,
      jornadaId: e.jornadaId,
      asignacionJornadaId: e.asignacionJornadaId,
      tipoEvento: e.tipoEvento,
      capturadoEn: e.capturadoEn,
      estadoSincronizacion: estado,
    );
  }
}

void main() {
  testWidgets(
    'captura INICIO y FIN sin conexión y reintenta conservando evidencia',
    (tester) async {
      tester.view.physicalSize = const Size(1400, 1400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final almacen = AlmacenPanel(), cliente = ClientePanel();
      final cola = ColaEjecucion(almacen, cliente);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              child: PanelEjecucionTecnica(
                jornadaId: '11111111-1111-4111-8111-111111111111',
                asignacionId: '22222222-2222-4222-8222-222222222222',
                apiUrl: 'http://127.0.0.1:8080/api',
                cola: cola,
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Registrar INICIO local'));
      await tester.pumpAndSettle();
      expect(find.textContaining('PENDIENTE'), findsOneWidget);
      await tester.tap(find.text('Registrar FIN local'));
      await tester.pumpAndSettle();
      expect(find.textContaining('PENDIENTE'), findsNWidgets(2));
      final anteriores = await almacen.listar();
      cliente.conectado = true;
      await tester.tap(find.text('Reintentar sincronización'));
      await tester.pumpAndSettle();
      expect(find.textContaining('SINCRONIZADO'), findsNWidgets(2));
      final finales = await almacen.listar();
      expect(
        finales.map((e) => e.eventoClienteId),
        anteriores.map((e) => e.eventoClienteId),
      );
      expect(tester.takeException(), isNull);
    },
  );
}
