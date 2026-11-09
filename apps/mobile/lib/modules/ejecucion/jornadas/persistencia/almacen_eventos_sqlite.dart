import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import '../dominio/evento_ejecucion_local.dart';
import '../../gps/dominio/posicion_gps_local.dart';

class AlmacenEventosSqlite implements AlmacenEventosEjecucion, AlmacenPosicionesGps {
  AlmacenEventosSqlite._(this._db);
  final Database _db;
  String get ruta => _db.path;
  Future<Map<String,Object?>?> contextoGpsActivo() async =>
    (await _db.query('captura_gps_local',where:'activa = 1',limit:1)).firstOrNull;

  static Future<AlmacenEventosSqlite> abrir({
    DatabaseFactory? factory,
    String? ruta,
  }) async {
    final motor = factory ?? databaseFactory;
    final archivo =
        ruta ??
        p.join(await motor.getDatabasesPath(), 'gamw_eventos_ejecucion.db');
    final db = await motor.openDatabase(
      archivo,
      options: OpenDatabaseOptions(
        version: 3,
        onConfigure: (db) async {
          await db.execute('PRAGMA synchronous = FULL');
        },
        onCreate: (db, version) async {
          await db.execute('''
          CREATE TABLE evento_ejecucion_local (
            evento_cliente_id TEXT PRIMARY KEY,
            jornada_id TEXT NOT NULL,
            asignacion_jornada_id TEXT NOT NULL,
            tipo_evento TEXT NOT NULL CHECK(tipo_evento IN ('INICIO','FIN')),
            capturado_en TEXT NOT NULL,
            usuario_id_captura TEXT,
            estado_sincronizacion TEXT NOT NULL CHECK(estado_sincronizacion IN ('PENDIENTE','SINCRONIZADO','CONFLICTO')),
            UNIQUE(jornada_id,tipo_evento)
          )
        ''');
          await _protegerIdentidad(db);
          await _crearGps(db);
        },
        onUpgrade: (db, anterior, nueva) async {
          if (anterior < 2) {
            await db.execute('ALTER TABLE evento_ejecucion_local ADD COLUMN usuario_id_captura TEXT');
            await db.execute('DROP TRIGGER IF EXISTS evento_local_identidad_inmutable');
            await _protegerIdentidad(db);
          }
          if (anterior < 3) await _crearGps(db);
        },
      ),
    );
    return AlmacenEventosSqlite._(db);
  }

  @override
  Future<void> registrar(EventoEjecucionLocal e) => _db.transaction((
    txn,
  ) async {
    final anteriores = await txn.query(
      'evento_ejecucion_local',
      where: 'jornada_id = ?',
      whereArgs: [e.jornadaId],
    );
    if (anteriores.any((fila) => fila['tipo_evento'] == e.tipoEvento.codigo)) {
      throw StateError(
        'Ya existe un evento local de ese tipo. Reintente sincronizarlo; no genere otro ID.',
      );
    }
    if (e.tipoEvento == TipoEventoEjecucion.fin) {
      final inicio = anteriores
          .where((fila) => fila['tipo_evento'] == 'INICIO')
          .firstOrNull;
      if (inicio == null) {
        throw StateError('Registre primero INICIO en este dispositivo.');
      }
      if (e.capturadoEn.isBefore(
        DateTime.parse(inicio['capturado_en'] as String),
      )) {
        throw StateError('FIN no puede tener una captura anterior a INICIO.');
      }
    }
    await txn.insert('evento_ejecucion_local', {
      'evento_cliente_id': e.eventoClienteId,
      'jornada_id': e.jornadaId,
      'asignacion_jornada_id': e.asignacionJornadaId,
      'tipo_evento': e.tipoEvento.codigo,
      'capturado_en': e.capturadoEn.toIso8601String(),
      'usuario_id_captura': e.usuarioIdCaptura,
      'estado_sincronizacion': EstadoSincronizacion.pendiente.codigo,
    });
    if(e.tipoEvento==TipoEventoEjecucion.fin) {
      await txn.update('captura_gps_local',{'activa':0},where:'jornada_id = ?',whereArgs:[e.jornadaId]);
    }
  });

  @override
  Future<List<EventoEjecucionLocal>> listar() async {
    final filas = await _db.query('evento_ejecucion_local');
    final eventos = filas
        .map(
          (f) => EventoEjecucionLocal(
            eventoClienteId: f['evento_cliente_id'] as String,
            jornadaId: f['jornada_id'] as String,
            asignacionJornadaId: f['asignacion_jornada_id'] as String,
            tipoEvento: TipoEventoEjecucion.values.firstWhere(
              (t) => t.codigo == f['tipo_evento'],
            ),
            capturadoEn: DateTime.parse(f['capturado_en'] as String),
            usuarioIdCaptura: f['usuario_id_captura'] as String?,
            estadoSincronizacion: EstadoSincronizacion.values.firstWhere(
              (s) => s.codigo == f['estado_sincronizacion'],
            ),
          ),
        )
        .toList();
    eventos.sort((a, b) {
      final orden = a.capturadoEn.compareTo(b.capturadoEn);
      if (orden != 0) return orden;
      final jornada = a.jornadaId.compareTo(b.jornadaId);
      if (jornada != 0) return jornada;
      final tipo = a.tipoEvento.index.compareTo(b.tipoEvento.index);
      return tipo != 0 ? tipo : a.eventoClienteId.compareTo(b.eventoClienteId);
    });
    return eventos;
  }

