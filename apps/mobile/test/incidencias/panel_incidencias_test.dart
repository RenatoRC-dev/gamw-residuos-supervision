import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gamw_mobile/modules/ejecucion/incidencias/presentacion/panel_incidencias.dart';
import 'package:gamw_mobile/shared/api/cliente_backend.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';
import 'package:gamw_mobile/shared/auth/sesion.dart';

// CU-14 (A5, en línea): el panel solo confirma lo que el servidor confirmó; sin cola offline ni SQLite (no habilitados).
const _usuario = '11111111-1111-4111-8111-111111111111';
const _otro = '55555555-5555-4555-8555-555555555555';
const _jornada = '22222222-2222-4222-8222-222222222222';
const _asignacion = '33333333-3333-4333-8333-333333333333';

class _Seguro implements AlmacenSesion {
  String? valor;
  @override Future<String?> leer() async => valor;
  @override Future<void> guardar(String s) async { valor = s; }
  @override Future<void> borrar() async { valor = null; }
}

class _Backend extends ClienteBackend {
  _Backend() : super(Uri.parse('http://127.0.0.1:1/api'));
  final enviadas = <Map<String, Object?>>[];
  final rutas = <String>[];
  /// Respuestas programadas para los POST de incidencia, en orden: un `int` = error HTTP, `'red'` = sin conexión, null = éxito.
  final respuestas = <Object?>[];
  @override
  Future<Object?> solicitar(String metodo, String ruta, {Object? cuerpo, String? token}) async {
    if (ruta.endsWith('login')) {
      final nombre = (cuerpo! as Map<String, Object?>)['nombreUsuario'];
      final id = nombre == 'otra' ? _otro : _usuario;
      return {'tokenAcceso': 'jwt-$id', 'expiraEn': DateTime.now().add(const Duration(hours: 1)).toUtc().toIso8601String(),
        'usuarioId': id, 'personaNaturalId': id, 'nombreUsuario': nombre};
    }
    rutas.add('$metodo $ruta token=$token');
    if (metodo == 'GET') return <Object?>[];
    final d = Map<String, Object?>.from(cuerpo! as Map);
    enviadas.add(d);
    final r = respuestas.isEmpty ? null : respuestas.removeAt(0);
    if (r == 'red') throw const SocketException('Sin conexión');
    if (r is int) throw ErrorBackend(r);
    return {'id': 'inc-${d['solicitudId']}', 'jornadaId': _jornada, 'usuarioId': _usuario, 'ocurridaEn': d['ocurridaEn'],
      'registradaEn': DateTime.now().toUtc().toIso8601String(), 'descripcion': d['descripcion'],
      'lugarDescripcion': d['lugarDescripcion'], 'evidenciaDeclarada': d['evidenciaDeclarada']};
  }
}

Future<(GestorSesion, _Backend)> _sesion({String cuenta = 'conductor'}) async {
  final api = _Backend();
  final sesion = GestorSesion(api, _Seguro());
  await sesion.iniciar(cuenta, 'clave');
  return (sesion, api);
}

Future<void> _montar(WidgetTester tester, GestorSesion sesion, {DateTime Function()? ahora}) async {
  await tester.pumpWidget(MaterialApp(home: Scaffold(body: SingleChildScrollView(child: PanelIncidencias(
    sesiones: sesion, usuarioId: _usuario, jornadaId: _jornada, asignacionId: _asignacion, ahora: ahora)))));
  await tester.tap(find.text('Incidencias de mi asignación'));
  await tester.pumpAndSettle();
}

