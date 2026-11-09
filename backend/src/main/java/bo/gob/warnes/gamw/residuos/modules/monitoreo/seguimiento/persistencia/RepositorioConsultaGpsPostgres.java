package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.persistencia;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioConsultaGps;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioConsultaGpsPostgres implements RepositorioConsultaGps {
    private final JdbcTemplate jdbc;
    public RepositorioConsultaGpsPostgres(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<PosicionGps> paginaPorJornada(UUID jornadaId, int limite, int desplazamiento, Instant corteRecepcion) {
        return jdbc.query("""
                SELECT id,captura_id,jornada_id,asignacion_jornada_id,usuario_id,capturado_en,
                       recibido_en,ST_X(geometria) AS longitud,ST_Y(geometria) AS latitud,precision_metros
                FROM posicion_gps WHERE jornada_id=? AND recibido_en<=?
                ORDER BY capturado_en,captura_id,id LIMIT ? OFFSET ?
                """, (r,i)->new PosicionGps(r.getObject("id",UUID.class),r.getObject("captura_id",UUID.class),
                r.getObject("jornada_id",UUID.class),r.getObject("asignacion_jornada_id",UUID.class),
                r.getObject("usuario_id",UUID.class),r.getTimestamp("capturado_en").toInstant(),r.getTimestamp("recibido_en").toInstant(),
                r.getDouble("latitud"),r.getDouble("longitud"),r.getDouble("precision_metros")),
                jornadaId,java.sql.Timestamp.from(corteRecepcion),limite,desplazamiento);
    }

    @Override public List<PosicionGps> porJornada(UUID jornadaId) {
        // ST_X/Y conservan double precision: no redondear coordenadas para visualización.
        return jdbc.query("""
                SELECT id,captura_id,jornada_id,asignacion_jornada_id,usuario_id,capturado_en,
                       recibido_en,ST_X(geometria) AS longitud,ST_Y(geometria) AS latitud,precision_metros
                FROM posicion_gps WHERE jornada_id=?
                ORDER BY capturado_en ASC,captura_id ASC,id ASC
                """, (r, i) -> new PosicionGps(r.getObject("id", UUID.class), r.getObject("captura_id", UUID.class),
                r.getObject("jornada_id", UUID.class), r.getObject("asignacion_jornada_id", UUID.class),
                r.getObject("usuario_id", UUID.class), r.getTimestamp("capturado_en").toInstant(),
                r.getTimestamp("recibido_en").toInstant(), r.getDouble("latitud"),
                r.getDouble("longitud"), r.getDouble("precision_metros")), jornadaId);
    }
}
