package bo.gob.warnes.gamw.residuos.modules.reclamos.persistencia;

import bo.gob.warnes.gamw.residuos.modules.reclamos.dominio.RepositorioReclamos;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioReclamosPostgres implements RepositorioReclamos {
    private final JdbcTemplate jdbc;
    public RepositorioReclamosPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static UUID id(ResultSet r,String c) throws SQLException { return r.getObject(c,UUID.class); }
    private static Instant fecha(ResultSet r,String c) throws SQLException { var t=r.getTimestamp(c);return t==null?null:t.toInstant(); }
    private static Timestamp tiempo(Instant t) { return t==null?null:Timestamp.from(t); }
    public Optional<Solicitud> solicitud(Registro tipo,UUID solicitud) {
        String tabla=switch(tipo) { case RECLAMO->"reclamo_institucional";case REVISION->"revision_reclamo";case PUNTO->"punto_critico";case OBSERVACION->"observacion_punto_critico"; };
        return jdbc.query("SELECT id,usuario_id,huella_solicitud FROM "+tabla+" WHERE solicitud_id=?",(r,n)->new Solicitud(id(r,"id"),id(r,"usuario_id"),r.getString("huella_solicitud")),solicitud).stream().findFirst();
    }
    public boolean bloquearReclamo(UUID id) { return !jdbc.queryForList("SELECT id FROM reclamo_institucional WHERE id=? FOR UPDATE",UUID.class,id).isEmpty(); }
    public boolean bloquearPunto(UUID id) { return !jdbc.queryForList("SELECT id FROM punto_critico WHERE id=? FOR UPDATE",UUID.class,id).isEmpty(); }
    public boolean existe(Tipo tipo,UUID id) {
        String tabla=switch(tipo) { case JORNADA->"jornada";case INCIDENCIA->"incidencia_operativa";case ACCION_CORRECTIVA->"accion_correctiva";case INSPECCION->"inspeccion"; };
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "+tabla+" WHERE id=?)",Boolean.class,id));
    }
    public Optional<UUID> jornadaAntecedente(Tipo tipo,UUID id) {
        String tabla=switch(tipo) { case JORNADA->"jornada";case INCIDENCIA->"incidencia_operativa";case ACCION_CORRECTIVA->"accion_correctiva";case INSPECCION->"inspeccion"; };
        return jdbc.queryForList("SELECT "+(tipo==Tipo.JORNADA?"id":"jornada_id")+" FROM "+tabla+" WHERE id=?",UUID.class,id).stream().findFirst();
    }
    private static final String RECLAMO="""
        SELECT r.*,ST_X(r.geometria) AS longitud,ST_Y(r.geometria) AS latitud,
         EXISTS(SELECT 1 FROM revision_reclamo v JOIN catalogo_valoracion_reclamo c ON c.id=v.resultado_id
          WHERE v.reclamo_id=r.id AND c.es_validacion) AS validado FROM reclamo_institucional r
        """;
    private Reclamo leerReclamo(ResultSet r,int n) throws SQLException { return new Reclamo(id(r,"id"),r.getString("canal_declarado"),fecha(r,"recibido_en"),r.getString("descripcion"),
        r.getString("lugar_descripcion"),r.getString("referencias_origen"),r.getObject("longitud",Double.class),r.getObject("latitud",Double.class),id(r,"jornada_id"),id(r,"usuario_id"),fecha(r,"registrado_en"),r.getBoolean("validado")); }
    public List<Reclamo> reclamos(int limite,int desde) { return jdbc.query(RECLAMO+" ORDER BY r.recibido_en DESC,r.id LIMIT ? OFFSET ?",(r,n)->leerReclamo(r,n),limite,desde); }
    public Optional<Reclamo> reclamo(UUID id) { return jdbc.query(RECLAMO+" WHERE r.id=?",(r,n)->leerReclamo(r,n),id).stream().findFirst(); }
    public List<Revision> revisiones(UUID id) { return jdbc.query("""
        SELECT v.*,c.nombre,c.referencia_aprobacion,c.es_validacion FROM revision_reclamo v LEFT JOIN catalogo_valoracion_reclamo c ON c.id=v.resultado_id
        WHERE v.reclamo_id=? ORDER BY v.registrada_en,v.id
        """,(r,n)->new Revision(id(r,"id"),id(r,"usuario_id"),id(r,"resultado_id"),r.getString("nombre"),r.getString("referencia_aprobacion"),r.getObject("es_validacion",Boolean.class),r.getString("observaciones"),fecha(r,"registrada_en")),id); }
    public List<Relacion> relaciones(UUID id) { return jdbc.query("SELECT * FROM relacion_reclamo WHERE reclamo_id=? ORDER BY registrada_en,id",(r,n)->{
        Tipo tipo=id(r,"jornada_id")!=null?Tipo.JORNADA:id(r,"incidencia_id")!=null?Tipo.INCIDENCIA:id(r,"accion_correctiva_id")!=null?Tipo.ACCION_CORRECTIVA:Tipo.INSPECCION;
        String campo=switch(tipo) { case JORNADA->"jornada_id";case INCIDENCIA->"incidencia_id";case ACCION_CORRECTIVA->"accion_correctiva_id";case INSPECCION->"inspeccion_id"; };
        return new Relacion(id(r,"id"),tipo,id(r,campo),r.getString("fundamento"),id(r,"usuario_id"),fecha(r,"registrada_en"));
        },id); }
    private Categoria leerCategoria(ResultSet r,int n) throws SQLException { return new Categoria(id(r,"id"),r.getString("codigo"),r.getString("nombre"),r.getString("referencia_aprobacion"),r.getBoolean("es_validacion")); }
    public List<Categoria> categorias() { return jdbc.query("SELECT * FROM catalogo_valoracion_reclamo WHERE habilitado ORDER BY codigo",(r,n)->leerCategoria(r,n)); }
    public Optional<Categoria> categoria(UUID id) { return jdbc.query("SELECT * FROM catalogo_valoracion_reclamo WHERE id=? AND habilitado FOR SHARE",(r,n)->leerCategoria(r,n),id).stream().findFirst(); }
    public boolean tieneDecision(UUID id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM revision_reclamo WHERE reclamo_id=? AND resultado_id IS NOT NULL)",Boolean.class,id)); }
    public List<Anexo> anexos(UUID id) { return jdbc.query("SELECT id,usuario_id,origen,descripcion,capturado_en,registrado_en,nombre_original,tipo_mime,sha256,referencia_politica,octet_length(contenido) AS tamano FROM anexo_reclamo WHERE reclamo_id=? ORDER BY registrado_en,id",
        (r,n)->new Anexo(id(r,"id"),id(r,"usuario_id"),r.getString("origen"),r.getString("descripcion"),fecha(r,"capturado_en"),fecha(r,"registrado_en"),r.getString("nombre_original"),r.getString("tipo_mime"),r.getString("sha256"),r.getInt("tamano"),r.getString("referencia_politica")),id); }
    public Optional<Archivo> archivo(UUID reclamo,UUID anexo) { return jdbc.query("SELECT nombre_original,tipo_mime,contenido FROM anexo_reclamo WHERE reclamo_id=? AND id=?",(r,n)->new Archivo(r.getString(1),r.getString(2),r.getBytes(3)),reclamo,anexo).stream().findFirst(); }
    public void registrar(UUID id,UUID solicitud,String huella,String canal,Instant recibido,String descripcion,String lugar,String origen,Double lon,Double lat,UUID jornada,UUID actor) {
        jdbc.update("""
            INSERT INTO reclamo_institucional(id,solicitud_id,huella_solicitud,canal_declarado,recibido_en,descripcion,lugar_descripcion,referencias_origen,geometria,jornada_id,usuario_id)
            VALUES (?,?,?,?,?,?,?,?,CASE WHEN ?::double precision IS NULL THEN NULL ELSE ST_SetSRID(ST_MakePoint(?,?),4326) END,?,?)
            """,id,solicitud,huella,canal,tiempo(recibido),descripcion,lugar,origen,lon,lon,lat,jornada,actor);
    }
    public void revisar(UUID id,UUID solicitud,String huella,UUID reclamo,UUID actor,UUID resultado,String observaciones) { jdbc.update("INSERT INTO revision_reclamo(id,solicitud_id,huella_solicitud,reclamo_id,usuario_id,resultado_id,observaciones) VALUES (?,?,?,?,?,?,?)",id,solicitud,huella,reclamo,actor,resultado,observaciones); }
    public void relacionar(UUID id,UUID reclamo,Tipo tipo,UUID referencia,String fundamento,UUID actor) {
        jdbc.update("INSERT INTO relacion_reclamo(id,reclamo_id,jornada_id,incidencia_id,accion_correctiva_id,inspeccion_id,fundamento,usuario_id) VALUES (?,?,?,?,?,?,?,?)",
            id,reclamo,tipo==Tipo.JORNADA?referencia:null,tipo==Tipo.INCIDENCIA?referencia:null,tipo==Tipo.ACCION_CORRECTIVA?referencia:null,tipo==Tipo.INSPECCION?referencia:null,fundamento,actor);
    }
    public void anexar(UUID id,UUID reclamo,UUID actor,String origen,String descripcion,Instant captura,String politica,String nombre,String mime,String sha,byte[] contenido) { jdbc.update("INSERT INTO anexo_reclamo(id,reclamo_id,usuario_id,origen,descripcion,capturado_en,referencia_politica,nombre_original,tipo_mime,sha256,contenido) VALUES (?,?,?,?,?,?,?,?,?,?,?)",id,reclamo,actor,origen,descripcion,tiempo(captura),politica,nombre,mime,sha,contenido); }
    private static final String PUNTO="SELECT p.*,ST_X(p.geometria) AS longitud,ST_Y(p.geometria) AS latitud,EXISTS(SELECT 1 FROM validacion_punto_critico v WHERE v.punto_id=p.id) AS validado FROM punto_critico p";
    private Punto leerPunto(ResultSet r,int n) throws SQLException { return new Punto(id(r,"id"),r.getString("denominacion"),r.getString("descripcion"),r.getDouble("longitud"),r.getDouble("latitud"),r.getString("sustento_municipal"),id(r,"reclamo_id"),id(r,"inspeccion_id"),id(r,"usuario_id"),fecha(r,"registrado_en"),r.getBoolean("validado")); }
    public List<Punto> puntos(int limite,int desde) { return jdbc.query(PUNTO+" ORDER BY p.registrado_en DESC,p.id LIMIT ? OFFSET ?",(r,n)->leerPunto(r,n),limite,desde); }
    public Optional<Punto> punto(UUID id) { return jdbc.query(PUNTO+" WHERE p.id=?",(r,n)->leerPunto(r,n),id).stream().findFirst(); }
    public List<Observacion> observaciones(UUID id) { return jdbc.query("SELECT * FROM observacion_punto_critico WHERE punto_id=? ORDER BY observada_en,id",(r,n)->new Observacion(id(r,"id"),id(r,"usuario_id"),fecha(r,"observada_en"),r.getString("descripcion"),id(r,"inspeccion_id"),id(r,"reclamo_id"),fecha(r,"registrada_en")),id); }
    public Optional<Validacion> validacion(UUID id) { return jdbc.query("SELECT * FROM validacion_punto_critico WHERE punto_id=?",(r,n)->new Validacion(id(r,"usuario_id"),r.getString("fundamento"),r.getString("referencia_politica"),fecha(r,"registrada_en")),id).stream().findFirst(); }
    public void crearPunto(UUID id,UUID solicitud,String huella,String nombre,String descripcion,double lon,double lat,String sustento,UUID reclamo,UUID inspeccion,UUID actor) {
        jdbc.update("INSERT INTO punto_critico(id,solicitud_id,huella_solicitud,denominacion,descripcion,geometria,sustento_municipal,reclamo_id,inspeccion_id,usuario_id) VALUES (?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),?,?,?,?)",id,solicitud,huella,nombre,descripcion,lon,lat,sustento,reclamo,inspeccion,actor);
    }
    public void observar(UUID solicitud,String huella,UUID punto,UUID actor,Instant fecha,String descripcion,UUID inspeccion,UUID reclamo) { jdbc.update("INSERT INTO observacion_punto_critico(id,solicitud_id,huella_solicitud,punto_id,usuario_id,observada_en,descripcion,inspeccion_id,reclamo_id) VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),solicitud,huella,punto,actor,tiempo(fecha),descripcion,inspeccion,reclamo); }
    public void validar(UUID punto,UUID actor,String fundamento,String politica) { jdbc.update("INSERT INTO validacion_punto_critico(punto_id,usuario_id,fundamento,referencia_politica) VALUES (?,?,?,?)",punto,actor,fundamento,politica); }
}
