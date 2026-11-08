package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.persistencia;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioRegistroOperativoPostgres implements RepositorioRegistroOperativo {
    private final JdbcTemplate jdbc;
    public RepositorioRegistroOperativoPostgres(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insertarPersona(PersonaNatural p) {
        jdbc.update("INSERT INTO persona_natural(id,ci,nombres,apellido_paterno,apellido_materno,telefono,activo,creado_en,actualizado_en) VALUES (?,?,?,?,?,?,?,?,?)",
                p.id(),p.ci(),p.nombres(),p.apellidoPaterno(),p.apellidoMaterno(),p.telefono(),p.activo(),Timestamp.from(p.creadoEn()),Timestamp.from(p.actualizadoEn()));
    }
    public void actualizarPersona(PersonaNatural p) {
        jdbc.update("UPDATE persona_natural SET ci=?,nombres=?,apellido_paterno=?,apellido_materno=?,telefono=?,activo=?,actualizado_en=? WHERE id=?",
                p.ci(),p.nombres(),p.apellidoPaterno(),p.apellidoMaterno(),p.telefono(),p.activo(),Timestamp.from(p.actualizadoEn()),p.id());
    }
    public Optional<PersonaNatural> persona(UUID id, boolean bloquear) {
        return jdbc.query("SELECT * FROM persona_natural WHERE id=?" + (bloquear ? " FOR UPDATE" : ""),this::leerPersona,id).stream().findFirst();
    }
    public List<PersonaNatural> personas() { return jdbc.query("SELECT * FROM persona_natural ORDER BY nombres,id",this::leerPersona); }
    public void insertarEmpresa(EmpresaOperadora e) {
        jdbc.update("INSERT INTO empresa_operadora(id,nit,razon_social,nombre_comercial,activo,creado_en,actualizado_en) VALUES (?,?,?,?,?,?,?)",
                e.id(),e.nit(),e.razonSocial(),e.nombreComercial(),e.activo(),Timestamp.from(e.creadoEn()),Timestamp.from(e.actualizadoEn()));
    }
    public void actualizarEmpresa(EmpresaOperadora e) {
        jdbc.update("UPDATE empresa_operadora SET nit=?,razon_social=?,nombre_comercial=?,activo=?,actualizado_en=? WHERE id=?",
                e.nit(),e.razonSocial(),e.nombreComercial(),e.activo(),Timestamp.from(e.actualizadoEn()),e.id());
    }
    public Optional<EmpresaOperadora> empresa(UUID id, boolean bloquear) {
        return jdbc.query("SELECT * FROM empresa_operadora WHERE id=?" + (bloquear ? " FOR UPDATE" : ""),this::leerEmpresa,id).stream().findFirst();
    }
    public List<EmpresaOperadora> empresas() { return jdbc.query("SELECT * FROM empresa_operadora ORDER BY razon_social,id",this::leerEmpresa); }
    public void insertarPeriodo(PeriodoServicio p) {
        jdbc.update("INSERT INTO periodo_servicio(id,empresa_operadora_id,fecha_inicio,fecha_fin,activo,creado_en,actualizado_en) VALUES (?,?,?,?,?,?,?)",
                p.id(),p.empresaOperadoraId(),p.fechaInicio(),p.fechaFin(),p.activo(),Timestamp.from(p.creadoEn()),Timestamp.from(p.actualizadoEn()));
    }
    public void actualizarPeriodo(PeriodoServicio p) {
        jdbc.update("UPDATE periodo_servicio SET fecha_inicio=?,fecha_fin=?,activo=?,actualizado_en=? WHERE id=? AND empresa_operadora_id=?",
                p.fechaInicio(),p.fechaFin(),p.activo(),Timestamp.from(p.actualizadoEn()),p.id(),p.empresaOperadoraId());
    }
    public Optional<PeriodoServicio> periodo(UUID empresaId, UUID id) {
        return jdbc.query("SELECT * FROM periodo_servicio WHERE empresa_operadora_id=? AND id=?",this::leerPeriodo,empresaId,id).stream().findFirst();
    }
    public List<PeriodoServicio> periodos(UUID empresaId) {
        return jdbc.query("SELECT * FROM periodo_servicio WHERE empresa_operadora_id=? ORDER BY fecha_inicio,id",this::leerPeriodo,empresaId);
    }
    public void insertarVinculo(VinculoPersonaEmpresa v) {
        jdbc.update("INSERT INTO vinculo_persona_empresa(id,persona_id,empresa_operadora_id,fecha_inicio,fecha_fin,activo,creado_en,actualizado_en) VALUES (?,?,?,?,?,?,?,?)",
                v.id(),v.personaId(),v.empresaOperadoraId(),v.fechaInicio(),v.fechaFin(),v.activo(),Timestamp.from(v.creadoEn()),Timestamp.from(v.actualizadoEn()));
    }
    public void actualizarVinculo(VinculoPersonaEmpresa v) {
        jdbc.update("UPDATE vinculo_persona_empresa SET fecha_fin=?,activo=?,actualizado_en=? WHERE id=? AND empresa_operadora_id=?",
                v.fechaFin(),v.activo(),Timestamp.from(v.actualizadoEn()),v.id(),v.empresaOperadoraId());
    }
    public Optional<VinculoPersonaEmpresa> vinculo(UUID empresaId, UUID id) {
        return jdbc.query("SELECT * FROM vinculo_persona_empresa WHERE empresa_operadora_id=? AND id=?",this::leerVinculo,empresaId,id).stream().findFirst();
    }
    public List<VinculoPersonaEmpresa> personal(UUID empresaId) {
        return jdbc.query("SELECT * FROM vinculo_persona_empresa WHERE empresa_operadora_id=? ORDER BY fecha_inicio,id",this::leerVinculo,empresaId);
    }
    public List<FuncionOperativa> funciones() { return jdbc.query("SELECT * FROM funcion_operativa ORDER BY codigo",this::leerFuncion); }
    public List<FuncionOperativa> funcionesDelVinculo(UUID vinculoId) {
        return jdbc.query("SELECT f.* FROM funcion_operativa f JOIN vinculo_persona_funcion v ON v.funcion_operativa_id=f.id WHERE v.vinculo_persona_empresa_id=? ORDER BY f.codigo",this::leerFuncion,vinculoId);
    }
    public void insertarFunciones(List<VinculoPersonaFuncion> funciones) {
        for (var funcion : funciones) {
            jdbc.update("INSERT INTO vinculo_persona_funcion(vinculo_persona_empresa_id,funcion_operativa_id) VALUES (?,?)",
                    funcion.vinculoPersonaEmpresaId(),funcion.funcionOperativaId());
        }
    }
    private PersonaNatural leerPersona(ResultSet r, int i) throws SQLException {
        return new PersonaNatural(r.getObject("id",UUID.class),r.getString("ci"),r.getString("nombres"),r.getString("apellido_paterno"),r.getString("apellido_materno"),
                r.getString("telefono"),r.getBoolean("activo"),r.getTimestamp("creado_en").toInstant(),r.getTimestamp("actualizado_en").toInstant());
    }
    private EmpresaOperadora leerEmpresa(ResultSet r, int i) throws SQLException {
        return new EmpresaOperadora(r.getObject("id",UUID.class),r.getString("nit"),r.getString("razon_social"),r.getString("nombre_comercial"),
                r.getBoolean("activo"),r.getTimestamp("creado_en").toInstant(),r.getTimestamp("actualizado_en").toInstant());
    }
    private PeriodoServicio leerPeriodo(ResultSet r, int i) throws SQLException {
        return new PeriodoServicio(r.getObject("id",UUID.class),r.getObject("empresa_operadora_id",UUID.class),r.getDate("fecha_inicio").toLocalDate(),r.getDate("fecha_fin").toLocalDate(),
                r.getBoolean("activo"),r.getTimestamp("creado_en").toInstant(),r.getTimestamp("actualizado_en").toInstant());
    }
    private VinculoPersonaEmpresa leerVinculo(ResultSet r, int i) throws SQLException {
        var fin = r.getDate("fecha_fin");
        return new VinculoPersonaEmpresa(r.getObject("id",UUID.class),r.getObject("persona_id",UUID.class),r.getObject("empresa_operadora_id",UUID.class),r.getDate("fecha_inicio").toLocalDate(),
                fin == null ? null : fin.toLocalDate(),r.getBoolean("activo"),r.getTimestamp("creado_en").toInstant(),r.getTimestamp("actualizado_en").toInstant());
    }
    private FuncionOperativa leerFuncion(ResultSet r, int i) throws SQLException {
        return new FuncionOperativa(r.getObject("id",UUID.class),r.getString("codigo"),r.getString("nombre"),r.getBoolean("activo"));
    }
}
