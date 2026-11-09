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
class PosicionesGpsIT extends IntegracionPostgresV8 {
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


    private String gps() { return url(jornadaA,"posiciones-gps"); }
    private Map<String,Object> lectura(UUID id) {
        // Datos de contrato sinteticos, no lectura real ni ruta oficial.
        return new LinkedHashMap<>(Map.of("capturaId",id,"capturadoEn",captura.plusSeconds(1).toString(),
                "latitud",20.25,"longitud",-10.75,"precisionMetros",7.5));
    }
    private Map<String,Object> lote(Map<String,Object> p) {
        return Map.of("asignacionJornadaId",asignacionA,"posiciones",List.of(p));
    }
    private JsonNode gps(UUID actor,Map<String,Object> d,int status) throws Exception {
        return enviar(post(gps()).header("Authorization","Bearer "+token(actor)),d,status);
    }
    private void iniciar() throws Exception {
        enviar(usuarioA,jornadaA,"inicio",datos(UUID.randomUUID(),asignacionA,captura),201);
    }
    @Test void migraV9RealConIntegridadEInmutabilidadEspacial() throws Exception {
        iniciar(); UUID id=UUID.randomUUID(); var p=lectura(id);
        var original=gps(usuarioA,lote(p),200);
        assertThat(original.get(0).get("usuarioId").asText()).isEqualTo(usuarioA.toString());
        assertThat(original.get(0).get("capturadoEn").asText()).isEqualTo(captura.plusSeconds(1).toString());
        assertThat(jdbc.queryForObject("SELECT ST_SRID(geometria) FROM posicion_gps WHERE captura_id=?",Integer.class,id)).isEqualTo(4326);
        assertThat(jdbc.queryForObject("SELECT GeometryType(geometria) FROM posicion_gps WHERE captura_id=?",String.class,id)).isEqualTo("POINT");
        assertThat(jdbc.queryForObject("SELECT ST_X(geometria) FROM posicion_gps WHERE captura_id=?",Double.class,id)).isEqualTo(-10.75);
        assertThat(jdbc.queryForObject("SELECT precision_metros FROM posicion_gps WHERE captura_id=?",Double.class,id)).isEqualTo(7.5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='9' AND success",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid='posicion_gps'::regclass AND contype='f' AND confdeltype='c'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE tablename='posicion_gps' AND indexdef LIKE '%USING gist%'",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("UPDATE posicion_gps SET precision_metros=1 WHERE captura_id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM posicion_gps WHERE captura_id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(gps(usuarioA,lote(p),200)).isEqualTo(original);
    }
    @Test void idempotenciaActorContenidoYConcurrencia() throws Exception {
        iniciar(); var p=lectura(UUID.randomUUID()); var d=lote(p);
        var primero=gps(usuarioA,d,200);
        assertThat(gps(usuarioA,d,200)).isEqualTo(primero);
        gps(otraCuentaA,d,409);
        p.put("latitud",21.0); gps(usuarioA,lote(p),409);
        var c=lote(lectura(UUID.randomUUID())); String bearer=token(usuarioA);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var uno=pool.submit(()->enviar(post(gps()).header("Authorization","Bearer "+bearer),c,200));
            var dos=pool.submit(()->enviar(post(gps()).header("Authorization","Bearer "+bearer),c,200));
            assertThat(uno.get(15,TimeUnit.SECONDS)).isEqualTo(dos.get(15,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",Integer.class,jornadaA)).isEqualTo(2);
        jdbc.update("UPDATE asignacion_jornada SET vigente_hasta=? WHERE id=?",Timestamp.from(captura.plusMillis(500)),asignacionA);
        p.put("latitud",20.25);
        assertThat(gps(usuarioA,lote(p),200)).isEqualTo(primero); // Reintento no reinterpreta evidencia ya aceptada.
    }
    @Test void rechazaSuplantacionAsignacionAjenaYSinJwt() throws Exception {
        iniciar(); var d=lote(lectura(UUID.randomUUID()));
        gps(usuarioB,d,403);
        enviar(post(gps()).with(csrf()),d,401);
        var falsa=new LinkedHashMap<>(d); falsa.put("usuarioId",usuarioB); gps(usuarioA,falsa,400);
        var p=lectura(UUID.randomUUID()); p.put("usuarioId",usuarioB); gps(usuarioA,lote(p),400);
        gps(usuarioA,Map.of("asignacionJornadaId",asignacionB,"posiciones",List.of(lectura(UUID.randomUUID()))),403);
        mvc.perform(get(gps()).header("Authorization","Bearer "+token(usuarioA))).andExpect(status().isForbidden());
        mvc.perform(get("/api/personas").header("Authorization","Bearer "+token(usuarioA))).andExpect(status().isForbidden());
    }
    @Test void requiereInicioYConservaCapturasOfflineDentroDeFin() throws Exception {
        var d=lote(lectura(UUID.randomUUID())); gps(usuarioA,d,409); iniciar();
        enviar(usuarioA,jornadaA,"fin",datos(UUID.randomUUID(),asignacionA,captura.plusSeconds(5)),201);
        gps(usuarioA,d,200); // Capturado antes de FIN y recibido posteriormente.
        var p=lectura(UUID.randomUUID()); p.put("capturadoEn",captura.plusSeconds(6).toString()); gps(usuarioA,lote(p),409);
        p.put("capturadoEn",captura.minusSeconds(1).toString()); gps(usuarioA,lote(p),409);
        p.put("capturadoEn",captura.minusSeconds(7200).toString()); gps(usuarioA,lote(p),400);
    }
    @Test void gpsUsaAsignacionHistoricaSinExigirElMismoActorDelInicio() throws Exception {
        iniciar();
        jdbc.update("UPDATE asignacion_jornada SET vigente_hasta=? WHERE id=?",Timestamp.from(captura.plusSeconds(2)),asignacionA);
        UUID nueva=UUID.randomUUID();
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,?,now())",nueva,jornadaA,vv,vpB,
            Timestamp.from(captura.plusSeconds(2)),"Reasignacion sintetica para regresion C1");
        var p=lectura(UUID.randomUUID()); p.put("capturadoEn",captura.plusSeconds(3).toString());
        var respuesta=gps(usuarioB,Map.of("asignacionJornadaId",nueva,"posiciones",List.of(p)),200);
        assertThat(respuesta.get(0).get("usuarioId").asText()).isEqualTo(usuarioB.toString());
        gps(usuarioA,lote(lectura(UUID.randomUUID())),200); // Evidencia anterior al cierre de su asignacion.
        gps(usuarioA,Map.of("asignacionJornadaId",nueva,"posiciones",List.of(lectura(UUID.randomUUID()))),403);
    }
    @Test void loteAtomicoRechazaCoordenadasInvalidasCamposAusentesYLimites() throws Exception {
        iniciar();
        for(String campo:List.of("capturaId","capturadoEn","latitud","longitud","precisionMetros")) {
            var p=lectura(UUID.randomUUID()); p.remove(campo); gps(usuarioA,lote(p),400);
        }
        for(var cambio:List.of(Map.entry("latitud",91.0),Map.entry("longitud",181.0),Map.entry("precisionMetros",-1.0))) {
            var p=lectura(UUID.randomUUID()); p.put(cambio.getKey(),cambio.getValue()); gps(usuarioA,lote(p),400);
        }
        var correcto=lectura(UUID.randomUUID()); var malo=lectura(UUID.randomUUID()); malo.put("latitud",-91);
        gps(usuarioA,Map.of("asignacionJornadaId",asignacionA,"posiciones",List.of(correcto,malo)),400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",Integer.class,jornadaA)).isZero();
        gps(usuarioA,Map.of("asignacionJornadaId",asignacionA,"posiciones",List.of()),400);
    }
}
