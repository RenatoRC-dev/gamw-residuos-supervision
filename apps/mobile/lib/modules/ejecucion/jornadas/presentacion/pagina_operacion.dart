import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import '../../../../shared/api/cliente_backend.dart';
import '../../../../shared/auth/almacen_sesion_seguro.dart';
import '../../../../shared/auth/sesion.dart';
import '../aplicacion/cola_ejecucion.dart';
import '../dominio/evento_ejecucion_local.dart';
import '../infraestructura/cliente_ejecucion_autenticada.dart';
import '../infraestructura/cache_jornadas.dart';
import 'contexto_jornada_asignada.dart';
import '../persistencia/almacen_eventos_sqlite.dart';
import '../../gps/infraestructura/captura_gps_android.dart';
import '../../gps/aplicacion/sincronizar_posiciones_gps.dart';
import '../../gps/aplicacion/transmitir_evidencia_automatica.dart';
import '../../gps/dominio/posicion_gps_local.dart';

class PaginaOperacion extends StatefulWidget {
  const PaginaOperacion({super.key, this.sesiones, this.almacen, this.cache});
  final GestorSesion? sesiones;
  final AlmacenEventosEjecucion? almacen;
  final CacheJornadas? cache;
  @override
  State<PaginaOperacion> createState() => _PaginaOperacionState();
}

class _PaginaOperacionState extends State<PaginaOperacion> with WidgetsBindingObserver {
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
  CapturaGpsAndroid? _gps;
  String _estadoGps='GPS detenido';
  List<PosicionGpsLocal> _posiciones=[];
  Timer? _refrescoGps;
  TransmitirEvidenciaAutomatica? _transmision;
  String _estadoEnvio = 'Envío automático no preparado';

  @override
  void initState() { super.initState(); WidgetsBinding.instance.addObserver(this); unawaited(_arrancar()); }

