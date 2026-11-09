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
