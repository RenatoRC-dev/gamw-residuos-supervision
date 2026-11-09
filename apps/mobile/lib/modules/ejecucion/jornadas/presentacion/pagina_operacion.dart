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
import '../../incidencias/presentacion/panel_incidencias.dart';
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
  int _revisionJornadas = 0;

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
      final cliente = ClienteEjecucionAutenticada(_sesiones!);
      _cola = ColaEjecucion(_almacen!, cliente,sincronizarGps:espacial?.sincronizar,confirmarInicioServidor:cliente.confirmarInicio);
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
        _revisionJornadas++;
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
    final revision = ++_revisionJornadas;
    final usuario = _sesiones!.actual?.usuarioId;
    if(usuario==null) return;
    if(_cache!=null) {
      try {
        final guardadas=await _cache!.leer(usuario);
        if(mounted && revision == _revisionJornadas && _sesiones!.actual?.usuarioId==usuario) {
          setState(() { _jornadas=guardadas; if (!guardadas.any((j) => j['asignacionJornadaId'] == _asignacion)) _asignacion=null; });
        }
      } on Exception { _mostrar('No se pudo recuperar el contexto local de esta cuenta.'); }
    }
    List<dynamic> datos;
    try { datos=await _sesiones!.solicitar('GET', 'mis-jornadas') as List<dynamic>; }
    on Exception { _mostrar('Sin actualización: use solo el contexto guardado de su cuenta. El servidor verificará la evidencia al sincronizar.'); return; }
    if (!mounted || revision != _revisionJornadas || _sesiones!.actual?.usuarioId != usuario) return;
    setState(() {
      final anteriores = {for (final j in _jornadas) j['asignacionJornadaId']: j['ejecucionConsultada']};
      _jornadas = datos.cast<Map<String, dynamic>>().map((j) => <String,dynamic>{...j,
        if (anteriores[j['asignacionJornadaId']] != null) 'ejecucionConsultada': anteriores[j['asignacionJornadaId']]}).toList();
      if (!_jornadas.any((j) => j['asignacionJornadaId'] == _asignacion)) _asignacion = null;
    });
    try { await _cache?.guardar(usuario,_jornadas); }
    on Exception { _mostrar('No se pudo conservar la lista para el próximo arranque offline.'); }
  }

  Future<void> _actualizar() async {
    try { await _sesiones!.identidadActual(); await _cargarJornadas(); await _consultarEjecucion(); }
    on Object { _mostrar('No se pudo actualizar. La evidencia local permanece guardada.'); }
  }

  Map<String,dynamic> _ejecucionRemota(Map<String,dynamic>? jornada) {
    final estado = jornada?['ejecucionConsultada'];
    return estado is Map<String,dynamic> ? estado : <String,dynamic>{};
  }

  Future<void> _consultarEjecucion() async {
    final asignacion = _asignacion;
    final seleccion = _jornadas.where((j) => j['asignacionJornadaId'] == asignacion).firstOrNull;
    if (seleccion == null || _sesiones?.actual == null) return;
    final sesion = _sesiones!.actual!;
    try {
      final estado = await _sesiones!.api.solicitar('GET',
        'mis-jornadas/${seleccion['jornadaId']}/ejecucion?asignacionId=${Uri.encodeQueryComponent(asignacion!)}', token: sesion.token) as Map<String,dynamic>;
      if (!mounted || _sesiones?.actual?.token != sesion.token || _asignacion != asignacion || estado['jornadaId'] != seleccion['jornadaId']) return;
      setState(() { seleccion['ejecucionConsultada'] = estado; });
      try { await _cache?.guardar(sesion.usuarioId,_jornadas); }
      on Exception { _mostrar('Estado consultado, pero no se pudo conservar su copia offline.'); }
      if (estado['fin'] != null) await _detenerGps(jornada: seleccion['jornadaId'] as String);
    } on ErrorBackend catch(e) {
      if (e.estado == 401) await _sesiones!.invalidar(sesion.token);
      if (mounted && _sesiones?.actual?.usuarioId == sesion.usuarioId) _mostrar('No se pudo verificar la ejecución actual; la copia guardada puede estar desactualizada. Evidencia local conservada.');
    } on Exception { _mostrar('Sin actualización de ejecución. Se conserva el contexto disponible; el servidor validará al sincronizar.'); }
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
        !eventos.any((f)=>f.jornadaId==e.jornadaId && f.tipoEvento==TipoEventoEjecucion.fin) &&
        !_jornadas.any((j)=>j['jornadaId']==e.jornadaId && _ejecucionRemota(j)['fin']!=null)).toList();
      ContextoCapturaGps? contexto;
      for(final j in _jornadas) {
        final remoto = _ejecucionRemota(j);
        final finLocal = eventos.any((e)=>e.jornadaId==j['jornadaId'] && e.tipoEvento==TipoEventoEjecucion.fin);
        if(j['asignacionJornadaId']==_asignacion && !finLocal && remoto['fin']==null &&
            (abiertos.any((e)=>e.jornadaId==j['jornadaId']) || remoto['inicio']!=null)) {
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
    try {
      await _gps?.detener(jornada:jornada);
      await _actualizarGps();
    } on Exception {
      if(mounted) setState(()=>_estadoGps='Detención GPS no confirmada; reintente y revise Android');
      _mostrar('No se pudo confirmar detención GPS; la evidencia permanece guardada.');
    }
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if(state==AppLifecycleState.resumed) {
      unawaited(_recuperarGps());
      unawaited(_refrescarEventos().catchError((Object _) => _mostrar('No se pudo refrescar la evidencia local. Reintente sin generar nuevos eventos.')));
      unawaited(_consultarEjecucion());
      _transmision?.solicitar();
    }
  }

  Future<void> _registrar(TipoEventoEjecucion tipo) async {
    setState(() => _capturando = true);
    bool guardado = false;
    try {
      final sesion = await _sesiones!.vigente();
      final jornada = _jornadas.firstWhere((j) => j['asignacionJornadaId'] == _asignacion);
      final inicioRemoto = _ejecucionRemota(jornada)['inicio'];
      final fechaInicio = inicioRemoto is Map<String,dynamic> ? DateTime.tryParse(inicioRemoto['capturadoEn'] as String) : null;
      await _cola!.registrarLocal(jornada['jornadaId'] as String, _asignacion!, tipo, usuarioIdCaptura: sesion.usuarioId, inicioServidor: fechaInicio);
      guardado = true;
      // FIN ya está durable: detener antes de cualquier lectura/presentación que pueda fallar.
      if(tipo==TipoEventoEjecucion.fin) await _detenerGps(jornada:jornada['jornadaId'] as String);
      try { await _refrescarEventos(); }
      on Object { _mostrar('Evento guardado. No se pudo refrescar la vista; no registre otro UUID.'); }
      if(tipo==TipoEventoEjecucion.inicio) { await _recuperarGps(pedirPermisos:true); }
      _transmision?.solicitar();
      _mostrar('Evidencia guardada en este dispositivo. No determina cumplimiento.');
    } on Object { _mostrar(guardado ? 'Evento guardado; no genere otro. Reintente consultar o sincronizar la evidencia.' : 'No se pudo registrar. Revise la sesión, la jornada y la evidencia existente.'); }
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
    final remoto = _ejecucionRemota(seleccion);
    final inicioConocido = existe(TipoEventoEjecucion.inicio) || remoto['inicio'] != null;
    final finConocido = existe(TipoEventoEjecucion.fin) || remoto['fin'] != null;
    final ahora = DateTime.now().toUtc();
    final desde = DateTime.tryParse(seleccion?['vigenteDesde'] as String? ?? '');
    final hasta = DateTime.tryParse(seleccion?['vigenteHasta'] as String? ?? '');
    final intervaloPropio = desde != null && !ahora.isBefore(desde) && (hasta == null || !ahora.isAfter(hasta));
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
          onChanged: _capturando ? null : (valor) { setState(() => _asignacion = valor); unawaited(_consultarEjecucion()); }),
        if (seleccion != null)
          ContextoJornadaAsignada(key: ValueKey(_asignacion), jornada: seleccion),
        Wrap(spacing: 12, children: [
          FilledButton(onPressed: _cola == null || seleccion == null || _capturando || inicioConocido || seleccion['vigente'] != true || !intervaloPropio
            ? null : () => _registrar(TipoEventoEjecucion.inicio), child: const Text('Registrar INICIO')),
          FilledButton(onPressed: _cola == null || seleccion == null || _capturando || !inicioConocido || finConocido || !intervaloPropio
            ? null : () => _registrar(TipoEventoEjecucion.fin), child: const Text('Registrar FIN')),
        ]),
        if (remoto.isNotEmpty) Text('Última consulta de ejecución: INICIO ${remoto['inicio'] == null ? 'no recibido' : 'recibido'} · FIN ${remoto['fin'] == null ? 'no recibido' : 'recibido'}. La copia puede estar desactualizada sin conexión.'),
        TextButton(onPressed: _cola == null || _sincronizando ? null : _sincronizar, child: const Text('Sincronizar pendientes')),
        if (seleccion != null)
          PanelIncidencias(key: ValueKey('incidencias:${sesion.usuarioId}:$_asignacion'), sesiones: _sesiones!,
            usuarioId: sesion.usuarioId, jornadaId: seleccion['jornadaId'] as String, asignacionId: _asignacion!),
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
