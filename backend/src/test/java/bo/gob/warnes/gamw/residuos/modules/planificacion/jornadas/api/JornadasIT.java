package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.*;
import java.time.*;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU-11/CU-12; RF-07/RF-08 y RF-20 parcial. Datos exclusivamente sintéticos, PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("desarrollo-local")
class JornadasIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean RepositorioJornadas repositorio;
    private final UUID empresa = UUID.randomUUID(), otraEmpresa = UUID.randomUUID(), periodo = UUID.randomUUID(), ruta = UUID.randomUUID(),
        rv1 = UUID.randomUUID(), rv2 = UUID.randomUUID(), v1 = UUID.randomUUID(), v2 = UUID.randomUUID(), p1 = UUID.randomUUID(), p2 = UUID.randomUUID(),
        vv1 = UUID.randomUUID(), vv2 = UUID.randomUUID(), vp1 = UUID.randomUUID(), vp2 = UUID.randomUUID();
    private String inicio() { return LocalDate.of(2026,5,10).atTime(23,30).atZone(ZoneId.of("America/La_Paz")).toInstant().toString(); }

    @BeforeEach void prepararDatosSinteticos() {
        for (UUID e : List.of(empresa,otraEmpresa)) jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",e,"SINTETICA-" + e);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2026-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"SINTETICA-" + ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z','2026-06-01T00:00:00Z',NULL,now())",rv1,ruta);
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,2,ST_GeomFromText('LINESTRING(-12 22,-13 23)',4326),'2026-06-01T00:00:00Z',NULL,'Cambio sintético',now())",rv2,ruta);
        for (UUID v : List.of(v1,v2)) jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",v,"SINTETICA-" + v.toString().toUpperCase(Locale.ROOT));
        for (UUID p : List.of(p1,p2)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"SINTETICA-" + p,"Persona sintética");
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv1,v1,empresa);
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv2,v2,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp1,p1,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp2,p2,empresa);
        for (UUID vp : List.of(vp1,vp2)) jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp); // Supervisor, sin CONDUCTOR.
    }
    @AfterEach void limpiarSoloDatosPropios() {
        jdbc.update("DELETE FROM asignacion_jornada WHERE jornada_id IN (SELECT id FROM jornada WHERE periodo_servicio_id=?)",periodo);
        jdbc.update("DELETE FROM jornada WHERE periodo_servicio_id=?",periodo);
        jdbc.update("DELETE FROM vinculo_persona_funcion WHERE vinculo_persona_empresa_id IN (?,?)",vp1,vp2);
        jdbc.update("DELETE FROM vinculo_persona_empresa WHERE id IN (?,?)",vp1,vp2);
        jdbc.update("DELETE FROM vinculo_vehiculo_empresa WHERE id IN (?,?)",vv1,vv2);
        jdbc.update("DELETE FROM persona_natural WHERE id IN (?,?)",p1,p2);
        jdbc.update("DELETE FROM vehiculo WHERE id IN (?,?)",v1,v2);
        jdbc.update("DELETE FROM periodo_servicio WHERE id=?",periodo);
        jdbc.update("DELETE FROM empresa_operadora WHERE id IN (?,?)",empresa,otraEmpresa);
        jdbc.update("DELETE FROM ruta_version WHERE ruta_id=?",ruta);
        jdbc.update("DELETE FROM ruta WHERE id=?",ruta);
    }
    @Test void programaConsultaYDerivaEmpresaSinAceptarCamposAlternos() throws Exception {
        var creada = crear();
        assertThat(creada.get("empresaOperadora").get("id").asText()).isEqualTo(empresa.toString());
        assertThat(creada.get("jornada").has("empresaOperadoraId")).isFalse();
        assertThat(creada.get("jornada").get("rutaVersionId").asText()).isEqualTo(rv1.toString());
        assertThat(creada.get("jornada").get("finProgramado").isNull()).isTrue();
        enviar(get("/api/jornadas/" + id(creada)),200);
        assertThat(enviar(get("/api/jornadas"),200).toString()).contains(id(creada));
        assertThat(enviar(get("/api/periodos-servicio"),200).toString()).contains(periodo.toString(),empresa.toString());
        for (String campo : List.of("empresaOperadoraId","vehiculoId","personaId","estado","rutaId","jornadaAntecedenteId")) {
            var d = programacion(inicio(),rv1); d.put(campo,UUID.randomUUID());
            enviar(datos(post("/api/jornadas"),d),400);
        }
        enviar(put("/api/jornadas/" + id(creada)),405);
        enviar(delete("/api/jornadas/" + id(creada)),405);
    }
    @Test void validaPeriodoVersionIntervaloYFinQueCruzaMedianoche() throws Exception {
        var d = programacion(inicio(),rv1);
        d.put("finProgramado",Instant.parse(inicio()).plusSeconds(7200).toString());
        enviar(datos(post("/api/jornadas"),d),201);
        d.put("finProgramado",inicio()); enviar(datos(post("/api/jornadas"),d),400);
        d.remove("finProgramado"); d.put("inicioProgramado",null); enviar(datos(post("/api/jornadas"),d),400);
        enviar(datos(post("/api/jornadas"),programacion("2027-01-10T12:00:00Z",rv2)),400);
        enviar(datos(post("/api/jornadas"),programacion(inicio(),rv2)),400);
        d = programacion(inicio(),rv1); d.put("periodoServicioId",UUID.randomUUID()); enviar(datos(post("/api/jornadas"),d),404);
        jdbc.update("UPDATE periodo_servicio SET activo=false WHERE id=?",periodo);
        enviar(datos(post("/api/jornadas"),programacion(inicio(),rv1)),400);
    }
    @Test void conservaCadenaJ1J2J3SinCopiarAsignacion() throws Exception {
        var j1 = crear(); String id1 = id(j1);
        enviar(datos(post("/api/jornadas/" + id1 + "/asignaciones"),recursos(vv1,vp1)),201);
        var d = programacion("2026-07-10T12:00:00Z",rv2); d.put("motivoReprogramacion","  Cambio sintético  ");
        var j2 = enviar(datos(post("/api/jornadas/" + id1 + "/reprogramaciones"),d),201);
        assertThat(id(j2)).isNotEqualTo(id1);
        assertThat(j2.get("jornada").get("jornadaAntecedenteId").asText()).isEqualTo(id1);
        assertThat(j2.get("jornada").get("motivoReprogramacion").asText()).isEqualTo("Cambio sintético");
        assertThat(j2.get("asignacionActual").isNull()).isTrue();
        var anterior = enviar(get("/api/jornadas/" + id1),200);
        assertThat(anterior.get("jornada").get("vigente").asBoolean()).isFalse();
        for (String campo : List.of("periodoServicioId","rutaVersionId","inicioProgramado","finProgramado","creadoEn")) {
            assertThat(anterior.get("jornada").get(campo)).isEqualTo(j1.get("jornada").get(campo));
        }
        assertThat(enviar(get("/api/jornadas/" + id1 + "/asignaciones"),200).size()).isEqualTo(1);
        var j3 = enviar(datos(post("/api/jornadas/" + id(j2) + "/reprogramaciones"),d),201);
        assertThat(j3.get("jornada").get("jornadaAntecedenteId").asText()).isEqualTo(id(j2));
        enviar(datos(post("/api/jornadas/" + id1 + "/reprogramaciones"),d),409);
        enviar(datos(post("/api/jornadas/" + id1 + "/asignaciones"),recursos(vv1,vp1)),409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jornada WHERE periodo_servicio_id=?",Integer.class,periodo)).isEqualTo(3);
    }
    @Test void conservaAsignacionesA1A2A3ConSupervisorComoResponsable() throws Exception {
        String id = id(crear()); String base = "/api/jornadas/" + id;
        var a1 = enviar(datos(post(base + "/asignaciones"),recursos(vv1,vp1)),201);
        assertThat(a1.get("motivoCambio").isNull()).isTrue();
        enviar(datos(post(base + "/asignaciones"),recursos(vv1,vp1)),409);
        var d = recursos(vv1,vp2); d.put("motivoCambio","  Cambio de persona  ");
        var a2 = enviar(datos(post(base + "/reasignaciones"),d),201);
        d = recursos(vv2,vp2); d.put("motivoCambio","Cambio de vehículo");
        var a3 = enviar(datos(post(base + "/reasignaciones"),d),201);
        var historia = enviar(get(base + "/asignaciones"),200);
        assertThat(historia.size()).isEqualTo(3);
        assertThat(historia.get(0).get("id")).isEqualTo(a1.get("id"));
        assertThat(historia.get(0).get("vinculoVehiculoEmpresaId")).isEqualTo(a1.get("vinculoVehiculoEmpresaId"));
        assertThat(historia.get(0).get("vinculoPersonaEmpresaId")).isEqualTo(a1.get("vinculoPersonaEmpresaId"));
        assertThat(historia.get(1).get("id")).isEqualTo(a2.get("id"));
        assertThat(historia.get(1).get("motivoCambio").asText()).isEqualTo("Cambio de persona");
        assertThat(historia.get(0).get("vigenteHasta")).isEqualTo(a2.get("vigenteDesde"));
        assertThat(historia.get(1).get("vigenteHasta")).isEqualTo(a3.get("vigenteDesde"));
        assertThat(historia.get(2).get("vigenteHasta").isNull()).isTrue();
        assertThat(enviar(get(base),200).get("asignacionActual").get("id")).isEqualTo(a3.get("id"));
    }
    @Test void rechazaEmpresaIncorrectaEnCualquieraDeLosDosVinculos() throws Exception {
        String base = "/api/jornadas/" + id(crear()) + "/asignaciones";
        jdbc.update("UPDATE vinculo_vehiculo_empresa SET empresa_operadora_id=? WHERE id=?",otraEmpresa,vv1);
        enviar(datos(post(base),recursos(vv1,vp1)),400);
        jdbc.update("UPDATE vinculo_vehiculo_empresa SET empresa_operadora_id=? WHERE id=?",empresa,vv1);
        jdbc.update("UPDATE vinculo_persona_empresa SET empresa_operadora_id=? WHERE id=?",otraEmpresa,vp1);
        enviar(datos(post(base),recursos(vv1,vp1)),400);
        jdbc.update("UPDATE vinculo_persona_empresa SET empresa_operadora_id=? WHERE id=?",empresa,vp1);
        enviar(datos(post(base),recursos(vv1,vp1)),201);
    }
    @Test void rechazaVinculosFueraDeFechaYRecursosInactivos() throws Exception {
        String base = "/api/jornadas/" + id(crear()) + "/asignaciones";
        jdbc.update("UPDATE vinculo_vehiculo_empresa SET fecha_fin=DATE '2026-04-01' WHERE id=?",vv1);
        enviar(datos(post(base),recursos(vv1,vp1)),400);
        jdbc.update("UPDATE vinculo_vehiculo_empresa SET fecha_fin=NULL WHERE id=?",vv1);
        jdbc.update("UPDATE vinculo_persona_empresa SET fecha_inicio=DATE '2026-06-01' WHERE id=?",vp1);
        enviar(datos(post(base),recursos(vv1,vp1)),400);
        jdbc.update("UPDATE vinculo_persona_empresa SET fecha_inicio=DATE '2026-01-01' WHERE id=?",vp1);
        for (var entrada : Map.of("vehiculo",v1,"persona_natural",p1,"vinculo_vehiculo_empresa",vv1,"vinculo_persona_empresa",vp1).entrySet()) {
            jdbc.update("UPDATE " + entrada.getKey() + " SET activo=false WHERE id=?",entrada.getValue());
            enviar(datos(post(base),recursos(vv1,vp1)),400);
            jdbc.update("UPDATE " + entrada.getKey() + " SET activo=true WHERE id=?",entrada.getValue());
        }
        enviar(datos(post(base),recursos(vv1,vp1)),201);
    }
    @Test void motivosYPrecondicionesSonObligatorios() throws Exception {
        String base = "/api/jornadas/" + id(crear());
        var d = programacion(inicio(),rv1);
        enviar(datos(post(base + "/reprogramaciones"),d),400);
        d.put("motivoReprogramacion"," "); enviar(datos(post(base + "/reprogramaciones"),d),400);
        var r = recursos(vv1,vp1);
        enviar(datos(post(base + "/reasignaciones"),r),400);
        r.put("motivoCambio","Cambio"); enviar(datos(post(base + "/reasignaciones"),r),409);
        enviar(datos(post(base + "/asignaciones"),recursos(vv1,vp1)),201);
        r.put("motivoCambio"," "); enviar(datos(post(base + "/reasignaciones"),r),400);
        r = recursos(vv1,vp1); r.put("vehiculoId",v1); enviar(datos(post(base + "/asignaciones"),r),400);
    }
    @Test void rollbackDeReasignacionYReprogramacionPreservaAntecedentes() throws Exception {
        String id = id(crear()), base = "/api/jornadas/" + id;
        enviar(datos(post(base + "/asignaciones"),recursos(vv1,vp1)),201);
        var antes = enviar(get(base + "/asignaciones"),200);
        doThrow(new DataIntegrityViolationException("Fallo sintético de inserción")).when(repositorio).insertarAsignacion(any());
        var r = recursos(vv2,vp2); r.put("motivoCambio","Cambio sintético");
        enviar(datos(post(base + "/reasignaciones"),r),409);
        assertThat(enviar(get(base + "/asignaciones"),200)).isEqualTo(antes);
        doCallRealMethod().when(repositorio).insertarAsignacion(any());
        doThrow(new DataIntegrityViolationException("Fallo sintético de inserción")).when(repositorio).insertar(any(Jornada.class));
        var d = programacion(inicio(),rv1); d.put("motivoReprogramacion","Cambio sintético");
        enviar(datos(post(base + "/reprogramaciones"),d),409);
        assertThat(enviar(get(base),200).get("jornada").get("vigente").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jornada WHERE periodo_servicio_id=?",Integer.class,periodo)).isEqualTo(1);
        doCallRealMethod().when(repositorio).insertar(any(Jornada.class));
    }
    @Test void concurrenciaImpideDobleAsignacionInicialYBifurcarReprogramacion() throws Exception {
        String id = id(crear()), base = "/api/jornadas/" + id;
        String cuerpo = json.writeValueAsString(recursos(vv1,vp1));
        assertThat(concurrentes(base + "/asignaciones",cuerpo)).containsExactlyInAnyOrder(201,409);
        var d = programacion(inicio(),rv1); d.put("motivoReprogramacion","Reprogramación concurrente");
        assertThat(concurrentes(base + "/reprogramaciones",json.writeValueAsString(d))).containsExactlyInAnyOrder(201,409);
    }
    @Test void postgresImponeTiempoClavesYProtegeIdentidadHistorica() throws Exception {
        var j = crear(); UUID jornada = UUID.fromString(id(j));
        var a = enviar(datos(post("/api/jornadas/" + jornada + "/asignaciones"),recursos(vv1,vp1)),201);
        UUID asignacion = UUID.fromString(a.get("id").asText());
        falla("UPDATE jornada SET inicio_programado=inicio_programado+interval '1 hour' WHERE id=?",jornada);
        falla("UPDATE jornada SET periodo_servicio_id=? WHERE id=?",UUID.randomUUID(),jornada);
        falla("UPDATE asignacion_jornada SET vinculo_persona_empresa_id=? WHERE id=?",vp2,asignacion);
        falla("UPDATE asignacion_jornada SET vinculo_vehiculo_empresa_id=? WHERE id=?",vv2,asignacion);
        falla("INSERT INTO asignacion_jornada SELECT ?,jornada_id,vinculo_vehiculo_empresa_id,vinculo_persona_empresa_id,vigente_desde,NULL,NULL,creado_en FROM asignacion_jornada WHERE id=?",UUID.randomUUID(),asignacion);
        falla("INSERT INTO asignacion_jornada SELECT ?,jornada_id,vinculo_vehiculo_empresa_id,vinculo_persona_empresa_id,vigente_desde,vigente_desde-interval '1 second',NULL,creado_en FROM asignacion_jornada WHERE id=?",UUID.randomUUID(),asignacion);
        falla("INSERT INTO asignacion_jornada SELECT ?,jornada_id,?,vinculo_persona_empresa_id,vigente_desde,vigente_desde,NULL,creado_en FROM asignacion_jornada WHERE id=?",UUID.randomUUID(),UUID.randomUUID(),asignacion);
        falla("INSERT INTO jornada SELECT ?,periodo_servicio_id,ruta_version_id,inicio_programado,inicio_programado,NULL,NULL,true,creado_en,actualizado_en FROM jornada WHERE id=?",UUID.randomUUID(),jornada);
        UUID mismo = UUID.randomUUID();
        falla("INSERT INTO jornada SELECT ?,periodo_servicio_id,ruta_version_id,inicio_programado,NULL,?,'Motivo',true,creado_en,actualizado_en FROM jornada WHERE id=?",mismo,mismo,jornada);
        falla("INSERT INTO jornada SELECT ?,?,ruta_version_id,inicio_programado,NULL,NULL,NULL,true,creado_en,actualizado_en FROM jornada WHERE id=?",UUID.randomUUID(),UUID.randomUUID(),jornada);
        falla("INSERT INTO jornada SELECT ?,periodo_servicio_id,?,inicio_programado,NULL,NULL,NULL,true,creado_en,actualizado_en FROM jornada WHERE id=?",UUID.randomUUID(),UUID.randomUUID(),jornada);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name='jornada' ORDER BY ordinal_position",String.class))
            .containsExactly("id","periodo_servicio_id","ruta_version_id","inicio_programado","fin_programado","jornada_antecedente_id","motivo_reprogramacion","vigente","creado_en","actualizado_en");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name='asignacion_jornada' ORDER BY ordinal_position",String.class))
            .containsExactly("id","jornada_id","vinculo_vehiculo_empresa_id","vinculo_persona_empresa_id","vigente_desde","vigente_hasta","motivo_cambio","creado_en");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE contype='f' AND conrelid IN ('jornada'::regclass,'asignacion_jornada'::regclass) AND confdeltype <> 'a'",Integer.class)).isZero();
    }
    @Test void accesoLocalYAusenciaDeActualizacionGenerica() throws Exception {
        for (String ruta : List.of("/api/jornadas","/api/periodos-servicio")) {
            mvc.perform(get(ruta)).andExpect(status().isForbidden());
            mvc.perform(get(ruta).header("X-GAMW-Development","gamw-desarrollo").header("Origin","https://externo.example")).andExpect(status().isForbidden());
            mvc.perform(get(ruta).header("X-GAMW-Development","gamw-desarrollo").with(r -> { r.setRemoteAddr("192.0.2.1"); return r; })).andExpect(status().isForbidden());
            enviar(get(ruta),200);
        }
    }
    private List<Integer> concurrentes(String ruta,String cuerpo) throws Exception {
        var inicio = new CountDownLatch(1); var listos = new CountDownLatch(2);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> tarea = () -> {
                listos.countDown(); if (!inicio.await(10,TimeUnit.SECONDS)) throw new AssertionError("Tiempo agotado");
                return mvc.perform(post(ruta).header("X-GAMW-Development","gamw-desarrollo").contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn().getResponse().getStatus();
            };
            var a = ejecutor.submit(tarea); var b = ejecutor.submit(tarea);
            assertThat(listos.await(10,TimeUnit.SECONDS)).isTrue(); inicio.countDown();
            return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    private void falla(String sql,Object... argumentos) { assertThatThrownBy(() -> jdbc.update(sql,argumentos)).isInstanceOf(DataIntegrityViolationException.class); }
    private JsonNode crear() throws Exception { return enviar(datos(post("/api/jornadas"),programacion(inicio(),rv1)),201); }
    private String id(JsonNode vista) { return vista.get("jornada").get("id").asText(); }
    private Map<String,Object> programacion(String desde,UUID version) {
        var d = new HashMap<String,Object>(); d.put("periodoServicioId",periodo); d.put("rutaVersionId",version); d.put("inicioProgramado",desde); return d;
    }
    private Map<String,Object> recursos(UUID v,UUID p) { return new HashMap<>(Map.of("vinculoVehiculoEmpresaId",v,"vinculoPersonaEmpresaId",p)); }
    private MockHttpServletRequestBuilder datos(MockHttpServletRequestBuilder solicitud,Object cuerpo) { return solicitud.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)); }
    private JsonNode enviar(MockHttpServletRequestBuilder solicitud,int esperado) throws Exception {
        var cuerpo = mvc.perform(solicitud.header("X-GAMW-Development","gamw-desarrollo")).andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return cuerpo.isBlank() ? null : json.readTree(cuerpo);
    }
}
