import '../../../../shared/auth/sesion.dart';
import '../../../../shared/api/cliente_backend.dart';
import '../aplicacion/cola_ejecucion.dart';
import '../dominio/evento_ejecucion_local.dart';

class ClienteEjecucionAutenticada implements ClienteEventosEjecucion {
  ClienteEjecucionAutenticada(this.sesiones);
  final GestorSesion sesiones;
  @override
  Future<ResultadoEnvio> enviar(EventoEjecucionLocal evento) async {
    final sesion = await sesiones.vigente();
    if (evento.usuarioIdCaptura == null || evento.usuarioIdCaptura!.toLowerCase() != sesion.usuarioId.toLowerCase()) {
      return ResultadoEnvio.bloqueado;
    }
    try {
      // Snapshot del token: cambiar de cuenta durante la petición no cambia el actor enviado.
      final d = await sesiones.api.solicitar('POST', 'jornadas/${evento.jornadaId}/ejecucion/${evento.tipoEvento.name}',
        cuerpo: evento.cuerpo, token: sesion.token) as Map<String, dynamic>;
      bool igual(Object? valor, String esperado) => valor is String && valor.toLowerCase() == esperado.toLowerCase();
      if (!igual(d['eventoClienteId'], evento.eventoClienteId) || !igual(d['jornadaId'], evento.jornadaId) ||
          !igual(d['asignacionJornadaId'], evento.asignacionJornadaId) || !igual(d['usuarioId'], evento.usuarioIdCaptura!) ||
          d['tipoEvento'] != evento.tipoEvento.codigo || DateTime.parse(d['capturadoEn'] as String).toUtc() != evento.capturadoEn) {
        throw const FormatException('La respuesta no confirma la misma evidencia y su actor.');
      }
      return ResultadoEnvio.sincronizado;
    } on ErrorBackend catch (e) {
      if (e.estado == 401) { await sesiones.invalidar(sesion.token); return ResultadoEnvio.pendiente; }
      if ([400, 403, 404, 409].contains(e.estado)) return ResultadoEnvio.conflicto;
      return ResultadoEnvio.pendiente;
    }
  }
}
