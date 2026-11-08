package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

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

/** CU-04/CU-05/CU-07 parcial; RF-03/RF-04 parcial. PostgreSQL real; limpieza limitada a identificadores propios. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("desarrollo-local")
class RegistroOperativoIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private final Set<UUID> empresasPropias = new HashSet<>();
    private final Set<UUID> personasPropias = new HashSet<>();

    @AfterEach
    void eliminarSoloDatosSinteticosPropios() {
        for (var id : empresasPropias) {
            jdbc.update("DELETE FROM vinculo_persona_funcion WHERE vinculo_persona_empresa_id IN (SELECT id FROM vinculo_persona_empresa WHERE empresa_operadora_id=?)",id);
            jdbc.update("DELETE FROM vinculo_persona_empresa WHERE empresa_operadora_id=?",id);
            jdbc.update("DELETE FROM periodo_servicio WHERE empresa_operadora_id=?",id);
            jdbc.update("DELETE FROM empresa_operadora WHERE id=?",id);
        }
        for (var id : personasPropias) jdbc.update("DELETE FROM persona_natural WHERE id=?",id);
    }

    @Test
    void registraActualizaYDesactivaPersonaConCiTextual() throws Exception {
        var p = persona();
        var id = id(p);
        assertThat(p.get("ci").isTextual()).isTrue();
        assertThat(p.get("apellidoPaterno").isNull()).isTrue();
        assertThat(p.get("apellidoMaterno").isNull()).isTrue();
        enviar(get("/api/personas/" + id),200);
        assertThat(enviar(get("/api/personas"),200).toString()).contains(id.toString());
        var datos = datosPersona(p.get("ci").asText());
        datos.put("nombres","Persona sintética actualizada");
        datos.put("telefono","00123-A");
        var actualizada = enviar(conDatos(put("/api/personas/" + id),datos),200);
        assertThat(actualizada.get("telefono").asText()).isEqualTo("00123-A");
        assertThat(actualizada.get("ci")).isEqualTo(p.get("ci"));
        for (boolean activo : List.of(false,true)) {
            assertThat(enviar(conDatos(patch("/api/personas/" + id + "/estado"),Map.of("activo",activo)),200).get("activo").asBoolean()).isEqualTo(activo);
        }
        assertThat(enviar(get("/api/personas/" + id),200).get("creadoEn")).isEqualTo(p.get("creadoEn"));
    }

    @Test
    void rechazaCiDuplicadoYDatosPersonalesVacios() throws Exception {
        var p = persona();
        enviar(conDatos(post("/api/personas"),datosPersona(p.get("ci").asText())),409);
        for (var campo : List.of("ci","nombres")) {
            var datos = datosPersona(UUID.randomUUID().toString());
            datos.put(campo," ");
            enviar(conDatos(post("/api/personas"),datos),400);
        }
        enviar(conDatos(patch("/api/personas/" + id(p) + "/estado"),Map.of()),400);
    }

    @Test
    void registraActualizaYDesactivaEmpresaConNitOpcional() throws Exception {
        var e = empresa(null);
        var otra = empresa(null);
        assertThat(otra.get("nit").isNull()).isTrue();
        var id = id(e);
        var actualizada = enviar(conDatos(put("/api/empresas-operadoras/" + id),datosEmpresa("000-" + UUID.randomUUID(),"Empresa sintética actualizada")),200);
        assertThat(actualizada.get("razonSocial").asText()).isEqualTo("Empresa sintética actualizada");
        assertThat(actualizada.get("creadoEn")).isEqualTo(e.get("creadoEn"));
        enviar(get("/api/empresas-operadoras/" + id),200);
        assertThat(enviar(get("/api/empresas-operadoras"),200).toString()).contains(id.toString());
        for (boolean activo : List.of(false,true)) {
            assertThat(enviar(conDatos(patch("/api/empresas-operadoras/" + id + "/estado"),Map.of("activo",activo)),200).get("activo").asBoolean()).isEqualTo(activo);
        }
    }

    @Test
    void rechazaNitDuplicadoVacioYRazonSocialVacia() throws Exception {
        var nit = "000-" + UUID.randomUUID();
        empresa(nit);
        enviar(conDatos(post("/api/empresas-operadoras"),datosEmpresa(nit,"Prueba sintética")),409);
        enviar(conDatos(post("/api/empresas-operadoras"),datosEmpresa(" ","Prueba sintética")),400);
        enviar(conDatos(post("/api/empresas-operadoras"),datosEmpresa(null," ")),400);
    }

    @Test
    void periodosAdmitenSolapamientoYPreservanRegistrosInactivos() throws Exception {
        var empresaId = id(empresa(null));
        var ruta = "/api/empresas-operadoras/" + empresaId + "/periodos";
        var p = enviar(conDatos(post(ruta),fechas("2026-01-01","2026-12-31")),201);
        enviar(conDatos(post(ruta),fechas("2026-01-01","2026-01-01")),201);
        var periodoId = id(p);
        enviar(conDatos(put(ruta + "/" + periodoId),fechas("2026-01-01","2027-01-01")),200);
        assertThat(enviar(conDatos(patch(ruta + "/" + periodoId + "/estado"),Map.of("activo",false)),200).get("activo").asBoolean()).isFalse();
        assertThat(enviar(get(ruta),200).size()).isEqualTo(2);
        enviar(conDatos(post(ruta),fechas("2026-02-01","2026-01-01")),400);
        enviar(conDatos(post("/api/empresas-operadoras/" + UUID.randomUUID() + "/periodos"),fechas("2026-01-01","2026-01-02")),404);
        var otraEmpresa = id(empresa(null));
        enviar(conDatos(put("/api/empresas-operadoras/" + otraEmpresa + "/periodos/" + periodoId),fechas("2026-01-01","2026-01-02")),404);
    }

    @Test
    void vinculosHistoricosYFuncionesNoAlteranIdentidadPersonal() throws Exception {
        var p = persona();
        var empresaId = id(empresa(null));
        var ruta = "/api/empresas-operadoras/" + empresaId + "/personal";
        var funciones = enviar(get("/api/empresas-operadoras/funciones-operativas"),200);
        assertThat(funciones.toString()).contains("CONDUCTOR","SUPERVISOR_EMPRESA");
        var funcionIds = new ArrayList<String>();
        funciones.forEach(f -> funcionIds.add(f.get("id").asText()));
        var datos = datosVinculo(id(p),funcionIds);
        var v = enviar(conDatos(post(ruta),datos),201);
        assertThat(v.get("funcionesOperativas").size()).isEqualTo(2);
        datos.remove("personaId");
        datos.remove("funcionOperativaIds");
        datos.put("fechaFin","2026-02-01");
        datos.put("activo",false);
        var cerrado = enviar(conDatos(put(ruta + "/" + id(v)),datos),200);
        assertThat(cerrado.get("activo").asBoolean()).isFalse();
        assertThat(cerrado.get("fechaFin").asText()).isEqualTo("2026-02-01");
        assertThat(cerrado.get("creadoEn")).isEqualTo(v.get("creadoEn"));
        assertThat(cerrado.get("funcionesOperativas")).isEqualTo(v.get("funcionesOperativas"));
        assertThat(enviar(get("/api/personas/" + id(p)),200)).isEqualTo(p);
        var segundo = enviar(conDatos(post(ruta),datosVinculo(id(p),List.of(funcionIds.getFirst()))),201);
        assertThat(segundo.get("funcionesOperativas").size()).isEqualTo(1);
        var historial = enviar(get(ruta),200);
        var primero = java.util.stream.StreamSupport.stream(historial.spliterator(),false)
                .filter(n -> n.get("id").equals(v.get("id"))).findFirst().orElseThrow();
        assertThat(primero.get("funcionesOperativas")).isEqualTo(v.get("funcionesOperativas"));
        var otraEmpresa = id(empresa(null));
        enviar(conDatos(post("/api/empresas-operadoras/" + otraEmpresa + "/personal"),datosVinculo(id(p),funcionIds)),201);
        assertThat(enviar(get(ruta),200).size()).isEqualTo(2);
        enviar(conDatos(put("/api/empresas-operadoras/" + otraEmpresa + "/personal/" + id(v)),datos),404);
    }

    @Test
    void vinculosValidanReferenciasFechasYFuncionesConRollbackAtomico() throws Exception {
        var personaId = id(persona());
        var empresaId = id(empresa(null));
        var ruta = "/api/empresas-operadoras/" + empresaId + "/personal";
        var datos = datosVinculo(personaId,List.of(UUID.randomUUID().toString()));
        enviar(conDatos(post(ruta),datos),400);
        assertThat(enviar(get(ruta),200).size()).isZero();
        datos.put("funcionOperativaIds",List.of("00000000-0000-4000-8000-000000000001"));
        datos.put("fechaFin","2025-12-31");
        enviar(conDatos(post(ruta),datos),400);
        datos.put("fechaFin",null);
        datos.put("personaId",UUID.randomUUID());
        enviar(conDatos(post(ruta),datos),404);
        enviar(conDatos(post("/api/empresas-operadoras/" + UUID.randomUUID() + "/personal"),datosVinculo(personaId,List.of("00000000-0000-4000-8000-000000000001"))),404);
        var v = enviar(conDatos(post(ruta),datosVinculo(personaId,List.of("00000000-0000-4000-8000-000000000001"))),201);
        var antes = enviar(get(ruta),200);
        datos = datosVinculo(personaId,List.of(UUID.randomUUID().toString()));
        datos.remove("personaId");
        datos.put("activo",false);
        assertThat(enviar(conDatos(put(ruta + "/" + id(v)),datos),400).get("detail").asText()).contains("históricas", "cree uno nuevo");
        assertThat(enviar(get(ruta),200)).isEqualTo(antes);
    }

    @Test
    void desactivarEmpresaNoReescribePersonasPeriodosNiVinculos() throws Exception {
        var p = persona();
        var empresaId = id(empresa(null));
        var ruta = "/api/empresas-operadoras/" + empresaId;
        enviar(conDatos(post(ruta + "/periodos"),fechas("2026-01-01","2026-12-31")),201);
        enviar(conDatos(post(ruta + "/personal"),datosVinculo(id(p),List.of("00000000-0000-4000-8000-000000000001"))),201);
        var personalAntes = enviar(get(ruta + "/personal"),200);
        var periodosAntes = enviar(get(ruta + "/periodos"),200);
        enviar(conDatos(patch(ruta + "/estado"),Map.of("activo",false)),200);
        assertThat(enviar(get(ruta + "/personal"),200)).isEqualTo(personalAntes);
        assertThat(enviar(get(ruta + "/periodos"),200)).isEqualTo(periodosAntes);
        assertThat(enviar(get("/api/personas/" + id(p)),200)).isEqualTo(p);
    }

    @Test
    void exigeFuncionesInicialesActivasNoRepetidas() throws Exception {
        var personaId = id(persona());
        var empresaId = id(empresa(null));
        var ruta = "/api/empresas-operadoras/" + empresaId + "/personal";
        String conductor = "00000000-0000-4000-8000-000000000001";
        var datos = datosVinculo(personaId,List.of());
        enviar(conDatos(post(ruta),datos),400);
        datos.put("funcionOperativaIds",null);
        enviar(conDatos(post(ruta),datos),400);
        datos.remove("funcionOperativaIds");
        enviar(conDatos(post(ruta),datos),400);
        datos.put("funcionOperativaIds",List.of(conductor,conductor));
        assertThat(enviar(conDatos(post(ruta),datos),400).get("detail").asText()).contains("repetirse");
        var inactiva = UUID.randomUUID();
        jdbc.update("INSERT INTO funcion_operativa(id,codigo,nombre,activo) VALUES (?,?,?,false)",
                inactiva,"SINTETICA-" + inactiva,"Función sintética inactiva");
        try {
            datos.put("funcionOperativaIds",List.of(inactiva.toString()));
            enviar(conDatos(post(ruta),datos),400);
            assertThat(enviar(get(ruta),200).size()).isZero();
        } finally {
            jdbc.update("DELETE FROM funcion_operativa WHERE id=?",inactiva);
        }
        var creado = enviar(conDatos(post(ruta),datosVinculo(personaId,List.of(conductor))),201);
        assertThat(creado.get("funcionesOperativas").size()).isEqualTo(1);
        var antes = enviar(get(ruta),200);
        for (String campo : List.of("funcionOperativaIds","funcionesOperativas")) {
            var cambio = fechas("2026-01-01","2026-05-31");
            cambio.put("activo",false);
            cambio.put(campo,null);
            assertThat(enviar(conDatos(put(ruta + "/" + id(creado)),cambio),400).get("detail").asText()).contains("históricas");
        }
        var cambioInicio = fechas("2026-01-02","2026-05-31");
        cambioInicio.put("activo",false);
        enviar(conDatos(put(ruta + "/" + id(creado)),cambioInicio),400);
        assertThat(enviar(get(ruta),200)).isEqualTo(antes);
    }

    @Test
    void postgresImponeIntegridadInclusoFueraDeLaApi() throws Exception {
        var p = persona();
        var empresaId = id(empresa(null));
        var v = enviar(conDatos(post("/api/empresas-operadoras/" + empresaId + "/personal"),datosVinculo(id(p),List.of("00000000-0000-4000-8000-000000000001"))),201);
        fallaSql("INSERT INTO vinculo_persona_funcion VALUES (?,?)",id(v),UUID.fromString("00000000-0000-4000-8000-000000000001"));
        fallaSql("INSERT INTO vinculo_persona_funcion VALUES (?,?)",id(v),UUID.randomUUID());
        fallaSql("UPDATE persona_natural SET ci=' ' WHERE id=?",id(p));
        fallaSql("UPDATE persona_natural SET nombres=' ' WHERE id=?",id(p));
        fallaSql("UPDATE empresa_operadora SET razon_social=' ' WHERE id=?",empresaId);
        fallaSql("UPDATE empresa_operadora SET nit=' ' WHERE id=?",empresaId);
        fallaSql("UPDATE vinculo_persona_empresa SET fecha_fin=fecha_inicio-1 WHERE id=?",id(v));
        fallaSql("UPDATE vinculo_persona_empresa SET persona_id=? WHERE id=?",UUID.randomUUID(),id(v));
        fallaSql("UPDATE vinculo_persona_empresa SET empresa_operadora_id=? WHERE id=?",UUID.randomUUID(),id(v));
        var periodo = enviar(conDatos(post("/api/empresas-operadoras/" + empresaId + "/periodos"),fechas("2026-01-01","2026-01-02")),201);
        fallaSql("UPDATE periodo_servicio SET fecha_fin=fecha_inicio-1 WHERE id=?",id(periodo));
        fallaSql("UPDATE periodo_servicio SET empresa_operadora_id=? WHERE id=?",UUID.randomUUID(),id(periodo));
        fallaSql("DELETE FROM persona_natural WHERE id=?",id(p));
        fallaSql("DELETE FROM empresa_operadora WHERE id=?",empresaId);
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_name='persona_natural' AND column_name='ci'",String.class)).isEqualTo("text");
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_name='empresa_operadora' AND column_name='nit'",String.class)).isEqualTo("text");
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.responsable_operativo')",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='persona_natural' AND column_name='empresa_operadora_id'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE contype='f' AND conrelid IN ('periodo_servicio'::regclass,'vinculo_persona_empresa'::regclass,'vinculo_persona_funcion'::regclass) AND confdeltype <> 'a'",Integer.class)).isZero();
    }

    @Test
    void seguridadLocalYAusenciaDeBorradoSeAplicanATodoElRegistro() throws Exception {
        for (var ruta : List.of("/api/personas","/api/empresas-operadoras","/api/empresas-operadoras/funciones-operativas")) {
            mvc.perform(get(ruta)).andExpect(status().isForbidden());
            mvc.perform(get(ruta).header("X-GAMW-Development","gamw-desarrollo").header("Origin","https://externo.example")).andExpect(status().isForbidden());
            mvc.perform(get(ruta).header("X-GAMW-Development","gamw-desarrollo").with(r -> { r.setRemoteAddr("192.0.2.1"); return r; })).andExpect(status().isForbidden());
            enviar(get(ruta).header("Origin","http://127.0.0.1:5173"),200);
        }
        var p = persona();
        enviar(delete("/api/personas/" + id(p)),405);
        assertThat(enviar(get("/api/personas/" + id(p)),200)).isEqualTo(p);
        enviar(get("/api/personas/" + UUID.randomUUID()),404);
    }

    private void fallaSql(String sql, Object... argumentos) {
        assertThatThrownBy(() -> jdbc.update(sql,argumentos)).isInstanceOf(DataIntegrityViolationException.class);
    }
    private JsonNode persona() throws Exception {
        var p = enviar(conDatos(post("/api/personas"),datosPersona("SINTETICA-" + UUID.randomUUID() + "-1A")),201);
        personasPropias.add(id(p)); return p;
    }
    private JsonNode empresa(String nit) throws Exception {
        var e = enviar(conDatos(post("/api/empresas-operadoras"),datosEmpresa(nit,"Empresa sintética " + UUID.randomUUID())),201);
        empresasPropias.add(id(e)); return e;
    }
    private static UUID id(JsonNode nodo) { return UUID.fromString(nodo.get("id").asText()); }
    private static Map<String,Object> datosPersona(String ci) {
        var d = new HashMap<String,Object>(); d.put("ci",ci); d.put("nombres","Persona sintética"); return d;
    }
    private static Map<String,Object> datosEmpresa(String nit, String razonSocial) {
        var d = new HashMap<String,Object>(); d.put("nit",nit); d.put("razonSocial",razonSocial); return d;
    }
    private static Map<String,Object> fechas(String inicio,String fin) {
        var d = new HashMap<String,Object>(); d.put("fechaInicio",inicio); d.put("fechaFin",fin); return d;
    }
    private static Map<String,Object> datosVinculo(UUID personaId,List<String> funciones) {
        var d = fechas("2026-01-01",null); d.put("personaId",personaId); d.put("activo",true); d.put("funcionOperativaIds",funciones); return d;
    }
    private MockHttpServletRequestBuilder conDatos(MockHttpServletRequestBuilder solicitud,Object datos) {
        return solicitud.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos));
    }
    private JsonNode enviar(MockHttpServletRequestBuilder solicitud,int codigo) throws Exception {
        var respuesta = mvc.perform(solicitud.header("X-GAMW-Development","gamw-desarrollo")).andExpect(status().is(codigo)).andReturn().getResponse();
        var contenido = respuesta.getContentAsString();
        return contenido.isBlank() ? null : json.readTree(contenido);
    }
}
