import 'dart:async';
import 'package:flutter/material.dart';
import '../../../../shared/api/cliente_backend.dart';
import '../../../../shared/auth/almacen_sesion_seguro.dart';
import '../../../../shared/auth/sesion.dart';
import '../aplicacion/cola_ejecucion.dart';
import '../dominio/evento_ejecucion_local.dart';
import '../infraestructura/cliente_ejecucion_autenticada.dart';
import '../infraestructura/cache_jornadas.dart';
import '../persistencia/almacen_eventos_sqlite.dart';

class PaginaOperacion extends StatefulWidget {
  const PaginaOperacion({super.key, this.sesiones, this.almacen, this.cache});
  final GestorSesion? sesiones;
  final AlmacenEventosEjecucion? almacen;
  final CacheJornadas? cache;
  @override
  State<PaginaOperacion> createState() => _PaginaOperacionState();
}

class _PaginaOperacionState extends State<PaginaOperacion> {
  final _nombre = TextEditingController();
  final _clave = TextEditingController();
  GestorSesion? _sesiones;
  AlmacenEventosEjecucion? _almacen;
  ColaEjecucion? _cola;
  CacheJornadas? _cache;
  List<Map<String, dynamic>> _jornadas = [];
  List<EventoEjecucionLocal> _eventos = [];
  String? _asignacion;
  String? _aviso;
  String? _usuarioVisible;
  bool _ocupado = false, _capturando = false, _sincronizando = false;

  @override
  void initState() { super.initState(); unawaited(_arrancar()); }

  Future<void> _arrancar() async {
    try {
      _sesiones = widget.sesiones ?? GestorSesion(ClienteBackend(Uri.parse(const String.fromEnvironment(
        'GAMW_API_URL', defaultValue: 'http://127.0.0.1:8080/api'))), AlmacenSesionSeguro());
      _sesiones!.addListener(_cambioSesion);
      _usuarioVisible = _sesiones!.actual?.usuarioId;
      _cache=widget.cache??(widget.sesiones==null?CacheJornadasSeguro():null);
      _almacen = widget.almacen ?? await AlmacenEventosSqlite.abrir();
      _cola = ColaEjecucion(_almacen!, ClienteEjecucionAutenticada(_sesiones!));
      if (widget.sesiones == null) await _sesiones!.restaurar();
      await _refrescarEventos();
      if (_sesiones!.actual != null) await _cargarJornadas();
    } on Object { _mostrar('No se pudo preparar la sesión. Compruebe configuración y conectividad.'); }
    if (mounted) setState(() {});
  }

  void _cambioSesion() {
    if (!mounted) return;
    final usuario = _sesiones?.actual?.usuarioId;
    setState(() {
      if (_usuarioVisible != usuario) {
        _jornadas = [];
        _asignacion = null;
        _usuarioVisible = usuario;
      }
    });
  }
  void _mostrar(String texto) { if (mounted) setState(() => _aviso = texto); }

  Future<void> _entrar() async {
    setState(() { _ocupado = true; _aviso = null; });
    try {
      await _sesiones!.iniciar(_nombre.text, _clave.text);
      await _refrescarEventos();
      await _cargarJornadas();
    } on ErrorBackend catch (e) {
      _mostrar(e.estado == 401 ? 'Credenciales inválidas o sesión vencida. Vuelva a autenticarse.' : 'No se pudo iniciar la sesión.');
    } on Object { _mostrar('Sin conexión o almacenamiento seguro no disponible.'); }
    finally { _clave.clear(); if (mounted) setState(() => _ocupado = false); }
  }

  Future<void> _cargarJornadas() async {
    final usuario = _sesiones!.actual?.usuarioId;
    if(usuario==null) return;
    if(_cache!=null) {
      try {
        final guardadas=await _cache!.leer(usuario);
        if(mounted && _sesiones!.actual?.usuarioId==usuario) {
          setState(() { _jornadas=guardadas; _asignacion=null; });
        }
      } on Exception { _mostrar('No se pudo recuperar el contexto local de esta cuenta.'); }
    }
    List<dynamic> datos;
    try { datos=await _sesiones!.solicitar('GET', 'mis-jornadas') as List<dynamic>; }
    on Exception { _mostrar('Sin actualización: use solo el contexto guardado de su cuenta. El servidor verificará la evidencia al sincronizar.'); return; }
    if (!mounted || _sesiones!.actual?.usuarioId != usuario) return;
    setState(() {
      _jornadas = datos.cast<Map<String, dynamic>>();
      _asignacion = null;
    });
    try { await _cache?.guardar(usuario,_jornadas); }
    on Exception { _mostrar('No se pudo conservar la lista para el próximo arranque offline.'); }
  }

  Future<void> _actualizar() async {
    try { await _sesiones!.identidadActual(); await _cargarJornadas(); }
    on Object { _mostrar('No se pudo actualizar. La evidencia local permanece guardada.'); }
  }

  Future<void> _refrescarEventos() async {
    final datos = await _cola!.listar();
    if (mounted) setState(() => _eventos = datos);
  }

