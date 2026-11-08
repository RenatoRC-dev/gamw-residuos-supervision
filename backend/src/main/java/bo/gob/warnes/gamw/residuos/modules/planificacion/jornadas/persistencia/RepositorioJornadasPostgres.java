package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.persistencia;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioJornadasPostgres implements RepositorioJornadas {
    private final JdbcTemplate jdbc;
    public RepositorioJornadasPostgres(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void insertar(Jornada j) {
        jdbc.update("""
                INSERT INTO jornada(id,periodo_servicio_id,ruta_version_id,inicio_programado,fin_programado,jornada_antecedente_id,motivo_reprogramacion,vigente,creado_en,actualizado_en)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,j.id(),j.periodoServicioId(),j.rutaVersionId(),Timestamp.from(j.inicioProgramado()),tiempo(j.finProgramado()),j.jornadaAntecedenteId(),
                j.motivoReprogramacion(),j.vigente(),Timestamp.from(j.creadoEn()),Timestamp.from(j.actualizadoEn()));
    }
    public Optional<Jornada> buscar(UUID id,boolean bloquear) {
        return jdbc.query("SELECT * FROM jornada WHERE id=?" + (bloquear ? " FOR UPDATE" : ""),this::leerJornada,id).stream().findFirst();
    }
    public List<Jornada> listar() { return jdbc.query("SELECT * FROM jornada ORDER BY inicio_programado,id",this::leerJornada); }
    public void sustituir(UUID id,Instant actualizadoEn) {
        jdbc.update("UPDATE jornada SET vigente=false,actualizado_en=? WHERE id=? AND vigente=true",Timestamp.from(actualizadoEn),id);
    }
    public void insertarAsignacion(AsignacionJornada a) {
        jdbc.update("""
                INSERT INTO asignacion_jornada(id,jornada_id,vinculo_vehiculo_empresa_id,vinculo_persona_empresa_id,vigente_desde,vigente_hasta,motivo_cambio,creado_en)
                VALUES (?,?,?,?,?,?,?,?)
                """,a.id(),a.jornadaId(),a.vinculoVehiculoEmpresaId(),a.vinculoPersonaEmpresaId(),Timestamp.from(a.vigenteDesde()),tiempo(a.vigenteHasta()),a.motivoCambio(),Timestamp.from(a.creadoEn()));
    }
    public Optional<AsignacionJornada> actual(UUID id) {
        return jdbc.query("SELECT * FROM asignacion_jornada WHERE jornada_id=? AND vigente_hasta IS NULL",this::leerAsignacion,id).stream().findFirst();
    }
    public List<AsignacionJornada> asignaciones(UUID id) {
        return jdbc.query("SELECT * FROM asignacion_jornada WHERE jornada_id=? ORDER BY vigente_desde,creado_en,id",this::leerAsignacion,id);
    }
    public void cerrarAsignacion(UUID id,Instant hasta) {
        jdbc.update("UPDATE asignacion_jornada SET vigente_hasta=? WHERE id=? AND vigente_hasta IS NULL",Timestamp.from(hasta),id);
    }
    private Timestamp tiempo(Instant instante) { return instante == null ? null : Timestamp.from(instante); }
    private Instant instante(ResultSet r,String columna) throws SQLException {
        var t = r.getTimestamp(columna); return t == null ? null : t.toInstant();
    }
    private Jornada leerJornada(ResultSet r,int i) throws SQLException {
        return new Jornada(r.getObject("id",UUID.class),r.getObject("periodo_servicio_id",UUID.class),r.getObject("ruta_version_id",UUID.class),
                instante(r,"inicio_programado"),instante(r,"fin_programado"),r.getObject("jornada_antecedente_id",UUID.class),
                r.getString("motivo_reprogramacion"),r.getBoolean("vigente"),instante(r,"creado_en"),instante(r,"actualizado_en"));
    }
    private AsignacionJornada leerAsignacion(ResultSet r,int i) throws SQLException {
        return new AsignacionJornada(r.getObject("id",UUID.class),r.getObject("jornada_id",UUID.class),r.getObject("vinculo_vehiculo_empresa_id",UUID.class),
                r.getObject("vinculo_persona_empresa_id",UUID.class),instante(r,"vigente_desde"),instante(r,"vigente_hasta"),r.getString("motivo_cambio"),instante(r,"creado_en"));
    }
}
