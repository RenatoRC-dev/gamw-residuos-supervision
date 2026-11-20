package bo.gob.warnes.gamw.residuos.modules.historial.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * CU-30: recuentos descriptivos de solo lectura sobre registros conservados. El alcance de jornadas es el mismo de CU-29: fecha de servicio
 * (inicio programado en America/La_Paz) dentro del rango, con filtros opcionales de periodo y empresa. Sin escrituras, porcentajes ni fórmulas
 * contractuales.
 */
@Repository
public class RepositorioReportesServicio {
    private final JdbcTemplate jdbc;
    public RepositorioReportesServicio(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Recuento y fecha del último registro (o null si no hay registros). */
    public record Conteo(long cantidad, Instant ultimoRegistro) { }

    private record Alcance(String sql, Object[] args) { }
    private static Alcance alcance(LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        var sql = new StringBuilder("""
            SELECT j.id FROM jornada j JOIN periodo_servicio ps ON ps.id=j.periodo_servicio_id
             WHERE (j.inicio_programado AT TIME ZONE 'America/La_Paz')::date BETWEEN ? AND ?""");
        var args = new ArrayList<Object>(List.of(java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta)));
        if (periodo != null) { sql.append(" AND ps.id=?"); args.add(periodo); }
        if (empresa != null) { sql.append(" AND ps.empresa_operadora_id=?"); args.add(empresa); }
        return new Alcance(sql.toString(), args.toArray());
    }
    private Conteo contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, (r, n) -> {
            Timestamp t = r.getTimestamp(2);
            return new Conteo(r.getLong(1), t == null ? null : t.toInstant());
        }, args);
    }

    public Conteo jornadas(LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        var a = alcance(desde, hasta, periodo, empresa);
        return contar("SELECT count(*),max(j.actualizado_en) FROM jornada j WHERE j.id IN (" + a.sql() + ")", a.args());
    }
    /** Jornadas del alcance sustituidas por una reprogramación (vigente=false). */
    public Conteo jornadasSustituidas(LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        var a = alcance(desde, hasta, periodo, empresa);
        return contar("SELECT count(*),max(j.actualizado_en) FROM jornada j WHERE NOT j.vigente AND j.id IN (" + a.sql() + ")", a.args());
    }
    /** Jornadas del alcance con al menos un registro de asignación (vigente o histórico). */
    public Conteo jornadasConAsignacion(LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        var a = alcance(desde, hasta, periodo, empresa);
        return contar("""
            SELECT count(DISTINCT a.jornada_id),max(a.creado_en) FROM asignacion_jornada a WHERE a.jornada_id IN (""" + a.sql() + ")", a.args());
    }
    public Conteo posicionesGps(LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        var a = alcance(desde, hasta, periodo, empresa);
        return contar("SELECT count(*),max(p.recibido_en) FROM posicion_gps p WHERE p.jornada_id IN (" + a.sql() + ")", a.args());
    }
    public Conteo jornadasConGps(LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        var a = alcance(desde, hasta, periodo, empresa);
        return contar("SELECT count(DISTINCT p.jornada_id),max(p.recibido_en) FROM posicion_gps p WHERE p.jornada_id IN (" + a.sql() + ")", a.args());
    }
    public Conteo incidencias(LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        var a = alcance(desde, hasta, periodo, empresa);
        return contar("SELECT count(*),max(i.registrada_en) FROM incidencia_operativa i WHERE i.jornada_id IN (" + a.sql() + ")", a.args());
    }
    public Conteo inspecciones(LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        var a = alcance(desde, hasta, periodo, empresa);
        return contar("SELECT count(*),max(n.registrada_en) FROM inspeccion n WHERE n.jornada_id IN (" + a.sql() + ")", a.args());
    }
    public Conteo reclamosVinculados(LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        var a = alcance(desde, hasta, periodo, empresa);
        return contar("SELECT count(*),max(r.registrado_en) FROM reclamo_institucional r WHERE r.jornada_id IN (" + a.sql() + ")", a.args());
    }
    /** Reclamos recibidos (fecha de recepción declarada, America/La_Paz) en el rango, con o sin jornada. No admite filtro de empresa. */
    public Conteo reclamosRecibidos(LocalDate desde, LocalDate hasta) {
        return contar("""
            SELECT count(*),max(r.registrado_en) FROM reclamo_institucional r
             WHERE (r.recibido_en AT TIME ZONE 'America/La_Paz')::date BETWEEN ? AND ?""",
            java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta));
    }
    public boolean periodoExiste(UUID periodo, UUID empresa) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM periodo_servicio WHERE id=? AND (?::uuid IS NULL OR empresa_operadora_id=?))",
            Boolean.class, periodo, empresa, empresa));
    }
    public boolean empresaExiste(UUID empresa) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM empresa_operadora WHERE id=?)", Boolean.class, empresa));
    }
}
