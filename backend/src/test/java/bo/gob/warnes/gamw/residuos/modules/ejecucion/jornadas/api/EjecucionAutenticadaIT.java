package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.RepositorioEventosEjecucion;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** C1: JWT y repositorios reales sobre el clúster desechable. Sin usuarios productivos. */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class EjecucionAutenticadaIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    @Autowired RepositorioEventosEjecucion eventos;
    private final UUID personaA=UUID.randomUUID(), personaB=UUID.randomUUID(), usuarioA=UUID.randomUUID(),
            usuarioB=UUID.randomUUID(), otraCuentaA=UUID.randomUUID(), empresa=UUID.randomUUID(), periodo=UUID.randomUUID(),
            ruta=UUID.randomUUID(), version=UUID.randomUUID(), vehiculo=UUID.randomUUID(), vv=UUID.randomUUID(),
            vpA=UUID.randomUUID(), vpB=UUID.randomUUID(), jornadaA=UUID.randomUUID(), jornadaB=UUID.randomUUID(),
            asignacionA=UUID.randomUUID(), asignacionB=UUID.randomUUID();
    private final Instant captura=Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.MICROS);

    @BeforeEach void preparar() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética C1");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2027-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"C1-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"C1-"+vehiculo.toString().toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        for (UUID persona : List.of(personaA,personaB)) {
            jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"C1-"+persona,"Persona sintética C1");
        }
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpA,personaA,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpB,personaB,empresa);
        for(UUID vp:List.of(vpA,vpB)) jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);
        for(UUID jornada:List.of(jornadaA,jornadaB)) jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacionA,jornadaA,vv,vpA,Timestamp.from(captura.minusSeconds(3600)));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacionB,jornadaB,vv,vpB,Timestamp.from(captura.minusSeconds(3600)));
        String hash=codificador.encode("clave-sintetica-C1");
        for(UUID usuario:List.of(usuarioA,usuarioB,otraCuentaA)) jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",usuario,
                usuario.equals(usuarioB)?personaB:personaA,"C1-"+usuario,hash);
    }

    @AfterEach void limpiarSoloFixturesPropios() {
        jdbc.update("DELETE FROM evento_ejecucion_jornada WHERE jornada_id IN (?,?)",jornadaA,jornadaB);
        jdbc.update("DELETE FROM usuario WHERE id IN (?,?,?)",usuarioA,usuarioB,otraCuentaA);
        jdbc.update("DELETE FROM asignacion_jornada WHERE jornada_id IN (?,?)",jornadaA,jornadaB);
        jdbc.update("DELETE FROM jornada WHERE id IN (?,?)",jornadaA,jornadaB);
        jdbc.update("DELETE FROM vinculo_persona_funcion WHERE vinculo_persona_empresa_id IN (?,?)",vpA,vpB);
        jdbc.update("DELETE FROM vinculo_persona_empresa WHERE id IN (?,?)",vpA,vpB);
        jdbc.update("DELETE FROM persona_natural WHERE id IN (?,?)",personaA,personaB);
        jdbc.update("DELETE FROM vinculo_vehiculo_empresa WHERE id=?",vv);
        jdbc.update("DELETE FROM vehiculo WHERE id=?",vehiculo);
        jdbc.update("DELETE FROM periodo_servicio WHERE id=?",periodo);
        jdbc.update("DELETE FROM empresa_operadora WHERE id=?",empresa);
        jdbc.update("DELETE FROM ruta_version WHERE id=?",version);
        jdbc.update("DELETE FROM ruta WHERE id=?",ruta);
    }

    private String token(UUID usuario) {
        // Los claims personales son deliberadamente falsos: el convertidor consulta Usuario persistido.
        return emisor.emitir(usuario,UUID.randomUUID(),"no-confiable").token();
    }
    private String url(UUID jornada,String tipo) { return "/api/jornadas/"+jornada+"/ejecucion/"+tipo; }
    private Map<String,Object> datos(UUID cliente,UUID asignacion,Instant fecha) {
        return new LinkedHashMap<>(Map.of("eventoClienteId",cliente,"asignacionJornadaId",asignacion,"capturadoEn",fecha.toString()));
    }
    private JsonNode enviar(UUID usuario,UUID jornada,String tipo,Map<String,Object> datos,int estado) throws Exception {
        return enviar(post(url(jornada,tipo)).header("Authorization","Bearer "+token(usuario)),datos,estado);
    }
    private JsonNode enviar(MockHttpServletRequestBuilder solicitud,Map<String,Object> datos,int estado) throws Exception {
        var resultado=mvc.perform(solicitud.contentType("application/json").content(json.writeValueAsString(datos)))
                .andExpect(status().is(estado)).andReturn();
        String cuerpo=resultado.getResponse().getContentAsString();
        return cuerpo.isBlank()?null:json.readTree(cuerpo);
    }

    @Test void usuarioAsignadoRegistraInicioFinConIdentidadPersistida() throws Exception {
        UUID cliente=UUID.randomUUID();
        var inicio=enviar(usuarioA,jornadaA,"inicio",datos(cliente,asignacionA,captura),201);
        var fin=enviar(usuarioA,jornadaA,"fin",datos(UUID.randomUUID(),asignacionA,captura.plusSeconds(1)),201);
        assertThat(inicio.get("usuarioId").asText()).isEqualTo(usuarioA.toString());
        assertThat(fin.get("usuarioId").asText()).isEqualTo(usuarioA.toString());
        assertThat(eventos.porCliente(cliente).orElseThrow().usuarioId()).isEqualTo(usuarioA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=? AND usuario_id=?",Integer.class,jornadaA,usuarioA)).isEqualTo(2);
    }

    @Test void rechazaAsignacionDeOtraPersonaSinInsertar() throws Exception {
        enviar(usuarioA,jornadaB,"inicio",datos(UUID.randomUUID(),asignacionB,captura),403);
        enviar(usuarioB,jornadaA,"inicio",datos(UUID.randomUUID(),asignacionA,captura),403);
        assertThat(eventos.porJornada(jornadaA)).isEmpty();
        assertThat(eventos.porJornada(jornadaB)).isEmpty();
    }

    @Test void reintentoPreservaActorTiemposYFuncionaTrasCierreAdministrativo() throws Exception {
        var d=datos(UUID.randomUUID(),asignacionA,captura);
        var original=enviar(usuarioA,jornadaA,"inicio",d,201);
        jdbc.update("UPDATE asignacion_jornada SET vigente_hasta=? WHERE id=?",Timestamp.from(captura.plusSeconds(30)),asignacionA);
        jdbc.update("UPDATE jornada SET vigente=false,actualizado_en=now() WHERE id=?",jornadaA);
        assertThat(enviar(usuarioA,jornadaA,"inicio",d,200)).isEqualTo(original);
        var cambiado=new LinkedHashMap<>(d); cambiado.put("capturadoEn",captura.plusSeconds(1).toString());
        enviar(usuarioA,jornadaA,"inicio",cambiado,409);
        assertThat(eventos.porJornada(jornadaA)).hasSize(1);
    }

    @Test void otraCuentaInclusoDeLaMismaPersonaNoSeApropiaDelEvento() throws Exception {
        var d=datos(UUID.randomUUID(),asignacionA,captura);
        var original=enviar(usuarioA,jornadaA,"inicio",d,201);
        enviar(otraCuentaA,jornadaA,"inicio",d,409);
        enviar(usuarioB,jornadaA,"inicio",d,409);
        assertThat(enviar(usuarioA,jornadaA,"inicio",d,200)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM evento_ejecucion_jornada WHERE jornada_id=?",UUID.class,jornadaA)).isEqualTo(usuarioA);
    }

    @Test void evidenciaHistoricaNulaSeLeeYReintentaSinRetroatribucion() throws Exception {
        UUID cliente=UUID.randomUUID();
        jdbc.update("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'INICIO',?,?,NULL)",UUID.randomUUID(),cliente,jornadaA,asignacionA,
                Timestamp.from(captura),Timestamp.from(captura.plusSeconds(60)));
        var antes=eventos.porCliente(cliente).orElseThrow();
        assertThat(antes.usuarioId()).isNull();
        var resultado=enviar(usuarioA,jornadaA,"inicio",datos(cliente,asignacionA,captura),200);
        assertThat(resultado.get("usuarioId").isNull()).isTrue();
        assertThat(eventos.porCliente(cliente)).contains(antes);
        enviar(usuarioB,jornadaA,"inicio",datos(cliente,asignacionA,captura),403);
        enviar(usuarioA,jornadaA,"fin",datos(UUID.randomUUID(),asignacionA,captura.plusSeconds(90)),201);
        assertThat(eventos.porCliente(cliente)).contains(antes);
    }

    @Test void identidadEnCabecerasOQueryNoSustituyeAlPrincipalYCuerpoLaRechaza() throws Exception {
        var d=datos(UUID.randomUUID(),asignacionA,captura);
        var respuesta=enviar(post(url(jornadaA,"inicio")).header("Authorization","Bearer "+token(usuarioA))
                .header("X-Usuario-Id",usuarioB).header("X-Persona-Id",personaB).param("usuarioId",usuarioB.toString()).param("personaId",personaB.toString()),d,201);
        assertThat(respuesta.get("usuarioId").asText()).isEqualTo(usuarioA.toString());
        var falsa=datos(UUID.randomUUID(),asignacionA,captura.plusSeconds(1)); falsa.put("usuarioId",usuarioB);
        enviar(usuarioA,jornadaA,"fin",falsa,400);
        falsa.remove("usuarioId"); falsa.put("personaId",personaA);
        enviar(usuarioA,jornadaA,"fin",falsa,400);
        enviar(post(url(jornadaA,"fin")).with(csrf()).header("X-GAMW-Development","gamw-desarrollo")
                .header("X-Usuario-Id",usuarioA),datos(UUID.randomUUID(),asignacionA,captura.plusSeconds(1)),401);
    }

    @Test void sinAutenticacionValidaNoRegistraYNingunPrincipalGenericoSirve() throws Exception {
        var d=datos(UUID.randomUUID(),asignacionA,captura);
        enviar(post(url(jornadaA,"inicio")).with(csrf()),d,401);
        enviar(post(url(jornadaA,"inicio")).header("Authorization","Bearer no-valido"),d,401);
        enviar(post(url(jornadaA,"inicio")).with(user("solo-test-no-GAMW")).with(csrf()),d,401);
        String vigente=token(usuarioA);
        jdbc.update("UPDATE usuario SET activo=false WHERE id=?",usuarioA);
        enviar(post(url(jornadaA,"inicio")).header("Authorization","Bearer "+vigente),d,401);
        assertThat(eventos.porJornada(jornadaA)).isEmpty();
    }

    @Test void soloSeAbrenPostDeEjecucionLasDemasApisSiguenCerradas() throws Exception {
        String valido=token(usuarioA);
        for(String ruta:List.of("/api/rutas","/api/personas","/api/empresas-operadoras","/api/vehiculos","/api/jornadas",
                "/api/periodos-servicio","/api/jornadas/"+jornadaA+"/ejecucion")) {
            mvc.perform(get(ruta).header("Authorization","Bearer "+valido)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/personas").with(csrf()).header("Authorization","Bearer "+valido)).andExpect(status().isForbidden());
        mvc.perform(put(url(jornadaA,"inicio")).with(csrf()).header("Authorization","Bearer "+valido)).andExpect(status().isForbidden());
    }

    @Test void misJornadasSoloDerivaPersonaDelPrincipalEIncluyeHistoria() throws Exception {
        String url="/api/mis-jornadas";
        mvc.perform(get(url).header("Authorization","Bearer "+token(usuarioA)).param("personaId",personaB.toString())
                .header("X-Persona-Id",personaB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jornadaId").value(jornadaA.toString()))
                .andExpect(jsonPath("$[0].asignacionJornadaId").value(asignacionA.toString()));
        jdbc.update("UPDATE asignacion_jornada SET vigente_hasta=? WHERE id=?",Timestamp.from(captura.plusSeconds(30)),asignacionA);
        jdbc.update("UPDATE jornada SET vigente=false,actualizado_en=now() WHERE id=?",jornadaA);
        mvc.perform(get(url).header("Authorization","Bearer "+token(usuarioA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].vigente").value(false));
        mvc.perform(get(url).header("Authorization","Bearer "+token(usuarioB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].jornadaId").value(jornadaB.toString()));
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).with(user("sin-principal-GAMW"))).andExpect(status().isUnauthorized());
    }

    @Test void conservaReglasDeFinTiempoYVigencia() throws Exception {
        enviar(usuarioA,jornadaA,"fin",datos(UUID.randomUUID(),asignacionA,captura),409);
        enviar(usuarioA,jornadaA,"inicio",datos(UUID.randomUUID(),asignacionA,captura.minusSeconds(7200)),400);
        enviar(usuarioA,jornadaA,"inicio",datos(UUID.randomUUID(),asignacionA,captura),201);
        enviar(usuarioA,jornadaA,"fin",datos(UUID.randomUUID(),asignacionA,captura.minusSeconds(1)),400);
        jdbc.update("UPDATE jornada SET vigente=false,actualizado_en=now() WHERE id=?",jornadaB);
        enviar(usuarioB,jornadaB,"inicio",datos(UUID.randomUUID(),asignacionB,captura),409);
    }

    @Test void finPuedeUsarOtraAsignacionYOtroUsuarioAutorizadoSinCambiarInicio() throws Exception {
        UUID cliente=UUID.randomUUID();
        enviar(usuarioA,jornadaA,"inicio",datos(cliente,asignacionA,captura),201);
        jdbc.update("UPDATE asignacion_jornada SET vigente_hasta=? WHERE id=?",Timestamp.from(captura.plusSeconds(5)),asignacionA);
        UUID nueva=UUID.randomUUID();
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,?,now())",nueva,jornadaA,vv,vpB,Timestamp.from(captura.plusSeconds(5)),"Cambio sintético C1");
        enviar(usuarioA,jornadaA,"fin",datos(UUID.randomUUID(),nueva,captura.plusSeconds(10)),403);
        jdbc.update("UPDATE jornada SET vigente=false,actualizado_en=now() WHERE id=?",jornadaA);
        var fin=enviar(usuarioB,jornadaA,"fin",datos(UUID.randomUUID(),nueva,captura.plusSeconds(10)),201);
        assertThat(fin.get("usuarioId").asText()).isEqualTo(usuarioB.toString());
        assertThat(eventos.porCliente(cliente).orElseThrow().usuarioId()).isEqualTo(usuarioA);
    }

    @Test void reintentosConcurrentesDeDosCuentasNoCambianLaAtribucion() throws Exception {
        var d=datos(UUID.randomUUID(),asignacionA,captura);
        String a=token(usuarioA), b=token(otraCuentaA);
        var iniciar=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> resultados=new ArrayList<>();
            for(String bearer:List.of(a,b)) resultados.add(pool.submit(() -> {
                iniciar.await();
                return mvc.perform(post(url(jornadaA,"inicio")).header("Authorization","Bearer "+bearer)
                        .contentType("application/json").content(json.writeValueAsString(d))).andReturn().getResponse().getStatus();
            }));
            iniciar.countDown();
            assertThat(List.of(resultados.get(0).get(15,TimeUnit.SECONDS),resultados.get(1).get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201,409);
        }
        assertThat(eventos.porJornada(jornadaA)).hasSize(1);
        UUID propietario=eventos.porJornada(jornadaA).getFirst().usuarioId();
        assertThat(propietario).isIn(usuarioA,otraCuentaA);
        enviar(propietario,jornadaA,"inicio",d,200);
    }
}
