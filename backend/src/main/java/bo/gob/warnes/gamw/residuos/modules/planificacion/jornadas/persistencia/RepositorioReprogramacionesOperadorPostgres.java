package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.persistencia;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.RepositorioReprogramacionesOperador;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioReprogramacionesOperadorPostgres implements RepositorioReprogramacionesOperador {
    private final JdbcTemplate jdbc;
    public RepositorioReprogramacionesOperadorPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<UUID> listar(UUID usuario,int limite,int desde) {
        return jdbc.queryForList("""
            SELECT j.id FROM jornada j WHERE EXISTS(SELECT 1 FROM concesion_periodo_operador c JOIN usuario u ON u.id=c.usuario_id
             WHERE u.id=? AND u.activo AND c.periodo_servicio_id=j.periodo_servicio_id AND c.permiso IN ('JORNADA_REPROGRAMAR','PLANIFICACION_CONSULTAR','JORNADA_PROGRAMAR','JORNADA_ASIGNAR')
              AND c.revocado_en IS NULL AND (c.incluye_historico OR (j.inicio_programado>=c.otorgado_en AND j.creado_en>=c.otorgado_en)))
            ORDER BY j.inicio_programado DESC,j.id LIMIT ? OFFSET ?
            """,UUID.class,usuario,limite,desde);
    }
    public Optional<UUID> sucesora(UUID jornada) { return jdbc.queryForList("SELECT id FROM jornada WHERE jornada_antecedente_id=?",UUID.class,jornada).stream().findFirst(); }
    public boolean tieneEvidencia(UUID jornada) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM evento_ejecucion_jornada WHERE jornada_id=?)
             OR EXISTS(SELECT 1 FROM posicion_gps WHERE jornada_id=?) OR EXISTS(SELECT 1 FROM fiscalizacion WHERE jornada_id=?)
            """,Boolean.class,jornada,jornada,jornada));
    }
    public Optional<Actuacion> solicitud(UUID id) {
        return jdbc.query("SELECT usuario_id,jornada_antecedente_id,jornada_sucesora_id,huella_solicitud FROM reprogramacion_autorizada WHERE solicitud_id=?",
            (r,n)->new Actuacion(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4)),id).stream().findFirst();
    }
    public void registrar(UUID solicitud,UUID anterior,UUID siguiente,UUID usuario,UUID concesion,String huella) {
        jdbc.update("INSERT INTO reprogramacion_autorizada(solicitud_id,jornada_antecedente_id,jornada_sucesora_id,usuario_id,concesion_periodo_id,huella_solicitud) VALUES (?,?,?,?,?,?)",
            solicitud,anterior,siguiente,usuario,concesion,huella);
    }
    public List<Periodo> periodos(UUID actor) { return jdbc.query("""
        SELECT p.id,p.empresa_operadora_id,e.razon_social,p.fecha_inicio,p.fecha_fin,bool_or(c.permiso='JORNADA_PROGRAMAR') AS programa
        FROM periodo_servicio p JOIN empresa_operadora e ON e.id=p.empresa_operadora_id
        JOIN concesion_periodo_operador c ON c.periodo_servicio_id=p.id JOIN usuario u ON u.id=c.usuario_id
        WHERE u.id=? AND u.activo AND c.revocado_en IS NULL AND c.permiso IN ('JORNADA_REPROGRAMAR','PLANIFICACION_CONSULTAR','JORNADA_PROGRAMAR','JORNADA_ASIGNAR')
        GROUP BY p.id,e.razon_social ORDER BY p.fecha_inicio,p.id
        """,(r,n)->new Periodo(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getObject(4,java.time.LocalDate.class),r.getObject(5,java.time.LocalDate.class),r.getBoolean(6)),actor); }
    public boolean bloquearPeriodo(UUID periodo) { return !jdbc.queryForList("SELECT id FROM periodo_servicio WHERE id=? FOR UPDATE",UUID.class,periodo).isEmpty(); }
    public Recursos recursos(UUID jornada,java.time.LocalDate fecha) {
        var vehiculos=jdbc.query("""
            SELECT v.id,x.id,x.placa FROM vinculo_vehiculo_empresa v JOIN vehiculo x ON x.id=v.vehiculo_id
             JOIN periodo_servicio p ON p.empresa_operadora_id=v.empresa_operadora_id JOIN jornada j ON j.periodo_servicio_id=p.id
            WHERE j.id=? AND v.activo AND x.activo AND v.fecha_inicio<=? AND (v.fecha_fin IS NULL OR v.fecha_fin>=?) ORDER BY x.placa,v.id
            """,(r,n)->new Candidato(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3)),jornada,fecha,fecha);
        var personas=jdbc.query("""
            SELECT v.id,x.id,concat_ws(' ',x.nombres,x.apellido_paterno,x.apellido_materno) FROM vinculo_persona_empresa v JOIN persona_natural x ON x.id=v.persona_id
             JOIN periodo_servicio p ON p.empresa_operadora_id=v.empresa_operadora_id JOIN jornada j ON j.periodo_servicio_id=p.id
            WHERE j.id=? AND v.activo AND x.activo AND v.fecha_inicio<=? AND (v.fecha_fin IS NULL OR v.fecha_fin>=?) ORDER BY x.nombres,v.id
            """,(r,n)->new Candidato(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3)),jornada,fecha,fecha);
        return new Recursos(vehiculos,personas);
    }
    public List<VersionOpcion> versionesProgramables() { return jdbc.query("SELECT v.id,v.ruta_id,r.nombre,v.numero_version,v.vigente_desde,v.vigente_hasta FROM ruta_version v JOIN ruta r ON r.id=v.ruta_id WHERE r.activo ORDER BY r.nombre,v.numero_version",
        (r,n)->new VersionOpcion(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getInt(4),r.getTimestamp(5).toInstant(),r.getTimestamp(6)==null?null:r.getTimestamp(6).toInstant())); }
    public Optional<RegistroPlan> operacion(UUID solicitud) { return jdbc.query("SELECT usuario_id,jornada_id,asignacion_id,operacion,huella_solicitud FROM actuacion_planificacion_operador WHERE solicitud_id=?",
        (r,n)->new RegistroPlan(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4),r.getString(5)),solicitud).stream().findFirst(); }
    public void registrarOperacion(UUID solicitud,UUID jornada,UUID asignacion,UUID actor,UUID concesion,String operacion,String huella) {
        jdbc.update("INSERT INTO actuacion_planificacion_operador(solicitud_id,jornada_id,asignacion_id,usuario_id,concesion_periodo_id,operacion,huella_solicitud) VALUES (?,?,?,?,?,?,?)",solicitud,jornada,asignacion,actor,concesion,operacion,huella);
    }
}
