package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import bo.gob.warnes.gamw.residuos.modules.reclamos.dominio.RepositorioReclamos;
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
 * CU-27 / RF-21: revisión municipal de un reclamo institucional (observaciones fácticas y vínculos justificados con antecedentes existentes),
 * con JWT real y PostgreSQL/PostGIS aislados. Catálogo de valoración sin categorías aprobadas (D1/PENDING): no se registra resultado oficial ni
 * estado de tratamiento. Sin acceso A4, sin anexos, sin SLA, sin sanciones. La BD efímera se comparte: solo se afirman IDs propios.
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class RevisionReclamosIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    @MockitoSpyBean RepositorioReclamos repositorio;
    private static final String R="/api/reclamos-institucionales";
    private final UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada1=UUID.randomUUID(),jornada2=UUID.randomUUID();
    private UUID registrador,revisor,otroRevisor,soloConsulta,soloRevisar,sinPermisos,inspeccion1,inspeccion2;
    private final Instant recibido=Instant.now().minus(5,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach void fixtures() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-27");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"RV-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada1,periodo,version);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-09T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada2,periodo,version);
        registrador=cuenta("RECLAMO_CONSULTAR","RECLAMO_REGISTRAR");
        revisor=cuenta("RECLAMO_CONSULTAR","RECLAMO_REVISAR");
        otroRevisor=cuenta("RECLAMO_CONSULTAR","RECLAMO_REVISAR");
        soloConsulta=cuenta("RECLAMO_CONSULTAR");
        soloRevisar=cuenta("RECLAMO_REVISAR");
        sinPermisos=cuenta("CORRECTIVA_CONSULTAR","FISCALIZACION_CONSULTAR");
        inspeccion1=inspeccion(jornada1); inspeccion2=inspeccion(jornada2);
    }
    @AfterEach void restaurar() { reset(repositorio); }
    private UUID cuenta(String... permisos) {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"RV-"+persona,"Persona sintética");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"rv-"+id,codificador.encode("clave-sintetica"));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private UUID inspeccion(UUID jornada) {
        UUID id=UUID.randomUUID(),inspector=cuenta();
        jdbc.update("INSERT INTO inspeccion(id,solicitud_id,huella_solicitud,jornada_id,inspector_usuario_id,realizada_en,observaciones) VALUES (?,?,'h',?,?,?,?)",
            id,UUID.randomUUID(),jornada,inspector,Timestamp.from(Instant.now().minus(1,ChronoUnit.DAYS)),"Inspección sintética");
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
    /** Reclamo registrado por CU-26 (vía HTTP, autor desde el principal). */
    private UUID reclamo(UUID jornada) throws Exception {
        var d=new LinkedHashMap<String,Object>();
        d.put("solicitudId",UUID.randomUUID()); d.put("canalDeclarado","Teléfono institucional (declarado)"); d.put("recibidoEn",recibido.toString());
        d.put("descripcion","Reclamo sintético CU-27"); d.put("lugarDescripcion","Calle sintética"); d.put("referenciasOrigen",jornada==null?"":"Registro de llamada sintético");
        d.put("longitud",null); d.put("latitud",null); d.put("jornadaId",jornada);
        return UUID.fromString(llamar(registrador,"POST",R,d,200).get("reclamo").get("id").asText());
    }
    private static Map<String,Object> revision(UUID solicitud,UUID resultado,String observaciones) {
        var m=new LinkedHashMap<String,Object>(); m.put("solicitudId",solicitud); m.put("resultadoId",resultado); m.put("observaciones",observaciones); return m;
    }
    private static Map<String,Object> relacion(String tipo,UUID referencia,String fundamento) {
        var m=new LinkedHashMap<String,Object>(); m.put("tipo",tipo); m.put("referenciaId",referencia); m.put("fundamento",fundamento); return m;
    }
    private int revisiones(UUID reclamo) { return jdbc.queryForObject("SELECT count(*) FROM revision_reclamo WHERE reclamo_id=?",Integer.class,reclamo); }
    private int relaciones(UUID reclamo) { return jdbc.queryForObject("SELECT count(*) FROM relacion_reclamo WHERE reclamo_id=?",Integer.class,reclamo); }
    private Map<String,Object> origen(UUID reclamo) {
        return jdbc.queryForMap("SELECT canal_declarado,recibido_en,descripcion,referencias_origen,jornada_id,usuario_id,registrado_en,huella_solicitud FROM reclamo_institucional WHERE id=?",reclamo);
    }

    @Test void observacionesFacticasSinResultadoConservanProcedenciaAutorEHistoria() throws Exception {
        UUID id=reclamo(null); var original=origen(id);
        int acciones=jdbc.queryForObject("SELECT count(*) FROM accion_correctiva",Integer.class),fiscalizaciones=jdbc.queryForObject("SELECT count(*) FROM fiscalizacion",Integer.class);
        var opciones=llamar(revisor,"GET",R+"/opciones",null,200);
        assertThat(opciones.get("puedeRevisarReclamos").asBoolean()).isTrue();
        var d1=llamar(revisor,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),null,"Se contrastó con la ruta declarada; falta contexto de horario."),200);
        var d2=llamar(otroRevisor,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),null,"Se solicitará información adicional al área receptora."),200);
        assertThat(d1.get("revisiones")).hasSize(1);
        var historia=d2.get("revisiones");
        assertThat(historia).hasSize(2);
        assertThat(historia.get(0).get("usuarioId").asText()).as("autor desde la sesión").isEqualTo(revisor.toString());
        assertThat(historia.get(1).get("usuarioId").asText()).isEqualTo(otroRevisor.toString());
        for(JsonNode r:historia) {
            assertThat(r.get("resultadoId").isNull()).as("sin resultado oficial").isTrue();
            assertThat(r.get("resultado").isNull()).isTrue();
            assertThat(Instant.parse(r.get("registradaEn").asText())).isAfter(recibido);
        }
        assertThat(Instant.parse(historia.get(1).get("registradaEn").asText())).isAfterOrEqualTo(Instant.parse(historia.get(0).get("registradaEn").asText()));
        var reclamo=d2.get("reclamo");
        assertThat(reclamo.get("validadoMunicipalmente").asBoolean()).as("una observación no valida el reclamo").isFalse();
        assertThat(reclamo.get("usuarioId").asText()).as("el registrante original se conserva").isEqualTo(registrador.toString());
        assertThat(Instant.parse(reclamo.get("recibidoEn").asText())).isEqualTo(recibido);
        assertThat(origen(id)).as("procedencia, fecha de recepción y autor intactos").isEqualTo(original);
        // Sin incumplimiento, acción correctiva ni fiscalización derivados (CA-27-07).
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accion_correctiva",Integer.class)).isEqualTo(acciones);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion",Integer.class)).isEqualTo(fiscalizaciones);
        // Historia inmutable a nivel PostgreSQL.
        UUID rev=UUID.fromString(historia.get(0).get("id").asText());
        assertThatThrownBy(() -> jdbc.update("UPDATE revision_reclamo SET observaciones='alterada' WHERE id=?",rev)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM revision_reclamo WHERE id=?",rev)).isInstanceOf(DataAccessException.class);
        // Consulta con solo RECLAMO_CONSULTAR: ve la historia completa sin poder actuar.
        var vista=llamar(soloConsulta,"GET",R+"/"+id,null,200);
        assertThat(vista.get("revisiones")).hasSize(2);
        assertThat(vista.get("revisiones").get(0).get("observaciones").asText()).isEqualTo("Se contrastó con la ruta declarada; falta contexto de horario.");
    }

    @Test void resultadosNoAprobadosYContratosInvalidosSeRechazanSinPersistir() throws Exception {
        UUID id=reclamo(null);
        // Categoría retirada (insertada ya deshabilitada: no amplía el catálogo vigente compartido).
        UUID retirada=UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo_valoracion_reclamo(id,codigo,nombre,referencia_aprobacion,observaciones_obligatorias,es_validacion,habilitado,operador_local) VALUES (?,?,?,?,true,true,false,'prueba-sintetica')",
            retirada,"RETIRADA-"+retirada,"Categoría sintética retirada","Sin aprobación (prueba)");
        llamar(revisor,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),UUID.randomUUID(),"Resultado inexistente"),409);
        llamar(revisor,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),retirada,"Resultado retirado"),409);
        var opciones=llamar(revisor,"GET",R+"/opciones",null,200);
        for(JsonNode c:opciones.get("resultados")) assertThat(c.get("id").asText()).as("la retirada no se ofrece").isNotEqualTo(retirada.toString());
        // Contratos inválidos: estado/autor/validación suministrados por el cliente, observación vacía, solicitud ausente.
        llamar(revisor,"POST",R+"/"+id+"/revisiones","{\"solicitudId\":\""+UUID.randomUUID()+"\",\"observaciones\":\"x\",\"estado\":\"CERRADO\"}",400);
        llamar(revisor,"POST",R+"/"+id+"/revisiones","{\"solicitudId\":\""+UUID.randomUUID()+"\",\"observaciones\":\"x\",\"usuarioId\":\""+otroRevisor+"\"}",400);
        llamar(revisor,"POST",R+"/"+id+"/revisiones","{\"solicitudId\":\""+UUID.randomUUID()+"\",\"observaciones\":\"x\",\"validadoMunicipalmente\":true}",400);
        llamar(revisor,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),null,"   "),400);
        llamar(revisor,"POST",R+"/"+id+"/revisiones",revision(null,null,"Sin solicitud"),400);
        llamar(revisor,"POST",R+"/"+id+"/revisiones","{malformado",400);
        llamar(revisor,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),null,"x".repeat(20001)),400);
        llamar(revisor,"POST",R+"/"+UUID.randomUUID()+"/revisiones",revision(UUID.randomUUID(),null,"Reclamo inexistente"),404);
        assertThat(revisiones(id)).as("nada rechazado deja revisión").isZero();
        assertThat(llamar(revisor,"GET",R+"/"+id,null,200).get("reclamo").get("validadoMunicipalmente").asBoolean()).isFalse();
    }

    @Test void solicitudesDuplicadasSonIdempotentesYLasConflictivasSeRechazan() throws Exception {
        UUID id=reclamo(null),otro=reclamo(null),solicitud=UUID.randomUUID();
        var cuerpo=revision(solicitud,null,"Observación fáctica única");
        var primera=llamar(revisor,"POST",R+"/"+id+"/revisiones",cuerpo,200);
        var replica=llamar(revisor,"POST",R+"/"+id+"/revisiones",cuerpo,200);
        assertThat(replica.get("revisiones")).isEqualTo(primera.get("revisiones"));
        assertThat(revisiones(id)).isEqualTo(1);
        llamar(revisor,"POST",R+"/"+id+"/revisiones",revision(solicitud,null,"Contenido distinto"),409);
        llamar(otroRevisor,"POST",R+"/"+id+"/revisiones",cuerpo,409);
        llamar(revisor,"POST",R+"/"+otro+"/revisiones",cuerpo,409);
        assertThat(revisiones(id)).isEqualTo(1);
        assertThat(revisiones(otro)).isZero();
        // Réplicas concurrentes de la misma solicitud: una sola revisión, todas responden con el original.
        UUID concurrente=UUID.randomUUID(); var c=revision(concurrente,null,"Observación con réplicas concurrentes");
        String t=token(revisor); var iniciar=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(4)) {
            List<Future<Integer>> estados=new ArrayList<>();
            for(int i=0;i<4;i++) estados.add(pool.submit(() -> { iniciar.await();
                return mvc.perform(post(R+"/"+otro+"/revisiones").header("Authorization","Bearer "+t).contentType("application/json").content(json.writeValueAsString(c))).andReturn().getResponse().getStatus(); }));
            iniciar.countDown();
            for(var e:estados) assertThat(e.get(20,TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(revisiones(otro)).isEqualTo(1);
    }

    @Test void vinculosSoloConAntecedentesExistentesJustificadosYCompatibles() throws Exception {
        UUID id=reclamo(null); var original=origen(id);
        // Inválidos: inexistente, sin fundamento, tipo no admitido (no existe vínculo directo a empresa), campos extra.
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",UUID.randomUUID(),"Fundamento"),400);
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("INSPECCION",UUID.randomUUID(),"Fundamento"),400);
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",jornada1,"  "),400);
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",null,"Fundamento"),400);
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("EMPRESA",empresa,"Atribución directa no admitida"),400);
        var extra=relacion("JORNADA",jornada1,"Fundamento"); extra.put("empresaId",empresa);
        llamar(revisor,"POST",R+"/"+id+"/relaciones",extra,400);
        llamar(revisor,"POST",R+"/"+UUID.randomUUID()+"/relaciones",relacion("JORNADA",jornada1,"Reclamo inexistente"),404);
        assertThat(relaciones(id)).isZero();
        // Vínculo justificado; réplica idéntica idempotente; mismo destino con otro fundamento u otro autor: conflicto (no se reemplaza).
        var d=llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",jornada1,"El lugar y horario coinciden con la jornada planificada."),200);
        var rel=d.get("relaciones").get(0);
        assertThat(rel.get("tipo").asText()).isEqualTo("JORNADA");
        assertThat(rel.get("referenciaId").asText()).isEqualTo(jornada1.toString());
        assertThat(rel.get("usuarioId").asText()).isEqualTo(revisor.toString());
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",jornada1,"El lugar y horario coinciden con la jornada planificada."),200);
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",jornada1,"Otro fundamento"),409);
        llamar(otroRevisor,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",jornada1,"El lugar y horario coinciden con la jornada planificada."),409);
        assertThat(relaciones(id)).isEqualTo(1);
        // Antecedente de la misma jornada: admitido. De otra jornada: conflicto (sin política interjornada aprobada).
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("INSPECCION",inspeccion1,"La inspección verificó el mismo tramo."),200);
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",jornada2,"Otra jornada"),409);
        llamar(revisor,"POST",R+"/"+id+"/relaciones",relacion("INSPECCION",inspeccion2,"Inspección de otra jornada"),409);
        assertThat(relaciones(id)).isEqualTo(2);
        // La relación no reescribe la jornada original del reclamo ni atribuye empresa.
        assertThat(origen(id)).isEqualTo(original);
        assertThat(origen(id).get("jornada_id")).isNull();
        UUID r=jdbc.queryForObject("SELECT id FROM relacion_reclamo WHERE reclamo_id=? AND jornada_id IS NOT NULL",UUID.class,id);
        assertThatThrownBy(() -> jdbc.update("UPDATE relacion_reclamo SET fundamento='alterado' WHERE id=?",r)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM relacion_reclamo WHERE id=?",r)).isInstanceOf(DataAccessException.class);
        // Reclamo registrado con jornada 1 (CU-26): no admite vínculo a jornada 2.
        UUID conJornada=reclamo(jornada1);
        llamar(revisor,"POST",R+"/"+conJornada+"/relaciones",relacion("JORNADA",jornada2,"Otra jornada"),409);
        llamar(revisor,"POST",R+"/"+conJornada+"/relaciones",relacion("INSPECCION",inspeccion1,"Misma jornada de origen"),200);
        assertThat(relaciones(conJornada)).isEqualTo(1);
    }

    @Test void soloLaRevisionMunicipalExplicitaPuedeActuarYLaRevocacionEsInmediata() throws Exception {
        UUID id=reclamo(null);
        for(UUID u:new UUID[]{soloConsulta,registrador,soloRevisar,sinPermisos}) {
            llamar(u,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),null,"Sin permiso de revisión"),403);
            llamar(u,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",jornada1,"Sin permiso de revisión"),403);
        }
        llamar(soloRevisar,"GET",R+"/"+id,null,403); // revisar sin consultar no basta
        llamar(sinPermisos,"GET",R,null,403);
        assertThat(mvc.perform(post(R+"/"+id+"/revisiones").contentType("application/json").content(json.writeValueAsString(revision(UUID.randomUUID(),null,"Anónimo"))))
            .andReturn().getResponse().getStatus()).isIn(401,403);
        assertThat(revisiones(id)+relaciones(id)).isZero();
        // Revocación sobre el mismo token.
        String t=token(revisor);
        llamar(t,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),null,"Antes de revocar"),200);
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='RECLAMO_REVISAR' AND revocado_en IS NULL",revisor);
        llamar(t,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),null,"Tras revocar"),403);
        llamar(t,"POST",R+"/"+id+"/relaciones",relacion("JORNADA",jornada1,"Tras revocar"),403);
        assertThat(llamar(t,"GET",R+"/opciones",null,200).get("puedeRevisarReclamos").asBoolean()).isFalse();
        assertThat(llamar(t,"GET",R+"/"+id,null,200).get("revisiones")).as("la consulta no revocada sigue").hasSize(1);
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='RECLAMO_CONSULTAR' AND revocado_en IS NULL",revisor);
        llamar(t,"GET",R+"/"+id,null,403);
        assertThat(revisiones(id)).isEqualTo(1);
        assertThat(relaciones(id)).isZero();
    }

    @Test void unFalloDePersistenciaResponde503YNoConfirmaNiDejaRastro() throws Exception {
        UUID id=reclamo(null);
        doAnswer(i -> { i.callRealMethod(); throw new DataAccessResourceFailureException("Fallo inyectado (prueba)."); }).when(repositorio).revisar(any(),any(),any(),any(),any(),any(),any());
        var r=mvc.perform(post(R+"/"+id+"/revisiones").header("Authorization","Bearer "+token(revisor)).contentType("application/json")
            .content(json.writeValueAsString(revision(UUID.randomUUID(),null,"Revisión con fallo")))).andReturn().getResponse();
        assertThat(r.getStatus()).as("fallo de persistencia, no «sin permiso»: %s",r.getContentAsString()).isEqualTo(503);
        assertThat(revisiones(id)).as("la inserción se revirtió").isZero();
        doAnswer(i -> { i.callRealMethod(); throw new DataAccessResourceFailureException("Fallo inyectado (prueba)."); }).when(repositorio).relacionar(any(),any(),any(),any(),any(),any());
        r=mvc.perform(post(R+"/"+id+"/relaciones").header("Authorization","Bearer "+token(revisor)).contentType("application/json")
            .content(json.writeValueAsString(relacion("JORNADA",jornada1,"Relación con fallo")))).andReturn().getResponse();
        assertThat(r.getStatus()).as("fallo de persistencia, no «sin permiso»: %s",r.getContentAsString()).isEqualTo(503);
        assertThat(relaciones(id)).isZero();
        // Tras el fallo, la misma operación se confirma normalmente.
        reset(repositorio);
        llamar(revisor,"POST",R+"/"+id+"/revisiones",revision(UUID.randomUUID(),null,"Revisión tras el fallo"),200);
        assertThat(revisiones(id)).isEqualTo(1);
    }
}
