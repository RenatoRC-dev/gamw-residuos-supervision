import 'dart:async';
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:gamw_mobile/modules/ejecucion/gps/infraestructura/captura_gps_android.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/persistencia/almacen_eventos_sqlite.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized(); sqfliteFfiInit();
  test('permiso denegado no inicia servicio ni produce coordenadas',() async {
    final temporal=await Directory.systemTemp.createTemp('gamw-gps-denegado-');
    final almacen=await AlmacenEventosSqlite.abrir(factory:databaseFactoryFfi,ruta:'${temporal.path}/gps.db');
    final llamadas=<String>[];
    const canal=MethodChannel('gamw/gps');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(canal,(call) async {
      llamadas.add(call.method); return false;
    });
    try {
      expect(await CapturaGpsAndroid().iniciar(almacen,const ContextoCapturaGps('J','AS','A')),'Permiso denegado');
      expect(llamadas,['permisos']);
      expect(await almacen.listarPosiciones('A'),isEmpty);
      expect(CapturaGpsAndroid.intervaloSegundos,30);
    } finally {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(canal,null);
      await almacen.cerrar();
    }
  });
  group('H-02/H-04: combinaciones de permisos',() {
    test('FINE concedida y notificaciones denegadas: inicia, con advertencia',() {
      final d=evaluarPermisosGps({'fina':true,'aproximada':true,'notificaciones':false});
      expect(d.capturar,isTrue); expect(d.motivo,isNull); expect(d.avisoNotificaciones,notificacionesDenegadas);
    });
    test('FINE y notificaciones concedidas: inicia sin advertencia',() {
      final d=evaluarPermisosGps({'fina':true,'aproximada':true,'notificaciones':true});
      expect(d.capturar,isTrue); expect(d.avisoNotificaciones,isNull);
    });
    test('solo aproximada: no inicia y lo explica (no "captura activa")',() {
      for(final n in [true,false]) {
        final d=evaluarPermisosGps({'fina':false,'aproximada':true,'notificaciones':n});
        expect(d.capturar,isFalse); expect(d.motivo,soloUbicacionAproximada);
      }
    });
    test('sin ubicación o respuesta desconocida: Permiso denegado',() {
      for(final r in <Object?>[{'fina':false,'aproximada':false,'notificaciones':true},false,null,'x',{}]) {
        final d=evaluarPermisosGps(r);
        expect(d.capturar,isFalse); expect(d.motivo,'Permiso denegado');
      }
    });
  });
  for(final caso in [
    (respuesta:<String,bool>{'fina':true,'aproximada':true,'notificaciones':false},esperado:'Esperando ubicación · $notificacionesDenegadas',llamadas:['permisos','iniciar']),
    (respuesta:<String,bool>{'fina':false,'aproximada':true,'notificaciones':true},esperado:soloUbicacionAproximada,llamadas:['permisos']),
  ]) {
    test('canal nativo ${caso.respuesta}: ${caso.llamadas.join(">")}',() async {
      final temporal=await Directory.systemTemp.createTemp('gamw-gps-combinacion-');
      final almacen=await AlmacenEventosSqlite.abrir(factory:databaseFactoryFfi,ruta:'${temporal.path}/gps.db');
      final llamadas=<String>[];
      const canal=MethodChannel('gamw/gps');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(canal,(call) async {
        llamadas.add(call.method);
        return call.method=='permisos'?caso.respuesta:'Esperando ubicación';
      });
      try {
        expect(await CapturaGpsAndroid().iniciar(almacen,const ContextoCapturaGps('J','AS','A')),caso.esperado);
        expect(llamadas,caso.llamadas);
        expect(await almacen.listarPosiciones('A'),isEmpty,reason:'Ninguna combinación de permisos fabrica posiciones.');
      } finally {
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(canal,null);
        await almacen.cerrar();
      }
    });
  }
  test('un permiso tardío no inicia captura con una cuenta ya cerrada',() async {
    final temporal=await Directory.systemTemp.createTemp('gamw-gps-permisos-');
    final almacen=await AlmacenEventosSqlite.abrir(factory:databaseFactoryFfi,ruta:'${temporal.path}/gps.db');
    final permiso=Completer<bool>(); final llamadas=<String>[];
    var autenticado=true;
    const canal=MethodChannel('gamw/gps');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(canal,(call) async {
      llamadas.add(call.method); return permiso.future;
    });
    try {
      final inicio=CapturaGpsAndroid().iniciar(almacen,const ContextoCapturaGps('J','AS','A'),sigueAutenticado:()=>autenticado);
      await Future<void>.delayed(Duration.zero);
      autenticado=false; permiso.complete(true);
      expect(await inicio,'GPS detenido: sesión cambiada');
      expect(llamadas,['permisos']); // Solo canal de permisos; no proveedor ni lecturas GPS simuladas.
    } finally {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(canal,null);
      await almacen.cerrar();
    }
  });
}
