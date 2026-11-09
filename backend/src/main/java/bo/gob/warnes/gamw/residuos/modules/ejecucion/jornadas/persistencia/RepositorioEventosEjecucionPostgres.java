package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.persistencia;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioEventosEjecucionPostgres implements RepositorioEventosEjecucion {
    private final JdbcTemplate jdbc;
    public RepositorioEventosEjecucionPostgres(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<EventoEjecucionJornada> porCliente(UUID id) {
        return jdbc.query("SELECT * FROM evento_ejecucion_jornada WHERE evento_cliente_id=?",this::leer,id).stream().findFirst();
    }
    public List<EventoEjecucionJornada> porJornada(UUID id) {
        return jdbc.query("SELECT * FROM evento_ejecucion_jornada WHERE jornada_id=? ORDER BY capturado_en,id",this::leer,id);
    }
    public Optional<EventoEjecucionJornada> insertarSiAusente(EventoEjecucionJornada e) {
        return jdbc.query("""
                INSERT INTO evento_ejecucion_jornada(id,evento_cliente_id,jornada_id,asignacion_jornada_id,tipo_evento,capturado_en,recibido_en,usuario_id)
                VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING RETURNING *
                """,this::leer,e.id(),e.eventoClienteId(),e.jornadaId(),e.asignacionJornadaId(),e.tipoEvento().name(),
                Timestamp.from(e.capturadoEn()),Timestamp.from(e.recibidoEn()),e.usuarioId()).stream().findFirst();
    }
    private EventoEjecucionJornada leer(ResultSet r,int i) throws SQLException {
        return new EventoEjecucionJornada(r.getObject("id",UUID.class),r.getObject("evento_cliente_id",UUID.class),r.getObject("jornada_id",UUID.class),
                r.getObject("asignacion_jornada_id",UUID.class),TipoEventoEjecucion.valueOf(r.getString("tipo_evento")),
                r.getTimestamp("capturado_en").toInstant(),r.getTimestamp("recibido_en").toInstant(),r.getObject("usuario_id",UUID.class));
    }
}