  Future<void> _registrar(TipoEventoEjecucion tipo) async {
    setState(() => _capturando = true);
    try {
      final sesion = await _sesiones!.vigente();
      final jornada = _jornadas.firstWhere((j) => j['asignacionJornadaId'] == _asignacion);
      await _cola!.registrarLocal(jornada['jornadaId'] as String, _asignacion!, tipo, usuarioIdCaptura: sesion.usuarioId);
      await _refrescarEventos();
      _mostrar('Evidencia guardada en este dispositivo. No determina cumplimiento.');
    } on Object { _mostrar('No se pudo registrar. Revise la sesión, la jornada y la evidencia existente.'); }
    finally { if (mounted) setState(() => _capturando = false); }
  }

  Future<void> _sincronizar() async {
    if (_sincronizando) return;
    setState(() => _sincronizando = true);
    try { await _cola!.sincronizar(); }
    on Object { _mostrar('La evidencia sigue guardada. Reintente tras recuperar sesión o conexión.'); }
    finally {
      try { await _refrescarEventos(); }
      on Object { _mostrar('No se pudo actualizar la vista local. La evidencia no se ha eliminado.'); }
      if (mounted) setState(() => _sincronizando = false);
    }
  }

  Future<void> _salir() async {
    try { await _sesiones!.cerrar(); }
    on Object { _mostrar('No se pudo borrar la sesión segura; la sesión en memoria está cerrada.'); }
    if (mounted) setState(() { _jornadas = []; _asignacion = null; });
  }

  @override
  void dispose() {
    _nombre.dispose(); _clave.dispose();
    _sesiones?.removeListener(_cambioSesion);
    if (widget.sesiones == null) { _sesiones?.api.cerrar(); _sesiones?.dispose(); }
    if (widget.almacen == null && _almacen is AlmacenEventosSqlite) unawaited((_almacen! as AlmacenEventosSqlite).cerrar());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sesion = _sesiones?.actual;
    Map<String, dynamic>? seleccion;
    for (final j in _jornadas) { if (j['asignacionJornadaId'] == _asignacion) seleccion = j; }
    bool existe(TipoEventoEjecucion tipo) => _eventos.any((e) => e.jornadaId == seleccion?['jornadaId'] && e.tipoEvento == tipo);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (_aviso != null) Padding(padding: const EdgeInsets.symmetric(vertical: 8), child: Text(_aviso!)),
      if (_sesiones == null) const Text('Preparando aplicación…')
      else if (sesion == null) ...[
        const Text('Acceso GAMW'),
        const Text('Autentíquese para operar. Sus registros pendientes se conservan al cerrar o vencer la sesión.'),
        TextField(controller: _nombre, decoration: const InputDecoration(labelText: 'Nombre de usuario'), autocorrect: false),
        TextField(controller: _clave, decoration: const InputDecoration(labelText: 'Contraseña'), obscureText: true,
          enableSuggestions: false, autocorrect: false),
        FilledButton(onPressed: _ocupado ? null : _entrar, child: const Text('Ingresar')),
      ] else ...[
        Text('Sesión: ${sesion.nombreUsuario}'),
        TextButton(onPressed: _salir, child: const Text('Cerrar sesión')),
        const Text('Mis jornadas'),
        TextButton(onPressed: _actualizar, child: const Text('Actualizar mis jornadas')),
        if (_jornadas.isEmpty) const Text('Sin jornadas cargadas para esta cuenta. Actualice al recuperar conexión.'),
        DropdownButton<String>(isExpanded: true, value: _asignacion, hint: const Text('Seleccione una asignación propia'),
          items: _jornadas.map((j) => DropdownMenuItem(value: j['asignacionJornadaId'] as String,
            child: Text('${j['inicioProgramado']} · ${j['vigente'] == true ? 'vigente' : 'antecedente'}', overflow: TextOverflow.ellipsis))).toList(),
          onChanged: (valor) => setState(() => _asignacion = valor)),
        Wrap(spacing: 12, children: [
          FilledButton(onPressed: _cola == null || seleccion == null || _capturando || existe(TipoEventoEjecucion.inicio)
            ? null : () => _registrar(TipoEventoEjecucion.inicio), child: const Text('Registrar INICIO')),
          FilledButton(onPressed: _cola == null || seleccion == null || _capturando || !existe(TipoEventoEjecucion.inicio) || existe(TipoEventoEjecucion.fin)
            ? null : () => _registrar(TipoEventoEjecucion.fin), child: const Text('Registrar FIN')),
        ]),
        TextButton(onPressed: _cola == null || _sincronizando ? null : _sincronizar, child: const Text('Sincronizar pendientes')),
      ],
      const Divider(),
      const Text('Evidencia en este dispositivo'),
      const Text('INICIO, FIN y GPS son evidencia; no prueban automáticamente la recolección.'),
      if (sesion == null) const Text('Inicie sesión con la cuenta de captura para consultar su evidencia local.'),
      if (sesion != null)
        for (final e in _eventos.where((e) => e.usuarioIdCaptura == sesion.usuarioId))
          Text('${e.tipoEvento.codigo} · ${e.capturadoEn.toLocal()} · ${e.estadoSincronizacion.codigo}'),
    ]);
  }
}
