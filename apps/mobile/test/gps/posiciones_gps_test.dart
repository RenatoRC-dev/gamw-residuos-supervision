import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:gamw_mobile/shared/api/cliente_backend.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';
import 'package:gamw_mobile/shared/auth/sesion.dart';
import 'package:gamw_mobile/modules/ejecucion/gps/dominio/posicion_gps_local.dart';
import 'package:gamw_mobile/modules/ejecucion/gps/aplicacion/sincronizar_posiciones_gps.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/aplicacion/cola_ejecucion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/infraestructura/cliente_ejecucion_autenticada.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/dominio/evento_ejecucion_local.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/persistencia/almacen_eventos_sqlite.dart';

const _a='11111111-1111-4111-8111-111111111111';
const _b='22222222-2222-4222-8222-222222222222';
const _j='33333333-3333-4333-8333-333333333333';
const _asig='44444444-4444-4444-8444-444444444444';
const _p='55555555-5555-4555-8555-555555555555';

class _SesionMemoria implements AlmacenSesion {
  @override Future<String?> leer() async => null;
  @override Future<void> guardar(String valor) async { }
  @override Future<void> borrar() async { }
}
class _Api extends ClienteBackend {
  _Api():super(Uri.parse('http://127.0.0.1:1/api'));
  int estado=200;
  final envios=<String>[];
  final capturasEnviadas=<String>[];
  @override Future<Object?> solicitar(String metodo,String ruta,{Object? cuerpo,String? token}) async {
    final d=cuerpo as Map<String,dynamic>;
    if(ruta.endsWith('login')) { return {'tokenAcceso':'token-${d['nombreUsuario']}',
      'expiraEn':DateTime.now().add(const Duration(hours:1)).toUtc().toIso8601String(),
      'usuarioId':d['nombreUsuario'],'personaNaturalId':d['nombreUsuario'],'nombreUsuario':d['nombreUsuario']}; }
    envios.add(ruta);
    if(ruta.endsWith('posiciones-gps')) {
      capturasEnviadas.addAll((d['posiciones'] as List).map((p)=>p['capturaId'] as String));
    }
    if(estado!=200) throw ErrorBackend(estado);
    expect(token,'token-$_a');
    if(ruta.endsWith('posiciones-gps')) {
      expect(d.containsKey('usuarioId'),isFalse);
      return (d['posiciones'] as List).map((p)=>{...p as Map<String,dynamic>,
        'usuarioId':_a,'jornadaId':_j,'asignacionJornadaId':d['asignacionJornadaId']}).toList();
    }
    return {...d,'usuarioId':_a,'jornadaId':_j,'tipoEvento':ruta.endsWith('inicio')?'INICIO':'FIN'};
  }
}
void main() {
  sqfliteFfiInit();
  late Directory temporal;
  late AlmacenEventosSqlite almacen;
  late _Api api;
  late GestorSesion sesiones;
  late ColaEjecucion cola;
  late SincronizarPosicionesGps gps;
  late String ruta;
  // Fixtures de contrato/persistencia. Nunca alimentan el proveedor de ubicación Android.
  PosicionGpsLocal posicion({String id=_p,String usuario=_a,int segundo=1}) => PosicionGpsLocal(
    capturaId:id,jornadaId:_j,asignacionJornadaId:_asig,usuarioIdCaptura:usuario,
    capturadoEn:DateTime.utc(2026,1,1,0,0,segundo),latitud:20.25,longitud:-10.75,precisionMetros:7.5);
  setUp(() async {
    temporal=await Directory.systemTemp.createTemp('gamw-gps-unit-'); ruta='${temporal.path}/gps.db';
    almacen=await AlmacenEventosSqlite.abrir(factory:databaseFactoryFfi,ruta:ruta);
    api=_Api(); sesiones=GestorSesion(api,_SesionMemoria()); await sesiones.iniciar(_a,'no-persistir');
    gps=SincronizarPosicionesGps(almacen,sesiones);
    var segundos=0;
    cola=ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones),sincronizarGps:gps.sincronizar,
      ahora:()=>DateTime.utc(2026,1,1,0,0,segundos++));
  });
  tearDown(() async {
    sesiones.dispose(); api.cerrar(); await almacen.cerrar();
    // Solo cerrar: los directorios temporales de tests quedan conservados.
  });
  test('SQLite conserva precisión, captura y propietario al reabrir; no envía al registrar',() async {
    await almacen.registrarPosicion(posicion());
    await almacen.cerrar(); almacen=await AlmacenEventosSqlite.abrir(factory:databaseFactoryFfi,ruta:ruta);
    final p=(await almacen.listarPosiciones(_a)).single;
    expect(p.capturadoEn,posicion().capturadoEn); expect(p.latitud,20.25); expect(p.longitud,-10.75);
    expect(p.precisionMetros,7.5); expect(p.estado,EstadoSincronizacion.pendiente); expect(api.envios,isEmpty);
    expect(await almacen.listarPosiciones(_b),isEmpty);
    final db=await databaseFactoryFfi.openDatabase(ruta);
    await expectLater(db.update('posicion_gps_local',{'usuario_id_captura':_b}),throwsA(isA<DatabaseException>()));
  });
  test('sincroniza INICIO, lotes GPS ordenados y FIN; no vuelve a enviar aceptados',() async {
    await cola.registrarLocal(_j,_asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:_a);
    await almacen.registrarPosicion(posicion(id:'P2',segundo:1));
    await almacen.registrarPosicion(posicion(id:'P1',segundo:0));
    final db=await databaseFactoryFfi.openDatabase(ruta);
    await db.insert('captura_gps_local',{'id':1,'jornada_id':_j,'asignacion_jornada_id':_asig,'usuario_id_captura':_a,'activa':1});
    await cola.registrarLocal(_j,_asig,TipoEventoEjecucion.fin,usuarioIdCaptura:_a);
    expect((await db.query('captura_gps_local')).single['activa'],0);
    await cola.sincronizar();
    expect(api.envios.map((r)=>r.split('/').last),['inicio','posiciones-gps','fin']);
    expect(api.capturasEnviadas,['P1','P2']);
    expect((await almacen.listarPosiciones(_a)).every((p)=>p.estado==EstadoSincronizacion.sincronizado),isTrue);
    await cola.sincronizar(); expect(api.envios.length,3);
  });
  test('cuenta ajena no transmite ni reasigna GPS; misma cuenta recupera cola',() async {
    await almacen.registrarPosicion(posicion()); await sesiones.iniciar(_b,'clave');
    expect(await gps.sincronizar(_j),isFalse); expect(api.envios,isEmpty);
    await sesiones.iniciar(_a,'clave'); expect(await gps.sincronizar(_j),isTrue);
    expect((await almacen.listarPosiciones(_a)).single.estado,EstadoSincronizacion.sincronizado);
  });
  test('reintento tras fallo conserva UUID, captura y precisión originales',() async {
    await almacen.registrarPosicion(posicion()); api.estado=503;
    expect(await gps.sincronizar(_j),isFalse);
    api.estado=200; expect(await gps.sincronizar(_j),isTrue);
    final p=(await almacen.listarPosiciones(_a)).single;
    expect(p.capturaId,_p); expect(p.capturadoEn,posicion().capturadoEn); expect(p.precisionMetros,7.5);
    expect(api.capturasEnviadas,[_p,_p]); // El reintento transmite el mismo ID y no fabrica otro.
    expect(api.envios,hasLength(2));
  });
  for(final estado in [401,403,409,503]) {
    test('HTTP $estado conserva evidencia GPS y bloquea FIN',() async {
      await cola.registrarLocal(_j,_asig,TipoEventoEjecucion.inicio,usuarioIdCaptura:_a);
      final inicio=(await cola.listar()).single;
      await almacen.cambiarEstado(inicio.eventoClienteId,EstadoSincronizacion.sincronizado);
      await almacen.registrarPosicion(posicion());
      await cola.registrarLocal(_j,_asig,TipoEventoEjecucion.fin,usuarioIdCaptura:_a);
      api.estado=estado; await cola.sincronizar();
      expect(api.envios.single,endsWith('posiciones-gps'));
      final p=(await almacen.listarPosiciones(_a)).single;
      expect(p.capturaId,_p); expect(p.capturadoEn,posicion().capturadoEn);
      expect(p.estado,[403,409].contains(estado)?EstadoSincronizacion.conflicto:EstadoSincronizacion.pendiente);
      expect((await cola.listar()).last.estadoSincronizacion,EstadoSincronizacion.pendiente);
      if(estado==401) expect(sesiones.actual,isNull);
    });
  }
  test('SQLite v2 a v3 preserva evidencia de ejecución existente',() async {
    await almacen.cerrar();
    final anterior=await databaseFactoryFfi.openDatabase('${temporal.path}/v2.db',options:OpenDatabaseOptions(version:2,onCreate:(db,_) async {
      await db.execute('CREATE TABLE evento_ejecucion_local(evento_cliente_id TEXT,jornada_id TEXT,asignacion_jornada_id TEXT,tipo_evento TEXT,capturado_en TEXT,usuario_id_captura TEXT,estado_sincronizacion TEXT)');
      await db.insert('evento_ejecucion_local',{'evento_cliente_id':'77777777-7777-4777-8777-777777777777','jornada_id':_j,'asignacion_jornada_id':_asig,'tipo_evento':'INICIO',
        'capturado_en':'2026-01-01T00:00:00Z','usuario_id_captura':_a,'estado_sincronizacion':'PENDIENTE'});
    }));
    await anterior.close();
    almacen=await AlmacenEventosSqlite.abrir(factory:databaseFactoryFfi,ruta:'${temporal.path}/v2.db');
    expect((await almacen.listar()).single.usuarioIdCaptura,_a);
    await almacen.registrarPosicion(posicion()); expect(await almacen.listarPosiciones(_a),hasLength(1));
  });
}
