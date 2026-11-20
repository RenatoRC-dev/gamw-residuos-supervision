package bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.dominio.RepositorioReferenciasEspaciales;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.sql.Timestamp;
import java.time.Instant;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * CU-10 (referencias espaciales) con JWT real y PostgreSQL/PostGIS aislados: GeoJSON 2D EPSG:4326 válido e inválido, asociación a
 * RutaVersion, versionado, cierre, preservación histórica para CU-18, autorización municipal explícita (sin A4), revocación y reversión.
 * Coordenadas y radios sintéticos de prueba: no son ubicaciones ni radios institucionales.
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class ReferenciasEspacialesIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    @MockitoSpyBean RepositorioReferenciasEspaciales repositorio;
    UUID version,gestor,lector,sinPermiso,operador,revisor;

    UUID usuario(String... permisos) {
        UUID id=UUID.randomUUID(),persona=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"RE-"+persona,"Cuenta sintética CU-10");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"re-"+id,codificador.encode("x"));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    @BeforeEach void preparar() {
        UUID ruta=UUID.randomUUID(); version=UUID.randomUUID();
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"RE-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.10 -17.70,-63.10 -17.60)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        gestor=usuario("REFERENCIA_ESPACIAL_CONSULTAR","REFERENCIA_ESPACIAL_GESTIONAR");
        lector=usuario("REFERENCIA_ESPACIAL_CONSULTAR");
        sinPermiso=usuario("TRAYECTORIA_JORNADA_CONSULTAR");
        operador=usuario();
        revisor=usuario("GPS_COINCIDENCIAS_REVISAR");
    }
    @AfterEach void restaurar() { reset(repositorio); }

    private String token(UUID u) { return emisor.emitir(u,UUID.randomUUID(),"no-confiable").token(); }
    private JsonNode llamar(UUID u,String metodo,String ruta,Object cuerpo,int estado) throws Exception {
        var b=metodo.equals("GET")?get(ruta):post(ruta);
        if(u!=null) b=b.header("Authorization","Bearer "+token(u));
        if(cuerpo!=null) b=b.contentType("application/json").content(cuerpo instanceof String s?s:json.writeValueAsString(cuerpo));
        var r=mvc.perform(b).andReturn();
        assertThat(r.getResponse().getStatus()).as("%s %s → %s",metodo,ruta,r.getResponse().getContentAsString()).isEqualTo(estado);
        String texto=r.getResponse().getContentAsString();
        return texto.isBlank()?null:json.readTree(texto);
    }
    private Map<String,Object> entrada(String nombre,String tipo,Object geometria,Double radio) {
        var m=new LinkedHashMap<String,Object>();
        m.put("rutaVersionId",version); m.put("nombre",nombre); m.put("tipo",tipo); m.put("geometria",geometria); m.put("radioMetros",radio); m.put("motivo","Registro sintético de prueba");
        return m;
    }
    private static Map<String,Object> punto(double lon,double lat) { return Map.of("type","Point","coordinates",List.of(lon,lat)); }
    private static Map<String,Object> poligono(double... xy) {
        var anillo=new ArrayList<List<Double>>(); for(int i=0;i<xy.length;i+=2) anillo.add(List.of(xy[i],xy[i+1]));
        return Map.of("type","Polygon","coordinates",List.of(anillo));
    }
    private static final Map<String,Object> CUADRADO=poligono(-63.11,-17.66,-63.09,-17.66,-63.09,-17.64,-63.11,-17.64,-63.11,-17.66);
    private int versiones() { return jdbc.queryForObject("SELECT count(*) FROM referencia_espacial_version WHERE ruta_version_id=?",Integer.class,version); }
    private JsonNode listadoDe(UUID referencia) throws Exception {
        var r=llamar(gestor,"GET","/api/referencias-espaciales?rutaVersionId="+version,null,200);
        var lista=new ArrayList<JsonNode>();
        for(JsonNode v:r.get("referencias")) if(v.get("referenciaId").asText().equals(referencia.toString())) lista.add(v);
        return json.valueToTree(lista);
    }

    @Test void registraYRecuperaPuntoYGeocercaEnEpsg4326AsociadosASuRutaVersion() throws Exception {
        var p=llamar(gestor,"POST","/api/referencias-espaciales",entrada("Punto de control sintético","PUNTO",punto(-63.1,-17.65),50.0),200);
        assertThat(p.get("numeroVersion").asInt()).isEqualTo(1);
        assertThat(p.get("rutaVersionId").asText()).isEqualTo(version.toString());
        var g=llamar(gestor,"POST","/api/referencias-espaciales",entrada("Geocerca sintética","GEOCERCA",CUADRADO,null),200);
        var multi=Map.of("type","MultiPolygon","coordinates",List.of(CUADRADO.get("coordinates"),
            poligono(-63.08,-17.66,-63.07,-17.66,-63.07,-17.65,-63.08,-17.66).get("coordinates")));
        llamar(gestor,"POST","/api/referencias-espaciales",entrada("Geocerca múltiple sintética","GEOCERCA",multi,null),200);
        var bd=jdbc.queryForMap("SELECT ST_SRID(geometria) AS srid,GeometryType(geometria) AS tipo,radio_metros,ST_NDims(geometria) AS dims FROM referencia_espacial_version WHERE id=?::uuid",p.get("id").asText());
        assertThat(bd).containsEntry("srid",4326).containsEntry("tipo","POINT").containsEntry("radio_metros",50.0);
        assertThat(((Number)bd.get("dims")).intValue()).as("2D").isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT GeometryType(geometria) FROM referencia_espacial_version WHERE id=?::uuid",String.class,g.get("id").asText())).isEqualTo("POLYGON");
        // CA-10-01/05: recuperación desde PostGIS con geometría y denominación, sin evaluación.
        var recuperado=listadoDe(UUID.fromString(p.get("referenciaId").asText())).get(0);
        assertThat(recuperado.get("geometria")).isEqualTo(json.valueToTree(punto(-63.1,-17.65)));
        assertThat(recuperado.get("nombre").asText()).isEqualTo("Punto de control sintético");
        assertThat(recuperado.has("cumplimiento")).isFalse();
        var geocerca=listadoDe(UUID.fromString(g.get("referenciaId").asText())).get(0);
        assertThat(geocerca.get("geometria")).isEqualTo(json.valueToTree(CUADRADO));
        assertThat(geocerca.get("radioMetros").isNull()).isTrue();
    }

    @Test void geometriasYContratosInvalidosSeRechazanSinPersistir() throws Exception {
        int antes=versiones();
        var invalidas=new LinkedHashMap<String,Map<String,Object>>();
        invalidas.put("polígono que se cruza",entrada("x","GEOCERCA",poligono(-63.11,-17.66,-63.09,-17.64,-63.09,-17.66,-63.11,-17.64,-63.11,-17.66),null));
        invalidas.put("anillo sin cerrar",entrada("x","GEOCERCA",poligono(-63.11,-17.66,-63.09,-17.66,-63.09,-17.64),null));
        invalidas.put("coordenada 3D",entrada("x","PUNTO",Map.of("type","Point","coordinates",List.of(-63.1,-17.65,400.0)),50.0));
        invalidas.put("longitud fuera de rango",entrada("x","PUNTO",punto(-200,-17.65),50.0));
        invalidas.put("latitud fuera de rango",entrada("x","PUNTO",punto(-63.1,-95),50.0));
        var conCrs=new LinkedHashMap<String,Object>(punto(-63.1,-17.65)); conCrs.put("crs",Map.of("type","name","properties",Map.of("name","EPSG:3857")));
        invalidas.put("CRS alternativo",entrada("x","PUNTO",conCrs,50.0));
        invalidas.put("tipo no admitido",entrada("x","GEOCERCA",Map.of("type","LineString","coordinates",List.of(List.of(-63.1,-17.6),List.of(-63.2,-17.7))),null));
        invalidas.put("punto sin radio",entrada("x","PUNTO",punto(-63.1,-17.65),null));
        invalidas.put("radio fuera de rango técnico",entrada("x","PUNTO",punto(-63.1,-17.65),10001.0));
        invalidas.put("geocerca con radio",entrada("x","GEOCERCA",CUADRADO,30.0));
        invalidas.put("tipo incoherente",entrada("x","PUNTO",CUADRADO,30.0));
        invalidas.put("coordenadas no numéricas",entrada("x","PUNTO",Map.of("type","Point","coordinates","texto"),50.0));
        invalidas.put("nombre vacío",entrada(" ","PUNTO",punto(-63.1,-17.65),50.0));
        var sinRuta=entrada("x","PUNTO",punto(-63.1,-17.65),50.0); sinRuta.put("rutaVersionId",UUID.randomUUID());
        invalidas.put("RutaVersion inexistente",sinRuta);
        for(var e:invalidas.entrySet()) {
            var r=mvc.perform(post("/api/referencias-espaciales").header("Authorization","Bearer "+token(gestor)).contentType("application/json")
                .content(json.writeValueAsString(e.getValue()))).andReturn();
            assertThat(r.getResponse().getStatus()).as("%s → %s",e.getKey(),r.getResponse().getContentAsString()).isEqualTo(400);
        }
        assertThat(versiones()).as("ninguna geometría inválida persistida").isEqualTo(antes);
    }

    @Test void versionarCerrarYConservarHistoriaSinSobrescribir() throws Exception {
        var v1=llamar(gestor,"POST","/api/referencias-espaciales",entrada("Control versionado","PUNTO",punto(-63.1,-17.65),50.0),200);
        UUID ref=UUID.fromString(v1.get("referenciaId").asText());
        var v2=llamar(gestor,"POST","/api/referencias-espaciales/"+ref+"/versiones",entrada("Control versionado","PUNTO",punto(-63.12,-17.65),80.0),200);
        assertThat(v2.get("numeroVersion").asInt()).isEqualTo(2);
        var historia=jdbc.queryForList("SELECT numero_version,vigente_desde,vigente_hasta,radio_metros,ST_AsGeoJSON(geometria) AS g FROM referencia_espacial_version WHERE referencia_id=? ORDER BY numero_version",ref);
        assertThat(historia).hasSize(2);
        assertThat(historia.get(0).get("vigente_hasta")).as("v1 cerrada en el inicio de v2").isEqualTo(historia.get(1).get("vigente_desde"));
        assertThat(historia.get(0).get("radio_metros")).as("v1 conserva su radio").isEqualTo(50.0);
        assertThat(json.readTree((String)historia.get(0).get("g"))).as("v1 conserva su geometría").isEqualTo(json.valueToTree(punto(-63.1,-17.65)));
        // Inmutabilidad en PostgreSQL: ni la API ni SQL directo sobrescriben o borran la versión anterior.
        assertThatThrownBy(() -> jdbc.update("UPDATE referencia_espacial_version SET radio_metros=10 WHERE referencia_id=? AND numero_version=1",ref)).isNotNull();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM referencia_espacial_version WHERE referencia_id=?",ref)).isNotNull();
        llamar(gestor,"POST","/api/referencias-espaciales/"+ref+"/cierre",Map.of("motivo","Cierre sintético"),204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM referencia_espacial_version WHERE referencia_id=? AND vigente_hasta IS NULL",Integer.class,ref)).isZero();
        llamar(gestor,"POST","/api/referencias-espaciales/"+ref+"/cierre",Map.of("motivo","Otra vez"),409);
        llamar(gestor,"POST","/api/referencias-espaciales/"+ref+"/versiones",entrada("Tras cierre","PUNTO",punto(-63.1,-17.65),50.0),409);
        llamar(gestor,"POST","/api/referencias-espaciales/"+UUID.randomUUID()+"/versiones",entrada("Inexistente","PUNTO",punto(-63.1,-17.65),50.0),409);
        llamar(gestor,"POST","/api/referencias-espaciales/"+UUID.randomUUID()+"/cierre",Map.of("motivo","Inexistente"),409);
        llamar(gestor,"POST","/api/referencias-espaciales/"+ref+"/cierre",Map.of("motivo"," "),400);
        assertThat(listadoDe(ref).size()).as("las versiones cerradas siguen consultables").isEqualTo(2);
    }

    @Test void cu18EvaluaCadaPosicionConLaVersionVigenteAlCapturarla() throws Exception {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),persona=UUID.randomUUID(),vp=UUID.randomUUID(),
             jornada=UUID.randomUUID(),asignacion=UUID.randomUUID(),conductor=usuario();
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-10");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2020-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO vehiculo(id,placa,activo,creado_en,actualizado_en) VALUES (?,?,true,now(),now())",vehiculo,("RE"+vehiculo).toUpperCase());
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2020-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"RE-"+persona,"Conductor sintético");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2020-01-01',NULL,true,now(),now())",vp,persona,empresa);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,now(),NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,'2020-01-01T00:00:00Z',NULL,NULL,now())",asignacion,jornada,vv,vp);
        var v1=llamar(gestor,"POST","/api/referencias-espaciales",entrada("Control histórico","PUNTO",punto(-63.1,-17.65),50.0),200);
        UUID ref=UUID.fromString(v1.get("referenciaId").asText());
        // Dos posiciones junto a v1, capturadas durante su vigencia.
        Instant captura=Instant.now();
        for(int i=0;i<2;i++) jdbc.update("INSERT INTO posicion_gps VALUES (?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(-63.1,-17.65),4326),5)",UUID.randomUUID(),UUID.randomUUID(),
            jornada,asignacion,conductor,Timestamp.from(captura.plusMillis(i)),Timestamp.from(captura.plusMillis(i)));
        while(!Instant.now().isAfter(captura.plusMillis(5))) Thread.onSpinWait();
        // v2 desplaza el control ~2 km: no debe reinterpretar las posiciones capturadas bajo v1.
        var v2=llamar(gestor,"POST","/api/referencias-espaciales/"+ref+"/versiones",entrada("Control histórico","PUNTO",punto(-63.12,-17.65),50.0),200);
        var r=llamar(revisor,"GET","/api/revision-gps/jornadas/"+jornada+"/coincidencias",null,200);
        Map<Integer,JsonNode> porVersion=new HashMap<>();
        for(JsonNode x:r.get("referencias")) porVersion.put(x.get("numeroVersion").asInt(),x);
        assertThat(porVersion.get(1).get("versionId").asText()).isEqualTo(v1.get("id").asText());
        assertThat(porVersion.get(1).get("muestras").asLong()).isEqualTo(2);
        assertThat(porVersion.get(1).get("observadas").asLong()).as("CA-10-04 / CA-10-07").isEqualTo(2);
        assertThat(porVersion.get(2).get("versionId").asText()).isEqualTo(v2.get("id").asText());
        assertThat(porVersion.get(2).get("muestras").asLong()).as("las posiciones anteriores a v2 no se reasignan").isZero();
    }

    @Test void autorizacionMunicipalExplicitaSinA4ConRevocacionEfectiva() throws Exception {
        var creada=llamar(gestor,"POST","/api/referencias-espaciales",entrada("Control permisos","PUNTO",punto(-63.1,-17.65),50.0),200);
        UUID ref=UUID.fromString(creada.get("referenciaId").asText());
        int antes=versiones();
        llamar(lector,"GET","/api/referencias-espaciales",null,200);
        llamar(lector,"GET","/api/referencias-espaciales/versiones-ruta",null,403);
        llamar(lector,"POST","/api/referencias-espaciales",entrada("Lector","PUNTO",punto(-63.1,-17.65),50.0),403);
        llamar(lector,"POST","/api/referencias-espaciales/"+ref+"/versiones",entrada("Lector","PUNTO",punto(-63.1,-17.65),50.0),403);
        llamar(lector,"POST","/api/referencias-espaciales/"+ref+"/cierre",Map.of("motivo","Lector"),403);
        for(UUID u:new UUID[]{sinPermiso,operador,revisor}) {
            llamar(u,"GET","/api/referencias-espaciales",null,403);
            llamar(u,"POST","/api/referencias-espaciales",entrada("Sin permiso","PUNTO",punto(-63.1,-17.65),50.0),403);
        }
        assertThat(mvc.perform(get("/api/referencias-espaciales")).andReturn().getResponse().getStatus()).isIn(401,403);
        // Revocación efectiva sobre el mismo token.
        String t=token(gestor);
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='REFERENCIA_ESPACIAL_GESTIONAR' AND revocado_en IS NULL",gestor);
        assertThat(mvc.perform(post("/api/referencias-espaciales/"+ref+"/cierre").header("Authorization","Bearer "+t).contentType("application/json")
            .content("{\"motivo\":\"tras revocar\"}")).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/referencias-espaciales").header("Authorization","Bearer "+t)).andReturn().getResponse().getStatus()).as("la consulta no revocada sigue").isEqualTo(200);
        assertThat(versiones()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM referencia_espacial_version WHERE referencia_id=? AND vigente_hasta IS NULL",Integer.class,ref)).isEqualTo(1);
    }

    @Test void unFalloAlGuardarLaNuevaVersionRevierteElCierreDeLaAnterior() throws Exception {
        var v1=llamar(gestor,"POST","/api/referencias-espaciales",entrada("Control reversión","GEOCERCA",CUADRADO,null),200);
        UUID ref=UUID.fromString(v1.get("referenciaId").asText());
        doAnswer(i -> { i.callRealMethod(); throw new DataAccessResourceFailureException("Fallo inyectado (prueba)."); }).when(repositorio).insertar(any(),any());
        var r=mvc.perform(post("/api/referencias-espaciales/"+ref+"/versiones").header("Authorization","Bearer "+token(gestor)).contentType("application/json")
            .content(json.writeValueAsString(entrada("Control reversión","GEOCERCA",CUADRADO,null)))).andReturn();
        assertThat(r.getResponse().getStatus()).as("fallo de persistencia, no «sin permiso»: %s",r.getResponse().getContentAsString()).isEqualTo(503);
        var historia=jdbc.queryForList("SELECT numero_version,vigente_hasta FROM referencia_espacial_version WHERE referencia_id=?",ref);
        assertThat(historia).as("v1 sigue abierta y no existe v2").hasSize(1);
        assertThat(historia.get(0).get("vigente_hasta")).isNull();
    }
}
