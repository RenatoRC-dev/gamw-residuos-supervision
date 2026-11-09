import 'dart:math';
import '../dominio/evento_ejecucion_local.dart';

enum ResultadoEnvio { sincronizado, conflicto, pendiente, bloqueado }

abstract interface class ClienteEventosEjecucion {
  Future<ResultadoEnvio> enviar(EventoEjecucionLocal evento);
}

String generarUuidEvento() {
  final random = Random.secure();
  final bytes = List<int>.generate(16, (_) => random.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
}

class ColaEjecucion {
  ColaEjecucion(
    this.almacen,
    this.cliente, {
    DateTime Function()? ahora,
    String Function()? generarId,
  }) : _ahora = ahora ?? DateTime.now,
       _generarId = generarId ?? generarUuidEvento;
  final AlmacenEventosEjecucion almacen;
  final ClienteEventosEjecucion cliente;
  final DateTime Function() _ahora;
  final String Function() _generarId;
  Future<void> _turno = Future<void>.value();
  Future<void> _turnoEnvio = Future<void>.value();

  Future<T> _serial<T>(Future<T> Function() accion) {
    final resultado = _turno.then((_) => accion());
    _turno = resultado.then<void>((_) {}, onError: (Object e, StackTrace s) {});
    return resultado;
  }

  // Esta operación termina con la escritura durable, antes de cualquier intento de envío.
  Future<EventoEjecucionLocal> registrarLocal(
    String jornadaId,
    String asignacionId,
    TipoEventoEjecucion tipo,
    {String? usuarioIdCaptura}
  ) {
    final id = _generarId();
    final captura = _ahora().toUtc();
    final evento = EventoEjecucionLocal(
      eventoClienteId: id,
      jornadaId: jornadaId,
      asignacionJornadaId: asignacionId,
      tipoEvento: tipo,
      capturadoEn: captura,
      usuarioIdCaptura: usuarioIdCaptura,
    );
    return _serial(() async {
      await almacen.registrar(evento);
      return evento;
    });
  }

  Future<List<EventoEjecucionLocal>> listar() => almacen.listar();

  Future<void> sincronizar() {
    final resultado = _turnoEnvio.then((_) => _sincronizarPendientes());
    _turnoEnvio = resultado.then<void>(
      (_) {},
      onError: (Object e, StackTrace s) {},
    );
    return resultado;
  }

  Future<void> _sincronizarPendientes() async {
    final eventos = await almacen.listar();
    final bloqueadas = eventos
        .where(
          (e) =>
              e.tipoEvento == TipoEventoEjecucion.inicio &&
              e.estadoSincronizacion == EstadoSincronizacion.conflicto,
        )
        .map((e) => e.jornadaId)
        .toSet();
    for (final evento in eventos.where(
      (e) => e.estadoSincronizacion == EstadoSincronizacion.pendiente,
    )) {
      if (bloqueadas.contains(evento.jornadaId)) continue;
      ResultadoEnvio resultado;
      try {
        resultado = await cliente.enviar(evento);
      } on Exception {
        break; // Sin red, timeout o respuesta perdida: no alterar la evidencia ni su ID.
      }
      if (resultado == ResultadoEnvio.pendiente) break;
      if (resultado == ResultadoEnvio.bloqueado) {
        if (evento.tipoEvento == TipoEventoEjecucion.inicio) bloqueadas.add(evento.jornadaId);
        continue;
      }
      await almacen.cambiarEstado(
        evento.eventoClienteId,
        resultado == ResultadoEnvio.sincronizado
            ? EstadoSincronizacion.sincronizado
            : EstadoSincronizacion.conflicto,
      );
      if (resultado == ResultadoEnvio.conflicto &&
          evento.tipoEvento == TipoEventoEjecucion.inicio) {
        bloqueadas.add(
          evento.jornadaId,
        ); // No enviar FIN de un INICIO que requiere resolución.
      }
    }
  }
}
