package bo.gob.warnes.gamw.residuos.modules.monitoreo.revision.persistencia;

import bo.gob.warnes.gamw.residuos.modules.monitoreo.revision.dominio.RepositorioRevisionGps;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.dominio.RepositorioReferenciasEspaciales.Version;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepositorioRevisionGpsPostgres implements RepositorioRevisionGps {
    private final JdbcTemplate jdbc;
    public RepositorioRevisionGpsPostgres(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static Instant instante(ResultSet r,String campo) throws SQLException { var t=r.getTimestamp(campo); return t==null?null:t.toInstant(); }
    public List<Muestra> muestras(UUID jornada,Instant corte,int limite) {
        return jdbc.query("""
          WITH base AS (
            SELECT p.*,count(*) OVER() AS total,max(capturado_en) OVER() AS ultima_captura,max(recibido_en) OVER() AS ultima_recepcion
            FROM posicion_gps p WHERE jornada_id=? AND recibido_en<=?
          ), seleccion AS (
            SELECT * FROM base ORDER BY capturado_en,captura_id,id LIMIT ?
          ), pares AS (
            SELECT seleccion.*,lag(geometria) OVER(ORDER BY capturado_en,captura_id,id) AS anterior FROM seleccion
          )
          SELECT p.*,ST_X(p.geometria) AS longitud,ST_Y(p.geometria) AS latitud,
                 ST_Distance(p.geometria::geography,rv.geometria::geography) AS distancia_ruta,
                 ST_Distance(p.geometria::geography,p.anterior::geography) AS distancia_anterior
          FROM pares p JOIN jornada j ON j.id=p.jornada_id JOIN ruta_version rv ON rv.id=j.ruta_version_id
          ORDER BY capturado_en,captura_id,p.id
          """,(r,i)->new Muestra(new PosicionGps(r.getObject("id",UUID.class),r.getObject("captura_id",UUID.class),jornada,
            r.getObject("asignacion_jornada_id",UUID.class),r.getObject("usuario_id",UUID.class),instante(r,"capturado_en"),instante(r,"recibido_en"),
            r.getDouble("latitud"),r.getDouble("longitud"),r.getDouble("precision_metros")),r.getDouble("distancia_ruta"),
            r.getObject("distancia_anterior",Double.class),r.getLong("total"),instante(r,"ultima_captura"),instante(r,"ultima_recepcion")),
            jornada,Timestamp.from(corte),limite);
    }
    public List<Coincidencia> coincidencias(UUID jornada,Instant corte,double precision) {
        return jdbc.query("""
          WITH muestras AS MATERIALIZED (
            SELECT * FROM posicion_gps WHERE jornada_id=? AND recibido_en<=?
            ORDER BY capturado_en,captura_id,id LIMIT 5000
          ), controles AS (
            SELECT c.* FROM referencia_espacial_version c JOIN jornada j ON j.ruta_version_id=c.ruta_version_id
            WHERE j.id=? AND c.vigente_desde<=? ORDER BY c.vigente_desde,c.id LIMIT 101
          ), pares AS (
            SELECT c.id,p.id AS posicion_id,p.capturado_en,p.precision_metros,
              CASE WHEN c.tipo='PUNTO' THEN ST_DWithin(p.geometria::geography,c.geometria::geography,c.radio_metros)
                   ELSE ST_Covers(c.geometria,p.geometria) END AS observada,
              ST_Distance(p.geometria::geography,c.geometria::geography) AS distancia,
              COALESCE(c.radio_metros,0) AS radio
            FROM controles c LEFT JOIN muestras p ON p.capturado_en>=c.vigente_desde
              AND (c.vigente_hasta IS NULL OR p.capturado_en<c.vigente_hasta)
          ), resumen AS (
            SELECT id,count(posicion_id) AS muestras,count(*) FILTER(WHERE observada) AS observadas,
              count(*) FILTER(WHERE posicion_id IS NOT NULL AND precision_metros<=?) AS precisas,
              count(*) FILTER(WHERE NOT observada AND distancia<=radio+precision_metros) AS inciertas,
              min(capturado_en) FILTER(WHERE observada) AS primera,max(capturado_en) FILTER(WHERE observada) AS ultima,
              min(distancia) AS distancia_minima FROM pares GROUP BY id
          )
          SELECT c.*,ST_AsGeoJSON(c.geometria,17) AS geojson,r.muestras,r.observadas,r.precisas,r.inciertas,r.primera,r.ultima,r.distancia_minima
          FROM controles c JOIN resumen r ON r.id=c.id ORDER BY c.vigente_desde,c.id
          """,(r,i)->new Coincidencia(new Version(r.getObject("id",UUID.class),r.getObject("referencia_id",UUID.class),r.getInt("numero_version"),
            r.getObject("ruta_version_id",UUID.class),r.getString("nombre"),r.getString("tipo"),r.getString("geojson"),r.getObject("radio_metros",Double.class),
            instante(r,"vigente_desde"),instante(r,"vigente_hasta"),r.getString("motivo")),r.getLong("muestras"),r.getLong("observadas"),
            r.getLong("precisas"),r.getLong("inciertas"),instante(r,"primera"),instante(r,"ultima"),r.getObject("distancia_minima",Double.class)),
            jornada,Timestamp.from(corte),jornada,Timestamp.from(corte),precision);
    }
}