  Future<void> _arrancar() async {
    try {
      _sesiones = widget.sesiones ?? GestorSesion(ClienteBackend(Uri.parse(const String.fromEnvironment(
        'GAMW_API_URL', defaultValue: 'http://127.0.0.1:8080/api'))), AlmacenSesionSeguro());
      _sesiones!.addListener(_cambioSesion);
      _usuarioVisible = _sesiones!.actual?.usuarioId;
      _cache=widget.cache??(widget.sesiones==null?CacheJornadasSeguro():null);
      _almacen = widget.almacen ?? await AlmacenEventosSqlite.abrir();
      final espacial=_almacen is AlmacenPosicionesGps
        ? SincronizarPosicionesGps(_almacen! as AlmacenPosicionesGps,_sesiones!) : null;
      _cola = ColaEjecucion(_almacen!, ClienteEjecucionAutenticada(_sesiones!),sincronizarGps:espacial?.sincronizar);
      if(!kIsWeb && defaultTargetPlatform==TargetPlatform.android && _almacen is AlmacenEventosSqlite) {
        _gps=CapturaGpsAndroid();
        _refrescoGps=Timer.periodic(const Duration(seconds:2),(_) => unawaited(_actualizarGps()));
      }
      if (widget.sesiones == null) await _sesiones!.restaurar();
      await _refrescarEventos();
      if (_sesiones!.actual != null) await _cargarJornadas();
      await _recuperarGps();
      if (_almacen is AlmacenPosicionesGps) {
        const segundos = int.fromEnvironment('GAMW_GPS_ENVIO_SEGUNDOS', defaultValue: CapturaGpsAndroid.intervaloSegundos);
        _transmision = TransmitirEvidenciaAutomatica(_cola!, _sesiones!, _almacen! as AlmacenPosicionesGps,
          intervalo: Duration(seconds: segundos), aviso: (texto) {
            if (!mounted) return;
            setState(() => _estadoEnvio = texto);
            unawaited(_refrescarEventos().catchError((Object _) {
              _mostrar('No se pudo actualizar la vista; la evidencia permanece conservada.');
            }));
          });
        _transmision!.iniciar();
        _estadoEnvio = 'Envío automático configurado: $segundos s, con reintentos acotados.';
      }
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
        _posiciones=[];
      }
    });
    // Un 401/expiración bloquea envíos; el servicio conserva la cuenta de captura
    // sin depender del JWT. Solo logout explícito, FIN o cambio de cuenta lo detienen.
  }
  void _mostrar(String texto) { if (mounted) setState(() => _aviso = texto); }

  Future<void> _entrar() async {
    setState(() { _ocupado = true; _aviso = null; });
    try {
      await _sesiones!.iniciar(_nombre.text, _clave.text);
      await _prepararCuentaGps();
      await _refrescarEventos();
      await _cargarJornadas();
      await _recuperarGps();
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
    await _actualizarGps();
  }

  Future<void> _actualizarGps() async {
    try {
      final usuario=_sesiones?.actual?.usuarioId;
      final posiciones=usuario!=null && _almacen is AlmacenPosicionesGps
        ? await (_almacen! as AlmacenPosicionesGps).listarPosiciones(usuario) : <PosicionGpsLocal>[];
      final estado=_gps==null?_estadoGps:await _gps!.estado();
      if(mounted && _sesiones?.actual?.usuarioId==usuario) setState(() { _posiciones=posiciones; _estadoGps=estado; });
    } on Exception { if(mounted) setState(()=>_estadoGps='No se pudo consultar GPS local'); }
  }

  Future<void> _recuperarGps({bool pedirPermisos=false}) async {
    if(_gps==null || _sesiones?.actual==null) return;
    try {
      await _prepararCuentaGps();
      if(_sesiones?.actual==null) return;
      final usuario=_sesiones!.actual!.usuarioId;
      final eventos=await _cola!.listar();
      final abiertos=eventos.where((e)=>e.tipoEvento==TipoEventoEjecucion.inicio &&
        !eventos.any((f)=>f.jornadaId==e.jornadaId && f.tipoEvento==TipoEventoEjecucion.fin)).toList();
      ContextoCapturaGps? contexto;
      for(final j in _jornadas) {
        if(j['asignacionJornadaId']==_asignacion && abiertos.any((e)=>e.jornadaId==j['jornadaId'])) {
          contexto=ContextoCapturaGps(j['jornadaId'] as String,_asignacion!,usuario);
        }
      }
      final propios=abiertos.where((e)=>e.usuarioIdCaptura==usuario).toList();
      if(contexto==null && propios.length==1) {
        contexto=ContextoCapturaGps(propios.single.jornadaId,propios.single.asignacionJornadaId,usuario);
      }
      if(contexto==null) return; // Varias jornadas/contextos: selección explícita, no regla institucional inventada.
      final estado=await _gps!.iniciar(_almacen! as AlmacenEventosSqlite,contexto,pedirPermisos:pedirPermisos,
        sigueAutenticado:()=>_sesiones?.actual?.usuarioId==usuario && mounted);
      if(mounted) setState(()=>_estadoGps=estado);
    } on Exception { if(mounted) setState(()=>_estadoGps='GPS no disponible; evidencia operativa conservada'); }
  }
  Future<void> _prepararCuentaGps() async {
    if(_gps==null || _almacen is! AlmacenEventosSqlite) return;
    final contexto=await (_almacen! as AlmacenEventosSqlite).contextoGpsActivo();
    final usuario=_sesiones?.actual?.usuarioId;
    if(usuario!=null && contexto!=null && contexto['usuario_id_captura']!=usuario) await _detenerGps();
  }
  Future<void> _detenerGps({String? jornada}) async {
    try { await _gps?.detener(jornada:jornada); }
    on Exception { _mostrar('No se pudo confirmar detención GPS; revise los permisos Android.'); }
    if(mounted) setState(()=>_estadoGps='GPS detenido');
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if(state==AppLifecycleState.resumed) {
      unawaited(_recuperarGps());
      _transmision?.solicitar();
    }
  }

  Future<void> _registrar(TipoEventoEjecucion tipo) async {
    setState(() => _capturando = true);
    try {
      final sesion = await _sesiones!.vigente();
      final jornada = _jornadas.firstWhere((j) => j['asignacionJornadaId'] == _asignacion);
      await _cola!.registrarLocal(jornada['jornadaId'] as String, _asignacion!, tipo, usuarioIdCaptura: sesion.usuarioId);
      await _refrescarEventos();
      if(tipo==TipoEventoEjecucion.inicio) { await _recuperarGps(pedirPermisos:true); }
      else { await _detenerGps(jornada:jornada['jornadaId'] as String); }
      _transmision?.solicitar();
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
    await _detenerGps();
    try { await _sesiones!.cerrar(); }
    on Object { _mostrar('No se pudo borrar la sesión segura; la sesión en memoria está cerrada.'); }
    if (mounted) setState(() { _jornadas = []; _asignacion = null; });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _refrescoGps?.cancel();
    final transmisionDetenida = _transmision?.detener();
    _nombre.dispose(); _clave.dispose();
    _sesiones?.removeListener(_cambioSesion);
    if (widget.sesiones == null) { _sesiones?.api.cerrar(); _sesiones?.dispose(); }
    if (widget.almacen == null && _almacen is AlmacenEventosSqlite) {
      unawaited(() async { await transmisionDetenida; await (_almacen! as AlmacenEventosSqlite).cerrar(); }());
    }
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
        if (seleccion != null)
          ContextoJornadaAsignada(key: ValueKey(_asignacion), jornada: seleccion),
        Wrap(spacing: 12, children: [
          FilledButton(onPressed: _cola == null || seleccion == null || _capturando || existe(TipoEventoEjecucion.inicio)
            ? null : () => _registrar(TipoEventoEjecucion.inicio), child: const Text('Registrar INICIO')),
          FilledButton(onPressed: _cola == null || seleccion == null || _capturando || !existe(TipoEventoEjecucion.inicio) || existe(TipoEventoEjecucion.fin)
            ? null : () => _registrar(TipoEventoEjecucion.fin), child: const Text('Registrar FIN')),
        ]),
        TextButton(onPressed: _cola == null || _sincronizando ? null : _sincronizar, child: const Text('Sincronizar pendientes')),
        if (_transmision != null) Text(_sesiones?.actual == null ? 'Reautenticación requerida; pendientes conservados.' : _estadoEnvio),
        if(_gps!=null) ...[
          Text(_estadoGps),
          TextButton(onPressed:()=>_recuperarGps(pedirPermisos:true),child:const Text('Reanudar GPS de jornada')),
          Text('Posiciones pendientes: ${_posiciones.where((p)=>p.estado==EstadoSincronizacion.pendiente).length}'),
          Text('Sincronizadas: ${_posiciones.where((p)=>p.estado==EstadoSincronizacion.sincronizado).length} · Conflictos: ${_posiciones.where((p)=>p.estado==EstadoSincronizacion.conflicto).length}'),
        ],
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
