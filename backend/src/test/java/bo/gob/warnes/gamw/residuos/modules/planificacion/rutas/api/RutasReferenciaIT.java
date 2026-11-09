package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;

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
@ActiveProfiles("desarrollo-local")
class RutasReferenciaIT extends IntegracionPostgresV8 {
    private static final String START = "2026-01-01T00:00:00Z";
    private static final String NEXT = "2026-01-02T00:00:00Z";
    private static final List<List<Double>> FIRST = List.of(List.of(-10.12345678901234, 20.5), List.of(-10.2, 20.6));
    private static final List<List<Double>> SECOND = List.of(List.of(-11.0, 21.0), List.of(-11.1, 21.2), List.of(-11.2, 21.3));
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private final Set<UUID> rutasPropias = new HashSet<>();

    @AfterEach
    void eliminarSoloDatosSinteticosPropios() {
        for (UUID id : rutasPropias) {
            jdbc.update("DELETE FROM ruta_version WHERE ruta_id = ?", id);
            jdbc.update("DELETE FROM ruta WHERE id = ?", id);
        }
    }

    @Test
    void esquemaYContratoUsanVocabularioEspanol() throws Exception {
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN ('ruta','ruta_version','route','route_version')
                ORDER BY table_name
                """, String.class)).containsExactly("ruta", "ruta_version");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'ruta' ORDER BY ordinal_position
                """, String.class)).containsExactly("id", "codigo", "nombre", "activo", "creado_en", "actualizado_en");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'ruta_version' ORDER BY ordinal_position
                """, String.class)).containsExactly("id", "ruta_id", "numero_version", "geometria", "vigente_desde",
                        "vigente_hasta", "motivo_cambio", "creado_en");
        var creada = crearRuta();
        assertThat(creada.get("ruta").toString()).contains("\"codigo\":", "\"nombre\":", "\"activo\":", "\"creadoEn\":", "\"actualizadoEn\":")
                .doesNotContain("\"code\":", "\"name\":", "\"active\":", "\"createdAt\":", "\"updatedAt\":");
        assertThat(creada.get("version").toString()).contains("\"rutaId\":", "\"numeroVersion\":", "\"geometria\":",
                        "\"vigenteDesde\":", "\"vigenteHasta\":", "\"motivoCambio\":", "\"creadoEn\":", "\"type\":", "\"coordinates\":")
                .doesNotContain("\"routeId\":", "\"versionNumber\":", "\"geometry\":", "\"validFrom\":", "\"validTo\":", "\"changeReason\":");
    }

    @Test
    void creaVersionInicialYRecuperaGeometriaEspacialPorRest() throws Exception {
        var created = crearRuta();
        UUID id = UUID.fromString(created.get("ruta").get("id").asText());
        assertThat(created.get("version").get("numeroVersion").asInt()).isEqualTo(1);
        mvc.perform(local(get("/api/rutas/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
        mvc.perform(local(get("/api/rutas"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(id.toString())));
        var returned = leerVersion(id, 1);
        assertThat(returned.get("geometria")).isEqualTo(created.get("version").get("geometria"));
        assertThat(returned.get("geometria").get("type").asText()).isEqualTo("LineString");
        assertThat(returned.get("geometria").get("coordinates").get(0).get(0).asDouble()).isEqualTo(FIRST.getFirst().getFirst());
        var spatial = jdbc.queryForMap("SELECT ST_SRID(geometria) AS srid, GeometryType(geometria) AS type, ST_IsValid(geometria) AS valid FROM ruta_version WHERE ruta_id = ?", id);
        assertThat(spatial).containsEntry("srid", 4326).containsEntry("type", "LINESTRING").containsEntry("valid", true);
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'ruta_version_geometria_gist'", String.class))
                .contains("USING gist");
    }

    @Test
    void agregaVersionYConservaGeometriaEIdentidadOriginales() throws Exception {
        UUID id = rutaId(crearRuta());
        var original = leerVersion(id, 1);
        mvc.perform(local(post("/api/rutas/" + id + "/versiones")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(datosVersion(SECOND, NEXT))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numeroVersion").value(2));
        var historical = leerVersion(id, 1);
        assertThat(historical.get("geometria")).isEqualTo(original.get("geometria"));
        assertThat(historical.get("id")).isEqualTo(original.get("id"));
        assertThat(historical.get("vigenteHasta").asText()).isEqualTo(NEXT);
        assertThat(leerVersion(id, 2).get("geometria").get("coordinates")).isEqualTo(json.valueToTree(SECOND));
        mvc.perform(local(get("/api/rutas/" + id + "/versiones"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void serializaVersionesConcurrentesSinNumerosDuplicados() throws Exception {
        UUID id = rutaId(crearRuta());
        String payload = json.writeValueAsString(datosVersion(SECOND, NEXT));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> append = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrency test timed out");
                return mvc.perform(local(post("/api/rutas/" + id + "/versiones"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn().getResponse().getStatus();
            };
            var one = executor.submit(append);
            var two = executor.submit(append);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForList("SELECT numero_version FROM ruta_version WHERE ruta_id = ? ORDER BY numero_version", Integer.class, id))
                .containsExactly(1, 2);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ruta_version(id, ruta_id, numero_version, geometria, vigente_desde, vigente_hasta, creado_en)
                SELECT ?, ruta_id, numero_version, geometria, vigente_desde, vigente_desde + interval '1 hour', creado_en
                FROM ruta_version WHERE ruta_id = ? AND numero_version = 1
                """, UUID.randomUUID(), id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rechazaGeometriaInvalidaSinCrearRutasHuerfanas() throws Exception {
        String codigo = "SYNTHETIC-" + UUID.randomUUID();
        var payload = datosRuta(codigo);
        for (Object geometria : List.of(Map.of("type", "Point", "coordinates", List.of(1, 2)),
                Map.of("type", "LineString", "coordinates", List.of(List.of(1, 2))),
                Map.of("type", "LineString", "coordinates", List.of(List.of(181, 0), List.of(1, 2))),
                Map.of("type", "LineString", "coordinates", List.of(List.of(0, -91), List.of(1, 2))),
                Map.of("type", "LineString", "coordinates", List.of(List.of("NaN", 0), List.of(1, 2))),
                Map.of("type", "LineString", "coordinates", List.of(List.of(1, 2), List.of(1, 2))))) {
            payload.put("geometria", geometria);
            mvc.perform(local(post("/api/rutas")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                    .andExpect(status().isBadRequest());
        }
        payload.put("geometria", null);
        mvc.perform(local(post("/api/rutas")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ruta WHERE codigo = ?", Integer.class, codigo)).isZero();
    }

    @Test
    void rechazaCodigosDuplicadosYVersionesRetroactivas() throws Exception {
        var created = crearRuta();
        UUID id = rutaId(created);
        mvc.perform(local(post("/api/rutas")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(datosRuta(created.get("ruta").get("codigo").asText()))))
                .andExpect(status().isConflict());
        mvc.perform(local(post("/api/rutas/" + id + "/versiones")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(datosVersion(SECOND, START))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ruta_version WHERE ruta_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void baseDeDatosProtegeGeometriaHistoricaFueraDeLaAplicacion() throws Exception {
        UUID id = rutaId(crearRuta());
        var before = leerVersion(id, 1);
        assertThatThrownBy(() -> jdbc.update("UPDATE ruta_version SET geometria = ST_GeomFromText('LINESTRING(1 2, 3 4)',4326) WHERE ruta_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(leerVersion(id, 1)).isEqualTo(before);
    }

    @Test
    void accesoDeDesarrolloExigeDireccionLocalCabeceraExplicitaYOrigenPermitido() throws Exception {
        mvc.perform(get("/api/rutas")).andExpect(status().isForbidden());
        mvc.perform(local(get("/api/rutas")).header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
        mvc.perform(local(get("/api/rutas")).with(request -> { request.setRemoteAddr("192.0.2.1"); return request; }))
                .andExpect(status().isForbidden());
        mvc.perform(local(get("/api/rutas/" + UUID.randomUUID()))).andExpect(status().isNotFound());
    }

    private JsonNode crearRuta() throws Exception {
        var response = mvc.perform(local(post("/api/rutas")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(datosRuta("SYNTHETIC-" + UUID.randomUUID()))))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var created = json.readTree(response.getContentAsString());
        rutasPropias.add(rutaId(created));
        return created;
    }
    private JsonNode leerVersion(UUID id, int version) throws Exception {
        return json.readTree(mvc.perform(local(get("/api/rutas/" + id + "/versiones/" + version)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private static UUID rutaId(JsonNode created) { return UUID.fromString(created.get("ruta").get("id").asText()); }
    private static Map<String, Object> datosRuta(String codigo) {
        var payload = datosVersion(FIRST, START);
        payload.put("codigo", codigo);
        payload.put("nombre", "Geometría sintética de prueba — no oficial");
        return payload;
    }
    private static Map<String, Object> datosVersion(List<List<Double>> coordinates, String vigenteDesde) {
        return new HashMap<>(Map.of("geometria", Map.of("type", "LineString", "coordinates", coordinates),
                "vigenteDesde", vigenteDesde, "motivoCambio", "Verificación sintética CU-09"));
    }
    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder request) {
        return request.header("X-GAMW-Development", "gamw-desarrollo");
    }
}
