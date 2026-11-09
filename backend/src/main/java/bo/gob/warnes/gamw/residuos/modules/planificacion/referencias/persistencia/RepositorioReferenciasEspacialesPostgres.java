package bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.persistencia;

import bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.dominio.RepositorioReferenciasEspaciales;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioReferenciasEspacialesPostgres implements RepositorioReferenciasEspaciales {
    private final JdbcTemplate jdbc;
    public RepositorioReferenciasEspacialesPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String LECTURA="SELECT *,ST_AsGeoJSON(geometria,17) AS geojson FROM referencia_espacial_version";
    private Version leer(ResultSet r,int i) throws SQLException {
        Timestamp hasta=r.getTimestamp("vigente_hasta");
        return new Version(r.getObject("id",UUID.class),r.getObject("referencia_id",UUID.class),r.getInt("numero_version"),
          r.getObject("ruta_version_id",UUID.class),r.getString("nombre"),r.getString("tipo"),r.getString("geojson"),
          r.getObject("radio_metros",Double.class),r.getTimestamp("vigente_desde").toInstant(),hasta==null?null:hasta.toInstant(),r.getString("motivo"));
    }
    public List<Version> listar(UUID ruta,int limite,int desplazamiento) {
        return ruta==null?jdbc.query(LECTURA+" ORDER BY vigente_desde DESC,id LIMIT ? OFFSET ?",this::leer,limite,desplazamiento)
          :jdbc.query(LECTURA+" WHERE ruta_version_id=? ORDER BY vigente_desde DESC,id LIMIT ? OFFSET ?",this::leer,ruta,limite,desplazamiento);
    }
    public List<OpcionRuta> rutas(int limite,int desplazamiento) {
        return jdbc.query("SELECT v.id,r.codigo,r.nombre,v.numero_version FROM ruta_version v JOIN ruta r ON r.id=v.ruta_id ORDER BY r.codigo,v.numero_version LIMIT ? OFFSET ?",
          (r,i)->new OpcionRuta(r.getObject("id",UUID.class),r.getString("codigo"),r.getString("nombre"),r.getInt("numero_version")),limite,desplazamiento);
    }
    public Optional<Version> actualBloqueada(UUID id) {
        // Serializa cambio de versión y cierre sobre la identidad estable.
        var ids=jdbc.queryForList("SELECT id FROM referencia_espacial WHERE id=? FOR UPDATE",UUID.class,id);
        if(ids.isEmpty()) return Optional.empty();
        return jdbc.query(LECTURA+" WHERE referencia_id=? AND vigente_hasta IS NULL",this::leer,id).stream().findFirst();
    }
    public void crearIdentidad(UUID id) { jdbc.update("INSERT INTO referencia_espacial(id) VALUES (?)",id); }
    public void insertar(Version v,UUID usuario) {
        jdbc.update("INSERT INTO referencia_espacial_version(id,referencia_id,numero_version,ruta_version_id,nombre,tipo,geometria,radio_metros,vigente_desde,motivo,creado_por) VALUES (?,?,?,?,?,?,ST_SetSRID(ST_GeomFromGeoJSON(?),4326),?,?,?,?)",
          v.id(),v.referenciaId(),v.numeroVersion(),v.rutaVersionId(),v.nombre(),v.tipo(),v.geojson(),v.radioMetros(),Timestamp.from(v.vigenteDesde()),v.motivo(),usuario);
    }
    public void cerrar(UUID id,Instant hasta,UUID usuario,String motivo) {
        jdbc.update("UPDATE referencia_espacial_version SET vigente_hasta=?,cerrado_por=?,motivo_cierre=? WHERE id=? AND vigente_hasta IS NULL",Timestamp.from(hasta),usuario,motivo,id);
    }
}
