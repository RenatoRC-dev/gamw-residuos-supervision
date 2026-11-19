import 'package:flutter/services.dart';
import '../../jornadas/persistencia/almacen_eventos_sqlite.dart';

class ContextoCapturaGps {
  const ContextoCapturaGps(this.jornadaId,this.asignacionJornadaId,this.usuarioId);
  final String jornadaId,asignacionJornadaId,usuarioId;
}

/// Decisión de permisos para la captura (H-02/H-04). Separa la ubicación de las notificaciones:
/// - GPS_PROVIDER exige ubicación PRECISA; con solo ubicación aproximada no se inicia ni se declara captura.
/// - Rechazar notificaciones NO impide iniciar el servicio en primer plano (su notificación obligatoria se crea igual);
///   solo se advierte que el aviso puede no mostrarse.
class DecisionPermisosGps {
  const DecisionPermisosGps(this.capturar,this.motivo,this.avisoNotificaciones);
  final bool capturar;
  final String? motivo,avisoNotificaciones;
}
const soloUbicacionAproximada='Ubicación precisa requerida: solo se concedió ubicación aproximada; no se captura GPS.';
const notificacionesDenegadas='Notificaciones denegadas: la captura continúa, pero su aviso puede no mostrarse.';
DecisionPermisosGps evaluarPermisosGps(Object? respuesta) {
  // Compatibilidad: plataformas/pruebas que responden con un booleano.
  if(respuesta==true) return const DecisionPermisosGps(true,null,null);
  if(respuesta is Map) {
    final aviso=respuesta['notificaciones']==false?notificacionesDenegadas:null;
    if(respuesta['fina']==true) return DecisionPermisosGps(true,null,aviso);
    if(respuesta['aproximada']==true) return DecisionPermisosGps(false,soloUbicacionAproximada,aviso);
  }
  return const DecisionPermisosGps(false,'Permiso denegado',null);
}

class CapturaGpsAndroid {
  static const _canal=MethodChannel('gamw/gps');
  static const intervaloSegundos=int.fromEnvironment('GAMW_GPS_INTERVALO_SEGUNDOS',defaultValue:30);
  Future<String> iniciar(AlmacenEventosSqlite almacen,ContextoCapturaGps contexto,{bool pedirPermisos=true,bool Function()? sigueAutenticado}) async {
    String? aviso;
    if(pedirPermisos) {
      final decision=evaluarPermisosGps(await _canal.invokeMethod<Object?>('permisos'));
      if(!decision.capturar) return decision.motivo!;
      aviso=decision.avisoNotificaciones;
    }
    if(sigueAutenticado!=null && !sigueAutenticado()) return 'GPS detenido: sesión cambiada';
    final estado=await _canal.invokeMethod<String>('iniciar',{'ruta':almacen.ruta,'jornadaId':contexto.jornadaId,
      'asignacionJornadaId':contexto.asignacionJornadaId,'usuarioId':contexto.usuarioId,
      'intervalo':(intervaloSegundos>0?intervaloSegundos:30)*1000}) ?? 'Esperando ubicación';
    return aviso==null || estado!='Esperando ubicación' ? estado : '$estado · $aviso';
  }
  Future<void> detener({String? jornada}) => _canal.invokeMethod<void>('detener',{'jornadaId':jornada});
  Future<String> estado() async => await _canal.invokeMethod<String>('estado') ?? 'Esperando ubicación';
}
