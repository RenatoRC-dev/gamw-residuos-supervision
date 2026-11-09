package bo.gob.warnes.gamw.residuos.modules.correctivas.persistencia;

import bo.gob.warnes.gamw.residuos.modules.correctivas.dominio.RepositorioActuaciones;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioActuacionesPostgres implements RepositorioActuaciones {
    private final JdbcTemplate jdbc;
    public RepositorioActuacionesPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static UUID uuid(ResultSet r,String campo) throws SQLException { return r.getObject(campo,UUID.class); }
    public Optional<Solicitud> solicitud(Registro tipo,UUID solicitud) {
        String tabla=switch(tipo) { case INCIDENCIA -> "incidencia_operativa"; case ACCION -> "accion_correctiva"; case VERIFICACION -> "verificacion_correctiva"; case RESPUESTA -> "respuesta_correctiva"; };
        return jdbc.query("SELECT id,usuario_id,huella_solicitud FROM "+tabla+" WHERE solicitud_id=?",
            (r,n)->new Solicitud(uuid(r,"id"),uuid(r,"usuario_id"),r.getString("huella_solicitud")),solicitud).stream().findFirst();
    }
    public boolean bloqueo(UUID jornada) { return !jdbc.queryForList("SELECT id FROM jornada WHERE id=? FOR UPDATE",UUID.class,jornada).isEmpty(); }
    public boolean enEjecucion(UUID jornada,Instant ocurrida) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM evento_ejecucion_jornada WHERE jornada_id=? AND tipo_evento='INICIO' AND capturado_en<=?)
              AND NOT EXISTS(SELECT 1 FROM evento_ejecucion_jornada WHERE jornada_id=? AND tipo_evento='FIN')
            """,Boolean.class,jornada,Timestamp.from(ocurrida),jornada));
    }
    private Incidencia incidencia(ResultSet r,int n) throws SQLException {
        return new Incidencia(uuid(r,"id"),uuid(r,"solicitud_id"),uuid(r,"jornada_id"),uuid(r,"asignacion_jornada_id"),uuid(r,"usuario_id"),
            r.getTimestamp("ocurrida_en").toInstant(),r.getTimestamp("registrada_en").toInstant(),r.getString("descripcion"),r.getString("evidencia_declarada"),r.getString("lugar_descripcion"));
    }
    private Accion accion(ResultSet r,int n) throws SQLException {
        return new Accion(uuid(r,"id"),uuid(r,"solicitud_id"),uuid(r,"jornada_id"),uuid(r,"usuario_id"),uuid(r,"incidencia_id"),uuid(r,"inspeccion_id"),
            uuid(r,"fiscalizacion_id"),r.getString("descripcion"),r.getTimestamp("creada_en").toInstant(),r.getString("antecedentes"));
    }
    public Optional<Incidencia> incidencia(UUID id) { return jdbc.query("SELECT * FROM incidencia_operativa WHERE id=?",(ResultSet r,int n)->incidencia(r,n),id).stream().findFirst(); }
    public List<Incidencia> incidencias(UUID jornada) { return jdbc.query("SELECT * FROM incidencia_operativa WHERE jornada_id=? ORDER BY ocurrida_en,id",(ResultSet r,int n)->incidencia(r,n),jornada); }
    public Optional<Accion> accion(UUID id) { return jdbc.query("SELECT * FROM accion_correctiva WHERE id=?",(ResultSet r,int n)->accion(r,n),id).stream().findFirst(); }
    public List<Accion> acciones(UUID jornada) { return jdbc.query("SELECT * FROM accion_correctiva WHERE jornada_id=? ORDER BY creada_en,id",(ResultSet r,int n)->accion(r,n),jornada); }
    public List<Respuesta> respuestas(UUID accion) {
        var referencias=new HashMap<UUID,List<UUID>>();
        jdbc.query("SELECT re.respuesta_id,re.evidencia_id FROM respuesta_correctiva_evidencia re JOIN respuesta_correctiva r ON r.id=re.respuesta_id WHERE r.accion_id=? ORDER BY re.evidencia_id",
            (org.springframework.jdbc.core.RowCallbackHandler) r->referencias.computeIfAbsent(uuid(r,"respuesta_id"),k->new ArrayList<>()).add(uuid(r,"evidencia_id")),accion);
        return jdbc.query("SELECT * FROM respuesta_correctiva WHERE accion_id=? ORDER BY registrada_en,id",(r,n)->new Respuesta(uuid(r,"id"),uuid(r,"accion_id"),
            uuid(r,"usuario_id"),r.getTimestamp("comunicada_en").toInstant(),r.getTimestamp("registrada_en").toInstant(),r.getString("descripcion"),r.getString("evidencia_declarada"),
            referencias.getOrDefault(uuid(r,"id"),List.of())),accion);
    }
    public List<Verificacion> verificaciones(UUID accion) {
        return jdbc.query("""
            SELECT v.*,c.nombre,c.referencia_aprobacion FROM verificacion_correctiva v
            JOIN catalogo_verificacion_correctiva c ON c.id=v.resultado_id WHERE v.accion_id=? ORDER BY v.registrada_en,v.id
            """,(r,n)->new Verificacion(uuid(r,"id"),uuid(r,"solicitud_id"),uuid(r,"accion_id"),uuid(r,"respuesta_id"),uuid(r,"usuario_id"),uuid(r,"resultado_id"),
                r.getString("nombre"),r.getString("referencia_aprobacion"),r.getString("observaciones"),uuid(r,"inspeccion_id"),r.getTimestamp("registrada_en").toInstant()),accion);
    }
    private Categoria categoria(ResultSet r,int n) throws SQLException {
        return new Categoria(uuid(r,"id"),r.getString("codigo"),r.getString("nombre"),r.getString("referencia_aprobacion"),r.getBoolean("observaciones_obligatorias"));
    }
    public List<Categoria> categorias() { return jdbc.query("SELECT * FROM catalogo_verificacion_correctiva WHERE habilitado ORDER BY codigo",(ResultSet r,int n)->categoria(r,n)); }
    public Optional<Categoria> categoria(UUID id) { return jdbc.query("SELECT * FROM catalogo_verificacion_correctiva WHERE id=? AND habilitado FOR SHARE",(ResultSet r,int n)->categoria(r,n),id).stream().findFirst(); }
    public void insertar(Incidencia e,String huella) {
        jdbc.update("""
            INSERT INTO incidencia_operativa(id,solicitud_id,huella_solicitud,jornada_id,asignacion_jornada_id,usuario_id,ocurrida_en,descripcion,evidencia_declarada,lugar_descripcion)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,e.id(),e.solicitudId(),huella,e.jornadaId(),e.asignacionJornadaId(),e.usuarioId(),Timestamp.from(e.ocurridaEn()),e.descripcion(),e.evidenciaDeclarada(),e.lugarDescripcion());
    }
    public void insertar(Accion a,String huella) {
        jdbc.update("""
            INSERT INTO accion_correctiva(id,solicitud_id,huella_solicitud,jornada_id,usuario_id,incidencia_id,inspeccion_id,fiscalizacion_id,descripcion,antecedentes)
            VALUES (?,?,?,?,?,?,?,?,?,?::jsonb)
            """,a.id(),a.solicitudId(),huella,a.jornadaId(),a.usuarioId(),a.incidenciaId(),a.inspeccionId(),a.fiscalizacionId(),a.descripcion(),a.antecedentes());
    }
    public void verificar(UUID id,UUID solicitud,UUID accion,UUID jornada,UUID respuesta,UUID actor,UUID resultado,String observaciones,UUID inspeccion,String huella) {
        jdbc.update("""
            INSERT INTO verificacion_correctiva(id,solicitud_id,huella_solicitud,accion_id,jornada_id,respuesta_id,usuario_id,resultado_id,observaciones,inspeccion_id)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """,id,solicitud,huella,accion,jornada,respuesta,actor,resultado,observaciones,inspeccion);
    }
    public List<ResumenEmpresa> accionesEmpresa(UUID usuario,int limite,int desde) {
        return jdbc.query("""
            SELECT a.id,a.jornada_id,j.periodo_servicio_id,ps.empresa_operadora_id,e.razon_social,j.inicio_programado,j.ruta_version_id,a.descripcion,a.creada_en
            FROM accion_correctiva a JOIN jornada j ON j.id=a.jornada_id
            JOIN periodo_servicio ps ON ps.id=j.periodo_servicio_id JOIN empresa_operadora e ON e.id=ps.empresa_operadora_id
            WHERE EXISTS(SELECT 1 FROM concesion_periodo_operador c JOIN usuario u ON u.id=c.usuario_id
              WHERE u.id=? AND u.activo AND c.periodo_servicio_id=j.periodo_servicio_id AND c.permiso='CORRECTIVA_CONSULTAR'
                AND c.revocado_en IS NULL AND (c.incluye_historico OR (j.inicio_programado>=c.otorgado_en AND a.creada_en>=c.otorgado_en)))
            ORDER BY a.creada_en DESC,a.id LIMIT ? OFFSET ?
            """,(r,n)->new ResumenEmpresa(uuid(r,"id"),uuid(r,"jornada_id"),uuid(r,"periodo_servicio_id"),uuid(r,"empresa_operadora_id"),
                r.getString("razon_social"),r.getTimestamp("inicio_programado").toInstant(),uuid(r,"ruta_version_id"),r.getString("descripcion"),r.getTimestamp("creada_en").toInstant()),usuario,limite,desde);
    }
    public List<UUID> evidenciasDisponibles(UUID accion) {
        // Solo soporte de la inspección origen o de inspecciones referenciadas por la fiscalización origen.
        return jdbc.queryForList("""
            SELECT e.id FROM evidencia_inspeccion e JOIN accion_correctiva a ON a.id=?
             JOIN inspeccion i ON i.id=e.inspeccion_id AND i.jornada_id=a.jornada_id
            WHERE i.id=a.inspeccion_id OR EXISTS(SELECT 1 FROM fiscalizacion_inspeccion fi
              WHERE fi.fiscalizacion_id=a.fiscalizacion_id AND fi.inspeccion_id=i.id AND fi.jornada_id=a.jornada_id)
            ORDER BY e.id
            """,UUID.class,accion);
    }
    public void responder(UUID id,UUID solicitud,UUID accion,UUID actor,UUID concesion,Instant comunicada,String descripcion,String evidencia,String huella,List<UUID> evidencias) {
        jdbc.update("""
            INSERT INTO respuesta_correctiva(id,solicitud_id,huella_solicitud,accion_id,usuario_id,concesion_periodo_id,comunicada_en,descripcion,evidencia_declarada)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,id,solicitud,huella,accion,actor,concesion,Timestamp.from(comunicada),descripcion,evidencia);
        for(UUID archivo:evidencias) jdbc.update("INSERT INTO respuesta_correctiva_evidencia(respuesta_id,evidencia_id) VALUES (?,?)",id,archivo);
    }
    public List<EvidenciaCompartida> soporteEmpresa(UUID accion) {
        return jdbc.query("""
            SELECT e.id,e.inspeccion_id,e.nombre_original,e.tipo_mime,e.origen,e.descripcion,e.capturada_en,e.incorporada_en,e.sha256,octet_length(e.contenido) AS tamano
            FROM evidencia_inspeccion e JOIN accion_correctiva a ON a.id=? JOIN inspeccion i ON i.id=e.inspeccion_id AND i.jornada_id=a.jornada_id
            WHERE i.id=a.inspeccion_id OR EXISTS(SELECT 1 FROM fiscalizacion_inspeccion fi
              WHERE fi.fiscalizacion_id=a.fiscalizacion_id AND fi.inspeccion_id=i.id AND fi.jornada_id=a.jornada_id)
            ORDER BY e.id
            """,(r,n)->new EvidenciaCompartida(uuid(r,"id"),uuid(r,"inspeccion_id"),r.getString("nombre_original"),r.getString("tipo_mime"),r.getString("origen"),r.getString("descripcion"),
                r.getTimestamp("capturada_en")==null?null:r.getTimestamp("capturada_en").toInstant(),r.getTimestamp("incorporada_en").toInstant(),r.getString("sha256"),r.getInt("tamano")),accion);
    }
}
