import 'package:flutter/services.dart';
import '../../jornadas/persistencia/almacen_eventos_sqlite.dart';

class ContextoCapturaGps {
  const ContextoCapturaGps(this.jornadaId,this.asignacionJornadaId,this.usuarioId);
  final String jornadaId,asignacionJornadaId,usuarioId;
}

class CapturaGpsAndroid {
  static const _canal=MethodChannel('gamw/gps');
  static const intervaloSegundos=int.fromEnvironment('GAMW_GPS_INTERVALO_SEGUNDOS',defaultValue:30);
  Future<String> iniciar(AlmacenEventosSqlite almacen,ContextoCapturaGps contexto,{bool pedirPermisos=true,bool Function()? sigueAutenticado}) async {
    if(pedirPermisos && await _canal.invokeMethod<bool>('permisos')!=true) return 'Permiso denegado';
    if(sigueAutenticado!=null && !sigueAutenticado()) return 'GPS detenido: sesión cambiada';
    return await _canal.invokeMethod<String>('iniciar',{'ruta':almacen.ruta,'jornadaId':contexto.jornadaId,
      'asignacionJornadaId':contexto.asignacionJornadaId,'usuarioId':contexto.usuarioId,
      'intervalo':(intervaloSegundos>0?intervaloSegundos:30)*1000}) ?? 'Esperando ubicación';
  }
  Future<void> detener({String? jornada}) => _canal.invokeMethod<void>('detener',{'jornadaId':jornada});
  Future<String> estado() async => await _canal.invokeMethod<String>('estado') ?? 'Esperando ubicación';
}
