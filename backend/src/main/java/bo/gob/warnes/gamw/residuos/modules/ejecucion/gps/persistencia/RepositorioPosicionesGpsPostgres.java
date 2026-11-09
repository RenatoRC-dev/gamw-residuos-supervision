package bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.persistencia;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioPosicionesGpsPostgres implements RepositorioPosicionesGps {
    private final JdbcTemplate jdbc;
    public RepositorioPosicionesGpsPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<PosicionGps> buscar(UUID jornada,UUID captura) {
        return jdbc.query("SELECT *,ST_X(geometria) AS longitud,ST_Y(geometria) AS latitud FROM posicion_gps WHERE jornada_id=? AND captura_id=?",
                (r,i) -> new PosicionGps(r.getObject("id",UUID.class),r.getObject("captura_id",UUID.class),r.getObject("jornada_id",UUID.class),
                        r.getObject("asignacion_jornada_id",UUID.class),r.getObject("usuario_id",UUID.class),
                        r.getTimestamp("capturado_en").toInstant(),r.getTimestamp("recibido_en").toInstant(),
                        r.getDouble("latitud"),r.getDouble("longitud"),r.getDouble("precision_metros")),jornada,captura).stream().findFirst();
    }
    public PosicionGps insertar(PosicionGps p) {
        jdbc.update("""
                INSERT INTO posicion_gps(id,captura_id,jornada_id,asignacion_jornada_id,usuario_id,
                    capturado_en,recibido_en,geometria,precision_metros)
                VALUES (?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),?)
                """,p.id(),p.capturaId(),p.jornadaId(),p.asignacionJornadaId(),p.usuarioId(),Timestamp.from(p.capturadoEn()),
                Timestamp.from(p.recibidoEn()),p.longitud(),p.latitud(),p.precisionMetros());
        return buscar(p.jornadaId(),p.capturaId()).orElseThrow();
    }
}
