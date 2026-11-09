package bo.gob.warnes.gamw.residuos.modules.monitoreo.posicion.persistencia;

import bo.gob.warnes.gamw.residuos.modules.monitoreo.posicion.dominio.RepositorioMonitoreo;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioContextoTrayectoria.Resumen;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioMonitoreoPostgres implements RepositorioMonitoreo {
    private final JdbcTemplate jdbc;
    public RepositorioMonitoreoPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<JornadaActiva> activas(int limite,int desplazamiento) {
        return jdbc.query("""
            WITH activas AS (
              SELECT j.*,i.capturado_en AS inicio_registrado,i.asignacion_jornada_id AS asignacion_inicio
              FROM jornada j JOIN evento_ejecucion_jornada i ON i.jornada_id=j.id AND i.tipo_evento='INICIO'
              WHERE NOT EXISTS (SELECT 1 FROM evento_ejecucion_jornada f WHERE f.jornada_id=j.id AND f.tipo_evento='FIN')
              ORDER BY i.capturado_en DESC,j.id LIMIT ? OFFSET ?
            )
            SELECT a.id AS jornada_id,a.inicio_programado,a.fin_programado,a.periodo_servicio_id,a.inicio_registrado,
                   e.razon_social,r.id AS ruta_id,r.codigo,r.nombre,rv.id AS version_id,rv.numero_version,
                   ST_AsGeoJSON(rv.geometria,17) AS referencia,v.id AS vehiculo_id,v.placa,
                   p.id AS gps_id,p.captura_id,p.asignacion_jornada_id,p.usuario_id,p.capturado_en,p.recibido_en,
                   ST_X(p.geometria) AS longitud,ST_Y(p.geometria) AS latitud,p.precision_metros
            FROM activas a JOIN periodo_servicio ps ON ps.id=a.periodo_servicio_id
            JOIN empresa_operadora e ON e.id=ps.empresa_operadora_id
            JOIN ruta_version rv ON rv.id=a.ruta_version_id JOIN ruta r ON r.id=rv.ruta_id
            LEFT JOIN LATERAL (
              SELECT * FROM posicion_gps WHERE jornada_id=a.id
              ORDER BY capturado_en DESC,captura_id DESC,id DESC LIMIT 1
            ) p ON true
            JOIN asignacion_jornada aj ON aj.id=COALESCE(p.asignacion_jornada_id,a.asignacion_inicio)
            JOIN vinculo_vehiculo_empresa vv ON vv.id=aj.vinculo_vehiculo_empresa_id JOIN vehiculo v ON v.id=vv.vehiculo_id
            ORDER BY a.inicio_registrado DESC,a.id
            """,(r,i)->{
                UUID jornada=r.getObject("jornada_id",UUID.class);
                Timestamp fin=r.getTimestamp("fin_programado");
                var contexto=new Resumen(jornada,r.getTimestamp("inicio_programado").toInstant(),fin==null?null:fin.toInstant(),
                    r.getObject("periodo_servicio_id",UUID.class),r.getString("razon_social"),r.getObject("ruta_id",UUID.class),
                    r.getString("codigo"),r.getString("nombre"),r.getObject("version_id",UUID.class),r.getInt("numero_version"));
                UUID gps=r.getObject("gps_id",UUID.class);
                PosicionGps posicion=gps==null?null:new PosicionGps(gps,r.getObject("captura_id",UUID.class),jornada,
                    r.getObject("asignacion_jornada_id",UUID.class),r.getObject("usuario_id",UUID.class),
                    r.getTimestamp("capturado_en").toInstant(),r.getTimestamp("recibido_en").toInstant(),
                    r.getDouble("latitud"),r.getDouble("longitud"),r.getDouble("precision_metros"));
                return new JornadaActiva(contexto,r.getObject("vehiculo_id",UUID.class),r.getString("placa"),
                    r.getTimestamp("inicio_registrado").toInstant(),posicion,r.getString("referencia"));
            },limite,desplazamiento);
    }
}
