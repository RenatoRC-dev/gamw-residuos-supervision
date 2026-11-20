package bo.gob.warnes.gamw.residuos.modules.correctivas.api;

import bo.gob.warnes.gamw.residuos.modules.correctivas.dominio.RepositorioActuaciones;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * CU-14 (A5, en línea) con JWT y PostgreSQL/PostGIS aislados: referencia válida, descripción y fecha declarada conservadas, datos
 * mínimos, actor ajeno, A4 sin acceso municipal, idempotencia, sin correctiva ni dictamen automáticos, fallo de almacenamiento sin
 * confirmación falsa, y consulta como antecedente. El registro offline/tardío sigue sin habilitar (decisión institucional pendiente).
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class IncidenciasOperativasIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    @MockitoSpyBean RepositorioActuaciones repositorio;
    private final UUID personaA=UUID.randomUUID(),personaB=UUID.randomUUID(),conductorA=UUID.randomUUID(),conductorB=UUID.randomUUID(),
            municipal=UUID.randomUUID(),supervisor=UUID.randomUUID(),empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),
            version=UUID.randomUUID(),vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),vpA=UUID.randomUUID(),vpB=UUID.randomUUID(),
            jornada=UUID.randomUUID(),asignacion=UUID.randomUUID();
    private final Instant inicio=Instant.now().minusSeconds(900).truncatedTo(ChronoUnit.MICROS);

    @BeforeEach void preparar() throws Exception {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-14");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2027-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"I14-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"I14-"+vehiculo.toString().toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        for(UUID p:List.of(personaA,personaB)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"I14-"+p,"Persona sintética CU-14");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpA,personaA,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpB,personaB,empresa);
        for(UUID vp:List.of(vpA,vpB)) jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000001')",vp);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",jornada,periodo,version,Timestamp.from(inicio.minusSeconds(600)));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacion,jornada,vv,vpA,Timestamp.from(inicio.minusSeconds(3600)));
        String hash=codificador.encode("clave-sintetica-I14");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",conductorA,personaA,"I14-A-"+conductorA,hash);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",conductorB,personaB,"I14-B-"+conductorB,hash);
        UUID pm=UUID.randomUUID(),ps=UUID.randomUUID();
        for(UUID p:List.of(pm,ps)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"I14-"+p,"Cuenta sintética CU-14");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",municipal,pm,"I14-M-"+municipal,hash);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",supervisor,ps,"I14-S-"+supervisor,hash);
        jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,'CORRECTIVA_CONSULTAR','MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),municipal);
        jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,'PLANIFICACION_CONSULTAR',true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),supervisor,periodo);
        // INICIO sincronizado por la API real (CU-13): precondición del registro directo.
        enviar(conductorA,"/api/jornadas/"+jornada+"/ejecucion/inicio",Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",inicio.toString()),201);
    }
    @AfterEach void restaurar() { reset(repositorio); }

    private String token(UUID usuario) { return emisor.emitir(usuario,UUID.randomUUID(),"no-confiable").token(); }
    private JsonNode enviar(UUID usuario,String ruta,Object cuerpo,int estado) throws Exception {
        var r=mvc.perform(post(ruta).header("Authorization","Bearer "+token(usuario)).contentType("application/json").content(json.writeValueAsString(cuerpo))).andReturn();
        assertThat(r.getResponse().getStatus()).as("%s → %s",ruta,r.getResponse().getContentAsString()).isEqualTo(estado);
        String texto=r.getResponse().getContentAsString();
        return texto.isBlank()?null:json.readTree(texto);
    }
    private JsonNode consultar(UUID usuario,String ruta,int estado) throws Exception {
        var r=mvc.perform(get(ruta).header("Authorization","Bearer "+token(usuario))).andReturn();
        assertThat(r.getResponse().getStatus()).as("%s → %s",ruta,r.getResponse().getContentAsString()).isEqualTo(estado);
        String texto=r.getResponse().getContentAsString();
        return texto.isBlank()?null:json.readTree(texto);
    }
    private Map<String,Object> incidencia(UUID solicitud,Instant ocurrida,String descripcion) {
        var m=new LinkedHashMap<String,Object>();
        m.put("solicitudId",solicitud); m.put("asignacionJornadaId",asignacion); m.put("ocurridaEn",ocurrida==null?null:ocurrida.toString());
        m.put("descripcion",descripcion); m.put("evidenciaDeclarada","Observación directa del conductor"); m.put("lugarDescripcion","Esquina sintética");
        return m;
    }
    private String rutaIncidencias() { return "/api/jornadas/"+jornada+"/incidencias"; }
    private int incidencias() { return jdbc.queryForObject("SELECT count(*) FROM incidencia_operativa WHERE jornada_id=?",Integer.class,jornada); }

    @Test void registraConJornadaValidaYConservaDescripcionFechaDeclaradaYRegistrante() throws Exception {
        Instant ocurrida=inicio.plusSeconds(120);
        var r=enviar(conductorA,rutaIncidencias(),incidencia(UUID.randomUUID(),ocurrida,"Vía inaccesible por obra (sintético)"),200);
        assertThat(r.get("jornadaId").asText()).isEqualTo(jornada.toString());
        assertThat(r.get("usuarioId").asText()).as("registrante = principal autenticado").isEqualTo(conductorA.toString());
        assertThat(Instant.parse(r.get("ocurridaEn").asText())).as("CA-14-02 fecha declarada, no la de recepción").isEqualTo(ocurrida);
        assertThat(r.get("descripcion").asText()).isEqualTo("Vía inaccesible por obra (sintético)");
        assertThat(Instant.parse(r.get("registradaEn").asText())).isAfter(ocurrida);
        // CA-14-07: antecedente consultable por el conductor y en el expediente municipal.
        var propias=consultar(conductorA,"/api/jornadas/"+jornada+"/incidencias-propias?asignacionId="+asignacion,200);
        assertThat(propias.size()).isEqualTo(1);
        assertThat(consultar(municipal,"/api/correctivas/jornadas/"+jornada,200).get("incidencias").get(0).get("id").asText()).isEqualTo(r.get("id").asText());
        // CA-14-05: ninguna correctiva, inspección ni fiscalización automática.
        for(String tabla:List.of("accion_correctiva","inspeccion","fiscalizacion"))
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+tabla+" WHERE jornada_id=?",Integer.class,jornada)).as(tabla).isZero();
    }

    @Test void datosMinimosInsuficientesOCamposDeIdentidadImpidenElRegistro() throws Exception {
        enviar(conductorA,rutaIncidencias(),incidencia(UUID.randomUUID(),inicio.plusSeconds(60),"   "),400);
        enviar(conductorA,rutaIncidencias(),incidencia(UUID.randomUUID(),null,"Sin fecha"),400);
        var sinAsignacion=incidencia(UUID.randomUUID(),inicio.plusSeconds(60),"Sin asignación"); sinAsignacion.remove("asignacionJornadaId");
        enviar(conductorA,rutaIncidencias(),sinAsignacion,400);
        var suplantacion=incidencia(UUID.randomUUID(),inicio.plusSeconds(60),"Con usuario impuesto"); suplantacion.put("usuarioId",conductorB);
        enviar(conductorA,rutaIncidencias(),suplantacion,400);
        enviar(conductorA,rutaIncidencias(),incidencia(UUID.randomUUID(),Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MICROS),"Futura"),400);
        assertThat(incidencias()).isZero();
    }

    @Test void jornadaInexistenteActorAjenoYSupervisorSinAlcanceNoRegistranNiConsultan() throws Exception {
        // E1: se autoriza antes de revelar existencia; una jornada inexistente no admite la asignación del actor (403, sin enumeración).
        enviar(conductorA,"/api/jornadas/"+UUID.randomUUID()+"/incidencias",incidencia(UUID.randomUUID(),inicio.plusSeconds(60),"Jornada inexistente"),403);
        enviar(conductorB,rutaIncidencias(),incidencia(UUID.randomUUID(),inicio.plusSeconds(60),"Conductor ajeno"),403);
        enviar(supervisor,rutaIncidencias(),incidencia(UUID.randomUUID(),inicio.plusSeconds(60),"Supervisor A4"),403);
        enviar(conductorA,rutaIncidencias(),incidencia(UUID.randomUUID(),inicio.plusSeconds(60),"Propia"),200);
        consultar(conductorB,"/api/jornadas/"+jornada+"/incidencias-propias?asignacionId="+asignacion,403);
        // CA-14-03: A4 no tiene acceso municipal al expediente (FA-03 de consulta por supervisor sin habilitar: D1).
        consultar(supervisor,"/api/correctivas/jornadas/"+jornada,403);
        consultar(conductorA,"/api/correctivas/jornadas/"+jornada,403);
        assertThat(incidencias()).isEqualTo(1);
    }

    @Test void fueraDeEjecucionNoSeRegistraYElReintentoEsIdempotente() throws Exception {
        enviar(conductorA,rutaIncidencias(),incidencia(UUID.randomUUID(),inicio.minusSeconds(1),"Antes del INICIO"),409);
        UUID solicitud=UUID.randomUUID();
        var cuerpo=incidencia(solicitud,inicio.plusSeconds(30),"Avería sintética");
        var primera=enviar(conductorA,rutaIncidencias(),cuerpo,200);
        var reintento=enviar(conductorA,rutaIncidencias(),cuerpo,200);
        assertThat(reintento.get("id").asText()).isEqualTo(primera.get("id").asText());
        assertThat(reintento.get("registradaEn").asText()).as("el reintento no modifica la recepción").isEqualTo(primera.get("registradaEn").asText());
        var distinto=incidencia(solicitud,inicio.plusSeconds(30),"Otro contenido");
        enviar(conductorA,rutaIncidencias(),distinto,409);
        assertThat(incidencias()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT descripcion FROM incidencia_operativa WHERE id=?::uuid",String.class,primera.get("id").asText())).isEqualTo("Avería sintética");
    }

    @Test void unFalloDeAlmacenamientoNoConfirmaLaIncidencia() throws Exception {
        doAnswer(i -> { i.callRealMethod(); throw new DataAccessResourceFailureException("Fallo de almacenamiento inyectado (prueba)."); })
            .when(repositorio).insertar(any(RepositorioActuaciones.Incidencia.class),anyString());
        var r=mvc.perform(post(rutaIncidencias()).header("Authorization","Bearer "+token(conductorA)).contentType("application/json")
            .content(json.writeValueAsString(incidencia(UUID.randomUUID(),inicio.plusSeconds(60),"No debe confirmarse")))).andReturn();
        assertThat(r.getResponse().getStatus()).as("CA-14-06 sin confirmación: %s",r.getResponse().getContentAsString()).isEqualTo(503);
        assertThat(r.getResponse().getContentAsString()).doesNotContain("No debe confirmarse");
        assertThat(incidencias()).as("la escritura se revirtió").isZero();
    }
}
