enum TipoEventoEjecucion {
  inicio('INICIO'),
  fin('FIN');

  const TipoEventoEjecucion(this.codigo);
  final String codigo;
}

enum EstadoSincronizacion {
  pendiente('PENDIENTE'),
  sincronizado('SINCRONIZADO'),
  conflicto('CONFLICTO');

  const EstadoSincronizacion(this.codigo);
  final String codigo;
}

class EventoEjecucionLocal {
  EventoEjecucionLocal({
    required this.eventoClienteId,
    required this.jornadaId,
    required this.asignacionJornadaId,
    required this.tipoEvento,
    required DateTime capturadoEn,
    this.estadoSincronizacion = EstadoSincronizacion.pendiente,
    this.usuarioIdCaptura,
  }) : capturadoEn = capturadoEn.toUtc() {
    final uuid = RegExp(
      r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
    );
    if (![
      eventoClienteId,
      jornadaId,
      asignacionJornadaId,
    ].every(uuid.hasMatch) || (usuarioIdCaptura != null && !uuid.hasMatch(usuarioIdCaptura!))) {
      throw ArgumentError('Los identificadores deben ser UUID válidos.');
    }
  }

  final String eventoClienteId;
  final String jornadaId;
  final String asignacionJornadaId;
  final TipoEventoEjecucion tipoEvento;
  final DateTime capturadoEn;
  final EstadoSincronizacion estadoSincronizacion;
  final String? usuarioIdCaptura;

  Map<String, Object?> get cuerpo => {
    'eventoClienteId': eventoClienteId,
    'asignacionJornadaId': asignacionJornadaId,
    'capturadoEn': capturadoEn.toIso8601String(),
  };
}

abstract interface class AlmacenEventosEjecucion {
  Future<void> registrar(EventoEjecucionLocal evento);
  Future<List<EventoEjecucionLocal>> listar();
  Future<void> cambiarEstado(
    String eventoClienteId,
    EstadoSincronizacion estado,
  );
}

/// FIN puede apoyarse en un INICIO original consultado al servidor, sin copiarlo
/// como evidencia local ni cambiar su autor (CU-13 FA-04).
abstract interface class AlmacenFinConfirmado {
  Future<void> registrarFinConfirmado(EventoEjecucionLocal evento, DateTime inicioServidor);
}
