import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:gamw_mobile/shared/api/cliente_backend.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';
import 'package:gamw_mobile/shared/auth/sesion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/aplicacion/cola_ejecucion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/dominio/evento_ejecucion_local.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/infraestructura/cliente_ejecucion_autenticada.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/persistencia/almacen_eventos_sqlite.dart';

class SesionMemoria implements AlmacenSesion {
  String? contenido;
  @override Future<String?> leer() async => contenido;
  @override Future<void> guardar(String valor) async { contenido = valor; }
  @override Future<void> borrar() async { contenido = null; }
}

void main() {
  sqfliteFfiInit();
  const a='11111111-1111-4111-8111-111111111111', b='22222222-2222-4222-8222-222222222222';
  const j='33333333-3333-4333-8333-333333333333', asig='44444444-4444-4444-8444-444444444444';
  late HttpServer servidor;
  late ClienteBackend api;
  late GestorSesion sesiones;
  late SesionMemoria seguro;
  late Directory temporal;
  late AlmacenEventosSqlite almacen;
  late DateTime ahora;
  var estado=200;
  var offline=false;
  var perder=false;
  Completer<void>? espera;
  final enviados=<Map<String,dynamic>>[];
  final headers=<String?>[];
  final serverEventos=<String,Map<String,dynamic>>{};
  setUp(() async {
    ahora=DateTime.utc(2026,11,8,12);
    estado=200; offline=false; perder=false; espera=null; enviados.clear(); headers.clear(); serverEventos.clear();
    temporal=await Directory.systemTemp.createTemp('gamw-auth-unit-');
    almacen=await AlmacenEventosSqlite.abrir(factory: databaseFactoryFfi,ruta:'${temporal.path}/eventos.db');
    seguro=SesionMemoria();
    servidor=await HttpServer.bind(InternetAddress.loopbackIPv4,0);
    servidor.listen((r) async {
      final cuerpo=await utf8.decoder.bind(r).join();
      final d=cuerpo.isEmpty?<String,dynamic>{}:jsonDecode(cuerpo) as Map<String,dynamic>;
      final token=r.headers.value('Authorization');
      if(r.uri.path.endsWith('/login')) {
        expect(token,isNull);
        final id=d['nombreUsuario']=='A'?a:b;
        r.response.headers.contentType=ContentType.json;
        r.response.write(jsonEncode({'tokenAcceso':'token-$id','expiraEn':ahora.add(const Duration(hours:1)).toIso8601String(),
          'usuarioId':id,'personaNaturalId':id,'nombreUsuario':d['nombreUsuario']}));
      } else if(r.uri.path.endsWith('/actual')) {
        final id=token=='Bearer token-$a'?a:b;
        r.response.headers.contentType=ContentType.json;
        r.response.write(jsonEncode({'usuarioId':id,'personaNaturalId':id,'nombreUsuario':id==a?'A':'B'}));
      } else {
        headers.add(token); enviados.add(d);
        if(espera!=null) await espera!.future;
        if(offline) { r.response.statusCode=503; }
        else if(estado!=200) { r.response.statusCode=estado; }
        else {
          final id=token=='Bearer token-$a'?a:b;
          final e=serverEventos.putIfAbsent(d['eventoClienteId'] as String,()=>{...d,'jornadaId':r.uri.pathSegments[2],
            'tipoEvento':r.uri.path.endsWith('/inicio')?'INICIO':'FIN','usuarioId':id});
          if(perder) { perder=false; r.response.statusCode=503; }
          else { r.response.headers.contentType=ContentType.json; r.response.write(jsonEncode(e)); }
        }
      }
      await r.response.close();
    });
    api=ClienteBackend(Uri.parse('http://127.0.0.1:${servidor.port}/api'));
    sesiones=GestorSesion(api,seguro,ahora:()=>ahora);
  });
  tearDown(() async {
    sesiones.dispose(); api.cerrar(); await servidor.close(force:true); await almacen.cerrar();
    // Solo archivos sintéticos propiedad de este test.
    await temporal.delete(recursive:true);
  });

  test('restaura sesión sin persistir contraseña y expira sin borrar evidencia',() async {
    await sesiones.iniciar('A','clave-sintetica');
    expect(seguro.contenido, isNot(contains('clave-sintetica')));
    final cola=ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones),ahora:()=>ahora);
    await cola.registrarLocal(j,asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:a);
    sesiones.dispose(); sesiones=GestorSesion(api,seguro,ahora:()=>ahora);
    await sesiones.restaurar(); expect(sesiones.actual!.usuarioId,a);
    ahora=ahora.add(const Duration(hours:2));
    await expectLater(sesiones.vigente(),throwsA(isA<ErrorBackend>()));
    expect(sesiones.actual,isNull); expect(seguro.contenido,isNull);
    expect((await almacen.listar()).single.usuarioIdCaptura,a);
  });

  test('cambio de cuenta bloquea cola ajena y luego recupera IDs y orden originales',() async {
    await sesiones.iniciar('A','clave');
    final cola=ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones),ahora:()=>ahora);
    final inicio=await cola.registrarLocal(j,asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:a);
    final fin=await cola.registrarLocal(j,asig,TipoEventoEjecucion.fin,usuarioIdCaptura:a);
    await sesiones.cerrar(); await sesiones.iniciar('B','clave');
    await cola.sincronizar(); expect(enviados,isEmpty);
    expect((await almacen.listar()).every((e)=>e.estadoSincronizacion==EstadoSincronizacion.pendiente),isTrue);
    await almacen.cerrar(); almacen=await AlmacenEventosSqlite.abrir(factory:databaseFactoryFfi,ruta:'${temporal.path}/eventos.db');
    await sesiones.cerrar(); await sesiones.iniciar('A','clave');
    await ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones)).sincronizar();
    expect(enviados.map((e)=>e['eventoClienteId']),[inicio.eventoClienteId,fin.eventoClienteId]);
    expect(headers.every((h)=>h=='Bearer token-$a'),isTrue);
    expect(enviados.every((e)=>!e.containsKey('usuarioIdCaptura')&&!e.containsKey('usuarioId')),isTrue);
  });

  test('401 conserva pendientes y pide reautenticación; 403 y 409 conservan conflicto',() async {
    await sesiones.iniciar('A','clave');
    final cola=ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones),ahora:()=>ahora);
    await cola.registrarLocal(j,asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:a);
    await cola.registrarLocal(j,asig,TipoEventoEjecucion.fin,usuarioIdCaptura:a);
    estado=401; await cola.sincronizar(); expect(sesiones.actual,isNull);
    expect((await almacen.listar()).first.estadoSincronizacion,EstadoSincronizacion.pendiente);
    await sesiones.iniciar('A','clave'); estado=403; await cola.sincronizar();
    expect((await almacen.listar()).first.estadoSincronizacion,EstadoSincronizacion.conflicto);
    expect((await almacen.listar()).last.estadoSincronizacion,EstadoSincronizacion.pendiente);
    final otro=await cola.registrarLocal(b,asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:a);
    estado=409; await cola.sincronizar();
    expect((await almacen.listar()).firstWhere((e)=>e.eventoClienteId==otro.eventoClienteId).estadoSincronizacion,EstadoSincronizacion.conflicto);
  });

  test('cola bloqueada de A no transmite su JWT ni impide otra jornada propia de B',() async {
    await sesiones.iniciar('A','clave');
    final cola=ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones),ahora:()=>ahora);
    final deA=await cola.registrarLocal(j,asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:a);
    await sesiones.cerrar(); await sesiones.iniciar('B','clave');
    final deB=await cola.registrarLocal(b,asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:b);
    await cola.sincronizar();
    expect(enviados.single['eventoClienteId'],deB.eventoClienteId);
    expect(headers.single,'Bearer token-$b');
    expect((await almacen.listar()).firstWhere((e)=>e.eventoClienteId==deA.eventoClienteId).estadoSincronizacion,EstadoSincronizacion.pendiente);
    expect((await almacen.listar()).firstWhere((e)=>e.eventoClienteId==deB.eventoClienteId).estadoSincronizacion,EstadoSincronizacion.sincronizado);
  });

  test('respuesta perdida reintenta sin duplicar ni cambiar captura',() async {
    await sesiones.iniciar('A','clave');
    final cola=ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones),ahora:()=>ahora);
    final e=await cola.registrarLocal(j,asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:a);
    perder=true; await cola.sincronizar(); await cola.sincronizar();
    expect(serverEventos.length,1); expect(enviados[0],enviados[1]);
    expect((await almacen.listar()).single.capturadoEn,e.capturadoEn);
    expect((await almacen.listar()).single.estadoSincronizacion,EstadoSincronizacion.sincronizado);
  });

  test('401 de una petición antigua no cierra la cuenta nueva',() async {
    await sesiones.iniciar('A','clave');
    final cola=ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones),ahora:()=>ahora);
    await cola.registrarLocal(j,asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:a);
    espera=Completer<void>(); estado=401;
    final envio=cola.sincronizar();
    while(enviados.isEmpty) { await Future<void>.delayed(const Duration(milliseconds:10)); }
    await sesiones.cerrar(); await sesiones.iniciar('B','clave');
    espera!.complete(); await envio;
    expect(sesiones.actual!.usuarioId,b);
    expect(headers.single,'Bearer token-$a');
  });

  test('migración SQLite v1 conserva filas y no permite atribución retroactiva',() async {
    final ruta='${temporal.path}/legacy.db';
    final anterior=await databaseFactoryFfi.openDatabase(ruta,options:OpenDatabaseOptions(version:1,onCreate:(db,_) async {
      await db.execute('CREATE TABLE evento_ejecucion_local(evento_cliente_id TEXT PRIMARY KEY,jornada_id TEXT,asignacion_jornada_id TEXT,tipo_evento TEXT,capturado_en TEXT,estado_sincronizacion TEXT)');
      await db.insert('evento_ejecucion_local',{'evento_cliente_id':a,'jornada_id':j,'asignacion_jornada_id':asig,
        'tipo_evento':'INICIO','capturado_en':ahora.toIso8601String(),'estado_sincronizacion':'PENDIENTE'});
    }));
    final antes=await anterior.query('evento_ejecucion_local'); await anterior.close();
    final nuevo=await AlmacenEventosSqlite.abrir(factory:databaseFactoryFfi,ruta:ruta);
    try {
      expect((await nuevo.listar()).single.usuarioIdCaptura,isNull);
      final db=await databaseFactoryFfi.openDatabase(ruta);
      final despues=await db.query('evento_ejecucion_local');
      expect({...despues.single}..remove('usuario_id_captura'),antes.single);
      await expectLater(db.update('evento_ejecucion_local',{'usuario_id_captura':b}),throwsA(isA<DatabaseException>()));
      await sesiones.iniciar('A','clave');
      await ColaEjecucion(nuevo,ClienteEjecucionAutenticada(sesiones)).sincronizar();
      expect(enviados,isEmpty);
    } finally { await nuevo.cerrar(); }
  });
}
