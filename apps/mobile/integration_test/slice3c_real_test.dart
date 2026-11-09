import 'dart:convert';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:sqflite/sqflite.dart';
import 'package:gamw_mobile/shared/api/cliente_backend.dart';
import 'package:gamw_mobile/shared/auth/almacen_sesion_seguro.dart';
import 'package:gamw_mobile/shared/auth/sesion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/aplicacion/cola_ejecucion.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/dominio/evento_ejecucion_local.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/infraestructura/cliente_ejecucion_autenticada.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/infraestructura/cache_jornadas.dart';
import 'package:gamw_mobile/modules/ejecucion/jornadas/persistencia/almacen_eventos_sqlite.dart';

class _RedControlada extends ClienteBackend {
  _RedControlada(super.base);
  bool desconectada=false, perderRespuesta=false;
  @override Future<Object?> solicitar(String metodo,String ruta,{Object? cuerpo,String? token}) async {
    if(desconectada) throw const SocketException('Corte de conectividad simulado en transporte');
    final respuesta=await super.solicitar(metodo,ruta,cuerpo:cuerpo,token:token);
    if(perderRespuesta && ruta.contains('/ejecucion/')) {
      perderRespuesta=false; throw const SocketException('Respuesta perdida después de persistencia real');
    }
    return respuesta;
  }
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  const fase=String.fromEnvironment('GAMW_IT_FASE');
  const url=String.fromEnvironment('GAMW_IT_API_URL');
  const a=String.fromEnvironment('GAMW_IT_USUARIO_A'), b=String.fromEnvironment('GAMW_IT_USUARIO_B');
  const clave=String.fromEnvironment('GAMW_IT_CLAVE');
  const jornada=String.fromEnvironment('GAMW_IT_JORNADA_A'), ajena=String.fromEnvironment('GAMW_IT_JORNADA_B');
  testWidgets('Slice 3C nativo fase $fase: backend y PostgreSQL aislados', (tester) async {
    if(url.isEmpty || fase.isEmpty) throw StateError('Ejecutar mediante FlujoMovilIT: requiere backend y fixtures aislados.');
    await tester.runAsync(() async {
      final api=_RedControlada(Uri.parse(url));
      final seguro=AlmacenSesionSeguro(clave:'gamw.it.$jornada');
      var sesiones=GestorSesion(api,seguro);
      final archivo='${await getDatabasesPath()}/slice3c_$jornada.db';
      final almacen=await AlmacenEventosSqlite.abrir(ruta:archivo);
      try {
        var cola=ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones));
        if(fase=='1') {
          expect(await almacen.listar(),isEmpty);
          await sesiones.iniciar(a,clave); await sesiones.identidadActual();
          final usuario=sesiones.actual!.usuarioId;
          final propias=await sesiones.solicitar('GET','mis-jornadas') as List<dynamic>;
          expect(propias.length,2); expect(propias.every((j)=>j['jornadaId']!=ajena),isTrue);
          await CacheJornadasSeguro().guardar(usuario,propias.cast<Map<String,dynamic>>());
          final asignacion=propias.firstWhere((j)=>j['jornadaId']==jornada)['asignacionJornadaId'] as String;
          api.desconectada=true;
          await cola.registrarLocal(jornada,asignacion,TipoEventoEjecucion.inicio,usuarioIdCaptura:usuario);
          await cola.registrarLocal(jornada,asignacion,TipoEventoEjecucion.fin,usuarioIdCaptura:usuario);
          await cola.sincronizar();
          expect((await almacen.listar()).every((e)=>e.estadoSincronizacion==EstadoSincronizacion.pendiente),isTrue);
          final persistida=await seguro.leer();
          expect(persistida,isNotNull);
          expect(persistida,isNot(contains(clave)));
          // No cerrar sesión: la siguiente ejecución instala/reinicia la app y restaura KeyStore/SQLite.
        } else {
          await sesiones.restaurar();
          expect(sesiones.actual!.nombreUsuario,a);
          final usuario=sesiones.actual!.usuarioId;
          expect((await CacheJornadasSeguro().leer(usuario)).length,2);
          final originales=await almacen.listar();
          expect(originales.length,2);
          expect(originales.every((e)=>e.usuarioIdCaptura==usuario),isTrue);
          await sesiones.cerrar(); await sesiones.iniciar(b,clave);
          final propiasB=await sesiones.solicitar('GET','mis-jornadas') as List<dynamic>;
          expect(propiasB.length,1); expect(propiasB.single['jornadaId'],ajena);
          expect(await CacheJornadasSeguro().leer(sesiones.actual!.usuarioId),isEmpty);
          await cola.sincronizar();
          expect((await almacen.listar()).every((e)=>e.estadoSincronizacion==EstadoSincronizacion.pendiente),isTrue);
          await expectLater(sesiones.solicitar('POST','jornadas/$jornada/ejecucion/inicio',cuerpo:originales.first.cuerpo),
            throwsA(isA<ErrorBackend>().having((e)=>e.estado,'estado',403)));
          await sesiones.cerrar(); await sesiones.iniciar(a,clave);
          api.perderRespuesta=true; await cola.sincronizar();
          expect((await almacen.listar()).first.estadoSincronizacion,EstadoSincronizacion.pendiente);
          await cola.sincronizar();
          final sincronizados=await almacen.listar();
          expect(sincronizados.every((e)=>e.estadoSincronizacion==EstadoSincronizacion.sincronizado),isTrue);
          expect(sincronizados.map((e)=>e.eventoClienteId),originales.map((e)=>e.eventoClienteId));
          expect(sincronizados.map((e)=>e.capturadoEn),originales.map((e)=>e.capturadoEn));
          final reintento=await sesiones.solicitar('POST','jornadas/$jornada/ejecucion/inicio',cuerpo:originales.first.cuerpo) as Map<String,dynamic>;
          expect(reintento['usuarioId'],usuario);
          await expectLater(sesiones.solicitar('POST','jornadas/$jornada/ejecucion/fin',cuerpo:{...originales.last.cuerpo,'usuarioId':'no-confiable'}),
            throwsA(isA<ErrorBackend>().having((e)=>e.estado,'estado',400)));
          await sesiones.cerrar(); await sesiones.iniciar(b,clave);
          await expectLater(sesiones.solicitar('POST','jornadas/$jornada/ejecucion/inicio',cuerpo:originales.first.cuerpo),
            throwsA(isA<ErrorBackend>().having((e)=>e.estado,'estado',409)));
          await expectLater(sesiones.solicitar('GET','jornadas'),throwsA(isA<ErrorBackend>().having((e)=>e.estado,'estado',403)));
          await sesiones.cerrar(); await sesiones.iniciar(a,clave);
          final propias=await sesiones.solicitar('GET','mis-jornadas') as List<dynamic>;
          final historica=propias.firstWhere((j)=>j['jornadaId']!=jornada) as Map<String,dynamic>;
          await cola.registrarLocal(historica['jornadaId'] as String,historica['asignacionJornadaId'] as String,
            TipoEventoEjecucion.inicio,usuarioIdCaptura:usuario);
          final invalida={...sesiones.actual!.datos,'tokenAcceso':'invalido-para-probar-401'};
          await seguro.guardar(jsonEncode(invalida));
          sesiones.dispose(); sesiones=GestorSesion(api,seguro);
          await expectLater(sesiones.restaurar(),throwsA(isA<ErrorBackend>()));
          expect(sesiones.actual,isNull);
          expect((await almacen.listar()).last.estadoSincronizacion,EstadoSincronizacion.pendiente);
          await sesiones.iniciar(a,clave);
          cola=ColaEjecucion(almacen,ClienteEjecucionAutenticada(sesiones));
          await cola.sincronizar(); // Ya existe INICIO histórico: 409, sin retroatribuir.
          expect((await almacen.listar()).last.estadoSincronizacion,EstadoSincronizacion.conflicto);
          await cola.registrarLocal(ajena,propiasB.single['asignacionJornadaId'] as String,
            TipoEventoEjecucion.inicio,usuarioIdCaptura:usuario);
          await cola.sincronizar(); // 403: evidencia retenida.
          expect((await almacen.listar()).last.estadoSincronizacion,EstadoSincronizacion.conflicto);
          await sesiones.cerrar(); expect((await almacen.listar()).length,4);
        }
      } finally { await almacen.cerrar(); sesiones.dispose(); api.cerrar(); }
    });
  },timeout:const Timeout(Duration(minutes:3)));
}
