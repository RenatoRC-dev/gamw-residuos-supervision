package bo.gob.warnes.gamw.residuos.modules.correctivas.api;

import bo.gob.warnes.gamw.residuos.modules.correctivas.dominio.RepositorioActuaciones;
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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * CU-23 / RF-19: cadena histórica completa (solicitud municipal A3 → atención A4 autorizada por periodo → verificación humana A3) con JWT real
 * y PostgreSQL/PostGIS aislados. PENDING-07: Flyway no precarga categorías; las categorías de este archivo son FIXTURES SINTÉTICAS de prueba
 * (`prueba-sintetica`), nunca oficiales. Sin estados, cierres, plazos ni sanciones automáticos. La BD efímera se comparte: solo se afirman IDs propios.
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class VerificacionCorrectivaIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    @MockitoSpyBean RepositorioActuaciones repositorio;
    private final UUID empresa=UUID.randomUUID(),empresaB=UUID.randomUUID(),periodo=UUID.randomUUID(),periodoB=UUID.randomUUID(),ruta=UUID.randomUUID(),
        version=UUID.randomUUID(),vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),personaC=UUID.randomUUID(),vp=UUID.randomUUID(),
        jornada=UUID.randomUUID(),otraJornada=UUID.randomUUID(),asignacion=UUID.randomUUID(),inspeccion=UUID.randomUUID(),inspeccionAjena=UUID.randomUUID();
    private final Instant inicio=Instant.now().minus(40,ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    private UUID conductor,solicitante,verificador,otroVerificador,soloConsulta,soloVerificar,operador,operadorB,incidencia;

    @BeforeEach void fixtures() throws Exception {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-23");
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaB,"Otra empresa sintética CU-23");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoB,empresaB);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"VC-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"VC-"+vehiculo.toString().substring(0,8).toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",personaC,"VC-"+personaC,"Conductor sintético");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,personaC,empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",otraJornada,periodoB,version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacion,jornada,vv,vp,Timestamp.from(inicio.minus(1,ChronoUnit.HOURS)));
        conductor=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",conductor,personaC,"vc-"+conductor,codificador.encode("clave-sintetica"));
        solicitante=cuenta("CORRECTIVA_CONSULTAR","CORRECTIVA_SOLICITAR");
        verificador=cuenta("CORRECTIVA_CONSULTAR","CORRECTIVA_VERIFICAR");
        otroVerificador=cuenta("CORRECTIVA_CONSULTAR","CORRECTIVA_VERIFICAR");
        soloConsulta=cuenta("CORRECTIVA_CONSULTAR");
        soloVerificar=cuenta("CORRECTIVA_VERIFICAR");
        operador=cuenta(); periodoOperador(operador,periodo); operadorB=cuenta(); periodoOperador(operadorB,periodoB);
        for(var i:List.of(Map.entry(inspeccion,jornada),Map.entry(inspeccionAjena,otraJornada)))
            jdbc.update("INSERT INTO inspeccion(id,solicitud_id,huella_solicitud,jornada_id,inspector_usuario_id,realizada_en,observaciones) VALUES (?,?,'h',?,?,?,?)",
                i.getKey(),UUID.randomUUID(),i.getValue(),cuenta(),Timestamp.from(inicio.plusSeconds(600)),"Inspección sintética de soporte");
        // A5 ejecuta por la API real y registra una incidencia durante la ejecución.
        llamar(conductor,"POST","/api/jornadas/"+jornada+"/ejecucion/inicio",Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",inicio.toString()),201);
        incidencia=UUID.fromString(llamar(conductor,"POST","/api/jornadas/"+jornada+"/incidencias",Map.of("solicitudId",UUID.randomUUID(),"asignacionJornadaId",asignacion,
            "ocurridaEn",inicio.plusSeconds(300).toString(),"descripcion","Contenedor bloqueado (sintético)","evidenciaDeclarada","","lugarDescripcion","Tramo sintético"),200).get("id").asText());
        llamar(conductor,"POST","/api/jornadas/"+jornada+"/ejecucion/fin",Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",inicio.plusSeconds(1200).toString()),201);
    }
    @AfterEach void restaurar() { reset(repositorio); }
    private UUID cuenta(String... permisos) {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"VC-"+persona,"Persona sintética");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"vc-"+id,codificador.encode("clave-sintetica"));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private void periodoOperador(UUID u,UUID p) {
        for(String permiso:List.of("CORRECTIVA_CONSULTAR","CORRECTIVA_RESPONDER"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),u,p,permiso);
    }
    /** Categoría de verificación SINTÉTICA de prueba (no oficial; PENDING-07). */
    private UUID categoria(boolean habilitada,boolean observacionesObligatorias) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo_verificacion_correctiva(id,codigo,nombre,referencia_aprobacion,observaciones_obligatorias,habilitado,operador_local) VALUES (?,?,?,'referencia sintética de prueba',?,?,'prueba-sintetica')",
            id,"CAT-SINT-"+id,"Categoría sintética "+id.toString().substring(0,8),observacionesObligatorias,habilitada);
        return id;
    }
    private String token(UUID u) { return emisor.emitir(u,UUID.randomUUID(),"no-confiable").token(); }
    private JsonNode llamar(UUID u,String metodo,String ruta,Object cuerpo,int estado) throws Exception { return llamar(u==null?null:token(u),metodo,ruta,cuerpo,estado); }
    private JsonNode llamar(String t,String metodo,String ruta,Object cuerpo,int estado) throws Exception {
        var b=metodo.equals("GET")?get(ruta):post(ruta);
        if(t!=null) b=b.header("Authorization","Bearer "+t);
        if(cuerpo!=null) b=b.contentType("application/json").content(cuerpo instanceof String s?s:json.writeValueAsString(cuerpo));
        var r=mvc.perform(b).andReturn().getResponse();
        assertThat(r.getStatus()).as("%s %s → %s",metodo,ruta,r.getContentAsString()).isEqualTo(estado);
        return r.getContentAsString().isBlank()?null:json.readTree(r.getContentAsString());
    }
    private UUID accion(UUID incidenciaId,UUID inspeccionId) throws Exception {
        var c=new LinkedHashMap<String,Object>(); c.put("solicitudId",UUID.randomUUID()); c.put("incidenciaId",incidenciaId); c.put("inspeccionId",inspeccionId); c.put("fiscalizacionId",null);
        c.put("descripcion","Retirar el bloqueo (solicitud municipal sintética)");
        return UUID.fromString(llamar(solicitante,"POST","/api/correctivas/jornadas/"+jornada+"/acciones",c,200).get("accion").get("id").asText());
    }
    private UUID responder(UUID accion) throws Exception {
        var c=Map.of("solicitudId",UUID.randomUUID(),"comunicadaEn",Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS).toString(),
            "observaciones","Se retiró el bloqueo (atención empresarial sintética)","evidenciaDeclarada","","evidenciasReferenciadas",List.of());
        return UUID.fromString(llamar(operador,"POST","/api/empresa/correctivas/"+accion+"/respuestas",c,200).get("id").asText());
    }
    private static Map<String,Object> verificacion(UUID solicitud,UUID respuesta,UUID resultado,String observaciones,UUID inspeccion) {
        var m=new LinkedHashMap<String,Object>(); m.put("solicitudId",solicitud); m.put("respuestaId",respuesta); m.put("resultadoId",resultado);
        m.put("observaciones",observaciones); m.put("inspeccionId",inspeccion); return m;
    }
    private String V(UUID accion) { return "/api/correctivas/acciones/"+accion+"/verificaciones"; }
    private int verificaciones(UUID accion) { return jdbc.queryForObject("SELECT count(*) FROM verificacion_correctiva WHERE accion_id=?",Integer.class,accion); }
    private Map<String,Object> historia(UUID accion) {
        return jdbc.queryForMap("""
            SELECT a.usuario_id,a.descripcion,a.creada_en,a.huella_solicitud,a.incidencia_id,
                   (SELECT string_agg(r.id::text||r.usuario_id||r.comunicada_en||r.registrada_en||r.descripcion,',' ORDER BY r.id) FROM respuesta_correctiva r WHERE r.accion_id=a.id) respuestas,
                   (SELECT string_agg(i.id::text||i.descripcion||i.ocurrida_en,',') FROM incidencia_operativa i WHERE i.id=a.incidencia_id) incidencia
            FROM accion_correctiva a WHERE a.id=?""",accion);
    }

    @Test void cadenaHistoricaCompletaSinAprobacionNiCierreAutomatico() throws Exception {
        UUID cat=categoria(true,false),a=accion(incidencia,null);
        // Sin atención empresarial no hay nada que verificar.
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),UUID.randomUUID(),cat,"x",null),409);
        UUID r=responder(a);
        // La atención empresarial no valida ni cierra: sin verificación, sin estado, sin acción nueva ni fiscalización.
        int acciones=jdbc.queryForObject("SELECT count(*) FROM accion_correctiva WHERE jornada_id=?",Integer.class,jornada);
        var exp=llamar(soloConsulta,"GET","/api/correctivas/jornadas/"+jornada,null,200);
        JsonNode h=null; for(JsonNode x:exp.get("acciones")) if(x.get("accion").get("id").asText().equals(a.toString())) h=x;
        assertThat(h).isNotNull();
        assertThat(h.get("accion").get("usuarioId").asText()).isEqualTo(solicitante.toString());
        assertThat(h.get("respuestas")).hasSize(1);
        var resp=h.get("respuestas").get(0);
        assertThat(resp.get("usuarioId").asText()).as("autor de la atención = operador autenticado").isEqualTo(operador.toString());
        assertThat(Instant.parse(resp.get("registradaEn").asText())).isAfterOrEqualTo(Instant.parse(resp.get("comunicadaEn").asText()));
        assertThat(h.get("verificaciones")).as("la empresa no se autoverifica").isEmpty();
        assertThat(exp.get("puedeVerificar").asBoolean()).isFalse();
        var antes=historia(a);
        // Verificación humana con categoría sintética: autor, fecha, respuesta e inspección de soporte de la misma jornada.
        UUID s=UUID.randomUUID(); var cuerpo=verificacion(s,r,cat,"Comprobado en visita (sintético)",inspeccion);
        var lista=llamar(verificador,"POST",V(a),cuerpo,200);
        assertThat(lista).hasSize(1);
        var v=lista.get(0);
        assertThat(v.get("usuarioId").asText()).isEqualTo(verificador.toString());
        assertThat(v.get("respuestaId").asText()).isEqualTo(r.toString());
        assertThat(v.get("resultadoId").asText()).isEqualTo(cat.toString());
        assertThat(v.get("referenciaAprobacion").asText()).isEqualTo("referencia sintética de prueba");
        assertThat(v.get("inspeccionId").asText()).isEqualTo(inspeccion.toString());
        assertThat(Instant.parse(v.get("registradaEn").asText())).isAfterOrEqualTo(Instant.parse(resp.get("registradaEn").asText()));
        // Idempotencia y no sustitución.
        assertThat(llamar(verificador,"POST",V(a),cuerpo,200)).isEqualTo(lista);
        llamar(verificador,"POST",V(a),verificacion(s,r,cat,"Otro contenido",inspeccion),409);
        llamar(otroVerificador,"POST",V(a),cuerpo,409);
        llamar(otroVerificador,"POST",V(a),verificacion(UUID.randomUUID(),r,cat,"Segunda evaluación",null),409);
        assertThat(verificaciones(a)).isEqualTo(1);
        // Historia previa intacta; sin cierre, acción nueva, fiscalización ni sanción.
        assertThat(historia(a)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accion_correctiva WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(acciones);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();
        // La empresa consulta la verificación, sin poder emitirla.
        var empresaVe=llamar(operador,"GET","/api/empresa/correctivas/"+a,null,200);
        assertThat(empresaVe.get("verificaciones")).hasSize(1);
        assertThat(empresaVe.get("respuestas")).hasSize(1);
        llamar(operador,"POST",V(a),verificacion(UUID.randomUUID(),r,cat,"x",null),403);
        // Inmutable en PostgreSQL.
        for(String[] t:new String[][]{{"verificacion_correctiva","observaciones","accion_id"},{"respuesta_correctiva","descripcion","accion_id"},{"accion_correctiva","descripcion","id"}}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE "+t[0]+" SET "+t[1]+"='alterada' WHERE "+t[2]+"=?",a)).as(t[0]).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM "+t[0]+" WHERE "+t[2]+"=?",a)).as(t[0]).isInstanceOf(DataAccessException.class);
        }
        assertThat(verificaciones(a)).isEqualTo(1);
    }

    @Test void sinCatalogoAprobadoPending07YReferenciasAjenasNoSeRegistraNada() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalogo_verificacion_correctiva WHERE operador_local<>'prueba-sintetica'",Integer.class))
            .as("Flyway V1–V21 no precarga categorías oficiales").isZero();
        UUID a=accion(incidencia,null),r=responder(a),otra=accion(null,inspeccion),rOtra=responder(otra);
        var antes=historia(a);
        UUID retirada=categoria(false,false),retiradaLuego=categoria(true,false),obligatoria=categoria(true,true),valida=categoria(true,false);
        // Retiro administrativo (identidad propietaria, como la provisión controlada); el rol de ejecución no altera el catálogo.
        var postgres=bo.gob.warnes.gamw.residuos.shared.config.PostgresV8Aislado.obtener();
        new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(postgres.urlLimpia(),postgres.usuario(),postgres.clave()))
            .update("UPDATE catalogo_verificacion_correctiva SET habilitado=false WHERE id=?",retiradaLuego);
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),r,UUID.randomUUID(),"x",null),409);   // inexistente
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),r,retirada,"x",null),409);             // inactiva
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),r,retiradaLuego,"x",null),409);        // retirada
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),r,obligatoria,"  ",null),400);         // observaciones exigidas
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),rOtra,valida,"x",null),409);           // respuesta de otra acción
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),UUID.randomUUID(),valida,"x",null),409); // respuesta inexistente
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),r,valida,"x",inspeccionAjena),400);    // inspección de otra jornada
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),r,valida,"x",UUID.randomUUID()),400);  // inspección inexistente
        llamar(verificador,"POST",V(a),verificacion(null,r,valida,"x",null),400);
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),null,valida,"x",null),400);
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),r,null,"x",null),400);
        llamar(verificador,"POST",V(a),verificacion(UUID.randomUUID(),r,valida,"x".repeat(20001),null),400);
        for(String campo:List.of("estado","cerrada","usuarioId","sancion")) {
            var c=verificacion(UUID.randomUUID(),r,valida,"x",null); c.put(campo,campo.equals("usuarioId")?otroVerificador.toString():"CERRADA");
            llamar(verificador,"POST",V(a),c,400);
        }
        llamar(verificador,"POST",V(a),"{malformado",400);
        llamar(verificador,"POST",V(UUID.randomUUID()),verificacion(UUID.randomUUID(),r,valida,"x",null),404);
        assertThat(verificaciones(a)+verificaciones(otra)).as("ningún intento rechazado deja rastro").isZero();
        assertThat(historia(a)).as("solicitud, atención e incidencia originales intactas").isEqualTo(antes);
        // El expediente solo ofrece categorías habilitadas; nunca las retiradas.
        var ofrecidas=new HashSet<String>(); for(JsonNode c:llamar(verificador,"GET","/api/correctivas/jornadas/"+jornada,null,200).get("resultados")) ofrecidas.add(c.get("id").asText());
        assertThat(ofrecidas).contains(valida.toString()).doesNotContain(retirada.toString(),retiradaLuego.toString());
    }

    @Test void soloVerificacionMunicipalExplicitaAislamientoA4YRevocacionInmediata() throws Exception {
        UUID cat=categoria(true,false),a=accion(incidencia,null),r=responder(a);
        for(UUID u:new UUID[]{solicitante,soloConsulta,soloVerificar,operador,operadorB,conductor})
            llamar(u,"POST",V(a),verificacion(UUID.randomUUID(),r,cat,"Sin facultad",null),403);
        assertThat(mvc.perform(post(V(a)).contentType("application/json").content(json.writeValueAsString(verificacion(UUID.randomUUID(),r,cat,"x",null))))
            .andReturn().getResponse().getStatus()).isIn(401,403);
        // A4 de otro periodo/empresa no ve la acción ni el expediente municipal.
        assertThat(mvc.perform(get("/api/empresa/correctivas/"+a).header("Authorization","Bearer "+token(operadorB))).andReturn().getResponse().getStatus()).isIn(403,404);
        assertThat(llamar(operadorB,"GET","/api/empresa/correctivas",null,200).get("acciones").toString()).doesNotContain(a.toString());
        llamar(operador,"GET","/api/correctivas/jornadas/"+jornada,null,403);
        assertThat(verificaciones(a)).isZero();
        // Revocación sobre el mismo token.
        String t=token(verificador);
        assertThat(llamar(t,"GET","/api/correctivas/jornadas/"+jornada,null,200).get("puedeVerificar").asBoolean()).isTrue();
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='CORRECTIVA_VERIFICAR' AND revocado_en IS NULL",verificador);
        llamar(t,"POST",V(a),verificacion(UUID.randomUUID(),r,cat,"Tras revocar",null),403);
        assertThat(llamar(t,"GET","/api/correctivas/jornadas/"+jornada,null,200).get("puedeVerificar").asBoolean()).isFalse();
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='CORRECTIVA_CONSULTAR' AND revocado_en IS NULL",verificador);
        llamar(t,"GET","/api/correctivas/jornadas/"+jornada,null,403);
        assertThat(verificaciones(a)).isZero();
    }

    @Test void verificacionesConcurrentesUnaSolaEvaluacionPorAtencion() throws Exception {
        UUID cat=categoria(true,false),a=accion(incidencia,null),r=responder(a);
        doAnswer(i -> { i.callRealMethod(); Thread.sleep(400); return null; }).when(repositorio).verificar(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        String t=token(verificador),c=json.writeValueAsString(verificacion(UUID.randomUUID(),r,cat,"Réplicas concurrentes",null));
        var replicas=simultaneas(V(a),List.of(t,t,t,t),List.of(c,c,c,c));
        assertThat(replicas).allSatisfy(x -> assertThat(x.getKey()).as("réplica: %s",x.getValue()).isEqualTo(200));
        assertThat(verificaciones(a)).isEqualTo(1);
        UUID b=accion(incidencia,null),rb=responder(b);
        var distintas=simultaneas(V(b),List.of(token(verificador),token(otroVerificador)),
            List.of(json.writeValueAsString(verificacion(UUID.randomUUID(),rb,cat,"Evaluación A",null)),json.writeValueAsString(verificacion(UUID.randomUUID(),rb,cat,"Evaluación B",null))));
        assertThat(distintas.stream().map(Map.Entry::getKey).sorted().toList()).as("%s",distintas).containsExactly(200,409);
        assertThat(verificaciones(b)).isEqualTo(1);
    }
    private List<Map.Entry<Integer,String>> simultaneas(String ruta,List<String> tokens,List<String> cuerpos) throws Exception {
        var iniciar=new CountDownLatch(1); var resultado=new ArrayList<Map.Entry<Integer,String>>();
        try(var pool=Executors.newFixedThreadPool(tokens.size())) {
            List<Future<Map.Entry<Integer,String>>> f=new ArrayList<>();
            for(int i=0;i<tokens.size();i++) { String t=tokens.get(i),c=cuerpos.get(i);
                f.add(pool.submit(() -> { iniciar.await();
                    var r=mvc.perform(post(ruta).header("Authorization","Bearer "+t).contentType("application/json").content(c)).andReturn().getResponse();
                    return Map.entry(r.getStatus(),r.getContentAsString()); })); }
            iniciar.countDown();
            for(var x:f) resultado.add(x.get(20,TimeUnit.SECONDS));
        }
        return resultado;
    }

    @Test void unFalloDePersistenciaResponde503ConservaElEstadoPrevioYElReintentoConfirma() throws Exception {
        UUID cat=categoria(true,false),a=accion(incidencia,null),r=responder(a);
        var antes=historia(a);
        doAnswer(i -> { i.callRealMethod(); throw new DataAccessResourceFailureException("Fallo inyectado (prueba)."); })
            .when(repositorio).verificar(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
        var cuerpo=verificacion(UUID.randomUUID(),r,cat,"Con fallo de guardado",null);
        llamar(verificador,"POST",V(a),cuerpo,503);
        assertThat(verificaciones(a)).as("sin falso éxito").isZero();
        assertThat(historia(a)).isEqualTo(antes);
        reset(repositorio);
        assertThat(llamar(verificador,"POST",V(a),cuerpo,200)).hasSize(1);
        assertThat(verificaciones(a)).isEqualTo(1);
    }
}
