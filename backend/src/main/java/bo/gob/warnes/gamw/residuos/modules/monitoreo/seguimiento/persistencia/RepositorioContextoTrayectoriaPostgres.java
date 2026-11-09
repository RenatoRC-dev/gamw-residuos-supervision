package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.persistencia;

import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioContextoTrayectoria;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioContextoTrayectoriaPostgres implements RepositorioContextoTrayectoria {
    private final JdbcTemplate jdbc;
    public RepositorioContextoTrayectoriaPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String CONSULTA="""
            SELECT j.id,j.inicio_programado,j.fin_programado,j.periodo_servicio_id,e.razon_social,
                   r.id AS ruta_id,r.codigo,r.nombre,rv.id AS version_id,rv.numero_version
            FROM jornada j JOIN periodo_servicio ps ON ps.id=j.periodo_servicio_id
            JOIN empresa_operadora e ON e.id=ps.empresa_operadora_id
            JOIN ruta_version rv ON rv.id=j.ruta_version_id JOIN ruta r ON r.id=rv.ruta_id
            """;
    private Resumen leer(ResultSet r,int indice) throws SQLException {
        Timestamp fin=r.getTimestamp("fin_programado");
        return new Resumen(r.getObject("id",UUID.class),r.getTimestamp("inicio_programado").toInstant(),
                fin==null?null:fin.toInstant(),r.getObject("periodo_servicio_id",UUID.class),r.getString("razon_social"),
                r.getObject("ruta_id",UUID.class),r.getString("codigo"),r.getString("nombre"),
                r.getObject("version_id",UUID.class),r.getInt("numero_version"));
    }
    public List<Resumen> listar(int limite,int desplazamiento) {
        return jdbc.query(CONSULTA+" ORDER BY j.inicio_programado DESC,j.id LIMIT ? OFFSET ?",this::leer,limite,desplazamiento);
    }
    public Optional<Resumen> obtener(UUID id) {
        return jdbc.query(CONSULTA+" WHERE j.id=?",this::leer,id).stream().findFirst();
    }
}
