import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import '../dominio/evento_ejecucion_local.dart';

class AlmacenEventosSqlite implements AlmacenEventosEjecucion {
  AlmacenEventosSqlite._(this._db);
  final Database _db;

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
        version: 2,
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
        },
        onUpgrade: (db, anterior, nueva) async {
          if (anterior < 2) {
            await db.execute('ALTER TABLE evento_ejecucion_local ADD COLUMN usuario_id_captura TEXT');
            await db.execute('DROP TRIGGER IF EXISTS evento_local_identidad_inmutable');
            await _protegerIdentidad(db);
          }
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

  static Future<void> _protegerIdentidad(Database db) => db.execute('''
    CREATE TRIGGER evento_local_identidad_inmutable BEFORE UPDATE ON evento_ejecucion_local
    WHEN NEW.evento_cliente_id <> OLD.evento_cliente_id OR NEW.jornada_id <> OLD.jornada_id
      OR NEW.asignacion_jornada_id <> OLD.asignacion_jornada_id
      OR NEW.tipo_evento <> OLD.tipo_evento OR NEW.capturado_en <> OLD.capturado_en
      OR NEW.usuario_id_captura IS NOT OLD.usuario_id_captura
    BEGIN SELECT RAISE(ABORT,'La evidencia local es inmutable'); END
  ''');
}
