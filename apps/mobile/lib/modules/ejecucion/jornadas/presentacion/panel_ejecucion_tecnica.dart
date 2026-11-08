import 'dart:async';
import 'package:flutter/material.dart';
import '../aplicacion/cola_ejecucion.dart';
import '../dominio/evento_ejecucion_local.dart';
import '../infraestructura/cliente_ejecucion_desarrollo.dart';
import '../persistencia/almacen_eventos_sqlite.dart';

class PanelEjecucionTecnica extends StatefulWidget {
  const PanelEjecucionTecnica({
    super.key,
    required this.jornadaId,
    required this.asignacionId,
    required this.apiUrl,
    this.cola,
  });
  final String jornadaId;
  final String asignacionId;
  final String apiUrl;
  final ColaEjecucion? cola;

  @override
  State<PanelEjecucionTecnica> createState() => _PanelEjecucionTecnicaState();
}

class _PanelEjecucionTecnicaState extends State<PanelEjecucionTecnica> {
  ColaEjecucion? _cola;
  AlmacenEventosSqlite? _almacenPropio;
  ClienteEjecucionDesarrollo? _clientePropio;
  List<EventoEjecucionLocal> _eventos = [];
  String _mensaje = '';
  bool _ocupado = true;

  @override
  void initState() {
    super.initState();
    _abrir();
  }

  Future<void> _abrir() async {
    try {
      _cola = widget.cola;
      if (_cola == null) {
        final almacen = await AlmacenEventosSqlite.abrir();
        if (!mounted) {
          await almacen.cerrar();
          return;
        }
        _almacenPropio = almacen;
        _clientePropio = ClienteEjecucionDesarrollo(Uri.parse(widget.apiUrl));
        _cola = ColaEjecucion(almacen, _clientePropio!);
      }
      final eventos = await _cola!.listar();
      if (mounted) {
        setState(() {
          _eventos = eventos;
          _ocupado = false;
        });
      }
    } on Object catch (e) {
      if (mounted) {
        setState(() {
          _mensaje = 'No se pudo abrir la evidencia local: $e';
          _ocupado = false;
        });
      }
    }
  }

  Future<void> _registrar(TipoEventoEjecucion tipo) async {
    if (_cola == null) return;
    setState(() {
      _ocupado = true;
      _mensaje = '';
    });
    try {
      await _cola!.registrarLocal(widget.jornadaId, widget.asignacionId, tipo);
      final eventos = await _cola!.listar();
      if (mounted) {
        setState(() {
          _eventos = eventos;
          _mensaje = 'Evidencia guardada localmente antes del envío.';
          _ocupado = false;
        });
      }
      unawaited(_sincronizar());
    } on Object catch (e) {
      if (mounted) {
        setState(() {
          _mensaje = 'No se pudo completar la operación: $e';
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _ocupado = false;
        });
      }
    }
  }

  Future<void> _sincronizar() async {
    if (_cola == null) return;
    try {
      await _cola!.sincronizar();
      final eventos = await _cola!.listar();
      if (mounted) {
        setState(() {
          _eventos = eventos;
          _mensaje =
              'Reintento de sincronización finalizado; revise los estados locales.';
        });
      }
    } on Object catch (e) {
      if (mounted) {
        setState(() {
          _mensaje = 'Se conserva la evidencia pendiente: $e';
        });
      }
    }
  }

  @override
  void dispose() {
    // La cola conserva la evidencia incluso si la vista desaparece.
    _clientePropio?.cerrar();
    _almacenPropio?.cerrar();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final propios = _eventos
        .where((e) => e.jornadaId == widget.jornadaId)
        .toList();
    final inicio = propios.any(
      (e) => e.tipoEvento == TipoEventoEjecucion.inicio,
    );
    final fin = propios.any((e) => e.tipoEvento == TipoEventoEjecucion.fin);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Prueba técnica local de ejecución',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
        const Text(
          'Sin identidad institucional verificada. No es Mis jornadas. INICIO y FIN no acreditan recolección ni conformidad.',
        ),
        Text('Jornada técnica: ${widget.jornadaId}'),
        if (widget.jornadaId.isEmpty || widget.asignacionId.isEmpty)
          const Text(
            'Configure la jornada y asignación de prueba al iniciar la aplicación. No seleccione una identidad personal.',
          ),
        if (_mensaje.isNotEmpty) Text(_mensaje),
        Wrap(
          children: [
            ElevatedButton(
              onPressed:
                  _ocupado ||
                      _cola == null ||
                      inicio ||
                      widget.jornadaId.isEmpty ||
                      widget.asignacionId.isEmpty
                  ? null
                  : () => _registrar(TipoEventoEjecucion.inicio),
              child: const Text('Registrar INICIO local'),
            ),
            ElevatedButton(
              onPressed:
                  _ocupado ||
                      _cola == null ||
                      !inicio ||
                      fin ||
                      widget.asignacionId.isEmpty
                  ? null
                  : () => _registrar(TipoEventoEjecucion.fin),
              child: const Text('Registrar FIN local'),
            ),
            OutlinedButton(
              onPressed: _ocupado || _cola == null ? null : _sincronizar,
              child: const Text('Reintentar sincronización'),
            ),
          ],
        ),
        if (_ocupado) const LinearProgressIndicator(),
        for (final e in propios)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Text(
              '${e.tipoEvento.codigo} · ${e.capturadoEn.toIso8601String()} · ${e.estadoSincronizacion.codigo}\nID cliente: ${e.eventoClienteId}',
            ),
          ),
        const Text(
          'Los conflictos se conservan para revisión; no se trasladan a otra jornada ni se resuelven automáticamente.',
        ),
      ],
    );
  }
}
