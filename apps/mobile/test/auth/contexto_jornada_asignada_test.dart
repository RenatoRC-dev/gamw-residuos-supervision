import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/presentacion/contexto_jornada_asignada.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/presentacion/pagina_operacion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/infraestructura/cache_jornadas.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/dominio/evento_ejecucion_local.dart';
import 'package:gamw_mobile/shared/api/cliente_backend.dart';
import 'package:gamw_mobile/shared/auth/sesion.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';

Map<String, dynamic> _jornada() => {
  'jornadaId': 'jornada-sintetica', 'asignacionJornadaId': 'asignacion-historica',
  'inicioProgramado': '2026-05-10T12:00:00Z', 'finProgramado': null, 'vigente': true,
  'vigenteDesde': '2026-05-10T11:00:00Z', 'vigenteHasta': '2026-05-10T13:00:00Z',
  'contextoOperativo': {
    'periodo': {'id': 'periodo-1', 'fechaInicio': '2026-01-01', 'fechaFin': '2026-12-31',
      'empresaOperadoraId': 'empresa-1', 'razonSocial': 'Empresa sintética CU13'},
    'ruta': {'rutaId': 'ruta-1', 'codigo': 'REF-SINTETICA', 'nombre': 'Referencia histórica',
      'rutaVersionId': 'version-antigua-1', 'numeroVersion': 1},
    'vehiculo': {'id': 'vehiculo-original', 'placa': 'SINTETICA-001', 'anio': null},
    'vinculoPersonaEmpresaId': 'vinculo-persona-1', 'vinculoVehiculoEmpresaId': 'vinculo-vehiculo-1',
  },
};

class _SesionMemoria implements AlmacenSesion {
  String? valor;
  @override Future<String?> leer() async => valor;
  @override Future<void> guardar(String s) async { valor = s; }
  @override Future<void> borrar() async { valor = null; }
}
class _EvidenciaMemoria implements AlmacenEventosEjecucion {
  @override Future<List<EventoEjecucionLocal>> listar() async => [];
  @override Future<void> registrar(EventoEjecucionLocal e) async => throw StateError('Este recorrido solo consulta');
  @override Future<void> cambiarEstado(String id, EstadoSincronizacion estado) async => throw StateError('No sincronizar');
}
class _CacheMemoria implements CacheJornadas {
  @override Future<List<Map<String,dynamic>>> leer(String usuarioId) async => usuarioId == 'cuenta-A' ? [_jornada()] : [];
  @override Future<void> guardar(String usuarioId, List<Map<String,dynamic>> jornadas) async { }
}
class _ApiContexto extends ClienteBackend {
  _ApiContexto() : super(Uri.parse('http://127.0.0.1:1/api'));
  bool sinRed = false;
  int consultas = 0, enviosOperativos = 0;
  @override Future<Object?> solicitar(String metodo, String ruta, {Object? cuerpo, String? token}) async {
    if (ruta == 'autenticacion/login') {
      final nombre = (cuerpo as Map<String,Object?>)['nombreUsuario'] as String;
      return {'tokenAcceso': nombre, 'usuarioId': nombre, 'personaNaturalId': 'persona-$nombre',
        'nombreUsuario': nombre, 'expiraEn': DateTime.now().add(const Duration(hours: 1)).toUtc().toIso8601String()};
    }
    if (sinRed) throw const SocketException('Sin actualización');
    if (ruta == 'mis-jornadas') {
      expect(metodo, 'GET');
      expect(token, 'cuenta-A');
      consultas++;
      return [_jornada()];
    }
    enviosOperativos++;
    throw StateError('No debe enviar evidencia al consultar el contexto');
  }
}

Future<void> _expandir(WidgetTester tester) async {
  final titulo = find.text('Contexto operativo de la jornada');
  await tester.ensureVisible(titulo);
  await tester.tap(titulo);
  await tester.pumpAndSettle();
}
Widget _pagina(Widget child) => MaterialApp(home: Scaffold(body: SingleChildScrollView(child: child)));

void main() {
  testWidgets('presenta periodo, vehículo y versión histórica exacta sin inventar campos opcionales', (tester) async {
    await tester.pumpWidget(_pagina(ContextoJornadaAsignada(jornada: _jornada())));
    await _expandir(tester);
    for (final texto in ['Jornada: jornada-sintetica', 'Asignación: asignacion-historica',
      'Empresa del periodo: Empresa sintética CU13', 'Periodo: periodo-1',
      'Versión de ruta asignada: version-antigua-1', 'Vehículo ID: vehiculo-original',
      'Asignación histórica cerrada', 'Año del vehículo: No disponible',
      'Fin programado: No disponible', 'Vínculo de responsable: vinculo-persona-1']) {
      expect(find.text(texto), findsOneWidget);
    }
    expect(find.textContaining('SINTETICA-001'), findsOneWidget);
    expect(find.textContaining('REF-SINTETICA'), findsOneWidget);
    expect(find.text('Inicio programado: 2026-05-10T12:00:00Z'), findsOneWidget);
    expect(find.text('Fechas del periodo: 2026-01-01 — 2026-12-31'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
  testWidgets('copia antigua sin contexto muestra indisponibilidad sin bloquear ni inventar datos', (tester) async {
    await tester.pumpWidget(_pagina(const ContextoJornadaAsignada(jornada: {
      'jornadaId': 'jornada-antigua', 'asignacionJornadaId': 'asignacion-antigua',
    })));
    await _expandir(tester);
    expect(find.textContaining('Contexto operativo no disponible en esta copia.'), findsOneWidget);
    expect(find.text('Jornada: jornada-antigua'), findsOneWidget);
    expect(find.textContaining('SINTETICA-001'), findsNothing);
    expect(tester.takeException(), isNull);
  });
  for (final offline in [false, true]) {
    testWidgets('Página consume contexto ${offline ? 'guardado por cuenta sin red' : 'desde la API autenticada'} sin sincronizar', (tester) async {
      final api = _ApiContexto();
      final sesiones = GestorSesion(api, _SesionMemoria());
      await sesiones.iniciar('cuenta-A', 'clave-sintetica-no-persistida');
      api.sinRed = offline;
      await tester.pumpWidget(_pagina(PaginaOperacion(sesiones: sesiones, almacen: _EvidenciaMemoria(),
        cache: offline ? _CacheMemoria() : null)));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(DropdownButton<String>));
      await tester.pumpAndSettle();
      await tester.tap(find.textContaining('vigente').last);
      await tester.pumpAndSettle();
      await _expandir(tester);
      expect(find.text('Versión de ruta asignada: version-antigua-1'), findsOneWidget);
      expect(find.textContaining('SINTETICA-001'), findsOneWidget);
      expect(api.consultas, offline ? 0 : 1);
      expect(api.enviosOperativos, 0);
      await sesiones.cerrar();
      await sesiones.iniciar('cuenta-B', 'otra-clave-sintetica');
      await tester.pumpAndSettle();
      expect(find.textContaining('SINTETICA-001'), findsNothing);
      expect(find.text('Versión de ruta asignada: version-antigua-1'), findsNothing);
      expect(api.enviosOperativos, 0);
      await tester.pumpWidget(const SizedBox.shrink());
      sesiones.dispose(); api.cerrar();
    });
  }
}
