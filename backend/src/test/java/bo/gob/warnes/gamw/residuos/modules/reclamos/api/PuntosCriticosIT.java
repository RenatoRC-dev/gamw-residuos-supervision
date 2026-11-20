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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * CU-28 / RF-22: registro municipal de un candidato a punto crítico, observaciones fácticas, vínculos con reclamos/inspecciones existentes,
 * consulta e historia inmutable, con JWT real y PostgreSQL/PostGIS aislados. La validación municipal (PENDING-10) permanece deshabilitada
 * (configuración por defecto): ningún candidato se presenta como punto crítico oficial. Coordenadas sintéticas fuera de Warnes: no se inventan
 * ubicaciones municipales. La BD efímera se comparte: solo se afirman IDs propios o diferencias.
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class PuntosCriticosIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    @MockitoSpyBean RepositorioReclamos repositorio;
    private static final String P="/api/puntos-criticos";
    // Coordenadas sintéticas (Atlántico): válidas en EPSG:4326, sin representar lugares municipales reales.
    private static final double LON=-10.123456789,LAT=20.987654321;
    private final UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada=UUID.randomUUID();
    private UUID registrador,otroRegistrador,soloConsulta,soloRegistrar,sinVinculos,validador,sinPermisos,reclamo1,reclamo2,inspeccion;

    @BeforeEach void fixtures() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-28");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"PC-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        String[] completo={"PUNTO_CRITICO_CONSULTAR","PUNTO_CRITICO_REGISTRAR","RECLAMO_CONSULTAR","FISCALIZACION_CONSULTAR"};
        registrador=cuenta(completo); otroRegistrador=cuenta(completo);
        soloConsulta=cuenta("PUNTO_CRITICO_CONSULTAR");
        soloRegistrar=cuenta("PUNTO_CRITICO_REGISTRAR");
        sinVinculos=cuenta("PUNTO_CRITICO_CONSULTAR","PUNTO_CRITICO_REGISTRAR");
        validador=cuenta("PUNTO_CRITICO_CONSULTAR","PUNTO_CRITICO_VALIDAR");
        sinPermisos=cuenta("RECLAMO_CONSULTAR","FISCALIZACION_CONSULTAR");
        reclamo1=reclamo(); reclamo2=reclamo();
        inspeccion=UUID.randomUUID();
        jdbc.update("INSERT INTO inspeccion(id,solicitud_id,huella_solicitud,jornada_id,inspector_usuario_id,realizada_en,observaciones) VALUES (?,?,'h',?,?,?,?)",
            inspeccion,UUID.randomUUID(),jornada,cuenta(),Timestamp.from(Instant.now().minus(1,ChronoUnit.DAYS)),"Inspección sintética CU-28");
    }
    @AfterEach void restaurar() { reset(repositorio); }
    private UUID cuenta(String... permisos) {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"PC-"+persona,"Persona sintética");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"pc-"+id,codificador.encode("clave-sintetica"));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    /** Reclamo institucional preexistente (CU-26), sembrado con su procedencia original. */
    private UUID reclamo() {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reclamo_institucional(id,solicitud_id,huella_solicitud,canal_declarado,recibido_en,descripcion,lugar_descripcion,referencias_origen,geometria,jornada_id,usuario_id)
            VALUES (?,?,'h','Teléfono institucional (declarado)',?,'Reclamo sintético CU-28','Calle sintética','',ST_SetSRID(ST_MakePoint(?,?),4326),NULL,?)""",
            id,UUID.randomUUID(),Timestamp.from(Instant.now().minus(2,ChronoUnit.HOURS)),LON,LAT,cuenta());
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
    private static Map<String,Object> punto(UUID solicitud,Object lon,Object lat,UUID reclamo,UUID inspeccion) {
        var m=new LinkedHashMap<String,Object>();
        m.put("solicitudId",solicitud); m.put("denominacion","Candidato sintético"); m.put("descripcion","Acumulación observada en vía pública (sintética).");
        m.put("longitud",lon); m.put("latitud",lat); m.put("sustentoMunicipal","Observación directa del fiscalizador (sintética).");
        m.put("reclamoId",reclamo); m.put("inspeccionId",inspeccion); return m;
    }
    private static Map<String,Object> observacion(UUID solicitud,Instant observada,String descripcion,UUID reclamo,UUID inspeccion) {
        var m=new LinkedHashMap<String,Object>();
        m.put("solicitudId",solicitud); m.put("observadaEn",observada==null?null:observada.toString()); m.put("descripcion",descripcion);
        m.put("reclamoId",reclamo); m.put("inspeccionId",inspeccion); return m;
    }
    private UUID crear(UUID u,Map<String,Object> cuerpo) throws Exception { return UUID.fromString(llamar(u,"POST",P,cuerpo,200).get("punto").get("id").asText()); }
    private int puntosDeSolicitud(UUID solicitud) { return jdbc.queryForObject("SELECT count(*) FROM punto_critico WHERE solicitud_id=?",Integer.class,solicitud); }
    private int observaciones(UUID punto) { return jdbc.queryForObject("SELECT count(*) FROM observacion_punto_critico WHERE punto_id=?",Integer.class,punto); }
    private int validaciones(UUID punto) { return jdbc.queryForObject("SELECT count(*) FROM validacion_punto_critico WHERE punto_id=?",Integer.class,punto); }
    private int total(String tabla) { return jdbc.queryForObject("SELECT count(*) FROM "+tabla,Integer.class); }
    private Map<String,Object> reclamoOriginal(UUID id) { return jdbc.queryForMap("SELECT canal_declarado,recibido_en,descripcion,referencias_origen,ST_AsText(geometria) g,jornada_id,usuario_id,registrado_en FROM reclamo_institucional WHERE id=?",id); }
    private Map<String,Object> inspeccionOriginal(UUID id) { return jdbc.queryForMap("SELECT * FROM inspeccion WHERE id=?",id); }

    @Test void candidatoConservaCoordenadasEpsg4326AutoriaFechaYAntecedentesOriginales() throws Exception {
        var r1=reclamoOriginal(reclamo1); var i1=inspeccionOriginal(inspeccion);
        int fiscalizaciones=total("fiscalizacion"),acciones=total("accion_correctiva");
        Instant antes=Instant.now();
        var d=llamar(registrador,"POST",P,punto(UUID.randomUUID(),LON,LAT,reclamo1,inspeccion),200);
        var p=d.get("punto"); UUID id=UUID.fromString(p.get("id").asText());
        assertThat(p.get("longitud").asDouble()).isEqualTo(LON);
        assertThat(p.get("latitud").asDouble()).isEqualTo(LAT);
        assertThat(p.get("usuarioId").asText()).as("autor desde el principal").isEqualTo(registrador.toString());
        assertThat(Instant.parse(p.get("registradoEn").asText())).isAfterOrEqualTo(antes.truncatedTo(ChronoUnit.MILLIS));
        assertThat(p.get("reclamoId").asText()).isEqualTo(reclamo1.toString());
        assertThat(p.get("inspeccionId").asText()).isEqualTo(inspeccion.toString());
        assertThat(p.get("validadoMunicipalmente").asBoolean()).as("candidato explícitamente no validado").isFalse();
        assertThat(d.get("validacion").isNull()).isTrue();
        assertThat(d.get("observaciones")).isEmpty();
        // PostGIS: punto EPSG:4326 en orden [longitud, latitud].
        var g=jdbc.queryForMap("SELECT ST_SRID(geometria) srid,ST_X(geometria) x,ST_Y(geometria) y,GeometryType(geometria) tipo,usuario_id FROM punto_critico WHERE id=?",id);
        assertThat(g).containsEntry("srid",4326).containsEntry("x",LON).containsEntry("y",LAT).containsEntry("tipo","POINT").containsEntry("usuario_id",registrador);
        // Antecedentes originales intactos; sin fiscalización, acción correctiva ni validación derivadas.
        assertThat(reclamoOriginal(reclamo1)).isEqualTo(r1);
        assertThat(inspeccionOriginal(inspeccion)).isEqualTo(i1);
        assertThat(total("fiscalizacion")).isEqualTo(fiscalizaciones);
        assertThat(total("accion_correctiva")).isEqualTo(acciones);
        assertThat(validaciones(id)).isZero();
        // Consulta (lista y detalle) por un lector sin facultad de registro: candidato visible y diferenciado.
        var lista=llamar(soloConsulta,"GET",P,null,200).get("registros");
        JsonNode enLista=null; for(JsonNode x:lista) if(x.get("id").asText().equals(id.toString())) enLista=x;
        assertThat(enLista).as("el candidato recién registrado figura en la primera página").isNotNull();
        assertThat(enLista.get("validadoMunicipalmente").asBoolean()).isFalse();
        assertThat(llamar(soloConsulta,"GET",P+"/"+id,null,200).get("punto")).isEqualTo(p);
        var opciones=llamar(soloConsulta,"GET","/api/reclamos-institucionales/opciones",null,200);
        assertThat(opciones.get("puedeConsultarPuntos").asBoolean()).isTrue();
        assertThat(opciones.get("puedeRegistrarPuntos").asBoolean()).isFalse();
        assertThat(opciones.get("politicaPuntosHabilitada").asBoolean()).as("PENDING-10 deshabilitado").isFalse();
        // Historia inmutable en PostgreSQL.
        assertThatThrownBy(() -> jdbc.update("UPDATE punto_critico SET denominacion='alterada' WHERE id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM punto_critico WHERE id=?",id)).isInstanceOf(DataAccessException.class);
    }

    @Test void observacionesAgreganHistoriaSinBorrarValidarNiDerivarSanciones() throws Exception {
        UUID id=crear(registrador,punto(UUID.randomUUID(),LON,LAT,null,null));
        int fiscalizaciones=total("fiscalizacion"),acciones=total("accion_correctiva"),jornadas=total("jornada"),puntos=total("punto_critico");
        Instant t1=Instant.now().minus(3,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS),t0=t1.minus(2,ChronoUnit.DAYS);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),t1,"Persiste acumulación (sintética).",reclamo1,null),200);
        llamar(otroRegistrador,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),t1.plusSeconds(60),"Nuevo reporte asociado (sintético).",reclamo2,inspeccion),200);
        // Antecedente anterior registrado después: se intercala por fecha observada sin alterar lo previo.
        var d=llamar(registrador,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),t0,"Antecedente previo documentado (sintético).",null,null),200);
        var historia=d.get("observaciones");
        assertThat(historia).hasSize(3);
        assertThat(historia.get(0).get("descripcion").asText()).isEqualTo("Antecedente previo documentado (sintético).");
        assertThat(Instant.parse(historia.get(1).get("observadaEn").asText())).isEqualTo(t1);
        assertThat(historia.get(1).get("usuarioId").asText()).isEqualTo(registrador.toString());
        assertThat(historia.get(1).get("reclamoId").asText()).isEqualTo(reclamo1.toString());
        assertThat(historia.get(2).get("usuarioId").asText()).isEqualTo(otroRegistrador.toString());
        assertThat(historia.get(2).get("inspeccionId").asText()).isEqualTo(inspeccion.toString());
        for(JsonNode o:historia) assertThat(Instant.parse(o.get("registradaEn").asText())).isAfter(Instant.parse(o.get("observadaEn").asText()));
        // Varias denuncias y observaciones no convierten el candidato en oficial ni crean otros puntos, sanciones o reprogramaciones.
        assertThat(d.get("punto").get("validadoMunicipalmente").asBoolean()).isFalse();
        assertThat(d.get("validacion").isNull()).isTrue();
        assertThat(validaciones(id)).isZero();
        assertThat(total("punto_critico")).isEqualTo(puntos);
        assertThat(total("fiscalizacion")).isEqualTo(fiscalizaciones);
        assertThat(total("accion_correctiva")).isEqualTo(acciones);
        assertThat(total("jornada")).isEqualTo(jornadas);
        // Lector: historia completa. Observación inmutable en PostgreSQL.
        assertThat(llamar(soloConsulta,"GET",P+"/"+id,null,200).get("observaciones")).isEqualTo(historia);
        UUID o=UUID.fromString(historia.get(1).get("id").asText());
        assertThatThrownBy(() -> jdbc.update("UPDATE observacion_punto_critico SET descripcion='alterada' WHERE id=?",o)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM observacion_punto_critico WHERE id=?",o)).isInstanceOf(DataAccessException.class);
        // Registrar reclamos con la misma coordenada (CU-26) no crea puntos críticos automáticamente.
        UUID receptor=cuenta("RECLAMO_CONSULTAR","RECLAMO_REGISTRAR");
        for(int i=0;i<3;i++) {
            var r=new LinkedHashMap<String,Object>();
            r.put("solicitudId",UUID.randomUUID()); r.put("canalDeclarado","Teléfono institucional (declarado)"); r.put("recibidoEn",Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS).toString());
            r.put("descripcion","Reclamo repetido sintético "+i); r.put("lugarDescripcion",""); r.put("referenciasOrigen",""); r.put("longitud",LON); r.put("latitud",LAT); r.put("jornadaId",null);
            llamar(receptor,"POST","/api/reclamos-institucionales",r,200);
        }
        assertThat(total("punto_critico")).as("sin zona roja ni punto por acumulación").isEqualTo(puntos);
    }

    @Test void coordenadasReferenciasYContratosInvalidosSeRechazanSinPersistir() throws Exception {
        var invalidos=new ArrayList<Map<String,Object>>();
        for(Object[] c:new Object[][]{{180.000001,LAT},{-180.5,LAT},{LON,90.000001},{LON,-91},{null,LAT},{LON,null},{null,null},{"NaN",LAT},{LON,"Infinity"},{"abc",LAT}})
            invalidos.add(punto(UUID.randomUUID(),c[0],c[1],null,null));
        invalidos.add(punto(UUID.randomUUID(),LON,LAT,UUID.randomUUID(),null));      // reclamo inexistente
        invalidos.add(punto(UUID.randomUUID(),LON,LAT,null,UUID.randomUUID()));      // inspección inexistente
        var vacio=punto(UUID.randomUUID(),LON,LAT,null,null); vacio.put("denominacion","  "); invalidos.add(vacio);
        var sinSustento=punto(UUID.randomUUID(),LON,LAT,null,null); sinSustento.put("sustentoMunicipal",""); invalidos.add(sinSustento);
        var largo=punto(UUID.randomUUID(),LON,LAT,null,null); largo.put("denominacion","x".repeat(201)); invalidos.add(largo);
        for(String campo:new String[]{"validadoMunicipalmente","usuarioId","oficial","recurrencia"}) {
            var extra=punto(UUID.randomUUID(),LON,LAT,null,null); extra.put(campo,campo.equals("usuarioId")?otroRegistrador:true); invalidos.add(extra);
        }
        for(var cuerpo:invalidos) {
            llamar(registrador,"POST",P,cuerpo,400);
            assertThat(puntosDeSolicitud((UUID)cuerpo.get("solicitudId"))).as("rechazo sin persistir: %s",cuerpo).isZero();
        }
        llamar(registrador,"POST",P,punto(null,LON,LAT,null,null),400);
        llamar(registrador,"POST",P,"{malformado",400);
        // Límites válidos exactos admitidos.
        UUID borde=crear(registrador,punto(UUID.randomUUID(),180.0,-90.0,null,null));
        assertThat(jdbc.queryForMap("SELECT ST_X(geometria) x,ST_Y(geometria) y FROM punto_critico WHERE id=?",borde)).containsEntry("x",180.0).containsEntry("y",-90.0);
        // Observaciones inválidas.
        UUID id=crear(registrador,punto(UUID.randomUUID(),LON,LAT,null,null));
        Instant ahora=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),ahora.plus(1,ChronoUnit.HOURS),"Futura",null,null),400);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),ahora.minusSeconds(10).plusNanos(1),"Nanosegundos",null,null),400);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),null,"Sin fecha",null,null),400);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),ahora,"   ",null,null),400);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),ahora,"Reclamo inexistente",UUID.randomUUID(),null),400);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),ahora,"Inspección inexistente",null,UUID.randomUUID()),400);
        var extra=observacion(UUID.randomUUID(),ahora,"Con autor del cliente",null,null); extra.put("usuarioId",otroRegistrador);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",extra,400);
        llamar(registrador,"POST",P+"/"+UUID.randomUUID()+"/observaciones",observacion(UUID.randomUUID(),ahora,"Punto inexistente",null,null),404);
        assertThat(observaciones(id)).isZero();
        llamar(registrador,"GET",P+"/"+UUID.randomUUID(),null,404);
        // PENDING-10: la validación está deshabilitada; ni el validador ni el registrador pueden oficializar el candidato.
        var confirmar=Map.of("confirmaValidacionMunicipal",true,"fundamento","Fundamento sintético");
        llamar(validador,"POST",P+"/"+id+"/validacion",confirmar,409);
        llamar(registrador,"POST",P+"/"+id+"/validacion",confirmar,403);
        assertThat(validaciones(id)).isZero();
        assertThat(llamar(validador,"GET",P+"/"+id,null,200).get("punto").get("validadoMunicipalmente").asBoolean()).isFalse();
        assertThat(llamar(validador,"GET","/api/reclamos-institucionales/opciones",null,200).get("puedeValidarPuntos").asBoolean())
            .as("tiene el permiso, pero la política sigue cerrada").isTrue();
    }

    @Test void solicitudesDuplicadasYConcurrentesSonIdempotentesYLasConflictivasSeRechazan() throws Exception {
        UUID solicitud=UUID.randomUUID(); var cuerpo=punto(solicitud,LON,LAT,reclamo1,null);
        var primera=llamar(registrador,"POST",P,cuerpo,200);
        assertThat(llamar(registrador,"POST",P,cuerpo,200)).isEqualTo(primera);
        var distinto=punto(solicitud,LON,LAT+0.001,reclamo1,null);
        llamar(registrador,"POST",P,distinto,409);
        llamar(otroRegistrador,"POST",P,cuerpo,409);
        assertThat(puntosDeSolicitud(solicitud)).isEqualTo(1);
        UUID id=UUID.fromString(primera.get("punto").get("id").asText());
        // Réplicas concurrentes del registro: un solo candidato, todas responden con el original. La inserción se retiene dentro de la
        // transacción para que las réplicas se superpongan de forma determinista (sin depender del azar del planificador).
        doAnswer(i -> { Object r=i.callRealMethod(); Thread.sleep(400); return r; })
            .when(repositorio).crearPunto(any(),any(),any(),any(),any(),anyDouble(),anyDouble(),any(),any(),any(),any());
        doAnswer(i -> { i.callRealMethod(); Thread.sleep(400); return null; }).when(repositorio).observar(any(),any(),any(),any(),any(),any(),any(),any());
        UUID concurrente=UUID.randomUUID(); String c=json.writeValueAsString(punto(concurrente,LON,LAT,null,inspeccion)),t=token(registrador);
        var respuestas=replicas(P,c,t);
        assertThat(respuestas).allSatisfy(r -> assertThat(r.getKey()).as("réplica concurrente: %s",r.getValue()).isEqualTo(200));
        assertThat(respuestas.stream().map(r -> json.readTree(r.getValue()).get("punto").get("id").asText()).distinct()).hasSize(1);
        assertThat(puntosDeSolicitud(concurrente)).isEqualTo(1);
        // Observaciones: réplica idéntica idempotente, contenido u otro punto con la misma solicitud → conflicto; réplicas concurrentes.
        UUID so=UUID.randomUUID(); var ob=observacion(so,Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.SECONDS),"Observación única",null,null);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",ob,200);
        llamar(registrador,"POST",P+"/"+id+"/observaciones",ob,200);
        assertThat(observaciones(id)).isEqualTo(1);
        var obDistinta=new LinkedHashMap<>(ob); obDistinta.put("descripcion","Otra descripción");
        llamar(registrador,"POST",P+"/"+id+"/observaciones",obDistinta,409);
        llamar(otroRegistrador,"POST",P+"/"+id+"/observaciones",ob,409);
        UUID otro=crear(registrador,punto(UUID.randomUUID(),LON,LAT,null,null));
        llamar(registrador,"POST",P+"/"+otro+"/observaciones",ob,409);
        assertThat(observaciones(id)).isEqualTo(1); assertThat(observaciones(otro)).isZero();
        String oc=json.writeValueAsString(observacion(UUID.randomUUID(),Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS),"Observación con réplicas concurrentes",null,null));
        assertThat(replicas(P+"/"+otro+"/observaciones",oc,t)).allSatisfy(r -> assertThat(r.getKey()).as("réplica concurrente: %s",r.getValue()).isEqualTo(200));
        assertThat(observaciones(otro)).isEqualTo(1);
    }
    private List<Map.Entry<Integer,String>> replicas(String ruta,String cuerpo,String t) throws Exception {
        var iniciar=new CountDownLatch(1); var resultado=new ArrayList<Map.Entry<Integer,String>>();
        try(var pool=Executors.newFixedThreadPool(4)) {
            List<Future<Map.Entry<Integer,String>>> f=new ArrayList<>();
            for(int i=0;i<4;i++) f.add(pool.submit(() -> { iniciar.await();
                var r=mvc.perform(post(ruta).header("Authorization","Bearer "+t).contentType("application/json").content(cuerpo)).andReturn().getResponse();
                return Map.entry(r.getStatus(),r.getContentAsString()); }));
            iniciar.countDown();
            for(var x:f) resultado.add(x.get(20,TimeUnit.SECONDS));
        }
        return resultado;
    }

    @Test void soloPermisosMunicipalesExplicitosActuanYLaRevocacionEsInmediata() throws Exception {
        UUID id=crear(registrador,punto(UUID.randomUUID(),LON,LAT,null,null));
        Instant t=Instant.now().minusSeconds(300).truncatedTo(ChronoUnit.SECONDS);
        for(UUID u:new UUID[]{soloConsulta,soloRegistrar,validador,sinPermisos}) {
            var s=UUID.randomUUID();
            llamar(u,"POST",P,punto(s,LON,LAT,null,null),403);
            assertThat(puntosDeSolicitud(s)).isZero();
            llamar(u,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),t,"Sin permiso",null,null),403);
        }
        llamar(soloRegistrar,"GET",P,null,403);   // registrar sin consultar no basta
        llamar(sinPermisos,"GET",P+"/"+id,null,403);
        llamar(sinPermisos,"GET",P,null,403);
        // Vincular evidencia exige poder consultarla: sin RECLAMO_CONSULTAR / FISCALIZACION_CONSULTAR no se asocia.
        var s1=UUID.randomUUID(); llamar(sinVinculos,"POST",P,punto(s1,LON,LAT,reclamo1,null),403);
        var s2=UUID.randomUUID(); llamar(sinVinculos,"POST",P,punto(s2,LON,LAT,null,inspeccion),403);
        assertThat(puntosDeSolicitud(s1)+puntosDeSolicitud(s2)).isZero();
        llamar(sinVinculos,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),t,"Con reclamo",reclamo1,null),403);
        crear(sinVinculos,punto(UUID.randomUUID(),LON,LAT,null,null));
        assertThat(mvc.perform(post(P).contentType("application/json").content(json.writeValueAsString(punto(UUID.randomUUID(),LON,LAT,null,null))))
            .andReturn().getResponse().getStatus()).isIn(401,403);
        assertThat(mvc.perform(get(P+"/"+id)).andReturn().getResponse().getStatus()).isIn(401,403);
        assertThat(observaciones(id)).isZero();
        // Revocación sobre el mismo token.
        String tk=token(registrador);
        llamar(tk,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),t,"Antes de revocar",null,null),200);
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='PUNTO_CRITICO_REGISTRAR' AND revocado_en IS NULL",registrador);
        var s3=UUID.randomUUID(); llamar(tk,"POST",P,punto(s3,LON,LAT,null,null),403);
        llamar(tk,"POST",P+"/"+id+"/observaciones",observacion(UUID.randomUUID(),t,"Tras revocar",null,null),403);
        assertThat(llamar(tk,"GET","/api/reclamos-institucionales/opciones",null,200).get("puedeRegistrarPuntos").asBoolean()).isFalse();
        assertThat(llamar(tk,"GET",P+"/"+id,null,200).get("observaciones")).as("la consulta no revocada sigue").hasSize(1);
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='PUNTO_CRITICO_CONSULTAR' AND revocado_en IS NULL",registrador);
        llamar(tk,"GET",P+"/"+id,null,403);
        llamar(tk,"GET",P,null,403);
        assertThat(puntosDeSolicitud(s3)).isZero();
        assertThat(observaciones(id)).isEqualTo(1);
    }

    @Test void unFalloDePersistenciaResponde503YNoConfirmaNiDejaRastro() throws Exception {
        UUID id=crear(registrador,punto(UUID.randomUUID(),LON,LAT,null,null));
        doAnswer(i -> { i.callRealMethod(); throw new DataAccessResourceFailureException("Fallo inyectado (prueba)."); })
            .when(repositorio).crearPunto(any(),any(),any(),any(),any(),anyDouble(),anyDouble(),any(),any(),any(),any());
        UUID s=UUID.randomUUID();
        var r=mvc.perform(post(P).header("Authorization","Bearer "+token(registrador)).contentType("application/json")
            .content(json.writeValueAsString(punto(s,LON,LAT,reclamo1,null)))).andReturn().getResponse();
        assertThat(r.getStatus()).as("fallo de persistencia, no «sin permiso»: %s",r.getContentAsString()).isEqualTo(503);
        assertThat(puntosDeSolicitud(s)).as("la inserción se revirtió").isZero();
        doAnswer(i -> { i.callRealMethod(); throw new DataAccessResourceFailureException("Fallo inyectado (prueba)."); })
            .when(repositorio).observar(any(),any(),any(),any(),any(),any(),any(),any());
        r=mvc.perform(post(P+"/"+id+"/observaciones").header("Authorization","Bearer "+token(registrador)).contentType("application/json")
            .content(json.writeValueAsString(observacion(UUID.randomUUID(),Instant.now().minusSeconds(30).truncatedTo(ChronoUnit.SECONDS),"Con fallo",null,null)))).andReturn().getResponse();
        assertThat(r.getStatus()).as("fallo de persistencia, no «sin permiso»: %s",r.getContentAsString()).isEqualTo(503);
        assertThat(observaciones(id)).isZero();
        // Tras el fallo, la misma solicitud se confirma normalmente (reintento seguro).
        reset(repositorio);
        llamar(registrador,"POST",P,punto(s,LON,LAT,reclamo1,null),200);
        assertThat(puntosDeSolicitud(s)).isEqualTo(1);
    }
}
