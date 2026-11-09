import '../../../../shared/auth/sesion.dart';
import '../../../../shared/api/cliente_backend.dart';
import '../aplicacion/cola_ejecucion.dart';
import '../dominio/evento_ejecucion_local.dart';

class ClienteEjecucionAutenticada implements ClienteEventosEjecucion {
  ClienteEjecucionAutenticada(this.sesiones);
  final GestorSesion sesiones;
  Future<bool> confirmarInicio(EventoEjecucionLocal fin) async {
    String? tokenPeticion;
    try {
      final sesion = await sesiones.vigente();
      tokenPeticion = sesion.token;
      if (sesion.usuarioId != fin.usuarioIdCaptura) return false;
      final datos = await sesiones.api.solicitar('GET',
        'mis-jornadas/${fin.jornadaId}/ejecucion?asignacionId=${Uri.encodeQueryComponent(fin.asignacionJornadaId)}',
        token: sesion.token) as Map<String, dynamic>;
      if (sesiones.actual?.token != sesion.token || datos['jornadaId'] != fin.jornadaId) return false;
      final inicio = datos['inicio'];
      return inicio is Map<String, dynamic> &&
        !fin.capturadoEn.isBefore(DateTime.parse(inicio['capturadoEn'] as String).toUtc());
    } on ErrorBackend catch (e) {
      if (e.estado == 401 && tokenPeticion != null) await sesiones.invalidar(tokenPeticion);
      return false;
    } on Exception { return false; }
  }
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
