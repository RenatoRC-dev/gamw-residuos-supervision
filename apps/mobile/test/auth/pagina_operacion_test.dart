import 'package:flutter/material.dart';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/persistencia/almacen_eventos_sqlite.dart';
import 'package:gamw_mobile/modules/ejecucion/gps/dominio/posicion_gps_local.dart';
import 'package:gamw_mobile/shared/api/cliente_backend.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';
import 'package:gamw_mobile/shared/auth/sesion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/dominio/evento_ejecucion_local.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/presentacion/pagina_operacion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/infraestructura/cache_jornadas.dart';

class _Seguro implements AlmacenSesion {
  String? valor;
  @override Future<String?> leer() async => valor;
  @override Future<void> guardar(String s) async { valor=s; }
  @override Future<void> borrar() async { valor=null; }
}
class _Almacen implements AlmacenEventosEjecucion {
  final eventos=<EventoEjecucionLocal>[];
  @override Future<void> registrar(EventoEjecucionLocal e) async { eventos.add(e); }
  @override Future<List<EventoEjecucionLocal>> listar() async => eventos.toList();
  @override Future<void> cambiarEstado(String id,EstadoSincronizacion estado) async {
    final i=eventos.indexWhere((e)=>e.eventoClienteId==id); final e=eventos[i];
    eventos[i]=EventoEjecucionLocal(eventoClienteId:e.eventoClienteId,jornadaId:e.jornadaId,
      asignacionJornadaId:e.asignacionJornadaId,tipoEvento:e.tipoEvento,capturadoEn:e.capturadoEn,
      usuarioIdCaptura:e.usuarioIdCaptura,estadoSincronizacion:estado);
  }
}
// Doble de control, sin proveedor ni coordenadas. La persistencia SQLite tiene sus tests propios.
class _AlmacenGps extends _Almacen implements AlmacenEventosSqlite {
  Map<String,Object?>? contexto;
  @override String get ruta => '/db/control-gps.db';
  @override Future<Map<String,Object?>?> contextoGpsActivo() async => contexto;
  @override Future<List<PosicionGpsLocal>> listarPosiciones(String usuario) async => [];
  @override Future<bool> hayGpsNoAceptado(String jornada) async => false;
  @override Future<void> cerrar() async { }
  @override Future<void> registrarPosicion(PosicionGpsLocal p) async => throw UnsupportedError('No hay proveedor en este test de control');
  @override Future<void> estadoPosicion(String captura,EstadoSincronizacion estado,String? error) async { }
}
class _Backend extends ClienteBackend {
  _Backend():super(Uri.parse('http://127.0.0.1:1/api'));
  static const id='11111111-1111-4111-8111-111111111111';
  static const otroId='55555555-5555-4555-8555-555555555555';
  static const jornada='22222222-2222-4222-8222-222222222222';
  static const asignacion='33333333-3333-4333-8333-333333333333';
  bool sinRed=false;
  bool rechazarEnvios=false;
  int envios=0;
  @override Future<Object?> solicitar(String metodo,String ruta,{Object? cuerpo,String? token}) async {
    if(ruta.endsWith('login')) {
      final nombre=(cuerpo! as Map<String,Object?>)['nombreUsuario'];
      final usuario=nombre=='Otra cuenta'?otroId:id;
      return {'tokenAcceso':usuario,'expiraEn':DateTime.now().add(const Duration(hours:1)).toUtc().toIso8601String(),
        'usuarioId':usuario,'personaNaturalId':usuario,'nombreUsuario':nombre};
    }
    if(sinRed) throw const SocketException('Sin conexión');
    if(ruta=='mis-jornadas') {
      if(token==otroId) return [];
      return [{'jornadaId':jornada,'asignacionJornadaId':asignacion,'inicioProgramado':'2026-11-08','vigente':true}];
    }
    envios++;
    if(rechazarEnvios) throw const ErrorBackend(401);
    final d=cuerpo! as Map<String,Object?>;
    return {...d,'jornadaId':jornada,'usuarioId':id,'tipoEvento':ruta.endsWith('inicio')?'INICIO':'FIN'};
  }
}
class _Cache implements CacheJornadas {
  @override Future<List<Map<String,dynamic>>> leer(String id) async => id==_Backend.id
    ? [{'jornadaId':_Backend.jornada,'asignacionJornadaId':_Backend.asignacion,'inicioProgramado':'2026-11-08','vigente':true}] : [];
  @override Future<void> guardar(String id,List<Map<String,dynamic>> j) async { }
}
void main() {
  testWidgets('expiración no corta GPS local; reautenticar otra cuenta detiene contexto anterior sin enviar', (tester) async {
    final almacen=_AlmacenGps();
    final sesion=GestorSesion(_Backend(),_Seguro());
    await sesion.iniciar('Cuenta de prueba','clave');
    final control=<String>[];
    const canal=MethodChannel('gamw/gps');
    // Solo control del servicio; no se simulan lecturas ni coordenadas GPS.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(canal,(call) async {
      control.add(call.method);
      if(call.method=='permisos') return true;
      if(call.method=='estado') return 'Esperando ubicación';
      if(call.method=='iniciar') {
        almacen.contexto={'usuario_id_captura':_Backend.id};
        return 'Esperando ubicación';
      }
      if(call.method=='detener') almacen.contexto=null;
      return null;
    });
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:SingleChildScrollView(child:PaginaOperacion(sesiones:sesion,almacen:almacen)))));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(DropdownButton<String>)); await tester.pumpAndSettle();
    await tester.tap(find.textContaining('vigente').last); await tester.pumpAndSettle();
    await tester.tap(find.text('Registrar INICIO')); await tester.pumpAndSettle();
    expect(control,contains('iniciar'));
    await sesion.invalidar(sesion.actual!.token); await tester.pumpAndSettle();
    expect(control, isNot(contains('detener')));
    expect((await almacen.contextoGpsActivo())!['usuario_id_captura'],_Backend.id);
    await tester.enterText(find.byType(TextField).first,'Otra cuenta');
    await tester.enterText(find.byType(TextField).last,'clave');
    await tester.tap(find.text('Ingresar')); await tester.pumpAndSettle();
    expect(control,contains('detener')); expect(await almacen.contextoGpsActivo(),isNull);
    expect((sesion.api as _Backend).envios,0);
    expect((await almacen.listar()).single.usuarioIdCaptura,_Backend.id);
    await tester.pumpWidget(const SizedBox.shrink());
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(canal,null);
    sesion.dispose(); sesion.api.cerrar(); await almacen.cerrar();
  });
  testWidgets('login, selección propia, INICIO/FIN y logout conservan evidencia', (tester) async {
    final api=_Backend(), seguro=_Seguro(), almacen=_Almacen();
    final sesion=GestorSesion(api,seguro);
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:SingleChildScrollView(child:PaginaOperacion(sesiones:sesion,almacen:almacen)))));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).first,'Cuenta de prueba');
    await tester.enterText(find.byType(TextField).last,'clave-sintetica');
    await tester.tap(find.text('Ingresar')); await tester.pumpAndSettle();
    expect(find.text('Sesión: Cuenta de prueba'),findsOneWidget);
    await tester.tap(find.byType(DropdownButton<String>)); await tester.pumpAndSettle();
    await tester.tap(find.textContaining('vigente').last); await tester.pumpAndSettle();
    await tester.tap(find.text('Registrar INICIO')); await tester.pumpAndSettle();
    await tester.tap(find.text('Registrar FIN')); await tester.pumpAndSettle();
    expect(almacen.eventos.length,2);
    expect(almacen.eventos.every((e)=>e.usuarioIdCaptura==_Backend.id),isTrue);
    expect(api.envios,0);
    expect(almacen.eventos.every((e)=>e.estadoSincronizacion==EstadoSincronizacion.pendiente),isTrue);
    expect(seguro.valor,isNot(contains('clave-sintetica')));
    await tester.tap(find.text('Cerrar sesión')); await tester.pumpAndSettle();
    expect(find.text('Ingresar'),findsOneWidget); expect(almacen.eventos.length,2);
    await tester.pumpWidget(const SizedBox.shrink()); sesion.dispose(); api.cerrar();
  });
  testWidgets('401 oculta detalles; login recarga evidencia propia sin sincronizar ni compartir cuentas', (tester) async {
    final api=_Backend(), seguro=_Seguro(), almacen=_Almacen();
    final sesion=GestorSesion(api,seguro);
    await sesion.iniciar('Cuenta de prueba','clave');
    final original=EventoEjecucionLocal(eventoClienteId:'44444444-4444-4444-8444-444444444444',
      jornadaId:_Backend.jornada,asignacionJornadaId:_Backend.asignacion,tipoEvento:TipoEventoEjecucion.inicio,
      capturadoEn:DateTime.utc(2026),usuarioIdCaptura:_Backend.id);
    await almacen.registrar(original);
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:SingleChildScrollView(
      child:PaginaOperacion(sesiones:sesion,almacen:almacen)))));
    await tester.pumpAndSettle();
    final evidencia=find.textContaining('INICIO ·');
    expect(evidencia,findsOneWidget);
    expect(api.envios,0);
    api.rechazarEnvios=true;
    await tester.tap(find.text('Sincronizar pendientes')); await tester.pumpAndSettle();
    expect(api.envios,1);
    expect(sesion.actual,isNull);
    expect(evidencia,findsNothing);
    expect(almacen.eventos.single,same(original));

    // Simula evidencia durable añadida mientras la pantalla estaba sin sesión.
    await almacen.registrar(EventoEjecucionLocal(eventoClienteId:'66666666-6666-4666-8666-666666666666',
      jornadaId:_Backend.jornada,asignacionJornadaId:_Backend.asignacion,tipoEvento:TipoEventoEjecucion.fin,
      capturadoEn:DateTime.utc(2026,1,2),usuarioIdCaptura:_Backend.id));
    await tester.enterText(find.byType(TextField).first,'Otra cuenta');
    await tester.enterText(find.byType(TextField).last,'clave');
    await tester.tap(find.text('Ingresar')); await tester.pumpAndSettle();
    expect(evidencia,findsNothing);
    expect(find.textContaining('FIN ·'),findsNothing);
    expect(find.textContaining('2026-11-08 ·'),findsNothing);
    expect(api.envios,1);
    await tester.tap(find.text('Sincronizar pendientes')); await tester.pumpAndSettle();
    expect(api.envios,1); // La cola ajena ni siquiera llega al transporte HTTP.
    expect(almacen.eventos.length,2);
    await tester.tap(find.text('Cerrar sesión')); await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).first,'Cuenta de prueba');
    await tester.enterText(find.byType(TextField).last,'clave');
    await tester.tap(find.text('Ingresar')); await tester.pumpAndSettle();
    expect(evidencia,findsOneWidget);
    expect(find.textContaining('FIN ·'),findsOneWidget);
    expect(api.envios,1); // Reautenticar y refrescar nunca envía evidencia.
    expect(almacen.eventos.every((e)=>e.estadoSincronizacion==EstadoSincronizacion.pendiente),isTrue);
    expect(almacen.eventos.first,same(original));
    await tester.pumpWidget(const SizedBox.shrink()); sesion.dispose(); api.cerrar();
  });
  testWidgets('contexto guardado permite FIN local después de reinicio sin red', (tester) async {
    final api=_Backend(), seguro=_Seguro(), almacen=_Almacen();
    final sesion=GestorSesion(api,seguro);
    await sesion.iniciar('Cuenta de prueba','clave');
    api.sinRed=true;
    await almacen.registrar(EventoEjecucionLocal(eventoClienteId:'44444444-4444-4444-8444-444444444444',
      jornadaId:_Backend.jornada,asignacionJornadaId:_Backend.asignacion,tipoEvento:TipoEventoEjecucion.inicio,
      capturadoEn:DateTime.utc(2026),usuarioIdCaptura:_Backend.id));
    await tester.pumpWidget(MaterialApp(home:Scaffold(body:SingleChildScrollView(
      child:PaginaOperacion(sesiones:sesion,almacen:almacen,cache:_Cache())))));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(DropdownButton<String>)); await tester.pumpAndSettle();
    await tester.tap(find.textContaining('vigente').last); await tester.pumpAndSettle();
    await tester.tap(find.text('Registrar FIN')); await tester.pumpAndSettle();
    expect(almacen.eventos.length,2);
    expect(almacen.eventos.last.usuarioIdCaptura,_Backend.id);
    expect(almacen.eventos.last.estadoSincronizacion,EstadoSincronizacion.pendiente);
    await tester.pumpWidget(const SizedBox.shrink()); sesion.dispose(); api.cerrar();
  });
}
