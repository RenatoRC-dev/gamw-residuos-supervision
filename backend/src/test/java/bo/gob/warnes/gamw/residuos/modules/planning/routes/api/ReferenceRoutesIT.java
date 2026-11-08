package bo.gob.warnes.gamw.residuos.modules.planning.routes.api;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-09 / RF-06 / CA-04..13. Real PostgreSQL/PostGIS, only owned synthetic fixtures are removed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("route-development")
class ReferenceRoutesIT {
    private static final String START = "2026-01-01T00:00:00Z";
    private static final String NEXT = "2026-01-02T00:00:00Z";
    private static final List<List<Double>> FIRST = List.of(List.of(-10.12345678901234, 20.5), List.of(-10.2, 20.6));
    private static final List<List<Double>> SECOND = List.of(List.of(-11.0, 21.0), List.of(-11.1, 21.2), List.of(-11.2, 21.3));
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private final Set<UUID> ownedRoutes = new HashSet<>();

    @AfterEach
    void removeOnlyOwnedSyntheticFixtures() {
        for (UUID id : ownedRoutes) {
            jdbc.update("DELETE FROM route_version WHERE route_id = ?", id);
            jdbc.update("DELETE FROM route WHERE id = ?", id);
        }
    }

    @Test
    void createsInitialVersionAndRoundTripsSpatialGeometryThroughRest() throws Exception {
        var created = createRoute();
        UUID id = UUID.fromString(created.get("route").get("id").asText());
        assertThat(created.get("version").get("versionNumber").asInt()).isEqualTo(1);
        mvc.perform(local(get("/api/routes/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(local(get("/api/routes"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(id.toString())));
        var returned = readVersion(id, 1);
        assertThat(returned.get("geometry")).isEqualTo(created.get("version").get("geometry"));
        assertThat(returned.get("geometry").get("type").asText()).isEqualTo("LineString");
        assertThat(returned.get("geometry").get("coordinates").get(0).get(0).asDouble()).isEqualTo(FIRST.getFirst().getFirst());
        var spatial = jdbc.queryForMap("SELECT ST_SRID(geometry) AS srid, GeometryType(geometry) AS type, ST_IsValid(geometry) AS valid FROM route_version WHERE route_id = ?", id);
        assertThat(spatial).containsEntry("srid", 4326).containsEntry("type", "LINESTRING").containsEntry("valid", true);
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'route_version_geometry_gist'", String.class))
                .contains("USING gist");
    }

    @Test
    void appendsVersionAndKeepsOriginalGeometryAndIdentity() throws Exception {
        UUID id = routeId(createRoute());
        var original = readVersion(id, 1);
        mvc.perform(local(post("/api/routes/" + id + "/versions")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(versionPayload(SECOND, NEXT))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(2));
        var historical = readVersion(id, 1);
        assertThat(historical.get("geometry")).isEqualTo(original.get("geometry"));
        assertThat(historical.get("id")).isEqualTo(original.get("id"));
        assertThat(historical.get("validTo").asText()).isEqualTo(NEXT);
        assertThat(readVersion(id, 2).get("geometry").get("coordinates")).isEqualTo(json.valueToTree(SECOND));
        mvc.perform(local(get("/api/routes/" + id + "/versions"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void serializesConcurrentVersionsWithoutDuplicateNumbers() throws Exception {
        UUID id = routeId(createRoute());
        String payload = json.writeValueAsString(versionPayload(SECOND, NEXT));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> append = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrency test timed out");
                return mvc.perform(local(post("/api/routes/" + id + "/versions"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn().getResponse().getStatus();
            };
            var one = executor.submit(append);
            var two = executor.submit(append);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForList("SELECT version_number FROM route_version WHERE route_id = ? ORDER BY version_number", Integer.class, id))
                .containsExactly(1, 2);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO route_version(id, route_id, version_number, geometry, valid_from, valid_to, created_at)
                SELECT ?, route_id, version_number, geometry, valid_from, valid_from + interval '1 hour', created_at
                FROM route_version WHERE route_id = ? AND version_number = 1
                """, UUID.randomUUID(), id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsInvalidGeometryThroughApiWithoutCreatingOrphanRoutes() throws Exception {
        String code = "SYNTHETIC-" + UUID.randomUUID();
        var payload = routePayload(code);
        for (Object geometry : List.of(Map.of("type", "Point", "coordinates", List.of(1, 2)),
                Map.of("type", "LineString", "coordinates", List.of(List.of(1, 2))),
                Map.of("type", "LineString", "coordinates", List.of(List.of(181, 0), List.of(1, 2))),
                Map.of("type", "LineString", "coordinates", List.of(List.of(0, -91), List.of(1, 2))),
                Map.of("type", "LineString", "coordinates", List.of(List.of("NaN", 0), List.of(1, 2))),
                Map.of("type", "LineString", "coordinates", List.of(List.of(1, 2), List.of(1, 2))))) {
            payload.put("geometry", geometry);
            mvc.perform(local(post("/api/routes")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
        payload.put("geometry", null);
        mvc.perform(local(post("/api/routes")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM route WHERE code = ?", Integer.class, code)).isZero();
    }

    @Test
    void rejectsDuplicateCodesAndBackdatedVersions() throws Exception {
        var created = createRoute();
        UUID id = routeId(created);
        mvc.perform(local(post("/api/routes")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(routePayload(created.get("route").get("code").asText()))))
                .andExpect(status().isConflict());
        mvc.perform(local(post("/api/routes/" + id + "/versions")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(versionPayload(SECOND, START))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM route_version WHERE route_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void databaseGuardsHistoricalGeometryEvenOutsideApplication() throws Exception {
        UUID id = routeId(createRoute());
        var before = readVersion(id, 1);
        assertThatThrownBy(() -> jdbc.update("UPDATE route_version SET geometry = ST_GeomFromText('LINESTRING(1 2, 3 4)',4326) WHERE route_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(readVersion(id, 1)).isEqualTo(before);
    }

    @Test
    void developmentAccessRequiresLocalAddressExplicitHeaderAndAllowedOrigin() throws Exception {
        mvc.perform(get("/api/routes")).andExpect(status().isForbidden());
        mvc.perform(local(get("/api/routes")).header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
        mvc.perform(local(get("/api/routes")).with(request -> { request.setRemoteAddr("192.0.2.1"); return request; }))
                .andExpect(status().isForbidden());
        mvc.perform(local(get("/api/routes/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    private JsonNode createRoute() throws Exception {
        var response = mvc.perform(local(post("/api/routes")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(routePayload("SYNTHETIC-" + UUID.randomUUID()))))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var created = json.readTree(response.getContentAsString());
        ownedRoutes.add(routeId(created));
        return created;
    }
    private JsonNode readVersion(UUID id, int version) throws Exception {
        return json.readTree(mvc.perform(local(get("/api/routes/" + id + "/versions/" + version)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private static UUID routeId(JsonNode created) { return UUID.fromString(created.get("route").get("id").asText()); }
    private static Map<String, Object> routePayload(String code) {
        var payload = versionPayload(FIRST, START);
        payload.put("code", code);
        payload.put("name", "Geometría sintética de prueba — no oficial");
        return payload;
    }
    private static Map<String, Object> versionPayload(List<List<Double>> coordinates, String validFrom) {
        return new HashMap<>(Map.of("geometry", Map.of("type", "LineString", "coordinates", coordinates),
                "validFrom", validFrom, "changeReason", "Verificación sintética CU-09"));
    }
    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder request) {
        return request.header("X-GAMW-Development", "route-poc");
    }
}
