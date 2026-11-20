package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import bo.gob.warnes.gamw.residuos.modules.reclamos.dominio.RepositorioReclamos;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * CU-26 / RF-21: registro de un reclamo recibido por canal institucional, con JWT real y PostgreSQL/PostGIS aislado. Registrar NO valida el reclamo,
 * NO imputa incumplimiento al operador y NO crea acciones correctivas (RN). Sin anexos, sin catálogo oficial de canales, sin validación (CU-27) y sin
 * portal ciudadano (CU-25).
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class ReclamoInstitucionalIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    @MockitoSpyBean RepositorioReclamos repositorio;
    @AfterEach void restaurar() { reset(repositorio); }
    private static final String R="/api/reclamos-institucionales";
    private final UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada=UUID.randomUUID();
    private UUID registrador,otroRegistrador,soloConsulta,sinPermisos;
    private final String marca=UUID.randomUUID().toString();

    @BeforeEach void fixtures() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-26");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"RC-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        registrador=cuenta("RECLAMO_CONSULTAR","RECLAMO_REGISTRAR","JORNADAS_CONSULTAR");
        otroRegistrador=cuenta("RECLAMO_CONSULTAR","RECLAMO_REGISTRAR");
        soloConsulta=cuenta("RECLAMO_CONSULTAR");
        sinPermisos=cuenta();
    }
    private UUID cuenta(String... permisos) {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"RC-"+persona,"Persona sintética");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"rc-"+id,codificador.encode("clave-sintetica"));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private String bearer(UUID usuario) { return "Bearer "+emisor.emitir(usuario,UUID.randomUUID(),"no-confiable").token(); }
    private Map<String,Object> reclamo(UUID solicitud,Instant recibido) {
        var d=new LinkedHashMap<String,Object>();
        d.put("solicitudId",solicitud); d.put("canalDeclarado","Teléfono institucional (declarado)"); d.put("recibidoEn",recibido.toString());
        d.put("descripcion","Vecino informa acumulación de residuos "+marca); d.put("lugarDescripcion","Calle sintética"); d.put("referenciasOrigen","");
        d.put("longitud",null); d.put("latitud",null); d.put("jornadaId",null);
        return d;
    }
    private JsonNode enviar(UUID usuario,Object cuerpo,int esperado) throws Exception {
        var b=post(R).contentType("application/json").content(json.writeValueAsString(cuerpo));
        if(usuario!=null) b.header("Authorization",bearer(usuario));
        var r=mvc.perform(b).andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(esperado);
        return r.getContentAsString().isBlank()?null:json.readTree(r.getContentAsString());
    }
    private int cantidad() { return jdbc.queryForObject("SELECT count(*) FROM reclamo_institucional WHERE descripcion LIKE ?",Integer.class,"%"+marca+"%"); }

    @Test void registraSinJornadaConservaOrigenAutorYTiemposYNoValidaNiImputa() throws Exception {
        Instant recibido=Instant.now().minus(3,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        int accionesAntes=jdbc.queryForObject("SELECT count(*) FROM accion_correctiva",Integer.class); // la BD efímera se comparte con otras pruebas
        var creado=enviar(registrador,reclamo(UUID.randomUUID(),recibido),200);
        var r=creado.get("reclamo");
        assertThat(r.get("canalDeclarado").asText()).isEqualTo("Teléfono institucional (declarado)");
        assertThat(Instant.parse(r.get("recibidoEn").asText())).isEqualTo(recibido);
        assertThat(r.get("descripcion").asText()).contains(marca);
        assertThat(r.get("usuarioId").asText()).as("autor desde la sesión").isEqualTo(registrador.toString());
        assertThat(Instant.parse(r.get("registradoEn").asText())).as("registro del servidor posterior a la recepción declarada").isAfter(recibido);
        assertThat(r.get("jornadaId").isNull()).as("un reclamo puede existir sin jornada identificada").isTrue();
        assertThat(r.get("validadoMunicipalmente").asBoolean()).as("registrar no valida el reclamo").isFalse();
        UUID id=UUID.fromString(r.get("id").asText());
        // No hay revisión/valoración, relaciones, acciones correctivas ni resultado de fiscalización derivados.
        assertThat(creado.get("revisiones").size()).isZero();
        assertThat(creado.get("relaciones").size()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revision_reclamo WHERE reclamo_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM relacion_reclamo WHERE reclamo_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accion_correctiva",Integer.class)).as("sin acción correctiva automática").isEqualTo(accionesAntes);
        // Consulta autorizada; sin jornada no cuenta en el historial CU-29 de ninguna jornada.
        var consultado=json.readTree(mvc.perform(get(R+"/"+id).header("Authorization",bearer(soloConsulta))).andReturn().getResponse().getContentAsString());
        assertThat(consultado.get("reclamo").get("id").asText()).isEqualTo(id.toString());
        assertThat(contarReclamosEnHistorial()).isZero();
        // Con ubicación opcional [longitud, latitud] SRID 4326.
        var conGeo=reclamo(UUID.randomUUID(),recibido.plusSeconds(60)); conGeo.put("longitud",-10.75); conGeo.put("latitud",20.25);
        var geo=enviar(registrador,conGeo,200).get("reclamo");
        assertThat(geo.get("longitud").asDouble()).isEqualTo(-10.75); assertThat(geo.get("latitud").asDouble()).isEqualTo(20.25);
        assertThat(jdbc.queryForObject("SELECT ST_SRID(geometria) FROM reclamo_institucional WHERE id=?",Integer.class,UUID.fromString(geo.get("id").asText()))).isEqualTo(4326);
        // Inmutable.
        assertThatThrownBy(() -> jdbc.update("UPDATE reclamo_institucional SET descripcion='alterada' WHERE id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM reclamo_institucional WHERE id=?",id)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT descripcion FROM reclamo_institucional WHERE id=?",String.class,id)).contains(marca);
    }

    @Test void conJornadaExigeReferenciaExistenteYOrigenDocumentadoYSoloEntoncesCuentaEnHistorial() throws Exception {
        Instant recibido=Instant.now().minus(2,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        var sinOrigen=reclamo(UUID.randomUUID(),recibido); sinOrigen.put("jornadaId",jornada);
        enviar(registrador,sinOrigen,400); // sin fundamento documentado no se atribuye
        var inexistente=reclamo(UUID.randomUUID(),recibido); inexistente.put("jornadaId",UUID.randomUUID()); inexistente.put("referenciasOrigen","Acta de llamada sintética");
        enviar(registrador,inexistente,400);
        assertThat(cantidad()).isZero();
        var valido=reclamo(UUID.randomUUID(),recibido); valido.put("jornadaId",jornada); valido.put("referenciasOrigen","Registro de llamada sintético nº 123");
        var creado=enviar(registrador,valido,200).get("reclamo");
        assertThat(creado.get("jornadaId").asText()).isEqualTo(jornada.toString());
        assertThat(creado.get("referenciasOrigen").asText()).isEqualTo("Registro de llamada sintético nº 123");
        assertThat(creado.get("validadoMunicipalmente").asBoolean()).isFalse();
        // CU-29: ahora (y solo ahora) la jornada cuenta un reclamo vinculado, con permiso propio; el reclamo sin jornada no cuenta.
        enviar(registrador,reclamo(UUID.randomUUID(),recibido.plusSeconds(5)),200);
        assertThat(contarReclamosEnHistorial()).isEqualTo(1);
        // Vincular no crea acción correctiva ni dictamen.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accion_correctiva WHERE jornada_id=?",Integer.class,jornada)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();
    }

    @Test void reintentosConflictosYDatosInvalidosNoAlteranLoRegistrado() throws Exception {
        UUID solicitud=UUID.randomUUID(); Instant recibido=Instant.now().minus(1,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        var primero=enviar(registrador,reclamo(solicitud,recibido),200).get("reclamo");
        var replica=enviar(registrador,reclamo(solicitud,recibido),200).get("reclamo");
        assertThat(replica.get("id")).isEqualTo(primero.get("id"));
        assertThat(replica.get("registradoEn")).as("la réplica devuelve el original").isEqualTo(primero.get("registradoEn"));
        assertThat(cantidad()).isEqualTo(1);
        var distinto=reclamo(solicitud,recibido); distinto.put("descripcion","Otro contenido "+marca);
        enviar(registrador,distinto,409);
        enviar(otroRegistrador,reclamo(solicitud,recibido),409);
        assertThat(cantidad()).isEqualTo(1);
        // Datos obligatorios, fechas y coordenadas inválidas.
        for(String campo:List.of("solicitudId","canalDeclarado","recibidoEn","descripcion")) {
            var d=reclamo(UUID.randomUUID(),recibido); d.remove(campo); enviar(registrador,d,400);
        }
        var canalVacio=reclamo(UUID.randomUUID(),recibido); canalVacio.put("canalDeclarado"," "); enviar(registrador,canalVacio,400);
        var futuro=reclamo(UUID.randomUUID(),recibido); futuro.put("recibidoEn",Instant.now().plus(1,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString()); enviar(registrador,futuro,400);
        var nanos=reclamo(UUID.randomUUID(),recibido); nanos.put("recibidoEn",recibido.plusNanos(7).toString()); enviar(registrador,nanos,400);
        var latFuera=reclamo(UUID.randomUUID(),recibido); latFuera.put("longitud",-10.0); latFuera.put("latitud",91.0); enviar(registrador,latFuera,400);
        var lonFuera=reclamo(UUID.randomUUID(),recibido); lonFuera.put("longitud",181.0); lonFuera.put("latitud",20.0); enviar(registrador,lonFuera,400);
        var soloLon=reclamo(UUID.randomUUID(),recibido); soloLon.put("longitud",-10.0); enviar(registrador,soloLon,400);
        var autorAjeno=reclamo(UUID.randomUUID(),recibido); autorAjeno.put("usuarioId",otroRegistrador); enviar(registrador,autorAjeno,400);
        var validacion=reclamo(UUID.randomUUID(),recibido); validacion.put("validadoMunicipalmente",true); enviar(registrador,validacion,400); // no se admite validar al registrar
        assertThat(cantidad()).as("nada inválido deja registro").isEqualTo(1);
    }

    @Test void soloQuienTienePermisoDeRegistroPuedeRegistrar() throws Exception {
        Instant recibido=Instant.now().minus(1,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        enviar(soloConsulta,reclamo(UUID.randomUUID(),recibido),403); // consultar no es registrar
        enviar(sinPermisos,reclamo(UUID.randomUUID(),recibido),403);
        var anonimo=post(R).contentType("application/json").content(json.writeValueAsString(reclamo(UUID.randomUUID(),recibido)));
        assertThat(mvc.perform(anonimo).andReturn().getResponse().getStatus()).isIn(401,403);
        assertThat(cantidad()).isZero();
        // Opciones: el consultor ve que NO puede registrar ni revisar; la consulta sin permiso se deniega.
        var opciones=json.readTree(mvc.perform(get(R+"/opciones").header("Authorization",bearer(soloConsulta))).andReturn().getResponse().getContentAsString());
        assertThat(opciones.get("puedeConsultarReclamos").asBoolean()).isTrue();
        assertThat(opciones.get("puedeRegistrarReclamos").asBoolean()).isFalse();
        assertThat(opciones.get("puedeRevisarReclamos").asBoolean()).isFalse();
        assertThat(mvc.perform(get(R).header("Authorization",bearer(sinPermisos))).andReturn().getResponse().getStatus()).isEqualTo(403);
        var reg=json.readTree(mvc.perform(get(R+"/opciones").header("Authorization",bearer(registrador))).andReturn().getResponse().getContentAsString());
        assertThat(reg.get("puedeRegistrarReclamos").asBoolean()).isTrue();
        assertThat(reg.get("anexosHabilitados").asBoolean()).as("anexos deshabilitados sin política").isFalse();
    }

    @Test void replicasConcurrentesDevuelvenElOriginalYLasDeOtroActorOContenidoSeRechazan() throws Exception {
        Instant recibido=Instant.now().minus(1,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        // La inserción se retiene dentro de la transacción para que las réplicas se superpongan de forma determinista.
        doAnswer(i -> { Object r=i.callRealMethod(); Thread.sleep(400); return r; }).when(repositorio).registrar(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        UUID solicitud=UUID.randomUUID(); String cuerpo=json.writeValueAsString(reclamo(solicitud,recibido)),t=bearer(registrador);
        var respuestas=simultaneas(List.of(t,t,t,t),List.of(cuerpo,cuerpo,cuerpo,cuerpo));
        assertThat(respuestas).allSatisfy(r -> assertThat(r.getKey()).as("réplica concurrente: %s",r.getValue()).isEqualTo(200));
        assertThat(respuestas.stream().map(r -> json.readTree(r.getValue()).get("reclamo").get("id").asText()).distinct()).hasSize(1);
        assertThat(cantidad()).isEqualTo(1);
        var original=jdbc.queryForMap("SELECT usuario_id,recibido_en,canal_declarado,huella_solicitud FROM reclamo_institucional WHERE solicitud_id=?",solicitud);
        assertThat(original).containsEntry("usuario_id",registrador);
        assertThat(((java.sql.Timestamp)original.get("recibido_en")).toInstant()).isEqualTo(recibido);
        // Misma solicitud, simultánea, desde otro actor y con otro contenido: una sola gana; las demás 409; el original no cambia.
        UUID otra=UUID.randomUUID(); var distinto=reclamo(otra,recibido); distinto.put("descripcion","Otro contenido "+marca);
        String a=json.writeValueAsString(reclamo(otra,recibido)),b=json.writeValueAsString(distinto);
        var mixtas=simultaneas(List.of(bearer(registrador),bearer(otroRegistrador),bearer(registrador)),List.of(a,a,b));
        assertThat(mixtas.stream().filter(r -> r.getKey()==200)).as("una sola gana: %s",mixtas).hasSize(1);
        assertThat(mixtas.stream().filter(r -> r.getKey()==409)).as("las demás se rechazan: %s",mixtas).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reclamo_institucional WHERE solicitud_id=?",Integer.class,otra)).isEqualTo(1);
        JsonNode ganador=json.readTree(mixtas.stream().filter(r -> r.getKey()==200).findFirst().orElseThrow().getValue()).get("reclamo");
        var guardado=jdbc.queryForMap("SELECT usuario_id,descripcion FROM reclamo_institucional WHERE solicitud_id=?",otra);
        assertThat(guardado.get("usuario_id")).as("autor del ganador, no reemplazado").isEqualTo(UUID.fromString(ganador.get("usuarioId").asText()));
        assertThat(guardado.get("descripcion")).isEqualTo(ganador.get("descripcion").asText());
        assertThat(jdbc.queryForMap("SELECT usuario_id,recibido_en,canal_declarado,huella_solicitud FROM reclamo_institucional WHERE solicitud_id=?",solicitud)).isEqualTo(original);
    }
    private List<Map.Entry<Integer,String>> simultaneas(List<String> tokens,List<String> cuerpos) throws Exception {
        var iniciar=new CountDownLatch(1); var resultado=new ArrayList<Map.Entry<Integer,String>>();
        try(var pool=Executors.newFixedThreadPool(tokens.size())) {
            List<Future<Map.Entry<Integer,String>>> f=new ArrayList<>();
            for(int i=0;i<tokens.size();i++) { String t=tokens.get(i),c=cuerpos.get(i);
                f.add(pool.submit(() -> { iniciar.await();
                    var r=mvc.perform(post(R).header("Authorization",t).contentType("application/json").content(c)).andReturn().getResponse();
                    return Map.entry(r.getStatus(),r.getContentAsString()); })); }
            iniciar.countDown();
            for(var x:f) resultado.add(x.get(20,TimeUnit.SECONDS));
        }
        return resultado;
    }

    @Test void unFalloDePersistenciaResponde503YLaMismaSolicitudSeConfirmaDespues() throws Exception {
        Instant recibido=Instant.now().minus(1,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS); UUID solicitud=UUID.randomUUID();
        doAnswer(i -> { i.callRealMethod(); throw new DataAccessResourceFailureException("Fallo inyectado (prueba)."); })
            .when(repositorio).registrar(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        enviar(registrador,reclamo(solicitud,recibido),503);
        assertThat(cantidad()).as("la inserción se revirtió").isZero();
        reset(repositorio);
        var r=enviar(registrador,reclamo(solicitud,recibido),200).get("reclamo");
        assertThat(r.get("usuarioId").asText()).isEqualTo(registrador.toString());
        assertThat(cantidad()).isEqualTo(1);
    }

    /** Cantidad de reclamos que el historial CU-29 atribuye a la jornada (visible solo con RECLAMO_CONSULTAR). */
    private int contarReclamosEnHistorial() throws Exception {
        var detalle=json.readTree(mvc.perform(get("/api/historial-servicio/jornadas/"+jornada).header("Authorization",bearer(registrador))).andReturn().getResponse().getContentAsString());
        for(JsonNode e:detalle.get("enlaces")) if(e.get("modulo").asText().startsWith("Reclamos")) return e.get("cantidad").asInt();
        throw new AssertionError("Sin entrada de reclamos en el historial");
    }
}
