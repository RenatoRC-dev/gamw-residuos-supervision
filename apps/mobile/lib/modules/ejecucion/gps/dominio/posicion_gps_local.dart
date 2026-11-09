import '../../jornadas/dominio/evento_ejecucion_local.dart';

class PosicionGpsLocal {
  const PosicionGpsLocal({required this.capturaId, required this.jornadaId,
    required this.asignacionJornadaId, required this.usuarioIdCaptura,
    required this.capturadoEn, required this.latitud, required this.longitud,
    required this.precisionMetros, this.estado = EstadoSincronizacion.pendiente, this.error});
  final String capturaId, jornadaId, asignacionJornadaId, usuarioIdCaptura;
  final DateTime capturadoEn;
  final double latitud, longitud, precisionMetros;
  final EstadoSincronizacion estado;
  final String? error;
  Map<String,Object> get cuerpo => {'capturaId':capturaId,
    'capturadoEn':capturadoEn.toUtc().toIso8601String(), 'latitud':latitud,
    'longitud':longitud,'precisionMetros':precisionMetros};
}

abstract interface class AlmacenPosicionesGps {
  Future<List<PosicionGpsLocal>> listarPosiciones(String usuario);
  Future<void> registrarPosicion(PosicionGpsLocal posicion);
  Future<void> estadoPosicion(String captura,EstadoSincronizacion estado,String? error);
  Future<bool> hayGpsNoAceptado(String jornada);
}
