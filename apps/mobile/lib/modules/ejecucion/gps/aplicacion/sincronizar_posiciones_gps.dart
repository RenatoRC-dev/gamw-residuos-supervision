import '../../../../shared/auth/sesion.dart';
import '../../../../shared/api/cliente_backend.dart';
import '../../jornadas/dominio/evento_ejecucion_local.dart';
import '../dominio/posicion_gps_local.dart';

class SincronizarPosicionesGps {
  SincronizarPosicionesGps(this.almacen,this.sesiones);
  final AlmacenPosicionesGps almacen;
  final GestorSesion sesiones;

  // Invocada por la misma cola manual/automática después de INICIO y antes de FIN.
  Future<bool> sincronizar(String jornada) async {
    final sesion=await sesiones.vigente();
    final filas=(await almacen.listarPosiciones(sesion.usuarioId)).where((p)=>p.jornadaId==jornada).toList();
    if(filas.any((p)=>p.estado==EstadoSincronizacion.conflicto)) return false;
    final pendientes=filas.where((p)=>p.estado==EstadoSincronizacion.pendiente).toList();
    for(var i=0;i<pendientes.length;) {
      if(sesiones.actual?.token!=sesion.token) return false;
      final asignacion=pendientes[i].asignacionJornadaId;
      final lote=<PosicionGpsLocal>[];
      while(i<pendientes.length && lote.length<200 && pendientes[i].asignacionJornadaId==asignacion) {
        lote.add(pendientes[i++]);
      }
      try {
        final respuesta=await sesiones.api.solicitar('POST','jornadas/$jornada/ejecucion/posiciones-gps',
          token:sesion.token,cuerpo:{'asignacionJornadaId':asignacion,'posiciones':lote.map((p)=>p.cuerpo).toList()}) as List<dynamic>;
        // Validar el lote entero antes de marcar una sola fila como aceptada.
        if(respuesta.length!=lote.length) throw const FormatException('Lote GPS incompleto.');
        for(var n=0;n<lote.length;n++) {
          final r=respuesta[n] as Map<String,dynamic>, p=lote[n];
          if(r['capturaId']!=p.capturaId || r['usuarioId']!=sesion.usuarioId || r['jornadaId']!=jornada ||
            r['asignacionJornadaId']!=asignacion || DateTime.parse(r['capturadoEn'] as String)!=p.capturadoEn ||
            r['latitud']!=p.latitud || r['longitud']!=p.longitud || r['precisionMetros']!=p.precisionMetros) {
            throw const FormatException('La respuesta no confirma la evidencia GPS original.');
          }
        }
        for(final p in lote) { await almacen.estadoPosicion(p.capturaId,EstadoSincronizacion.sincronizado,null); }
      } on ErrorBackend catch(e) {
        if(e.estado==401) await sesiones.invalidar(sesion.token);
        final conflicto=[400,403,404,409].contains(e.estado);
        for(final p in lote) { await almacen.estadoPosicion(p.capturaId,
          conflicto?EstadoSincronizacion.conflicto:EstadoSincronizacion.pendiente,'HTTP ${e.estado}'); }
        return false;
      } on Exception { return false; }
    }
    // FIN tampoco adelanta posiciones retenidas de otra cuenta; no las transmite ni revela.
    return !await almacen.hayGpsNoAceptado(jornada);
  }
}
