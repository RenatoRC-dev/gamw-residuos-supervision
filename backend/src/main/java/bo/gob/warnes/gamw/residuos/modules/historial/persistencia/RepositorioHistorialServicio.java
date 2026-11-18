package bo.gob.warnes.gamw.residuos.modules.historial.persistencia;

import bo.gob.warnes.gamw.residuos.modules.historial.aplicacion.ConsultarHistorialServicio.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** CU-29: lecturas puras de valores históricos tal como fueron registrados. Sin escrituras ni sustitución por datos vigentes. */
@Repository
public class RepositorioHistorialServicio {
    private static final String BASE = """
        SELECT j.id,j.inicio_programado,j.fin_programado,j.jornada_antecedente_id,j.motivo_reprogramacion,j.vigente,
               ps.id AS periodo_id,ps.fecha_inicio,ps.fecha_fin,e.id AS empresa_id,e.razon_social,
               r.id AS ruta_id,r.codigo,r.nombre AS ruta_nombre,rv.id AS version_id,rv.numero_version,rv.vigente_desde,rv.vigente_hasta,
               (SELECT s.id FROM jornada s WHERE s.jornada_antecedente_id=j.id ORDER BY s.creado_en LIMIT 1) AS sucesora_id,
               (SELECT count(*) FROM asignacion_jornada a WHERE a.jornada_id=j.id) AS cantidad_asignaciones
        FROM jornada j JOIN periodo_servicio ps ON ps.id=j.periodo_servicio_id
        JOIN empresa_operadora e ON e.id=ps.empresa_operadora_id
        JOIN ruta_version rv ON rv.id=j.ruta_version_id JOIN ruta r ON r.id=rv.ruta_id
        """;
    private final JdbcTemplate jdbc;
    public RepositorioHistorialServicio(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final Set<String> TABLAS_EVIDENCIA = Set.of("posicion_gps","inspeccion","fiscalizacion","incidencia_operativa","accion_correctiva","reclamo_institucional");
    /** Solo cuenta registros que referencian la jornada; la tabla procede de una lista cerrada. */
    public int contarReferencias(String tabla,UUID jornada) {
        if (!TABLAS_EVIDENCIA.contains(tabla)) throw new IllegalArgumentException("Tabla no admitida.");
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla + " WHERE jornada_id=?",Integer.class,jornada);
    }
    /**
     * Reclamos distintos vinculados a la jornada: directos (reclamo_institucional.jornada_id) o por relacion_reclamo hacia la propia
     * jornada o hacia sus incidencias, acciones correctivas e inspecciones. UNION evita duplicados. Solo cuenta; no expone datos del reclamo.
     */
    public int contarReclamos(UUID jornada) {
        return jdbc.queryForObject("""
            SELECT count(*) FROM (
              SELECT id FROM reclamo_institucional WHERE jornada_id=?
              UNION SELECT reclamo_id FROM relacion_reclamo WHERE jornada_id=?
              UNION SELECT rr.reclamo_id FROM relacion_reclamo rr JOIN incidencia_operativa i ON i.id=rr.incidencia_id WHERE i.jornada_id=?
              UNION SELECT rr.reclamo_id FROM relacion_reclamo rr JOIN accion_correctiva a ON a.id=rr.accion_correctiva_id WHERE a.jornada_id=?
              UNION SELECT rr.reclamo_id FROM relacion_reclamo rr JOIN inspeccion n ON n.id=rr.inspeccion_id WHERE n.jornada_id=?
            ) r
            """,Integer.class,jornada,jornada,jornada,jornada,jornada);
    }
    public List<ResumenJornada> buscar(UUID jornada,UUID periodo,UUID empresa,UUID ruta,LocalDate desde,LocalDate hasta,int limite,int desplazamiento) {
        var sql = new StringBuilder(BASE).append(" WHERE true");
        var args = new ArrayList<Object>();
        if (jornada != null) { sql.append(" AND j.id=?"); args.add(jornada); }
        if (periodo != null) { sql.append(" AND ps.id=?"); args.add(periodo); }
        if (empresa != null) { sql.append(" AND e.id=?"); args.add(empresa); }
        if (ruta != null) { sql.append(" AND r.id=?"); args.add(ruta); }
        // Fecha de servicio en la zona operativa del GAMW, no en la zona del servidor.
        if (desde != null) { sql.append(" AND (j.inicio_programado AT TIME ZONE 'America/La_Paz')::date >= ?"); args.add(java.sql.Date.valueOf(desde)); }
        if (hasta != null) { sql.append(" AND (j.inicio_programado AT TIME ZONE 'America/La_Paz')::date <= ?"); args.add(java.sql.Date.valueOf(hasta)); }
        sql.append(" ORDER BY j.inicio_programado DESC,j.id LIMIT ? OFFSET ?"); args.add(limite); args.add(desplazamiento);
        return jdbc.query(sql.toString(),this::resumen,args.toArray());
    }
    public Optional<ResumenJornada> jornada(UUID id) {
        return jdbc.query(BASE + " WHERE j.id=?",this::resumen,id).stream().findFirst();
    }
    /** Cadena completa: todos los antecedentes y sucesoras de la jornada, ordenadas por creación. */
    public List<ResumenJornada> cadena(UUID id) {
        return jdbc.query("""
            WITH RECURSIVE ant(id,padre) AS (
              SELECT id,jornada_antecedente_id FROM jornada WHERE id=?
              UNION SELECT p.id,p.jornada_antecedente_id FROM jornada p JOIN ant ON p.id=ant.padre),
            suc(id) AS (
              SELECT id FROM jornada WHERE id=?
              UNION SELECT s.id FROM jornada s JOIN suc ON s.jornada_antecedente_id=suc.id)
            """ + BASE + " WHERE j.id IN (SELECT id FROM ant UNION SELECT id FROM suc) ORDER BY j.creado_en,j.id",this::resumen,id,id);
    }
    public List<AsignacionHistorica> asignaciones(UUID jornada) {
        return jdbc.query("""
            SELECT a.id,a.vigente_desde,a.vigente_hasta,a.motivo_cambio,a.vinculo_vehiculo_empresa_id,a.vinculo_persona_empresa_id,
                   ve.placa,p.nombres,p.apellido_paterno,p.apellido_materno,vv.empresa_operadora_id AS empresa_vehiculo,vp.empresa_operadora_id AS empresa_persona
            FROM asignacion_jornada a
            JOIN vinculo_vehiculo_empresa vv ON vv.id=a.vinculo_vehiculo_empresa_id JOIN vehiculo ve ON ve.id=vv.vehiculo_id
            JOIN vinculo_persona_empresa vp ON vp.id=a.vinculo_persona_empresa_id JOIN persona_natural p ON p.id=vp.persona_id
            WHERE a.jornada_id=? ORDER BY a.vigente_desde,a.creado_en,a.id
            """,(r,i) -> new AsignacionHistorica(r.getObject("id",UUID.class),instante(r,"vigente_desde"),instante(r,"vigente_hasta"),r.getString("motivo_cambio"),
                r.getObject("vinculo_vehiculo_empresa_id",UUID.class),r.getObject("vinculo_persona_empresa_id",UUID.class),r.getString("placa"),
                String.join(" ",List.of(r.getString("nombres"),Objects.toString(r.getString("apellido_paterno"),""),Objects.toString(r.getString("apellido_materno"),""))).replaceAll("\\s+"," ").strip(),
                r.getObject("empresa_vehiculo",UUID.class),r.getObject("empresa_persona",UUID.class)),jornada);
    }
    private ResumenJornada resumen(ResultSet r,int i) throws SQLException {
        return new ResumenJornada(r.getObject("id",UUID.class),instante(r,"inicio_programado"),instante(r,"fin_programado"),r.getBoolean("vigente"),
            r.getObject("jornada_antecedente_id",UUID.class),r.getObject("sucesora_id",UUID.class),r.getString("motivo_reprogramacion"),
            new Periodo(r.getObject("periodo_id",UUID.class),r.getObject("fecha_inicio",LocalDate.class),r.getObject("fecha_fin",LocalDate.class)),
            new Empresa(r.getObject("empresa_id",UUID.class),r.getString("razon_social")),
            new RutaVersionHistorica(r.getObject("ruta_id",UUID.class),r.getString("codigo"),r.getString("ruta_nombre"),r.getObject("version_id",UUID.class),
                r.getInt("numero_version"),instante(r,"vigente_desde"),instante(r,"vigente_hasta")),
            r.getInt("cantidad_asignaciones"));
    }
    private static Instant instante(ResultSet r,String columna) throws SQLException {
        Timestamp t = r.getTimestamp(columna); return t == null ? null : t.toInstant();
    }
}
