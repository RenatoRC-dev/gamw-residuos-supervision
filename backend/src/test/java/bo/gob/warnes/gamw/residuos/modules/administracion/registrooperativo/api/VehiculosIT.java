package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;

import java.util.*;
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

/** CU-06 / RF-04. PostgreSQL real; solamente se eliminan las filas sintéticas creadas por cada prueba. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("desarrollo-local")
class VehiculosIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private final Set<UUID> vehiculosPropios = new HashSet<>();
    private final Set<UUID> marcasPropias = new HashSet<>();
    private final Set<UUID> modelosPropios = new HashSet<>();
    private final Set<UUID> tiposPropios = new HashSet<>();
    private final Set<UUID> empresasPropias = new HashSet<>();

    @AfterEach
    void limpiarSoloFilasSinteticasPropias() {
        for (var id : vehiculosPropios) {
            jdbc.update("DELETE FROM vinculo_vehiculo_empresa WHERE vehiculo_id=?",id);
            jdbc.update("DELETE FROM vehiculo WHERE id=?",id);
        }
        for (var id : modelosPropios) jdbc.update("DELETE FROM modelo_vehiculo WHERE id=?",id);
        for (var id : marcasPropias) jdbc.update("DELETE FROM marca_vehiculo WHERE id=?",id);
        for (var id : tiposPropios) jdbc.update("DELETE FROM tipo_vehiculo WHERE id=?",id);
        for (var id : empresasPropias) jdbc.update("DELETE FROM empresa_operadora WHERE id=?",id);
    }
    @Test
    void registraActualizaYDesactivaVehiculoConPlacaTextualNormalizada() throws Exception {
        String placa = "  001-sintetica-" + UUID.randomUUID() + "  ";
        var v = vehiculo(Map.of("placa",placa));
        var vehiculoId = id(v);
        assertThat(v.get("placa").asText()).isEqualTo(placa.strip().toUpperCase(Locale.ROOT));
        assertThat(v.get("modeloVehiculoId").isNull()).isTrue();
        assertThat(v.get("tipoVehiculoId").isNull()).isTrue();
        var datos = new HashMap<String,Object>(Map.of("placa",placa,"anio",2026,"capacidadCargaKg",1050.125,"capacidadVolumenM3",12.345));
        var actualizado = enviar(conDatos(put("/api/vehiculos/" + vehiculoId),datos),200);
        assertThat(actualizado.get("creadoEn")).isEqualTo(v.get("creadoEn"));
        assertThat(jdbc.queryForObject("SELECT capacidad_carga_kg FROM vehiculo WHERE id=?",java.math.BigDecimal.class,vehiculoId)).isEqualByComparingTo("1050.125");
        assertThat(jdbc.queryForObject("SELECT capacidad_volumen_m3 FROM vehiculo WHERE id=?",java.math.BigDecimal.class,vehiculoId)).isEqualByComparingTo("12.345");
        for (boolean activo : List.of(false,true)) {
            assertThat(enviar(conDatos(patch("/api/vehiculos/" + vehiculoId + "/estado"),Map.of("activo",activo)),200).get("activo").asBoolean()).isEqualTo(activo);
        }
        assertThat(enviar(get("/api/vehiculos/" + vehiculoId),200).get("placa")).isEqualTo(v.get("placa"));
        assertThat(enviar(get("/api/vehiculos"),200).toString()).contains(vehiculoId.toString());
        enviar(conDatos(post("/api/vehiculos"),Map.of("placa",placa.toLowerCase(Locale.ROOT))),409);
        enviar(delete("/api/vehiculos/" + vehiculoId),405);
    }
    @Test
    void rechazaPlacaAnioYCapacidadesInvalidos() throws Exception {
        enviar(conDatos(post("/api/vehiculos"),Map.of("placa"," ")),400);
        for (var campo : List.of("capacidadCargaKg","capacidadVolumenM3")) {
            for (int valor : List.of(0,-1)) enviar(conDatos(post("/api/vehiculos"),Map.of("placa",marcaSintetica(),campo,valor)),400);
        }
        enviar(conDatos(post("/api/vehiculos"),Map.of("placa",marcaSintetica(),"anio",1899)),400);
        enviar(conDatos(post("/api/vehiculos"),Map.of("placa",marcaSintetica(),"modeloVehiculoId",UUID.randomUUID())),404);
        enviar(conDatos(post("/api/vehiculos"),Map.of("placa",marcaSintetica(),"tipoVehiculoId",UUID.randomUUID())),404);
    }
    @Test
    void catalogosUnicosPorNombreCodigoYMarcaSinValoresPrecargados() throws Exception {
        String nombre = marcaSintetica();
        var marca = marca("  " + nombre + "  ");
        assertThat(marca.get("nombre").asText()).isEqualTo(nombre);
        enviar(conDatos(post("/api/vehiculos/marcas"),Map.of("nombre",nombre)),409);
        enviar(conDatos(post("/api/vehiculos/marcas"),Map.of("nombre"," ")),400);
        var modelo = modelo(id(marca),"Modelo sintético");
        enviar(conDatos(post("/api/vehiculos/modelos"),Map.of("marcaVehiculoId",id(marca),"nombre","Modelo sintético")),409);
        var otra = marca(marcaSintetica());
        modelo(id(otra),"Modelo sintético");
        assertThat(enviar(get("/api/vehiculos/modelos").param("marcaVehiculoId",id(marca).toString()),200).size()).isEqualTo(1);
        assertThat(enviar(get("/api/vehiculos/modelos"),200).toString()).contains(id(modelo).toString());
        var tipo = tipo(marcaSintetica(),marcaSintetica());
        enviar(conDatos(post("/api/vehiculos/tipos"),Map.of("codigo",tipo.get("codigo").asText(),"nombre",marcaSintetica())),409);
        enviar(conDatos(post("/api/vehiculos/tipos"),Map.of("codigo",marcaSintetica(),"nombre",tipo.get("nombre").asText())),409);
        enviar(conDatos(post("/api/vehiculos/tipos"),Map.of("codigo"," ","nombre","Prueba")),400);
        enviar(conDatos(post("/api/vehiculos/modelos"),Map.of("marcaVehiculoId",UUID.randomUUID(),"nombre","Prueba")),404);
        enviar(conDatos(post("/api/vehiculos/modelos"),Map.of("marcaVehiculoId",id(marca),"nombre"," ")),400);
        var v = vehiculo(Map.of("placa",marcaSintetica(),"modeloVehiculoId",id(modelo),"tipoVehiculoId",id(tipo)));
        assertThat(v.get("modeloVehiculoId").asText()).isEqualTo(id(modelo).toString());
        assertThat(v.get("tipoVehiculoId").asText()).isEqualTo(id(tipo).toString());
        enviar(get("/api/vehiculos/marcas"),200); enviar(get("/api/vehiculos/tipos"),200);
    }
    @Test
    void rechazaReferenciasACatalogosInactivos() throws Exception {
        var marca = marca(marcaSintetica());
        var modelo = modelo(id(marca),"Prueba sintética");
        var tipo = tipo(marcaSintetica(),marcaSintetica());
        var datos = new HashMap<String,Object>(Map.of("placa",marcaSintetica(),"modeloVehiculoId",id(modelo),"tipoVehiculoId",id(tipo)));
        var v = vehiculo(datos);
        jdbc.update("UPDATE tipo_vehiculo SET activo=false WHERE id=?",id(tipo));
        enviar(conDatos(post("/api/vehiculos"),datos),400);
        enviar(conDatos(put("/api/vehiculos/" + id(v)),datos),400);
        jdbc.update("UPDATE tipo_vehiculo SET activo=true WHERE id=?",id(tipo));
        jdbc.update("UPDATE modelo_vehiculo SET activo=false WHERE id=?",id(modelo));
        enviar(conDatos(post("/api/vehiculos"),datos),400);
        jdbc.update("UPDATE modelo_vehiculo SET activo=true WHERE id=?",id(modelo));
        jdbc.update("UPDATE marca_vehiculo SET activo=false WHERE id=?",id(marca));
        enviar(conDatos(post("/api/vehiculos"),datos),400);
        enviar(conDatos(post("/api/vehiculos/modelos"),Map.of("marcaVehiculoId",id(marca),"nombre","Otro sintético")),400);
    }
    @Test
    void conservaVehiculoYSegmentosAlCambiarDeEmpresaYRegresar() throws Exception {
        var v = vehiculo(Map.of("placa",marcaSintetica()));
        var empresaA = empresa(); var empresaB = empresa();
        String rutaA = "/api/empresas-operadoras/" + empresaA + "/vehiculos";
        String rutaB = "/api/empresas-operadoras/" + empresaB + "/vehiculos";
        var primero = enviar(conDatos(post(rutaA),datosVinculo(id(v),"2026-01-01")),201);
        var cerrado = enviar(conDatos(put(rutaA + "/" + id(primero)),datosCierre("2027-12-31",false)),200);
        assertThat(cerrado.get("id")).isEqualTo(primero.get("id"));
        assertThat(cerrado.get("vehiculoId")).isEqualTo(primero.get("vehiculoId"));
        assertThat(cerrado.get("empresaOperadoraId")).isEqualTo(primero.get("empresaOperadoraId"));
        assertThat(cerrado.get("fechaInicio")).isEqualTo(primero.get("fechaInicio"));
        assertThat(cerrado.get("creadoEn")).isEqualTo(primero.get("creadoEn"));
        enviar(conDatos(post(rutaB),datosVinculo(id(v),"2028-01-01")),201);
        enviar(conDatos(post(rutaA),datosVinculo(id(v),"2029-01-01")),201);
        enviar(conDatos(post(rutaA),datosVinculo(id(v),"2029-01-01")),201); // Solapamientos y varios segmentos admitidos.
        var historialA = enviar(get(rutaA),200);
        assertThat(historialA.size()).isEqualTo(3);
        assertThat(historialA.get(0)).isEqualTo(cerrado);
        assertThat(enviar(get(rutaB),200).size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vehiculo WHERE placa=?",Integer.class,v.get("placa").asText())).isEqualTo(1);
        var actualizado = enviar(conDatos(put("/api/vehiculos/" + id(v)),Map.of("placa",v.get("placa").asText(),"anio",2025)),200);
        assertThat(actualizado.get("id")).isEqualTo(v.get("id"));
        enviar(conDatos(patch("/api/vehiculos/" + id(v) + "/estado"),Map.of("activo",false)),200);
        enviar(conDatos(patch("/api/empresas-operadoras/" + empresaA + "/estado"),Map.of("activo",false)),200);
        assertThat(enviar(get(rutaA),200)).isEqualTo(historialA);
    }
    @Test
    void rechazaCambiosDeIdentidadFechasYReferenciasInvalidas() throws Exception {
        var v = vehiculo(Map.of("placa",marcaSintetica()));
        var empresaId = empresa();
        String ruta = "/api/empresas-operadoras/" + empresaId + "/vehiculos";
        var vinculo = enviar(conDatos(post(ruta),datosVinculo(id(v),"2026-01-01")),201);
        for (String campo : List.of("vehiculoId","empresaOperadoraId","fechaInicio")) {
            var datos = datosCierre("2026-06-01",false);
            datos.put(campo,campo.equals("fechaInicio") ? "2026-01-02" : UUID.randomUUID());
            assertThat(enviar(conDatos(put(ruta + "/" + id(vinculo)),datos),400).get("detail").asText()).contains("históricos");
        }
        enviar(conDatos(put(ruta + "/" + id(vinculo)),datosCierre("2025-12-31",false)),400);
        var datos = datosVinculo(id(v),"2026-01-01"); datos.put("fechaFin","2025-12-31");
        enviar(conDatos(post(ruta),datos),400);
        enviar(conDatos(post(ruta),datosVinculo(UUID.randomUUID(),"2026-01-01")),404);
        enviar(conDatos(post("/api/empresas-operadoras/" + UUID.randomUUID() + "/vehiculos"),datosVinculo(id(v),"2026-01-01")),404);
        enviar(conDatos(put("/api/empresas-operadoras/" + empresa() + "/vehiculos/" + id(vinculo)),datosCierre(null,false)),404);
        assertThat(enviar(get(ruta),200).get(0)).isEqualTo(vinculo);
        enviar(delete(ruta + "/" + id(vinculo)),405);
    }
    @Test
    void postgresImponeRestriccionesYNoContieneAtributosEspeculativos() throws Exception {
        var marca = marca(marcaSintetica()); var modelo = modelo(id(marca),"Modelo sintético"); var tipo = tipo(marcaSintetica(),marcaSintetica());
        var v = vehiculo(Map.of("placa",marcaSintetica(),"modeloVehiculoId",id(modelo),"tipoVehiculoId",id(tipo)));
        var otro = vehiculo(Map.of("placa",marcaSintetica()));
        var empresaId = empresa();
        var vinculo = enviar(conDatos(post("/api/empresas-operadoras/" + empresaId + "/vehiculos"),datosVinculo(id(v),"2026-01-01")),201);
        fallaSql("UPDATE vehiculo SET placa=? WHERE id=?",v.get("placa").asText(),id(otro));
        fallaSql("UPDATE vehiculo SET placa=' ' WHERE id=?",id(v));
        fallaSql("UPDATE vehiculo SET placa='minuscula' WHERE id=?",id(v));
        fallaSql("UPDATE vehiculo SET anio=1899 WHERE id=?",id(v));
        for (String columna : List.of("capacidad_carga_kg","capacidad_volumen_m3")) {
            for (int valor : List.of(0,-1)) fallaSql("UPDATE vehiculo SET " + columna + "=? WHERE id=?",valor,id(v));
            fallaSql("UPDATE vehiculo SET " + columna + "='NaN'::numeric WHERE id=?",id(v));
        }
        fallaSql("UPDATE vehiculo SET modelo_vehiculo_id=? WHERE id=?",UUID.randomUUID(),id(v));
        fallaSql("UPDATE vehiculo SET tipo_vehiculo_id=? WHERE id=?",UUID.randomUUID(),id(v));
        fallaSql("UPDATE modelo_vehiculo SET marca_vehiculo_id=? WHERE id=?",UUID.randomUUID(),id(modelo));
        fallaSql("UPDATE vinculo_vehiculo_empresa SET vehiculo_id=? WHERE id=?",UUID.randomUUID(),id(vinculo));
        fallaSql("UPDATE vinculo_vehiculo_empresa SET empresa_operadora_id=? WHERE id=?",UUID.randomUUID(),id(vinculo));
        fallaSql("UPDATE vinculo_vehiculo_empresa SET fecha_fin=fecha_inicio-1 WHERE id=?",id(vinculo));
        fallaSql("UPDATE marca_vehiculo SET nombre=' ' WHERE id=?",id(marca));
        fallaSql("UPDATE tipo_vehiculo SET codigo=' ' WHERE id=?",id(tipo));
        fallaSql("DELETE FROM vehiculo WHERE id=?",id(v));
        fallaSql("DELETE FROM marca_vehiculo WHERE id=?",id(marca));
        fallaSql("DELETE FROM modelo_vehiculo WHERE id=?",id(modelo));
        fallaSql("DELETE FROM tipo_vehiculo WHERE id=?",id(tipo));
        fallaSql("DELETE FROM empresa_operadora WHERE id=?",empresaId);
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='vehiculo' ORDER BY ordinal_position
                """,String.class)).containsExactly("id","placa","modelo_vehiculo_id","tipo_vehiculo_id","anio","capacidad_carga_kg","capacidad_volumen_m3","activo","creado_en","actualizado_en");
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='vinculo_vehiculo_empresa' ORDER BY ordinal_position
                """,String.class)).containsExactly("id","vehiculo_id","empresa_operadora_id","fecha_inicio","fecha_fin","activo","creado_en","actualizado_en");
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_name='vehiculo' AND column_name='placa'",String.class)).isEqualTo("text");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE contype='f'
                AND conrelid IN ('modelo_vehiculo'::regclass,'vehiculo'::regclass,'vinculo_vehiculo_empresa'::regclass) AND confdeltype <> 'a'
                """,Integer.class)).isZero();
    }
    @Test
    void seguridadLocalCubreVehiculosYCatalogos() throws Exception {
        for (String ruta : List.of("/api/vehiculos","/api/vehiculos/marcas","/api/vehiculos/modelos","/api/vehiculos/tipos")) {
            mvc.perform(get(ruta)).andExpect(status().isForbidden());
            mvc.perform(get(ruta).header("X-GAMW-Development","gamw-desarrollo").header("Origin","https://externo.example")).andExpect(status().isForbidden());
            mvc.perform(get(ruta).header("X-GAMW-Development","gamw-desarrollo").with(r -> { r.setRemoteAddr("192.0.2.1"); return r; })).andExpect(status().isForbidden());
            enviar(get(ruta).header("Origin","http://127.0.0.1:5173"),200);
        }
    }
    private void fallaSql(String sql,Object... parametros) {
        assertThatThrownBy(() -> jdbc.update(sql,parametros)).isInstanceOf(DataIntegrityViolationException.class);
    }
    private static String marcaSintetica() { return "SINTETICA-" + UUID.randomUUID(); }
    private JsonNode marca(String nombre) throws Exception {
        var dato = enviar(conDatos(post("/api/vehiculos/marcas"),Map.of("nombre",nombre)),201); marcasPropias.add(id(dato)); return dato;
    }
    private JsonNode modelo(UUID marcaId,String nombre) throws Exception {
        var dato = enviar(conDatos(post("/api/vehiculos/modelos"),Map.of("marcaVehiculoId",marcaId,"nombre",nombre)),201); modelosPropios.add(id(dato)); return dato;
    }
    private JsonNode tipo(String codigo,String nombre) throws Exception {
        var dato = enviar(conDatos(post("/api/vehiculos/tipos"),Map.of("codigo",codigo,"nombre",nombre)),201); tiposPropios.add(id(dato)); return dato;
    }
    private JsonNode vehiculo(Map<String,?> datos) throws Exception {
        var dato = enviar(conDatos(post("/api/vehiculos"),datos),201); vehiculosPropios.add(id(dato)); return dato;
    }
    private UUID empresa() throws Exception {
        var dato = enviar(conDatos(post("/api/empresas-operadoras"),Map.of("razonSocial",marcaSintetica())),201); empresasPropias.add(id(dato)); return id(dato);
    }
    private static UUID id(JsonNode dato) { return UUID.fromString(dato.get("id").asText()); }
    private static Map<String,Object> datosVinculo(UUID vehiculoId,String inicio) {
        var datos = datosCierre(null,true); datos.put("vehiculoId",vehiculoId); datos.put("fechaInicio",inicio); return datos;
    }
    private static Map<String,Object> datosCierre(String fin,boolean activo) {
        var datos = new HashMap<String,Object>(); datos.put("fechaFin",fin); datos.put("activo",activo); return datos;
    }
    private MockHttpServletRequestBuilder conDatos(MockHttpServletRequestBuilder solicitud,Object datos) {
        return solicitud.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos));
    }
    private JsonNode enviar(MockHttpServletRequestBuilder solicitud,int estado) throws Exception {
        var respuesta = mvc.perform(solicitud.header("X-GAMW-Development","gamw-desarrollo")).andExpect(status().is(estado)).andReturn().getResponse().getContentAsString();
        return respuesta.isBlank() ? null : json.readTree(respuesta);
    }
}
