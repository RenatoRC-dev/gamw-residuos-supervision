package bo.gob.warnes.gamw.residuos.modules.planning.routes.persistence;

import bo.gob.warnes.gamw.residuos.modules.planning.routes.domain.*;
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
public class PostgisRouteRepository implements RouteRepository {
    private static final String VERSION_SELECT = """
            SELECT id, route_id, version_number, ST_AsGeoJSON(geometry, 17, 0) AS geojson,
                   valid_from, valid_to, change_reason, created_at FROM route_version
            """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PostgisRouteRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(Route route) {
        jdbc.update("INSERT INTO route(id, code, name, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                route.id(), route.code(), route.name(), route.active(),
                Timestamp.from(route.createdAt()), Timestamp.from(route.updatedAt()));
    }

    @Override
    public void insert(RouteVersion version) {
        String geometry = json.writeValueAsString(new GeometryDocument("LineString", version.geometry().coordinates()));
        jdbc.update("""
                INSERT INTO route_version(id, route_id, version_number, geometry, valid_from, change_reason, created_at)
                VALUES (?, ?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326), ?, ?, ?)
                """, version.id(), version.routeId(), version.versionNumber(), geometry,
                Timestamp.from(version.validFrom()), version.changeReason(), Timestamp.from(version.createdAt()));
    }

    @Override
    public List<Route> list() {
        return jdbc.query("SELECT * FROM route ORDER BY code, id", this::readRoute);
    }

    @Override
    public Optional<Route> find(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM route WHERE id = ?" + (lock ? " FOR UPDATE" : ""), this::readRoute, id)
                .stream().findFirst();
    }

    @Override
    public List<RouteVersion> versions(UUID routeId) {
        return jdbc.query(VERSION_SELECT + " WHERE route_id = ? ORDER BY version_number DESC", this::readVersion, routeId);
    }

    @Override
    public Optional<RouteVersion> latest(UUID routeId) {
        return jdbc.query(VERSION_SELECT + " WHERE route_id = ? ORDER BY version_number DESC LIMIT 1", this::readVersion, routeId)
                .stream().findFirst();
    }

    @Override
    public Optional<RouteVersion> version(UUID routeId, int number) {
        return jdbc.query(VERSION_SELECT + " WHERE route_id = ? AND version_number = ?", this::readVersion, routeId, number)
                .stream().findFirst();
    }

    @Override
    public void closeVersion(UUID versionId, Instant validTo) {
        jdbc.update("UPDATE route_version SET valid_to = ? WHERE id = ? AND valid_to IS NULL",
                Timestamp.from(validTo), versionId);
    }

    @Override
    public void touch(UUID routeId, Instant updatedAt) {
        jdbc.update("UPDATE route SET updated_at = ? WHERE id = ?", Timestamp.from(updatedAt), routeId);
    }

    private Route readRoute(ResultSet row, int index) throws SQLException {
        return new Route(row.getObject("id", UUID.class), row.getString("code"), row.getString("name"),
                row.getBoolean("active"), row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }

    private RouteVersion readVersion(ResultSet row, int index) throws SQLException {
        var geometry = json.readValue(row.getString("geojson"), GeometryDocument.class);
        var end = row.getTimestamp("valid_to");
        return new RouteVersion(row.getObject("id", UUID.class), row.getObject("route_id", UUID.class),
                row.getInt("version_number"), new RouteGeometry(geometry.coordinates()),
                row.getTimestamp("valid_from").toInstant(), end == null ? null : end.toInstant(),
                row.getString("change_reason"), row.getTimestamp("created_at").toInstant());
    }

    private record GeometryDocument(String type, List<List<Double>> coordinates) { }
}
