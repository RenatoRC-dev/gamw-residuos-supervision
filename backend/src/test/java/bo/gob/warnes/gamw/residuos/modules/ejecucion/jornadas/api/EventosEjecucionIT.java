package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
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

/** RF-09 técnico / CU-13 parcial. Solo evidencia y registros sintéticos propios. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("desarrollo-local")
class EventosEjecucionIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    private final UUID empresa=UUID.randomUUID(), periodo=UUID.randomUUID(), ruta=UUID.randomUUID(), version=UUID.randomUUID(),
        v1=UUID.randomUUID(), v2=UUID.randomUUID(), p1=UUID.randomUUID(), p2=UUID.randomUUID(), vv1=UUID.randomUUID(), vv2=UUID.randomUUID(),
        vp1=UUID.randomUUID(), vp2=UUID.randomUUID(), j1=UUID.randomUUID(), j2=UUID.randomUUID(), a1=UUID.randomUUID(), otraAsignacion=UUID.randomUUID();
    private final Instant captura = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.MICROS);
    private String inicio() { return "/api/jornadas/" + j1 + "/ejecucion/inicio"; }
    private String fin() { return "/api/jornadas/" + j1 + "/ejecucion/fin"; }

    @BeforeEach void preparar() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"SINTETICA-" + empresa);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2027-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"SINTETICA-" + ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        for (var v : List.of(v1,v2)) jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",v,"SINTETICA-" + v.toString().toUpperCase(Locale.ROOT));
        for (var p : List.of(p1,p2)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"SINTETICA-" + p,"Responsable sintético");
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv1,v1,empresa);
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv2,v2,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp1,p1,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp2,p2,empresa);
        for (var vp : List.of(vp1,vp2)) jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);
        for (var j : List.of(j1,j2)) jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-10T12:00:00Z',NULL,NULL,NULL,true,now(),now())",j,periodo,version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",a1,j1,vv1,vp1,Timestamp.from(captura.minusSeconds(3600)));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",otraAsignacion,j2,vv2,vp2,Timestamp.from(captura.minusSeconds(3600)));
    }
    @AfterEach void limpiar() {
        jdbc.update("DELETE FROM evento_ejecucion_jornada WHERE jornada_id IN (SELECT id FROM jornada WHERE periodo_servicio_id=?)",periodo);
        jdbc.update("DELETE FROM asignacion_jornada WHERE jornada_id IN (SELECT id FROM jornada WHERE periodo_servicio_id=?)",periodo);
        jdbc.update("DELETE FROM jornada WHERE periodo_servicio_id=?",periodo);
        jdbc.update("DELETE FROM vinculo_persona_funcion WHERE vinculo_persona_empresa_id IN (?,?)",vp1,vp2);
        jdbc.update("DELETE FROM vinculo_persona_empresa WHERE id IN (?,?)",vp1,vp2);
        jdbc.update("DELETE FROM vinculo_vehiculo_empresa WHERE id IN (?,?)",vv1,vv2);
        jdbc.update("DELETE FROM persona_natural WHERE id IN (?,?)",p1,p2);
        jdbc.update("DELETE FROM vehiculo WHERE id IN (?,?)",v1,v2);
        jdbc.update("DELETE FROM periodo_servicio WHERE id=?",periodo);
        jdbc.update("DELETE FROM empresa_operadora WHERE id=?",empresa);
        jdbc.update("DELETE FROM ruta_version WHERE ruta_id=?",ruta); jdbc.update("DELETE FROM ruta WHERE id=?",ruta);
    }
    @Test void capturaYRecepcionSonDistintasYReintentoNoLasReescribe() throws Exception {
        var cliente = UUID.randomUUID(); var d = datos(cliente,a1,captura);
        var creado = enviar(post(inicio()),d,201);
        assertThat(creado.get("eventoClienteId").asText()).isEqualTo(cliente.toString());
        assertThat(Instant.parse(creado.get("capturadoEn").asText())).isEqualTo(captura);
        assertThat(Instant.parse(creado.get("recibidoEn").asText())).isAfter(captura);
        assertThat(enviar(post(inicio()),d,200)).isEqualTo(creado);
        assertThat(jdbc.queryForObject("SELECT capturado_en FROM evento_ejecucion_jornada WHERE evento_cliente_id=?",Timestamp.class,cliente).toInstant()).isEqualTo(captura);
        jdbc.update("UPDATE jornada SET vigente=false,actualizado_en=now() WHERE id=?",j1);
        assertThat(enviar(post(inicio()),d,200)).isEqualTo(creado); // Incluso después del cambio de planificación.
        enviar(post(inicio()),datos(UUID.randomUUID(),a1,captura),409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",Integer.class,j1)).isEqualTo(1);
    }
    @Test void idClienteConContenidoDistintoEsConflictoSinMutacion() throws Exception {
        var cliente=UUID.randomUUID(); var creado=enviar(post(inicio()),datos(cliente,a1,captura),201);
        enviar(post(fin()),datos(cliente,a1,captura.plusSeconds(1)),409);
        enviar(post(inicio()),datos(cliente,a1,captura.plusSeconds(1)),409);
        enviar(post(inicio()),datos(cliente,otraAsignacion,captura),409);
        enviar(post("/api/jornadas/" + j2 + "/ejecucion/inicio"),datos(cliente,otraAsignacion,captura),409);
        assertThat(enviar(post(inicio()),datos(cliente,a1,captura),200)).isEqualTo(creado);
    }
    @Test void finExigeInicioYOrdenPeroAdmiteIgualdadYReintentos() throws Exception {
        enviar(post(fin()),datos(UUID.randomUUID(),a1,captura),409);
        var inicio=enviar(post(inicio()),datos(UUID.randomUUID(),a1,captura),201);
        enviar(post(fin()),datos(UUID.randomUUID(),a1,captura.minusSeconds(1)),400);
        var d=datos(UUID.randomUUID(),a1,captura); var fin=enviar(post(fin()),d,201);
        assertThat(enviar(post(fin()),d,200)).isEqualTo(fin);
        enviar(post(fin()),datos(UUID.randomUUID(),a1,captura.plusSeconds(1)),409);
        assertThat(lectura().get("inicio")).isEqualTo(inicio);
    }
    @Test void finAdmiteOtraAsignacionYJornadaReprogramadaSinAlterarInicio() throws Exception {
        var inicio=enviar(post(inicio()),datos(UUID.randomUUID(),a1,captura),201);
        var r=enviar(post("/api/jornadas/" + j1 + "/reasignaciones"),
            Map.of("vinculoVehiculoEmpresaId",vv2,"vinculoPersonaEmpresaId",vp2,"motivoCambio","Cambio sintético"),201);
        var programacion = new HashMap<String,Object>(Map.of("periodoServicioId",periodo,"rutaVersionId",version,
            "inicioProgramado","2026-11-20T12:00:00Z","motivoReprogramacion","Cambio sintético"));
        enviar(post("/api/jornadas/" + j1 + "/reprogramaciones"),programacion,201);
        var fin=enviar(post(fin()),datos(UUID.randomUUID(),UUID.fromString(r.get("id").asText()),Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS)),201);
        assertThat(fin.get("asignacionJornadaId")).isEqualTo(r.get("id"));
        assertThat(lectura().get("inicio")).isEqualTo(inicio);
        assertThat(lectura().get("fin")).isEqualTo(fin);
    }
    @Test void validaJornadaAsignacionYVigenciaHistorica() throws Exception {
        enviar(post("/api/jornadas/" + UUID.randomUUID() + "/ejecucion/inicio"),datos(UUID.randomUUID(),a1,captura),404);
        enviar(post(inicio()),datos(UUID.randomUUID(),UUID.randomUUID(),captura),404);
        enviar(post(inicio()),datos(UUID.randomUUID(),otraAsignacion,captura),400);
        enviar(post(inicio()),datos(UUID.randomUUID(),a1,captura.minusSeconds(7200)),400);
        jdbc.update("UPDATE asignacion_jornada SET vigente_hasta=? WHERE id=?",Timestamp.from(captura.plusSeconds(10)),a1);
        enviar(post(inicio()),datos(UUID.randomUUID(),a1,captura.plusSeconds(11)),400);
        enviar(post(inicio()),datos(UUID.randomUUID(),a1,captura),201); // Sincronización histórica válida, aunque la asignación ya cerró.
    }
    @Test void inicioNuevoSobreJornadaSustituidaEsConflicto() throws Exception {
        jdbc.update("UPDATE jornada SET vigente=false,actualizado_en=now() WHERE id=?",j1);
        enviar(post(inicio()),datos(UUID.randomUUID(),a1,captura),409);
        assertThat(lectura().get("inicio").isNull()).isTrue();
    }
    @Test void contratoRechazaCamposNoPermitidosYNulos() throws Exception {
        assertThat(lectura().get("inicio").isNull()).isTrue(); assertThat(lectura().get("fin").isNull()).isTrue();
        for (String campo : List.of("id","recibidoEn","tipoEvento","empresaOperadoraId","vehiculoId","personaId","inicioReal","finReal","latitud","cumplimiento")) {
            var d=datos(UUID.randomUUID(),a1,captura); d.put(campo,"No permitido"); enviar(post(inicio()),d,400);
        }
        for (String campo : List.of("eventoClienteId","asignacionJornadaId","capturadoEn")) {
            var d=datos(UUID.randomUUID(),a1,captura); d.put(campo,null); enviar(post(inicio()),d,400);
        }
        enviar(post(inicio()),datos(UUID.randomUUID(),a1,Instant.parse("2026-11-10T11:02:00.123456789Z")),400);
        enviar(put(inicio()),null,405); enviar(delete(inicio()),null,405);
    }
    @Test void concurrenciaDeIniciosDistintosSoloPersisteUno() throws Exception {
        var respuestas=concurrentes(inicio(),datos(UUID.randomUUID(),a1,captura),inicio(),datos(UUID.randomUUID(),a1,captura));
        assertThat(respuestas).containsExactlyInAnyOrder(201,409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",Integer.class,j1)).isEqualTo(1);
    }
    @Test void concurrenciaDeReintentosIdenticosDevuelve201Y200() throws Exception {
        var d=datos(UUID.randomUUID(),a1,captura);
        assertThat(concurrentes(inicio(),d,inicio(),d)).containsExactlyInAnyOrder(201,200);
    }
    @Test void concurrenciaDeFinSoloPersisteUno() throws Exception {
        enviar(post(inicio()),datos(UUID.randomUUID(),a1,captura),201);
        assertThat(concurrentes(fin(),datos(UUID.randomUUID(),a1,captura.plusSeconds(1)),fin(),datos(UUID.randomUUID(),a1,captura.plusSeconds(1))))
            .containsExactlyInAnyOrder(201,409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=? AND tipo_evento='FIN'",Integer.class,j1)).isEqualTo(1);
    }
    @Test void idClienteGlobalConcurrenteEntreJornadasNoSeReinterpreta() throws Exception {
        var cliente=UUID.randomUUID();
        assertThat(concurrentes(inicio(),datos(cliente,a1,captura),"/api/jornadas/" + j2 + "/ejecucion/inicio",datos(cliente,otraAsignacion,captura)))
            .containsExactlyInAnyOrder(201,409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE evento_cliente_id=?",Integer.class,cliente)).isEqualTo(1);
    }
    @Test void postgresProtegeUnicidadTipoClavesEInmutabilidad() throws Exception {
        var e=enviar(post(inicio()),datos(UUID.randomUUID(),a1,captura),201); var id=UUID.fromString(e.get("id").asText());
        falla("INSERT INTO evento_ejecucion_jornada SELECT ?,evento_cliente_id,?,?,tipo_evento,capturado_en,recibido_en FROM evento_ejecucion_jornada WHERE id=?",UUID.randomUUID(),j2,otraAsignacion,id);
        falla("INSERT INTO evento_ejecucion_jornada SELECT ?,?,jornada_id,asignacion_jornada_id,tipo_evento,capturado_en,recibido_en FROM evento_ejecucion_jornada WHERE id=?",UUID.randomUUID(),UUID.randomUUID(),id);
        falla("INSERT INTO evento_ejecucion_jornada SELECT ?,?,jornada_id,asignacion_jornada_id,'OTRO',capturado_en,recibido_en FROM evento_ejecucion_jornada WHERE id=?",UUID.randomUUID(),UUID.randomUUID(),id);
        falla("INSERT INTO evento_ejecucion_jornada SELECT ?,?,?,asignacion_jornada_id,'FIN',capturado_en,recibido_en FROM evento_ejecucion_jornada WHERE id=?",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),id);
        falla("INSERT INTO evento_ejecucion_jornada SELECT ?,?,jornada_id,?,'FIN',capturado_en,recibido_en FROM evento_ejecucion_jornada WHERE id=?",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),id);
        for (String columna : List.of("id","evento_cliente_id","jornada_id","asignacion_jornada_id")) {
            falla("UPDATE evento_ejecucion_jornada SET " + columna + "=? WHERE id=?",UUID.randomUUID(),id);
        }
        falla("UPDATE evento_ejecucion_jornada SET tipo_evento='FIN' WHERE id=?",id);
        falla("UPDATE evento_ejecucion_jornada SET capturado_en=capturado_en+interval '1 second' WHERE id=?",id);
        falla("UPDATE evento_ejecucion_jornada SET recibido_en=recibido_en+interval '1 second' WHERE id=?",id);
        assertThat(lectura().get("inicio")).isEqualTo(e);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name='evento_ejecucion_jornada' ORDER BY ordinal_position",String.class))
            .containsExactly("id","evento_cliente_id","jornada_id","asignacion_jornada_id","tipo_evento","capturado_en","recibido_en");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE contype='f' AND conrelid='evento_ejecucion_jornada'::regclass AND confdeltype <> 'a'",Integer.class)).isZero();
    }
    @Test void seguridadLocalSinSuplantacionDeActor() throws Exception {
        String ruta="/api/jornadas/" + j1 + "/ejecucion";
        mvc.perform(get(ruta)).andExpect(status().isForbidden());
        mvc.perform(get(ruta).header("X-GAMW-Development","gamw-desarrollo").header("Origin","https://externo.example")).andExpect(status().isForbidden());
        mvc.perform(get(ruta).header("X-GAMW-Development","gamw-desarrollo").with(r -> { r.setRemoteAddr("192.0.2.1"); return r; })).andExpect(status().isForbidden());
        enviar(get(ruta),null,200);
    }
    private JsonNode lectura() throws Exception { return enviar(get("/api/jornadas/" + j1 + "/ejecucion"),null,200); }
    private Map<String,Object> datos(UUID cliente,UUID asignacion,Instant tiempo) {
        return new HashMap<>(Map.of("eventoClienteId",cliente,"asignacionJornadaId",asignacion,"capturadoEn",tiempo.toString()));
    }
    private JsonNode enviar(MockHttpServletRequestBuilder solicitud,Object cuerpo,int codigo) throws Exception {
        solicitud.header("X-GAMW-Development","gamw-desarrollo");
        if (cuerpo != null) solicitud.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        var texto=mvc.perform(solicitud).andExpect(status().is(codigo)).andReturn().getResponse().getContentAsString();
        return texto.isBlank()?null:json.readTree(texto);
    }
    private void falla(String sql,Object... argumentos) { assertThatThrownBy(() -> jdbc.update(sql,argumentos)).isInstanceOf(DataIntegrityViolationException.class); }
    private List<Integer> concurrentes(String rutaA,Object cuerpoA,String rutaB,Object cuerpoB) throws Exception {
        var inicio=new CountDownLatch(1); var listos=new CountDownLatch(2);
        try (var ejecutor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> a=() -> llamadaConcurrente(rutaA,cuerpoA,listos,inicio);
            Callable<Integer> b=() -> llamadaConcurrente(rutaB,cuerpoB,listos,inicio);
            var x=ejecutor.submit(a); var y=ejecutor.submit(b);
            assertThat(listos.await(10,TimeUnit.SECONDS)).isTrue(); inicio.countDown();
            return List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));
        }
    }
    private int llamadaConcurrente(String ruta,Object datos,CountDownLatch listos,CountDownLatch inicio) throws Exception {
        listos.countDown(); if (!inicio.await(10,TimeUnit.SECONDS)) throw new AssertionError("Tiempo agotado");
        return mvc.perform(post(ruta).header("X-GAMW-Development","gamw-desarrollo").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(datos))).andReturn().getResponse().getStatus();
    }
}