Finder _campo(String etiqueta) => find.widgetWithText(TextField, etiqueta);
Future<void> _registrar(WidgetTester tester, String boton) async {
  await tester.ensureVisible(find.text(boton));
  await tester.tap(find.text(boton));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('registra con la fecha declarada y confirma solo tras la respuesta del servidor', (tester) async {
    final (sesion, api) = await _sesion();
    await _montar(tester, sesion);
    await tester.enterText(_campo('Fecha del hecho ISO 8601 (Z = UTC; Bolivia −04:00)'), '2026-11-19T10:00:00-04:00');
    await tester.enterText(_campo('Descripción factual obligatoria'), 'Vía inaccesible por obra');
    await tester.enterText(_campo('Lugar descrito (opcional)'), 'Esquina sintética');
    await _registrar(tester, 'Registrar incidencia');
    expect(api.enviadas, hasLength(1));
    final d = api.enviadas.single;
    expect(d['asignacionJornadaId'], _asignacion);
    expect(d['ocurridaEn'], '2026-11-19T14:00:00.000Z', reason: 'fecha declarada convertida a UTC sin sustituirla por la recepción');
    expect(d['descripcion'], 'Vía inaccesible por obra');
    expect(d.containsKey('usuarioId'), isFalse, reason: 'el registrante lo determina el servidor');
    expect(api.rutas.single, 'POST jornadas/$_jornada/incidencias token=jwt-$_usuario');
    expect(find.textContaining('Incidencia confirmada por el servidor'), findsOneWidget);
    expect(find.textContaining('No determina incumplimiento'), findsOneWidget);
    expect(find.text('Vía inaccesible por obra'), findsOneWidget, reason: 'queda visible como antecedente');
  });

  testWidgets('sin descripción o sin zona horaria explícita no se envía nada', (tester) async {
    final (sesion, api) = await _sesion();
    await _montar(tester, sesion);
    await _registrar(tester, 'Registrar incidencia');
    expect(find.textContaining('Complete descripción y fecha ISO 8601'), findsOneWidget);
    await tester.enterText(_campo('Descripción factual obligatoria'), 'Avería');
    await tester.enterText(_campo('Fecha del hecho ISO 8601 (Z = UTC; Bolivia −04:00)'), '2026-11-19T10:00:00');
    await _registrar(tester, 'Registrar incidencia');
    expect(find.textContaining('Complete descripción y fecha ISO 8601'), findsOneWidget);
    expect(api.enviadas, isEmpty);
  });

  testWidgets('sin red o con 503 no hay confirmación falsa y el reintento conserva la misma solicitud', (tester) async {
    final (sesion, api) = await _sesion();
    api.respuestas.addAll(['red', 503, null]);
    await _montar(tester, sesion);
    await tester.enterText(_campo('Descripción factual obligatoria'), 'Contenedor bloqueado');
    await _registrar(tester, 'Registrar incidencia');
    expect(find.textContaining('Sin confirmación'), findsOneWidget);
    expect(find.textContaining('Incidencia confirmada'), findsNothing);
    final campo = tester.widget<TextField>(_campo('Descripción factual obligatoria'));
    expect(campo.enabled, isFalse, reason: 'la solicitud pendiente no se puede alterar antes del reintento');
    await _registrar(tester, 'Reintentar misma solicitud');
    expect(find.textContaining('Registro no confirmado'), findsOneWidget);
    expect(find.textContaining('no es una cola offline'), findsOneWidget);
    await _registrar(tester, 'Reintentar misma solicitud');
    expect(find.textContaining('Incidencia confirmada por el servidor'), findsOneWidget);
    expect(api.enviadas, hasLength(3));
    expect(api.enviadas.map((e) => e['solicitudId']).toSet(), hasLength(1), reason: 'idempotencia: mismo solicitudId');
    expect(api.enviadas.map((e) => e['ocurridaEn']).toSet(), hasLength(1));
  });

  testWidgets('400 permite corregir; 403 y 409 se informan sin confirmar', (tester) async {
    final (sesion, api) = await _sesion();
    api.respuestas.addAll([400, 403, 409]);
    await _montar(tester, sesion);
    await tester.enterText(_campo('Descripción factual obligatoria'), 'Hecho');
    await _registrar(tester, 'Registrar incidencia');
    expect(find.textContaining('Datos rechazados'), findsOneWidget);
    expect(tester.widget<TextField>(_campo('Descripción factual obligatoria')).enabled, isTrue, reason: 'tras 400 se puede corregir');
    await _registrar(tester, 'Registrar incidencia');
    expect(find.textContaining('No tiene autorización para esta asignación'), findsOneWidget);
    await _registrar(tester, 'Reintentar misma solicitud');
    expect(find.textContaining('Se requiere INICIO sincronizado y jornada sin FIN'), findsOneWidget);
    expect(find.textContaining('Incidencia confirmada'), findsNothing);
    expect(api.enviadas[0]['solicitudId'], isNot(api.enviadas[1]['solicitudId']), reason: 'tras 400 se genera una solicitud nueva');
    expect(api.enviadas[1]['solicitudId'], api.enviadas[2]['solicitudId']);
  });

  testWidgets('la fecha sugerida se toma al abrir el formulario, no al seleccionar la asignación', (tester) async {
    final (sesion, api) = await _sesion();
    var reloj = DateTime.utc(2026, 11, 19, 12);          // selección de la asignación (antes del INICIO)
    await tester.pumpWidget(MaterialApp(home: Scaffold(body: SingleChildScrollView(child: PanelIncidencias(
      sesiones: sesion, usuarioId: _usuario, jornadaId: _jornada, asignacionId: _asignacion, ahora: () => reloj)))));
    reloj = DateTime.utc(2026, 11, 19, 14, 30);          // tras INICIO, el conductor abre el formulario
    await tester.tap(find.text('Incidencias de mi asignación'));
    await tester.pumpAndSettle();
    final fecha = _campo('Fecha del hecho ISO 8601 (Z = UTC; Bolivia −04:00)');
    expect(tester.widget<TextField>(fecha).controller!.text, '2026-11-19T14:30:00.000Z');
    await tester.enterText(_campo('Descripción factual obligatoria'), 'Avería sintética');
    await _registrar(tester, 'Registrar incidencia');
    expect(api.enviadas.single['ocurridaEn'], '2026-11-19T14:30:00.000Z');
    // Una fecha editada por el conductor no se sustituye al reabrir.
    await tester.enterText(fecha, '2026-11-19T10:05:00-04:00');
    reloj = DateTime.utc(2026, 11, 19, 16);
    await tester.tap(find.text('Incidencias de mi asignación')); await tester.pumpAndSettle();
    await tester.tap(find.text('Incidencias de mi asignación')); await tester.pumpAndSettle();
    expect(tester.widget<TextField>(fecha).controller!.text, '2026-11-19T10:05:00-04:00');
  });

  testWidgets('una sesión de otra cuenta no puede registrar ni consultar con este panel', (tester) async {
    final (sesion, api) = await _sesion(cuenta: 'otra');
    await _montar(tester, sesion);
    final registrar = tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Registrar incidencia'));
    expect(registrar.onPressed, isNull);
    final consultar = tester.widget<TextButton>(find.widgetWithText(TextButton, 'Consultar incidencias registradas'));
    expect(consultar.onPressed, isNull);
    expect(api.rutas, isEmpty);
  });

  testWidgets('consulta solo las incidencias propias de la asignación', (tester) async {
    final (sesion, api) = await _sesion();
    await _montar(tester, sesion);
    await _registrar(tester, 'Consultar incidencias registradas');
    expect(api.rutas.single, 'GET jornadas/$_jornada/incidencias-propias?asignacionId=$_asignacion token=jwt-$_usuario');
  });
}
