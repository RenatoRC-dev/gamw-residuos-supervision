import 'package:flutter/material.dart';
import '../../../../shared/api/cliente_backend.dart';
import '../../../../shared/auth/sesion.dart';
import '../../jornadas/aplicacion/cola_ejecucion.dart' show generarUuidEvento;

/// CU-14 directo en línea. No altera SQLite ni la cola de INICIO/FIN/GPS.
class PanelIncidencias extends StatefulWidget {
  const PanelIncidencias({super.key, required this.sesiones, required this.usuarioId,
    required this.jornadaId, required this.asignacionId});
  final GestorSesion sesiones;
  final String usuarioId, jornadaId, asignacionId;
  @override
  State<PanelIncidencias> createState() => _PanelIncidenciasState();
}

class _PanelIncidenciasState extends State<PanelIncidencias> {
  final _descripcion = TextEditingController(), _evidencia = TextEditingController(), _lugar = TextEditingController();
  final _fecha = TextEditingController(text: DateTime.now().toUtc().toIso8601String());
  Map<String, Object?>? _pendiente;
  List<Map<String, dynamic>> _historia = [];
  bool _ocupado = false;
  String _aviso = '';
  bool get _cuentaPropia => widget.sesiones.actual?.usuarioId == widget.usuarioId;

  Future<Object?> _solicitar(String metodo, String ruta, {Object? cuerpo}) async {
    final sesion = await widget.sesiones.vigente();
    if (sesion.usuarioId != widget.usuarioId) throw const ErrorBackend(403);
    try {
      return await widget.sesiones.api.solicitar(metodo, ruta, cuerpo: cuerpo, token: sesion.token);
    } on ErrorBackend catch (e) {
      if (e.estado == 401) await widget.sesiones.invalidar(sesion.token);
      rethrow;
    }
  }

  Future<void> _cargar() async {
    if (_ocupado) return;
    setState(() { _ocupado = true; _aviso = ''; });
    try {
      final datos = await _solicitar('GET', 'jornadas/${widget.jornadaId}/incidencias-propias?asignacionId=${Uri.encodeQueryComponent(widget.asignacionId)}') as List<dynamic>;
      if (mounted && _cuentaPropia) setState(() => _historia = datos.cast<Map<String, dynamic>>());
    } on Object {
      if (mounted && _cuentaPropia) setState(() => _aviso = 'No se pudo consultar. Recupere sesión y conexión; no se elimina evidencia operativa.');
    } finally { if (mounted) setState(() => _ocupado = false); }
  }

  Future<void> _registrar() async {
    if (_ocupado) return;
    if (_pendiente == null) {
      final texto = _fecha.text.trim();
      final fecha = DateTime.tryParse(texto);
      final zonaExplicita = RegExp(r'(Z|[+-]\d{2}:\d{2})$').hasMatch(texto);
      if (fecha == null || !zonaExplicita || _descripcion.text.trim().isEmpty ||
          _descripcion.text.length > 20000 || _evidencia.text.length > 10000 || _lugar.text.length > 2000) {
        setState(() => _aviso = 'Complete descripción y fecha ISO 8601 con Z o desplazamiento explícito; respete los límites de texto.');
        return;
      }
      _pendiente = {'solicitudId': generarUuidEvento(), 'asignacionJornadaId': widget.asignacionId,
        'ocurridaEn': fecha.toUtc().toIso8601String(), 'descripcion': _descripcion.text,
        'evidenciaDeclarada': _evidencia.text, 'lugarDescripcion': _lugar.text};
    }
    setState(() { _ocupado = true; _aviso = ''; });
    try {
      final registrado = await _solicitar('POST', 'jornadas/${widget.jornadaId}/incidencias', cuerpo: _pendiente) as Map<String, dynamic>;
      if (!mounted || !_cuentaPropia) return;
      setState(() {
        _historia = [..._historia.where((e) => e['id'] != registrado['id']), registrado];
        _pendiente = null; _descripcion.clear(); _evidencia.clear(); _lugar.clear();
        _fecha.text = DateTime.now().toUtc().toIso8601String();
        _aviso = 'Incidencia confirmada por el servidor. No determina incumplimiento ni genera una correctiva automáticamente.';
      });
    } on ErrorBackend catch (e) {
      if (!mounted || !_cuentaPropia) return;
      setState(() {
        if (e.estado == 400) _pendiente = null; // Rechazo de validación: permite corregir sin sustituir evidencia persistida.
        _aviso = e.estado == 400 ? 'Datos rechazados; revise fecha, descripción y asignación.'
          : e.estado == 403 ? 'No tiene autorización para esta asignación.'
          : e.estado == 409 ? 'Se requiere INICIO sincronizado y jornada sin FIN, o existe un conflicto. Consulte las incidencias antes de reintentar.'
          : 'Registro no confirmado. Recupere sesión/conexión y reintente la misma solicitud.';
      });
    } on Object {
      if (mounted && _cuentaPropia) setState(() => _aviso = 'Sin confirmación. Este formulario conserva la misma solicitud mientras permanezca abierto; no es una cola offline.');
    } finally { if (mounted) setState(() => _ocupado = false); }
  }

  @override
  void dispose() { _descripcion.dispose(); _evidencia.dispose(); _lugar.dispose(); _fecha.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) => ExpansionTile(title: const Text('Incidencias de mi asignación'), children: [
    const Text('Registro directo con conexión, INICIO sincronizado y sin FIN. Captura offline de incidencias y acceso de supervisor aún no habilitados.'),
    const Text('Describa hechos, no conclusiones de fiscalización. No se exigen fotografías ni coordenadas.'),
    if (_aviso.isNotEmpty) Text(_aviso, semanticsLabel: _aviso),
    TextField(controller: _fecha, enabled: !_ocupado && _pendiente == null, decoration: const InputDecoration(labelText: 'Fecha del hecho ISO 8601 (Z = UTC; Bolivia −04:00)')),
    TextField(controller: _descripcion, enabled: !_ocupado && _pendiente == null, maxLength: 20000, maxLines: 3, decoration: const InputDecoration(labelText: 'Descripción factual obligatoria')),
    TextField(controller: _lugar, enabled: !_ocupado && _pendiente == null, maxLength: 2000, decoration: const InputDecoration(labelText: 'Lugar descrito (opcional)')),
    TextField(controller: _evidencia, enabled: !_ocupado && _pendiente == null, maxLength: 10000, maxLines: 2,
      decoration: const InputDecoration(labelText: 'Soporte disponible y procedencia declarados (sin adjuntar archivos)')),
    FilledButton(onPressed: _ocupado || !_cuentaPropia ? null : _registrar,
      child: Text(_pendiente == null ? 'Registrar incidencia' : 'Reintentar misma solicitud')),
    TextButton(onPressed: _ocupado || !_cuentaPropia ? null : _cargar, child: const Text('Consultar incidencias registradas')),
    for (final i in _historia) ListTile(title: Text(i['descripcion'] as String),
      subtitle: Text('Hecho: ${i['ocurridaEn']} · Recepción: ${i['registradaEn']}\n${i['lugarDescripcion']}\n${i['evidenciaDeclarada']}')),
  ]);
}