  @override
  Future<void> cambiarEstado(String id, EstadoSincronizacion estado) async {
    await _db.update(
      'evento_ejecucion_local',
      {'estado_sincronizacion': estado.codigo},
      where: 'evento_cliente_id = ?',
      whereArgs: [id],
    );
  }

  Future<void> cerrar() => _db.close();

  @override
  Future<List<PosicionGpsLocal>> listarPosiciones(String usuario) async =>
    (await _db.query('posicion_gps_local',where:'usuario_id_captura = ?',whereArgs:[usuario],
      orderBy:'capturado_en,captura_id')).map((r) => PosicionGpsLocal(
        capturaId:r['captura_id'] as String,jornadaId:r['jornada_id'] as String,
        asignacionJornadaId:r['asignacion_jornada_id'] as String,usuarioIdCaptura:r['usuario_id_captura'] as String,
        capturadoEn:DateTime.parse(r['capturado_en'] as String),latitud:(r['latitud'] as num).toDouble(),
        longitud:(r['longitud'] as num).toDouble(),precisionMetros:(r['precision_metros'] as num).toDouble(),
        estado:EstadoSincronizacion.values.firstWhere((e)=>e.codigo==r['estado_sincronizacion']),error:r['error'] as String?)).toList();

  @override
  Future<void> registrarPosicion(PosicionGpsLocal p) async {
    await _db.insert('posicion_gps_local',{'captura_id':p.capturaId,'jornada_id':p.jornadaId,
      'asignacion_jornada_id':p.asignacionJornadaId,'usuario_id_captura':p.usuarioIdCaptura,
      'capturado_en':p.capturadoEn.toUtc().toIso8601String(),'latitud':p.latitud,'longitud':p.longitud,
      'precision_metros':p.precisionMetros,'estado_sincronizacion':'PENDIENTE'});
  }
  @override
  Future<void> estadoPosicion(String captura,EstadoSincronizacion estado,String? error) async {
    await _db.update('posicion_gps_local',{'estado_sincronizacion':estado.codigo,'error':error},
      where:'captura_id = ?',whereArgs:[captura]);
  }
  @override
  Future<bool> hayGpsNoAceptado(String jornada) async => (await _db.query('posicion_gps_local',columns:['captura_id'],
    where:"jornada_id = ? AND estado_sincronizacion <> 'SINCRONIZADO'",whereArgs:[jornada],limit:1)).isNotEmpty;

  static Future<void> _crearGps(Database db) async {
    await db.execute('''CREATE TABLE posicion_gps_local (
      captura_id TEXT PRIMARY KEY, jornada_id TEXT NOT NULL, asignacion_jornada_id TEXT NOT NULL,
      usuario_id_captura TEXT NOT NULL, capturado_en TEXT NOT NULL, latitud REAL NOT NULL CHECK(latitud BETWEEN -90 AND 90),
      longitud REAL NOT NULL CHECK(longitud BETWEEN -180 AND 180), precision_metros REAL NOT NULL CHECK(precision_metros>=0),
      estado_sincronizacion TEXT NOT NULL CHECK(estado_sincronizacion IN ('PENDIENTE','SINCRONIZADO','CONFLICTO')),
      error TEXT)''');
    await db.execute('CREATE INDEX gps_local_cola ON posicion_gps_local(usuario_id_captura,jornada_id,capturado_en)');
    await db.execute('''CREATE TRIGGER gps_local_inmutable BEFORE UPDATE ON posicion_gps_local
      WHEN NEW.captura_id IS NOT OLD.captura_id OR NEW.jornada_id IS NOT OLD.jornada_id
      OR NEW.asignacion_jornada_id IS NOT OLD.asignacion_jornada_id OR NEW.usuario_id_captura IS NOT OLD.usuario_id_captura
      OR NEW.capturado_en IS NOT OLD.capturado_en OR NEW.latitud IS NOT OLD.latitud
      OR NEW.longitud IS NOT OLD.longitud OR NEW.precision_metros IS NOT OLD.precision_metros
      BEGIN SELECT RAISE(ABORT,'La evidencia GPS local es inmutable'); END''');
    await db.execute('''CREATE TABLE captura_gps_local (
      id INTEGER PRIMARY KEY CHECK(id=1), jornada_id TEXT NOT NULL, asignacion_jornada_id TEXT NOT NULL,
      usuario_id_captura TEXT NOT NULL, activa INTEGER NOT NULL CHECK(activa IN (0,1)))''');
  }

  static Future<void> _protegerIdentidad(Database db) => db.execute('''
    CREATE TRIGGER evento_local_identidad_inmutable BEFORE UPDATE ON evento_ejecucion_local
    WHEN NEW.evento_cliente_id <> OLD.evento_cliente_id OR NEW.jornada_id <> OLD.jornada_id
      OR NEW.asignacion_jornada_id <> OLD.asignacion_jornada_id
      OR NEW.tipo_evento <> OLD.tipo_evento OR NEW.capturado_en <> OLD.capturado_en
      OR NEW.usuario_id_captura IS NOT OLD.usuario_id_captura
    BEGIN SELECT RAISE(ABORT,'La evidencia local es inmutable'); END
  ''');
}
