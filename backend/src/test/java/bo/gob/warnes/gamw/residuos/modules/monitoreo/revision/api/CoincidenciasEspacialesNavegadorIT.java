package bo.gob.warnes.gamw.residuos.modules.monitoreo.revision.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * CU-18 (A3) con navegador real + REST real + PostgreSQL/PostGIS aislados y datos sintéticos.
 * Controles puntuales con radio EXPLÍCITO (ST_DWithin, geography, metros) y geocercas (ST_Covers), vigencia por capturado_en, RutaVersion de la jornada.
 * Radios y tolerancias de la prueba son provisionales: no se inventa política del GAMW. Una coincidencia no es recolección, presencia ni cumplimiento.
 * La base de la JVM es compartida: solo se verifican los identificadores sembrados aquí. CU-19 fuera de este corte.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class CoincidenciasEspacialesNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);
    private UUID uRevisor;
    private UUID empresa,periodo,veh,vv,vp,conductor;

    private UUID persona(String nombres) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"CO-"+id,nombres);
        return id;
    }
    private UUID usuario(String nombre) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona("Cuenta "+nombre),nombre,codificador.encode(clave));
        return id;
    }
    private void conceder(UUID usuario,String permiso) {
        jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),usuario,permiso);
    }
    private HttpResponse<String> get(String ruta,String jwt) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(30)).GET();
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String token(String nombre) throws Exception {
        var r=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+"/api/autenticacion/login")).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("nombreUsuario",nombre,"contrasena",clave)))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        return json.readTree(r.body()).get("tokenAcceso").asText();
    }
    private int contar(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }
    private UUID version() {
        UUID ruta=UUID.randomUUID(),v=UUID.randomUUID();
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"CO-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.1 -17.7,-63.1 -17.6)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",v,ruta);
        return v;
    }
    private UUID jornada(UUID version) {
        UUID j=UUID.randomUUID();
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",j,periodo,version,Timestamp.from(Instant.now().plus(1,ChronoUnit.DAYS)));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",UUID.randomUUID(),j,vv,vp,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        return j;
    }
    private UUID asignacionDe(UUID jornada) { return jdbc.queryForObject("SELECT id FROM asignacion_jornada WHERE jornada_id=?",UUID.class,jornada); }
    private void posicion(UUID jornada,Instant capturado,Instant recibido,double lon,double lat,double precision) {
        jdbc.update("INSERT INTO posicion_gps VALUES (?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),?)",UUID.randomUUID(),UUID.randomUUID(),jornada,asignacionDe(jornada),conductor,Timestamp.from(capturado),Timestamp.from(recibido),lon,lat,precision);
    }
    /** Inserta una versión de control. wkt en EPSG:4326; radio nulo para geocercas; hasta nulo = abierta. Devuelve el id de la versión. */
    private UUID control(UUID rutaVersion,UUID referencia,int numero,String nombre,String tipo,String wkt,Double radio,Instant desde,Instant hasta) {
        UUID id=UUID.randomUUID();
        if(contar("SELECT count(*) FROM referencia_espacial WHERE id=?",referencia)==0) jdbc.update("INSERT INTO referencia_espacial VALUES (?)",referencia);
        jdbc.update("INSERT INTO referencia_espacial_version(id,referencia_id,numero_version,ruta_version_id,nombre,tipo,geometria,radio_metros,vigente_desde,vigente_hasta,motivo,creado_por,cerrado_por,motivo_cierre) VALUES (?,?,?,?,?,?,ST_GeomFromText(?,4326),?,?,?,'fixture sintético',?,?,?)",
                id,referencia,numero,rutaVersion,nombre,tipo,wkt,radio,Timestamp.from(desde),hasta==null?null:Timestamp.from(hasta),uRevisor,hasta==null?null:uRevisor,hasta==null?null:"cierre sintético");
        return id;
    }
    private double distancia(double lon1,double lat1,double lon2,double lat2) {
        return jdbc.queryForObject("SELECT ST_Distance(ST_SetSRID(ST_MakePoint(?,?),4326)::geography,ST_SetSRID(ST_MakePoint(?,?),4326)::geography)",Double.class,lon1,lat1,lon2,lat2);
    }
    private static String punto(double lon,double lat) { return "POINT("+lon+" "+lat+")"; }
    private JsonNode coincidencias(String jornada,String query,String jwt) throws Exception {
        var r=get("/api/revision-gps/jornadas/"+jornada+"/coincidencias?"+query,jwt);
        assertThat(r.statusCode()).as(query+" "+r.body()).isEqualTo(200);
        return json.readTree(r.body());
    }
    private Map<String,JsonNode> porNombre(JsonNode v) {
        var m=new LinkedHashMap<String,JsonNode>();
        v.get("referencias").forEach(r -> m.put(r.get("nombre").asText()+"@v"+r.get("numeroVersion").asInt(),r));
        return m;
    }

    @Test @Timeout(value=8,unit=TimeUnit.MINUTES)
    void municipalRevisaCoincidenciasEspacialesConRadiosExplicitosYVigenciaPorCaptura() throws Exception {
        UUID rutaVersionJc=version();
        empresa=UUID.randomUUID(); periodo=UUID.randomUUID();
        String revisor="coi-rev-"+sufijo,operador="coi-op-"+sufijo,sinPermiso="coi-sin-"+sufijo,otro="coi-otro-"+sufijo;
        uRevisor=usuario(revisor); UUID uO=usuario(operador); usuario(sinPermiso); UUID uX=usuario(otro); conductor=usuario("coi-cond-"+sufijo);
        conceder(uRevisor,"GPS_COINCIDENCIAS_REVISAR");
        conceder(uX,"GPS_INTEGRIDAD_REVISAR");   // permisos de revisión independientes
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética coincidencias "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        for(String p:List.of("PLANIFICACION_CONSULTAR","JORNADA_PROGRAMAR","JORNADA_ASIGNAR","JORNADA_REPROGRAMAR"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),uO,periodo,p);
        veh=UUID.randomUUID(); vv=UUID.randomUUID(); vp=UUID.randomUUID();
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",veh,"COI-"+sufijo.toUpperCase());
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,veh,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,persona("Conductor coincidencias "+sufijo),empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);

        Instant b=Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(2,ChronoUnit.HOURS),antes=b.minus(1,ChronoUnit.HOURS);
        UUID jc=jornada(rutaVersionJc);
        posicion(jc,b.plusSeconds(10),b.plusSeconds(500),-63.1,-17.7,5);          // S1: sincronización tardía (recibida mucho después)
        posicion(jc,b.plusSeconds(20),b.plusSeconds(25),-63.0995,-17.7,5);        // S2: borde de la geocerca, 53 m del centro
        posicion(jc,b.plusSeconds(30),b.plusSeconds(35),-63.0989,-17.7,5);        // S3: ~116 m del centro, fuera de la geocerca
        posicion(jc,b.plusSeconds(40),b.plusSeconds(45),-63.1,-17.709,60);        // S4: precisión baja (>50 m)
        posicion(jc,b.plusSeconds(50),b.plusSeconds(55),-63.2,-17.7,5);           // S5: aislada
        double distE=distancia(-63.2,-17.7,-63.2,-17.7006);
        String n=sufijo;
        var esperadas=new ArrayList<Map<String,Object>>();
        record Ctl(String nombre,int version,String estado,int obs,int mue) { }
        var lista=new ArrayList<Ctl>();
        lista.add(new Ctl("CP-A-"+n,1,"COINCIDENCIA_OBSERVADA",2,5));
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-A-"+n,"PUNTO",punto(-63.1,-17.7),100.0,antes,null);
        lista.add(new Ctl("CP-B-"+n,1,"SIN_COINCIDENCIA_OBSERVADA",0,5));
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-B-"+n,"PUNTO",punto(-63.1,-17.69),50.0,antes,null);
        lista.add(new Ctl("CP-C-"+n,1,"EVIDENCIA_INSUFICIENTE",0,5));      // proximidad incierta: 100 m con radio 50 y precisión 60
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-C-"+n,"PUNTO",punto(-63.1,-17.7099),50.0,antes,null);
        lista.add(new Ctl("CP-D-"+n,1,"COINCIDENCIA_OBSERVADA",2,5));      // geocerca: interior y borde (ST_Covers)
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-D-"+n,"GEOCERCA","POLYGON((-63.1005 -17.7005,-63.0995 -17.7005,-63.0995 -17.6995,-63.1005 -17.6995,-63.1005 -17.7005))",null,antes,null);
        lista.add(new Ctl("CP-E-"+n,1,"COINCIDENCIA_OBSERVADA",1,5));      // radio = distancia exacta: límite inclusivo
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-E-"+n,"PUNTO",punto(-63.2,-17.7006),distE,antes,null);
        lista.add(new Ctl("CP-F-"+n,1,"EVIDENCIA_INSUFICIENTE",0,5));      // 0,01 m por fuera: incierta por precisión, no «sin coincidencia»
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-F-"+n,"PUNTO",punto(-63.2,-17.7006),distE-0.01,antes,null);
        lista.add(new Ctl("CP-G-"+n,1,"SIN_COINCIDENCIA_OBSERVADA",0,5));  // 10 m por fuera con precisión 5: sin coincidencia
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-G-"+n,"PUNTO",punto(-63.2,-17.7006),distE-10,antes,null);
        lista.add(new Ctl("CP-H-"+n,1,"SIN_COINCIDENCIA_OBSERVADA",0,3));  // vigente desde b+25: solo S3, S4, S5 (por captura, no por recepción)
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-H-"+n,"PUNTO",punto(-63.1,-17.7),100.0,b.plusSeconds(25),null);
        lista.add(new Ctl("CP-I-"+n,1,"EVIDENCIA_INSUFICIENTE",0,0));      // cerrada antes de toda muestra
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-I-"+n,"PUNTO",punto(-63.1,-17.7),100.0,antes,b.plusSeconds(5));
        lista.add(new Ctl("CP-J-"+n,1,"EVIDENCIA_INSUFICIENTE",0,0));      // vigente_hasta = captura de S1: exclusivo
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-J-"+n,"PUNTO",punto(-63.1,-17.7),100.0,antes,b.plusSeconds(10));
        lista.add(new Ctl("CP-K-"+n,1,"COINCIDENCIA_OBSERVADA",2,5));      // vigente_desde = captura de S1: inclusivo
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-K-"+n,"PUNTO",punto(-63.1,-17.7),100.0,b.plusSeconds(10),null);
        UUID refL=UUID.randomUUID();                                         // versiones históricas de una misma referencia
        lista.add(new Ctl("CP-L-"+n,1,"COINCIDENCIA_OBSERVADA",1,1));
        control(rutaVersionJc,refL,1,"CP-L-"+n,"PUNTO",punto(-63.1,-17.7),100.0,antes,b.plusSeconds(15));
        lista.add(new Ctl("CP-L-"+n,2,"SIN_COINCIDENCIA_OBSERVADA",0,4));
        control(rutaVersionJc,refL,2,"CP-L-"+n,"PUNTO",punto(-63.5,-17.7),100.0,b.plusSeconds(15),null);
        // Controles que NO deben afectar: otra RutaVersion y vigencia futura.
        UUID otraVersion=version();
        control(otraVersion,UUID.randomUUID(),1,"CX-OTRA-RUTA-"+n,"PUNTO",punto(-63.1,-17.7),10000.0,antes,null);
        control(rutaVersionJc,UUID.randomUUID(),1,"CP-FUTURA-"+n,"PUNTO",punto(-63.1,-17.7),100.0,Instant.now().plus(1,ChronoUnit.DAYS),null);
        for(var c:lista) esperadas.add(Map.of("nombre",c.nombre(),"version",c.version(),"estado",c.estado(),"observadas",c.obs(),"muestras",c.mue()));
        UUID jcero=jornada(rutaVersionJc);                                   // mismas referencias, cero muestras
        UUID jotra=jornada(otraVersion);
        posicion(jotra,b.plusSeconds(10),b.plusSeconds(12),-63.1,-17.7,5);
        UUID jsinRef=jornada(version());                                     // sin referencias aplicables
        posicion(jsinRef,b.plusSeconds(10),b.plusSeconds(12),-63.1,-17.7,5); posicion(jsinRef,b.plusSeconds(20),b.plusSeconds(22),-63.1,-17.7,5);
        UUID versionLim=version(),jlim=jornada(versionLim);                  // 101 controles
        posicion(jlim,b.plusSeconds(10),b.plusSeconds(12),-63.1,-17.7,5); posicion(jlim,b.plusSeconds(20),b.plusSeconds(22),-63.1,-17.7,5);
        for(int i=0;i<101;i++) control(versionLim,UUID.randomUUID(),1,"CL-"+i+"-"+n,"PUNTO",punto(-63.1+i*0.0001,-17.7),10.0,antes,null);
        UUID versionP=version(),jp=jornada(versionP);                        // 5001 muestras
        control(versionP,UUID.randomUUID(),1,"CP-5000-"+n,"PUNTO",punto(-63.1,-17.7),100.0,antes,null);
        jdbc.update("INSERT INTO posicion_gps SELECT gen_random_uuid(),gen_random_uuid(),?,?,?,(?::timestamptz)+i*interval '1 second',(?::timestamptz)+i*interval '1 second'+interval '1 second',ST_SetSRID(ST_MakePoint(-63.1,-17.7),4326),5 FROM generate_series(1,5001) i",
                jp,asignacionDe(jp),conductor,Timestamp.from(b),Timestamp.from(b));
        int gpsAntes=contar("SELECT count(*) FROM posicion_gps"),refsAntes=contar("SELECT count(*) FROM referencia_espacial_version");

        // ---------- Navegador ----------
        int web;
        try(var disponible=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { web=disponible.getLocalPort(); }
        Path directorio=Path.of("..","apps","web").toAbsolutePath().normalize();
        var vite=new ProcessBuilder("node","node_modules/vite/bin/vite.js","--host","127.0.0.1","--port",Integer.toString(web),"--strictPort")
                .directory(directorio.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        vite.environment().put("GAMW_WEB_INTEGRACION","true");
        vite.environment().put("VITE_API_BASE_URL","/api");
        vite.environment().put("VITE_MAP_STYLE_URL","data:application/json,%7B%22version%22%3A8%2C%22sources%22%3A%7B%7D%2C%22layers%22%3A%5B%5D%7D");
        vite.environment().put("BACKEND_PROXY_TARGET","http://127.0.0.1:"+puerto);
        Process servidor=vite.start();
        try {
            long limite=System.nanoTime()+Duration.ofSeconds(25).toNanos(); boolean listo=false;
            while(System.nanoTime()<limite && servidor.isAlive()) {
                try {
                    var h=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+web+"/acceso")).header("Accept","text/html").timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding());
                    if(h.statusCode()==200) { listo=true; break; }
                } catch(java.io.IOException error) { /* arranque del proceso propio */ }
                Thread.sleep(100);
            }
            assertThat(listo).as("Vite nuevo en puerto aislado").isTrue();
            var payload=new LinkedHashMap<String,Object>();
            payload.put("clave",clave); payload.put("revisor",revisor); payload.put("operador",operador); payload.put("sinPermiso",sinPermiso);
            payload.put("j",Map.of("principal",jc,"cero",jcero,"sinReferencias",jsinRef,"limite",jlim));
            payload.put("esperadas",esperadas); payload.put("excluidas",List.of("CX-OTRA-RUTA-"+n,"CP-FUTURA-"+n));
            payload.put("nombreA","CP-A-"+n); payload.put("nombreD","CP-D-"+n);
            payload.put("primeraA",b.plusSeconds(10).toString()); payload.put("ultimaA",b.plusSeconds(20).toString());
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-coincidencias-gps.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lector.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E coincidencias GPS: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        assertThat(contar("SELECT count(*) FROM posicion_gps")).as("revisar no edita evidencia").isEqualTo(gpsAntes);
        assertThat(contar("SELECT count(*) FROM referencia_espacial_version")).isEqualTo(refsAntes);

        // ---------- REST real ----------
        String tr=token(revisor);
        JsonNode v=coincidencias(jc.toString(),"precisionMetros=50",tr);
        var mapa=porNombre(v);
        assertThat(mapa).as("solo controles de la RutaVersion con vigencia alcanzada").hasSize(esperadas.size());
        for(var c:lista) {
            var r=mapa.get(c.nombre()+"@v"+c.version());
            assertThat(r).as(c.nombre()+" v"+c.version()).isNotNull();
            assertThat(r.get("estado").asText()).as(c.nombre()+" v"+c.version()).isEqualTo(c.estado());
            assertThat(r.get("observadas").asLong()).as(c.nombre()+" observadas").isEqualTo(c.obs());
            assertThat(r.get("muestras").asLong()).as(c.nombre()+" muestras").isEqualTo(c.mue());
            assertThat(r.get("limitacion").asText()).contains("precisión y huecos limitan interpretación");
        }
        assertThat(mapa.keySet().stream().noneMatch(k -> k.startsWith("CX-") || k.startsWith("CP-FUTURA"))).isTrue();
        assertThat(v.get("estado").asText()).isEqualTo("REVISION_TECNICA");
        assertThat(v.get("politica").asText()).contains("no certifica integridad, presencia del camión ni cumplimiento");
        assertThat(Instant.parse(mapa.get("CP-A-"+n+"@v1").get("primera").asText())).isEqualTo(b.plusSeconds(10));
        assertThat(Instant.parse(mapa.get("CP-A-"+n+"@v1").get("ultima").asText())).isEqualTo(b.plusSeconds(20));
        assertThat(mapa.get("CP-A-"+n+"@v1").get("distanciaMinimaMetros").asDouble()).isEqualTo(0.0);
        assertThat(mapa.get("CP-E-"+n+"@v1").get("distanciaMinimaMetros").asDouble()).as("distancia geográfica en metros").isEqualTo(distE);
        assertThat(mapa.get("CP-A-"+n+"@v1").get("geometria").get("type").asText()).isEqualTo("Point");
        assertThat(mapa.get("CP-D-"+n+"@v1").get("geometria").get("type").asText()).isEqualTo("Polygon");
        assertThat(mapa.get("CP-D-"+n+"@v1").get("limitacion").asText()).contains("Geocerca poligonal sin radio.");
        assertThat(mapa.get("CP-A-"+n+"@v1").get("limitacion").asText()).contains("Radio explícito: 100.0 m.");
        assertThat(mapa.get("CP-A-"+n+"@v1").get("versionId").asText()).as("identificador de versión trazable").isEqualTo(
                jdbc.queryForObject("SELECT id FROM referencia_espacial_version WHERE nombre=?",UUID.class,"CP-A-"+n).toString());
        // Trazabilidad de captura: marcas originales y precisión.
        var m0=v.get("muestras").get(0);
        assertThat(Instant.parse(m0.get("capturadoEn").asText())).isEqualTo(b.plusSeconds(10));
        assertThat(Instant.parse(m0.get("recibidoEn").asText())).as("la recepción tardía conserva su hora").isEqualTo(b.plusSeconds(500));
        assertThat(m0.get("precisionMetros").asDouble()).isEqualTo(5.0);
        assertThat(v.get("muestras").get(3).get("precisionMetros").asDouble()).isEqualTo(60.0);
        // Tolerancia explícita: con 3 m ninguna muestra es «precisa»; lo observado sigue observado, lo no observado pasa a insuficiente.
        var estricta=porNombre(coincidencias(jc.toString(),"precisionMetros=3",tr));
        assertThat(estricta.get("CP-B-"+n+"@v1").get("estado").asText()).isEqualTo("EVIDENCIA_INSUFICIENTE");
        assertThat(estricta.get("CP-A-"+n+"@v1").get("estado").asText()).isEqualTo("COINCIDENCIA_OBSERVADA");
        // Referencias y muestras de otra versión de ruta.
        var otra=porNombre(coincidencias(jotra.toString(),"",tr));
        assertThat(otra.keySet()).containsExactly("CX-OTRA-RUTA-"+n+"@v1");
        assertThat(otra.get("CX-OTRA-RUTA-"+n+"@v1").get("observadas").asLong()).isEqualTo(1);
        // Cero muestras, cero referencias, límites de cómputo.
        var cero=coincidencias(jcero.toString(),"",tr);
        assertThat(cero.get("estado").asText()).as("hay referencias; faltan muestras aplicables").isEqualTo("EVIDENCIA_INSUFICIENTE_SIN_MUESTRAS_APLICABLES");
        assertThat(cero.get("referencias")).hasSize(esperadas.size());
        var sinRef=coincidencias(jsinRef.toString(),"",tr);
        assertThat(sinRef.get("referencias")).isEmpty();
        assertThat(sinRef.get("estado").asText()).as("hay muestras; no hay referencias").isEqualTo("EVIDENCIA_INSUFICIENTE_SIN_REFERENCIAS_APLICABLES");
        // Referencias presentes y muestras presentes pero fuera de toda vigencia: también «sin muestras aplicables».
        UUID versionFuera=version(),jfuera=jornada(versionFuera);
        control(versionFuera,UUID.randomUUID(),1,"CP-FUERA-"+n,"PUNTO",punto(-63.1,-17.7),100.0,b.plusSeconds(1000),null);
        posicion(jfuera,b.plusSeconds(10),b.plusSeconds(12),-63.1,-17.7,5); posicion(jfuera,b.plusSeconds(20),b.plusSeconds(22),-63.1,-17.7,5);
        assertThat(coincidencias(jfuera.toString(),"",tr).get("estado").asText()).isEqualTo("EVIDENCIA_INSUFICIENTE_SIN_MUESTRAS_APLICABLES");
        var parcial=coincidencias(jlim.toString(),"",tr);
        assertThat(parcial.get("referencias")).hasSize(100); assertThat(parcial.get("parcial").asBoolean()).isTrue();
        assertThat(parcial.get("estado").asText()).isEqualTo("REVISION_PARCIAL");
        var cinco=coincidencias(jp.toString(),"",tr);
        assertThat(cinco.get("muestrasDisponibles").asLong()).isEqualTo(5001); assertThat(cinco.get("muestrasAnalizadas").asInt()).isEqualTo(5000);
        assertThat(cinco.get("parcial").asBoolean()).isTrue();
        assertThat(cinco.get("referencias").get(0).get("muestras").asLong()).as("cómputo acotado a 5000").isEqualTo(5000);
        // Captura tardía: recalcula sin tocar la evidencia original.
        var originales=jdbc.queryForList("SELECT * FROM posicion_gps WHERE jornada_id=? ORDER BY captura_id",jc);
        posicion(jc,b.plusSeconds(12),Instant.now().truncatedTo(ChronoUnit.MICROS),-63.1,-17.7,5);
        var recalculado=porNombre(coincidencias(jc.toString(),"precisionMetros=50",tr));
        assertThat(recalculado.get("CP-A-"+n+"@v1").get("muestras").asLong()).isEqualTo(6);
        assertThat(recalculado.get("CP-A-"+n+"@v1").get("observadas").asLong()).isEqualTo(3);
        assertThat(recalculado.get("CP-H-"+n+"@v1").get("muestras").asLong()).as("ventana desde b+25 no cambia").isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT * FROM posicion_gps WHERE jornada_id=? AND capturado_en<>?::timestamptz ORDER BY captura_id",jc,Timestamp.from(b.plusSeconds(12)))).as("evidencia original intacta").isEqualTo(originales);
        // Parámetros, tipo, inexistente, acceso, revocación, inmutabilidad.
        for(String q:List.of("precisionMetros=0","precisionMetros=-1","precisionMetros=NaN","precisionMetros=1000001","precisionMetros=abc"))
            assertThat(get("/api/revision-gps/jornadas/"+jc+"/coincidencias?"+q,tr).statusCode()).as(q).isEqualTo(400);
        assertThat(get("/api/revision-gps/jornadas/"+UUID.randomUUID()+"/coincidencias",tr).statusCode()).as("jornada inexistente").isEqualTo(404);
        assertThat(get("/api/revision-gps/jornadas/"+jc+"/coincidencias",null).statusCode()).isIn(401,403);
        for(String u:List.of(operador,sinPermiso,otro)) {
            var x=get("/api/revision-gps/jornadas/"+jc+"/coincidencias",token(u));
            assertThat(x.statusCode()).as(u).isEqualTo(403);
            assertThat(x.body()).doesNotContain(jc.toString());
        }
        assertThat(contar("SELECT count(*) FROM concesion_permiso WHERE usuario_id=? AND permiso LIKE 'GPS_%'",uO)).as("A4 sin permisos GPS").isZero();
        assertThat(catchThrowable(() -> jdbc.update("UPDATE referencia_espacial_version SET nombre='alterado' WHERE nombre=?","CP-A-"+n))).as("referencia histórica inmutable").isNotNull();
        assertThat(catchThrowable(() -> jdbc.update("UPDATE posicion_gps SET precision_metros=1 WHERE jornada_id=?",jc))).as("evidencia inmutable").isNotNull();
        assertThat(jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba',motivo_revocacion='fixture' WHERE usuario_id=? AND permiso='GPS_COINCIDENCIAS_REVISAR' AND revocado_en IS NULL",uRevisor)).isEqualTo(1);
        assertThat(get("/api/revision-gps/jornadas/"+jc+"/coincidencias",tr).statusCode()).as("permiso revocado").isEqualTo(403);
    }
}
