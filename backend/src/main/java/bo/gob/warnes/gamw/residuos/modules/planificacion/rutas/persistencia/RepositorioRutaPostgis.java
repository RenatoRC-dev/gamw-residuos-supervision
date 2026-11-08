package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.persistencia;

import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RepositorioRutaPostgis implements RepositorioRuta {
    private static final String CONSULTA_VERSION = """
            SELECT id, ruta_id, numero_version, ST_AsGeoJSON(geometria, 17, 0) AS geojson,
                   vigente_desde, vigente_hasta, motivo_cambio, creado_en FROM ruta_version
            """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public RepositorioRutaPostgis(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insertar(Ruta ruta) {
        jdbc.update("INSERT INTO ruta(id, codigo, nombre, activo, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?)",
                ruta.id(), ruta.codigo(), ruta.nombre(), ruta.activo(),
                Timestamp.from(ruta.creadoEn()), Timestamp.from(ruta.actualizadoEn()));
    }

    @Override
    public void insertar(RutaVersion version) {
        String geometria = json.writeValueAsString(new DocumentoGeoJson("LineString", version.geometria().coordenadas()));
        jdbc.update("""
                INSERT INTO ruta_version(id, ruta_id, numero_version, geometria, vigente_desde, motivo_cambio, creado_en)
                VALUES (?, ?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326), ?, ?, ?)
                """, version.id(), version.rutaId(), version.numeroVersion(), geometria,
                Timestamp.from(version.vigenteDesde()), version.motivoCambio(), Timestamp.from(version.creadoEn()));
    }

    @Override
    public List<Ruta> listar() {
        return jdbc.query("SELECT * FROM ruta ORDER BY codigo, id", this::leerRuta);
    }

    @Override
    public Optional<Ruta> buscar(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM ruta WHERE id = ?" + (lock ? " FOR UPDATE" : ""), this::leerRuta, id)
                .stream().findFirst();
    }

    @Override
    public List<RutaVersion> versiones(UUID rutaId) {
        return jdbc.query(CONSULTA_VERSION + " WHERE ruta_id = ? ORDER BY numero_version DESC", this::leerVersion, rutaId);
    }

    @Override
    public Optional<RutaVersion> ultima(UUID rutaId) {
        return jdbc.query(CONSULTA_VERSION + " WHERE ruta_id = ? ORDER BY numero_version DESC LIMIT 1", this::leerVersion, rutaId)
                .stream().findFirst();
    }

    @Override
    public Optional<RutaVersion> obtenerVersion(UUID rutaId, int numeroVersion) {
        return jdbc.query(CONSULTA_VERSION + " WHERE ruta_id = ? AND numero_version = ?", this::leerVersion, rutaId, numeroVersion)
                .stream().findFirst();
    }

    @Override
    public void cerrarVersion(UUID versionId, Instant vigenteHasta) {
        jdbc.update("UPDATE ruta_version SET vigente_hasta = ? WHERE id = ? AND vigente_hasta IS NULL",
                Timestamp.from(vigenteHasta), versionId);
    }

    @Override
    public void actualizarFecha(UUID rutaId, Instant actualizadoEn) {
        jdbc.update("UPDATE ruta SET actualizado_en = ? WHERE id = ?", Timestamp.from(actualizadoEn), rutaId);
    }

    private Ruta leerRuta(ResultSet row, int index) throws SQLException {
        return new Ruta(row.getObject("id", UUID.class), row.getString("codigo"), row.getString("nombre"),
                row.getBoolean("activo"), row.getTimestamp("creado_en").toInstant(), row.getTimestamp("actualizado_en").toInstant());
    }

    private RutaVersion leerVersion(ResultSet row, int index) throws SQLException {
        var geometria = json.readValue(row.getString("geojson"), DocumentoGeoJson.class);
        var end = row.getTimestamp("vigente_hasta");
        return new RutaVersion(row.getObject("id", UUID.class), row.getObject("ruta_id", UUID.class),
                row.getInt("numero_version"), new GeometriaRuta(geometria.coordinates()),
                row.getTimestamp("vigente_desde").toInstant(), end == null ? null : end.toInstant(),
                row.getString("motivo_cambio"), row.getTimestamp("creado_en").toInstant());
    }

    private record DocumentoGeoJson(String type, List<List<Double>> coordinates) { }
}
