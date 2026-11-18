package bo.gob.warnes.gamw.residuos.modules.bitacora.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * CU-31: lectura unificada de eventos ya persistidos e inmutables. No existe INSERT/UPDATE/DELETE aquí y no se
 * fabrican eventos: solo se proyectan registros de actuación existentes. Las concesiones otorgadas/revocadas por la
 * vía HTTP ya constan como ACTUACION_ADMINISTRATIVA y no se duplican.
 */
@Repository
public class RepositorioBitacora {
    public record Fila(String eventoId,String fuente,Instant ocurridoEn,String accion,UUID actorUsuarioId,String actorNombreUsuario,boolean origenLocal,
            String entidadTipo,UUID entidadId,String motivo,String anterior,String posterior) { }

    private static final String EVENTOS = """
        WITH ev AS (
          SELECT 'ADM:'||a.id AS evento_id,'ACTUACION_ADMINISTRATIVA' AS fuente,a.registrada_en AS ocurrido_en,a.tipo AS accion,a.actor_usuario_id AS actor_id,false AS origen_local,
                 'USUARIO' AS entidad_tipo,a.usuario_objetivo_id AS entidad_id,a.motivo AS motivo,a.anterior::text AS anterior,a.posterior::text AS posterior
            FROM actuacion_administrativa a
          UNION ALL
          SELECT 'PLN:'||p.solicitud_id,'PLANIFICACION_OPERADOR',p.registrada_en,p.operacion,p.usuario_id,false,'JORNADA',p.jornada_id,NULL::text,NULL::text,NULL::text
            FROM actuacion_planificacion_operador p
          UNION ALL
          SELECT 'REP:'||r.solicitud_id,'REPROGRAMACION_OPERADOR',r.registrada_en,'REPROGRAMAR',r.usuario_id,false,'JORNADA',r.jornada_antecedente_id,NULL::text,NULL::text,NULL::text
            FROM reprogramacion_autorizada r
          UNION ALL
          SELECT 'CMO:'||c.id,'CONCESION_MUNICIPAL_LOCAL',c.otorgado_en,'PERMISO_OTORGADO:'||c.permiso,NULL::uuid,true,'USUARIO',c.usuario_id,c.motivo,NULL::text,NULL::text
            FROM concesion_permiso c WHERE c.operador_local NOT LIKE 'HTTP_USUARIO:%'
          UNION ALL
          SELECT 'CMR:'||c.id,'CONCESION_MUNICIPAL_LOCAL',c.revocado_en,'PERMISO_REVOCADO:'||c.permiso,NULL::uuid,true,'USUARIO',c.usuario_id,c.motivo_revocacion,NULL::text,NULL::text
            FROM concesion_permiso c WHERE c.revocado_en IS NOT NULL AND c.revocado_por_local NOT LIKE 'HTTP_USUARIO:%'
          UNION ALL
          SELECT 'CPO:'||c.id,'CONCESION_PERIODO_LOCAL',c.otorgado_en,'PERMISO_PERIODO_OTORGADO:'||c.permiso,NULL::uuid,true,'PERIODO_SERVICIO',c.periodo_servicio_id,c.motivo,NULL::text,NULL::text
            FROM concesion_periodo_operador c WHERE c.operador_local NOT LIKE 'HTTP_USUARIO:%'
          UNION ALL
          SELECT 'CPR:'||c.id,'CONCESION_PERIODO_LOCAL',c.revocado_en,'PERMISO_PERIODO_REVOCADO:'||c.permiso,NULL::uuid,true,'PERIODO_SERVICIO',c.periodo_servicio_id,c.motivo_revocacion,NULL::text,NULL::text
            FROM concesion_periodo_operador c WHERE c.revocado_en IS NOT NULL AND c.revocado_por_local NOT LIKE 'HTTP_USUARIO:%'
        )
        SELECT e.*,u.nombre_usuario FROM ev e LEFT JOIN usuario u ON u.id=e.actor_id
        """;
    private final JdbcTemplate jdbc;
    public RepositorioBitacora(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Fila> buscar(LocalDate desde,LocalDate hasta,UUID actor,String accion,String entidadTipo,UUID entidadId,int limite,int desplazamiento) {
        var sql = new StringBuilder(EVENTOS).append(" WHERE true");
        var args = new ArrayList<Object>();
        if (desde != null) { sql.append(" AND (e.ocurrido_en AT TIME ZONE 'America/La_Paz')::date >= ?"); args.add(java.sql.Date.valueOf(desde)); }
        if (hasta != null) { sql.append(" AND (e.ocurrido_en AT TIME ZONE 'America/La_Paz')::date <= ?"); args.add(java.sql.Date.valueOf(hasta)); }
        if (actor != null) { sql.append(" AND e.actor_id=?"); args.add(actor); }
        if (accion != null) { sql.append(" AND e.accion=?"); args.add(accion); }
        if (entidadTipo != null) { sql.append(" AND e.entidad_tipo=?"); args.add(entidadTipo); }
        if (entidadId != null) { sql.append(" AND e.entidad_id=?"); args.add(entidadId); }
        sql.append(" ORDER BY e.ocurrido_en DESC,e.evento_id LIMIT ? OFFSET ?"); args.add(limite); args.add(desplazamiento);
        return jdbc.query(sql.toString(),this::fila,args.toArray());
    }
    public Optional<Fila> evento(String eventoId) {
        return jdbc.query(EVENTOS + " WHERE e.evento_id=?",this::fila,eventoId).stream().findFirst();
    }
    private Fila fila(ResultSet r,int i) throws SQLException {
        Timestamp t = r.getTimestamp("ocurrido_en");
        return new Fila(r.getString("evento_id"),r.getString("fuente"),t.toInstant(),r.getString("accion"),r.getObject("actor_id",UUID.class),r.getString("nombre_usuario"),
            r.getBoolean("origen_local"),r.getString("entidad_tipo"),r.getObject("entidad_id",UUID.class),r.getString("motivo"),r.getString("anterior"),r.getString("posterior"));
    }
}
