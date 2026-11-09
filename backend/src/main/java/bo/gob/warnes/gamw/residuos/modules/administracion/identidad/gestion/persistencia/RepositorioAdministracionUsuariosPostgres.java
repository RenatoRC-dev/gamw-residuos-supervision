package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.persistencia;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.dominio.RepositorioAdministracionUsuarios;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioAdministracionUsuariosPostgres implements RepositorioAdministracionUsuarios {
    private final JdbcTemplate jdbc;
    public RepositorioAdministracionUsuariosPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static UUID id(ResultSet r,String c) throws SQLException { return r.getObject(c,UUID.class); }
    private static Instant fecha(ResultSet r,String c) throws SQLException { var t=r.getTimestamp(c);return t==null?null:t.toInstant(); }
    private static final String CUENTA="""
        SELECT u.id,u.persona_natural_id,u.nombre_usuario,u.activo,u.creado_en,u.actualizado_en,p.nombres,p.apellido_paterno,p.apellido_materno,
         (EXISTS(SELECT 1 FROM concesion_permiso c WHERE c.usuario_id=u.id AND c.revocado_en IS NULL
           AND c.permiso IN ('USUARIOS_CONSULTAR','USUARIOS_GESTIONAR','PERMISOS_ADMINISTRAR'))
          OR EXISTS(SELECT 1 FROM facultad_delegacion f WHERE f.administrador_id=u.id AND f.revocado_en IS NULL)) AS protegida
        FROM usuario u JOIN persona_natural p ON p.id=u.persona_natural_id
        """;
    private Cuenta leer(ResultSet r,int n) throws SQLException { return new Cuenta(id(r,"id"),id(r,"persona_natural_id"),r.getString("nombre_usuario"),r.getBoolean("activo"),
        r.getString("nombres"),r.getString("apellido_paterno"),r.getString("apellido_materno"),fecha(r,"creado_en"),fecha(r,"actualizado_en"),r.getBoolean("protegida")); }
    public List<Cuenta> listar(int limite,int desde) { return jdbc.query(CUENTA+" ORDER BY u.nombre_usuario,u.id LIMIT ? OFFSET ?",(r,n)->leer(r,n),limite,desde); }
    public Optional<Cuenta> cuenta(UUID usuario,boolean bloquear) { return jdbc.query(CUENTA+" WHERE u.id=?"+(bloquear?" FOR UPDATE OF u":""),(r,n)->leer(r,n),usuario).stream().findFirst(); }
    public List<Persona> personas(String ci) { return jdbc.query("SELECT id,ci,nombres,apellido_paterno,apellido_materno,activo FROM persona_natural WHERE ci=? ORDER BY id LIMIT 20",
        (r,n)->new Persona(id(r,"id"),r.getString("ci"),r.getString("nombres"),r.getString("apellido_paterno"),r.getString("apellido_materno"),r.getBoolean("activo")),ci); }
    public boolean personaValida(UUID persona) { return !jdbc.queryForList("SELECT id FROM persona_natural WHERE id=? AND activo FOR SHARE",UUID.class,persona).isEmpty(); }
    public void crear(UUID usuario,UUID persona,String nombre,String hash) { jdbc.update("INSERT INTO usuario(id,persona_natural_id,nombre_usuario,contrasena_hash,activo,creado_en,actualizado_en) VALUES (?,?,?,?,true,clock_timestamp(),clock_timestamp())",usuario,persona,nombre,hash); }
    public void renombrar(UUID usuario,String nombre) { jdbc.update("UPDATE usuario SET nombre_usuario=?,actualizado_en=clock_timestamp() WHERE id=?",nombre,usuario); }
    public void desactivar(UUID usuario) { jdbc.update("UPDATE usuario SET activo=false,actualizado_en=clock_timestamp() WHERE id=?",usuario); }
    private static final String CONCESIONES="""
        SELECT id,usuario_id,permiso,NULL::uuid AS periodo_servicio_id,false AS incluye_historico,otorgado_en,revocado_en FROM concesion_permiso
        UNION ALL SELECT id,usuario_id,permiso,periodo_servicio_id,incluye_historico,otorgado_en,revocado_en FROM concesion_periodo_operador
        """;
    private Concesion leerConcesion(ResultSet r,int n) throws SQLException { return new Concesion(id(r,"id"),r.getString("permiso"),id(r,"periodo_servicio_id"),r.getBoolean("incluye_historico"),fecha(r,"otorgado_en"),fecha(r,"revocado_en")); }
    public List<Concesion> concesiones(UUID usuario,int limite,int desde) { return jdbc.query("SELECT * FROM ("+CONCESIONES+") c WHERE usuario_id=? ORDER BY otorgado_en DESC,id LIMIT ? OFFSET ?",(r,n)->leerConcesion(r,n),usuario,limite,desde); }
    public Optional<Concesion> concesion(UUID usuario,UUID concesion,boolean periodo) {
        return jdbc.query("SELECT * FROM ("+CONCESIONES+") c WHERE usuario_id=? AND id=? AND (periodo_servicio_id IS NOT NULL)=?",(r,n)->leerConcesion(r,n),usuario,concesion,periodo).stream().findFirst();
    }
    public List<Facultad> facultades(UUID actor,boolean bloquear) { return jdbc.query("""
        SELECT f.id,f.permiso,f.periodo_servicio_id,f.permite_historico,e.razon_social,p.fecha_inicio,p.fecha_fin FROM facultad_delegacion f
        LEFT JOIN periodo_servicio p ON p.id=f.periodo_servicio_id LEFT JOIN empresa_operadora e ON e.id=p.empresa_operadora_id
        WHERE f.administrador_id=? AND f.revocado_en IS NULL ORDER BY f.permiso,f.id
        """+(bloquear?" FOR SHARE OF f":""),(r,n)->new Facultad(id(r,"id"),r.getString("permiso"),id(r,"periodo_servicio_id"),r.getString("razon_social"),r.getString("fecha_inicio"),r.getString("fecha_fin"),r.getBoolean("permite_historico")),actor); }
    public UUID otorgar(UUID usuario,String permiso,UUID periodo,boolean historico,String motivo,UUID actor) {
        UUID nueva=UUID.randomUUID();String origen="HTTP_USUARIO:"+actor;
        if(periodo==null) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW',?,?)",nueva,usuario,permiso,origen,motivo);
        else jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,?,?,?)",nueva,usuario,periodo,permiso,historico,origen,motivo);
        return nueva;
    }
    public boolean revocar(Concesion c,UUID actor,String motivo) {
        String tabla=c.periodoServicioId()==null?"concesion_permiso":"concesion_periodo_operador";
        return jdbc.update("UPDATE "+tabla+" SET revocado_en=clock_timestamp(),revocado_por_local=?,motivo_revocacion=? WHERE id=? AND revocado_en IS NULL","HTTP_USUARIO:"+actor,motivo,c.id())==1;
    }
    public void auditar(UUID actor,UUID objetivo,String tipo,String motivo,String anterior,String posterior) { jdbc.update("INSERT INTO actuacion_administrativa(id,actor_usuario_id,usuario_objetivo_id,tipo,motivo,anterior,posterior) VALUES (?,?,?,?,?,?::jsonb,?::jsonb)",UUID.randomUUID(),actor,objetivo,tipo,motivo,anterior,posterior); }
    public List<Actuacion> actuaciones(UUID usuario,int limite,int desde) { return jdbc.query("SELECT id,actor_usuario_id,tipo,motivo,registrada_en FROM actuacion_administrativa WHERE usuario_objetivo_id=? ORDER BY registrada_en DESC,id LIMIT ? OFFSET ?",
        (r,n)->new Actuacion(id(r,"id"),id(r,"actor_usuario_id"),r.getString("tipo"),r.getString("motivo"),fecha(r,"registrada_en")),usuario,limite,desde); }
}
