import 'dart:async';
import '../../../../shared/auth/sesion.dart';
import '../../jornadas/aplicacion/cola_ejecucion.dart';
import '../../jornadas/dominio/evento_ejecucion_local.dart';
import '../dominio/posicion_gps_local.dart';

/// Reutiliza INICIO -> GPS -> FIN; no captura posiciones ni elimina evidencia.
/// Timer de un solo turno: no acumula envíos si la red tarda más que el intervalo.
class TransmitirEvidenciaAutomatica {
  TransmitirEvidenciaAutomatica(this.cola, this.sesiones, this.gps,
      {required this.intervalo, required this.aviso}) {
    if (intervalo <= Duration.zero) throw ArgumentError('Intervalo de envío positivo requerido');
  }
  final ColaEjecucion cola;
  final GestorSesion sesiones;
  final AlmacenPosicionesGps gps;
  final Duration intervalo;
  final void Function(String) aviso;
  Timer? _timer;
  Future<void>? _envio;
  bool _cerrado = false;
  int _fallos = 0;

  void iniciar() {
    sesiones.addListener(_cambioSesion);
    _cambioSesion();
  }
  void _cambioSesion() {
    _fallos = 0;
    _timer?.cancel();
    if (!_cerrado && sesiones.actual != null && _envio == null) _programar(intervalo);
  }
  void solicitar() {
    if (!_cerrado && sesiones.actual != null && _envio == null) _programar(Duration.zero);
  }
  void _programar(Duration espera) {
    _timer?.cancel();
    _timer = Timer(espera, () {
      if (_cerrado || _envio != null) return;
      final turno = _transmitir();
      _envio = turno;
      unawaited(turno.whenComplete(() {
        _envio = null;
        if (!_cerrado && sesiones.actual != null) {
          _programar(intervalo * (1 << _fallos.clamp(0, 3)));
        }
      }));
    });
  }
  Future<void> _transmitir() async {
    final actual = sesiones.actual;
    if (actual == null || _cerrado) return;
    try {
      await sesiones.vigente();
      final usuario = actual.usuarioId;
      final eventos = (await cola.listar()).where((e) => e.usuarioIdCaptura == usuario).toList();
      final pendiente = eventos.any((e) => e.estadoSincronizacion == EstadoSincronizacion.pendiente);
      final abierta = eventos.any((e) => e.tipoEvento == TipoEventoEjecucion.inicio &&
          !eventos.any((f) => f.jornadaId == e.jornadaId && f.tipoEvento == TipoEventoEjecucion.fin));
      final posiciones = await gps.listarPosiciones(usuario);
      final pendienteGps = posiciones.any((p) => p.estado == EstadoSincronizacion.pendiente);
      if (!pendiente && !abierta && !pendienteGps) return;
      if (_cerrado || sesiones.actual?.token != actual.token) return;
      // La misma cola serializa también los intentos manuales y verifica cada actor.
      await cola.sincronizar();
      if (_cerrado || sesiones.actual?.token != actual.token) return;
      final restantes = await gps.listarPosiciones(usuario);
      final operativos = (await cola.listar()).where((e) => e.usuarioIdCaptura == usuario);
      if (_cerrado || sesiones.actual?.token != actual.token) return;
      final sinAceptar = restantes.any((p) => p.estado == EstadoSincronizacion.pendiente) ||
          operativos.any((e) => e.estadoSincronizacion == EstadoSincronizacion.pendiente);
      _fallos = sinAceptar ? (_fallos + 1).clamp(0, 3) : 0;
      aviso(sinAceptar ? 'Envío automático pendiente; evidencia conservada. Se reintentará con sesión y conexión.'
          : 'Envío automático disponible. Las confirmaciones y conflictos se conservan localmente.');
    } on Object {
      _fallos = (_fallos + 1).clamp(0, 3);
      if (!_cerrado) aviso('Sin confirmación automática. Pendientes conservados; revise conexión o autenticación.');
    }
  }
  Future<void> detener() async {
    _cerrado = true;
    _timer?.cancel();
    sesiones.removeListener(_cambioSesion);
    await _envio;
  }
}
