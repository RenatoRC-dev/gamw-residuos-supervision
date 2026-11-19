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
 * CU-19 (A3) con navegador real + REST real + PostgreSQL/PostGIS aislados y datos sintéticos.
 * Separación (metros, geography) respecto de la RutaVersion HISTÓRICA de la jornada y detenciones candidatas (≥3 muestras precisas, cercanas a un ancla,
 * sin puentear huecos ni intervalos cero, duración mínima). Umbrales provisionales explícitos; no se inventan criterios del GAMW. Sin veredictos.
 * Base compartida: solo se verifican los identificadores sembrados aquí. Incidencias asociadas: no implementadas en esta revisión (brecha registrada).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class DesviosDetencionesNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);
    private UUID periodo,vv,vp,conductor;
    private static final String UMBRALES="huecoSegundos=120&precisionMetros=50&desvioMetros=100&radioDetencionMetros=25&detencionSegundos=180";

    private UUID persona(String nombres) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"DE-"+id,nombres);
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
    private UUID[] rutaVersion() {
        UUID ruta=UUID.randomUUID(),v=UUID.randomUUID();
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"DE-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.1 -17.7,-63.1 -17.6)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",v,ruta);
        return new UUID[]{ruta,v};
    }
    private UUID jornada(UUID version) {
        UUID j=UUID.randomUUID();
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",j,periodo,version,Timestamp.from(Instant.now().plus(1,ChronoUnit.DAYS)));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",UUID.randomUUID(),j,vv,vp,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        return j;
    }
    private UUID asignacionDe(UUID jornada) { return jdbc.queryForObject("SELECT id FROM asignacion_jornada WHERE jornada_id=?",UUID.class,jornada); }
    private UUID posicion(UUID jornada,Instant capturado,Instant recibido,double lon,double lat,double precision) {
        UUID captura=UUID.randomUUID();
        jdbc.update("INSERT INTO posicion_gps VALUES (?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),?)",UUID.randomUUID(),captura,jornada,asignacionDe(jornada),conductor,Timestamp.from(capturado),Timestamp.from(recibido),lon,lat,precision);
        return captura;
    }
    private List<String> tipos(JsonNode v) { var l=new ArrayList<String>(); v.get("hallazgos").forEach(h -> l.add(h.get("tipo").asText())); Collections.sort(l); return l; }
    private List<JsonNode> de(JsonNode v,String tipo) { var l=new ArrayList<JsonNode>(); v.get("hallazgos").forEach(h -> { if(h.get("tipo").asText().equals(tipo)) l.add(h); }); return l; }
    private JsonNode desvios(String jornada,String query,String jwt) throws Exception {
        var r=get("/api/revision-gps/jornadas/"+jornada+"/desvios?"+query,jwt);
        assertThat(r.statusCode()).as(query+" "+r.body()).isEqualTo(200);
        return json.readTree(r.body());
    }

    @Test @Timeout(value=8,unit=TimeUnit.MINUTES)
    void municipalRevisaSeparacionesYDetencionesCandidatasSinDictaminar() throws Exception {
        UUID empresa=UUID.randomUUID(); periodo=UUID.randomUUID();
        String revisor="des-rev-"+sufijo,operador="des-op-"+sufijo,sinPermiso="des-sin-"+sufijo,otro="des-otro-"+sufijo;
        UUID uR=usuario(revisor),uO=usuario(operador); usuario(sinPermiso); UUID uX=usuario(otro); conductor=usuario("des-cond-"+sufijo);
        conceder(uR,"GPS_DESVIOS_REVISAR");
        conceder(uX,"GPS_COINCIDENCIAS_REVISAR");   // permisos de revisión independientes
        String revisorContexto="des-ctx-"+sufijo;    // H-08: revisor con el permiso existente de consulta correctiva
        UUID uC=usuario(revisorContexto);
        conceder(uC,"GPS_DESVIOS_REVISAR"); conceder(uC,"CORRECTIVA_CONSULTAR");
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética desvíos "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        for(String p:List.of("PLANIFICACION_CONSULTAR","JORNADA_PROGRAMAR","JORNADA_ASIGNAR","JORNADA_REPROGRAMAR"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),uO,periodo,p);
        UUID veh=UUID.randomUUID(); vv=UUID.randomUUID(); vp=UUID.randomUUID();
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",veh,"DES-"+sufijo.toUpperCase());
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,veh,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,persona("Conductor desvíos "+sufijo),empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);

        Instant b=Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(3,ChronoUnit.HOURS);
        UUID[] rv=rutaVersion();
        UUID jp=jornada(rv[1]);
        // Inserción desordenada a propósito: el orden debe salir de capturado_en.
        // Detención candidata: 4 muestras precisas, ~0,5 m entre sí, 200 s; S3 llega última (sincronización tardía).
        posicion(jp,b.plusSeconds(1200),b.plusSeconds(1205),-63.100005,-17.650005,5);
        posicion(jp,b.plusSeconds(1120),b.plusSeconds(5000),-63.1,-17.650005,5);
        posicion(jp,b.plusSeconds(1000),b.plusSeconds(1005),-63.1,-17.65,5);
        posicion(jp,b.plusSeconds(1060),b.plusSeconds(1065),-63.100005,-17.65,5);
        // Separaciones: sobre la ruta, ~212 m (precisión 5), ~138 m (precisión 50) y ~159 m (precisión 80).
        posicion(jp,b,b.plusSeconds(5),-63.1,-17.69,5);
        UUID capD2=posicion(jp,b.plusSeconds(30),b.plusSeconds(35),-63.098,-17.69,5);
        posicion(jp,b.plusSeconds(60),b.plusSeconds(65),-63.0987,-17.69,50);
        posicion(jp,b.plusSeconds(90),b.plusSeconds(95),-63.0985,-17.69,80);
        // Falsos candidatos rechazados:
        for(int t:new int[]{2000,2150,2300}) posicion(jp,b.plusSeconds(t),b.plusSeconds(t+5),-63.1,-17.64,5);                 // huecos > 120 s
        for(int t:new int[]{3000,3090,3090,3180}) posicion(jp,b.plusSeconds(t),b.plusSeconds(t+5),-63.1,-17.63,5);            // instante duplicado
        posicion(jp,b.plusSeconds(4000),b.plusSeconds(4005),-63.1,-17.62,5); posicion(jp,b.plusSeconds(4060),b.plusSeconds(4065),-63.1,-17.62,30);  // precisión baja
        posicion(jp,b.plusSeconds(4120),b.plusSeconds(4125),-63.1,-17.62,5); posicion(jp,b.plusSeconds(4200),b.plusSeconds(4205),-63.1,-17.62,5);
        posicion(jp,b.plusSeconds(5000),b.plusSeconds(5005),-63.1,-17.61,5); posicion(jp,b.plusSeconds(5060),b.plusSeconds(5065),-63.1,-17.6097,5); // dispersión > 25 m
        posicion(jp,b.plusSeconds(5120),b.plusSeconds(5125),-63.1,-17.61,5); posicion(jp,b.plusSeconds(5200),b.plusSeconds(5205),-63.1,-17.6097,5);
        for(int t:new int[]{6000,6080,6170}) posicion(jp,b.plusSeconds(t),b.plusSeconds(t+5),-63.1,-17.605,5);                 // 170 s: tiempo insuficiente
        // Cambio posterior de la ruta: la jornada conserva su versión histórica.
        jdbc.update("UPDATE ruta_version SET vigente_hasta='2026-06-01T00:00:00Z' WHERE id=?",rv[1]);
        UUID v2=UUID.randomUUID();
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,2,ST_GeomFromText('LINESTRING(-64.1 -18.7,-64.2 -18.8)',4326),'2026-06-01T00:00:00Z',NULL,NULL,now())",v2,rv[0]);
        UUID jsin=jornada(rutaVersion()[1]);
        for(int t:new int[]{0,60,120}) posicion(jsin,b.plusSeconds(t),b.plusSeconds(t+5),-63.1,-17.69+t*0.00001,5);           // en ruta, en movimiento
        UUID jcero=jornada(rutaVersion()[1]);
        UUID juna=jornada(rutaVersion()[1]);
        posicion(juna,b,b.plusSeconds(5),-63.1,-17.69,5);
        UUID j5=jornada(rutaVersion()[1]);
        jdbc.update("INSERT INTO posicion_gps SELECT gen_random_uuid(),gen_random_uuid(),?,?,?,(?::timestamptz)+i*interval '1 second',(?::timestamptz)+i*interval '1 second'+interval '1 second',ST_SetSRID(ST_MakePoint(-63.1,-17.65),4326),5 FROM generate_series(1,5001) i",
                j5,asignacionDe(j5),conductor,Timestamp.from(b),Timestamp.from(b));
        // Incidencia ya registrada por el conductor durante la jornada (contexto H-08; no se asocia a ninguna señal).
        String textoIncidencia="Desvío por calle bloqueada (sintético "+sufijo+")";
        Instant ocurrida=b.plusSeconds(1100);
        jdbc.update("INSERT INTO incidencia_operativa(id,solicitud_id,huella_solicitud,jornada_id,asignacion_jornada_id,usuario_id,ocurrida_en,descripcion,evidencia_declarada,lugar_descripcion) VALUES (?,?,'h',?,?,?,?,?,'','Calle sintética')",
                UUID.randomUUID(),UUID.randomUUID(),jp,asignacionDe(jp),conductor,Timestamp.from(ocurrida),textoIncidencia);
        var esperados=new ArrayList<>(List.of("DESVIO_CANDIDATO","DETENCION_CANDIDATA","SEPARACION_INDETERMINADA_POR_PRECISION","SEPARACION_INDETERMINADA_POR_PRECISION"));
        Collections.sort(esperados);
        int gpsAntes=contar("SELECT count(*) FROM posicion_gps");

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
            var payload=new LinkedHashMap<String,Object>(Map.of("clave",clave,"revisor",revisor,"operador",operador,"sinPermiso",sinPermiso,
                "j",Map.of("principal",jp,"sinCandidatos",jsin,"cero",jcero,"una",juna),"esperados",esperados,
                "detencion",Map.of("desde",b.plusSeconds(1000).toString(),"hasta",b.plusSeconds(1200).toString()),"versionHistorica",rv[1].toString()));
            payload.put("revisorContexto",revisorContexto); payload.put("incidencia",textoIncidencia); payload.put("incidenciaOcurrida",ocurrida.toString());
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-desvios-gps.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lector.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E desvíos GPS: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        assertThat(contar("SELECT count(*) FROM posicion_gps")).as("revisar no edita evidencia").isEqualTo(gpsAntes);

        // ---------- REST real ----------
        String tr=token(revisor);
        JsonNode v=desvios(jp.toString(),UMBRALES,tr);
        assertThat(tipos(v)).isEqualTo(esperados);
        assertThat(v.get("estado").asText()).isEqualTo("REVISION_TECNICA");
        assertThat(v.get("rutaVersionId").asText()).as("RutaVersion histórica, no la vigente").isEqualTo(rv[1].toString());
        assertThat(v.get("rutaReferencia").get("coordinates")).hasSize(2);
        assertThat(v.get("rutaReferencia").get("coordinates").get(0).get(0).asDouble()).isEqualTo(-63.1);
        double d2=jdbc.queryForObject("SELECT ST_Distance(p.geometria::geography,rv.geometria::geography) FROM posicion_gps p JOIN ruta_version rv ON rv.id=? WHERE p.captura_id=?",Double.class,rv[1],capD2);
        var desvio=de(v,"DESVIO_CANDIDATO").getFirst();
        assertThat(desvio.get("valor").asDouble()).as("distancia en metros a la RutaVersion histórica").isEqualTo(d2);
        assertThat(d2).isBetween(200.0,225.0);
        assertThat(desvio.get("unidad").asText()).isEqualTo("m");
        var det=de(v,"DETENCION_CANDIDATA").getFirst();
        assertThat(Instant.parse(det.get("desde").asText())).isEqualTo(b.plusSeconds(1000));
        assertThat(Instant.parse(det.get("hasta").asText())).isEqualTo(b.plusSeconds(1200));
        assertThat(det.get("valor").asDouble()).isEqualTo(200.0);
        assertThat(det.get("capturas")).as("cuatro muestras, en orden de captura").hasSize(4);
        for(var h:v.get("hallazgos")) {
            assertThat(h.get("estado").asText()).isEqualTo("POR_REVISAR");
            assertThat(h.get("detalle").asText()).containsAnyOf("no prueba camión detenido","no tramo recorrido ni incumplimiento");
        }
        List<Instant> orden=new ArrayList<>(); v.get("muestras").forEach(m -> orden.add(Instant.parse(m.get("capturadoEn").asText())));
        assertThat(orden).as("orden por captura").isSorted();
        // Límites exactos e incertidumbre por precisión (D2 a distancia d2, precisión 5 m).
        assertThat(de(desvios(jp.toString(),UMBRALES.replace("desvioMetros=100","desvioMetros="+d2),tr),"DESVIO_CANDIDATO")).as("distancia = umbral: no es señal").isEmpty();
        var justo=desvios(jp.toString(),UMBRALES.replace("desvioMetros=100","desvioMetros="+(d2-0.001)),tr);
        assertThat(de(justo,"DESVIO_CANDIDATO")).as("dentro de la precisión reportada: incierta").isEmpty();
        assertThat(de(justo,"SEPARACION_INDETERMINADA_POR_PRECISION").stream().anyMatch(h -> h.get("valor").asDouble()==d2)).isTrue();
        assertThat(de(desvios(jp.toString(),UMBRALES.replace("desvioMetros=100","desvioMetros="+(d2-10)),tr),"DESVIO_CANDIDATO")).hasSize(1);
        assertThat(de(desvios(jp.toString(),UMBRALES.replace("detencionSegundos=180","detencionSegundos=200"),tr),"DETENCION_CANDIDATA")).as("duración = umbral: inclusivo").hasSize(1);
        assertThat(de(desvios(jp.toString(),UMBRALES.replace("detencionSegundos=180","detencionSegundos=201"),tr),"DETENCION_CANDIDATA")).isEmpty();
        assertThat(de(desvios(jp.toString(),UMBRALES.replace("huecoSegundos=120","huecoSegundos=150"),tr),"DETENCION_CANDIDATA")).as("al ampliar el hueco, el caso de huecos de 150 s sí agrupa").hasSize(2);
        // Captura tardía: tres muestras de 170 s pasan a cuatro de 250 s → nueva candidata; la evidencia original no cambia.
        var originales=jdbc.queryForList("SELECT * FROM posicion_gps WHERE jornada_id=? ORDER BY captura_id",jp);
        UUID tardia=posicion(jp,b.plusSeconds(6250),Instant.now().truncatedTo(ChronoUnit.MICROS),-63.1,-17.605,5);
        var recalculado=desvios(jp.toString(),UMBRALES,tr);
        assertThat(de(recalculado,"DETENCION_CANDIDATA")).hasSize(2);
        assertThat(jdbc.queryForList("SELECT * FROM posicion_gps WHERE jornada_id=? AND captura_id<>? ORDER BY captura_id",jp,tardia)).as("evidencia original intacta").isEqualTo(originales);
        // Cero candidatos, insuficiencia, truncamiento.
        var sin=desvios(jsin.toString(),UMBRALES,tr);
        assertThat(sin.get("hallazgos")).isEmpty(); assertThat(sin.get("estado").asText()).isEqualTo("REVISION_TECNICA");
        assertThat(desvios(jcero.toString(),UMBRALES,tr).get("estado").asText()).as("cero muestras: insuficiente (regresión)").isEqualTo("EVIDENCIA_INSUFICIENTE");
        assertThat(desvios(juna.toString(),UMBRALES,tr).get("estado").asText()).as("una muestra: insuficiente (regresión)").isEqualTo("EVIDENCIA_INSUFICIENTE");
        var parcial=desvios(j5.toString(),UMBRALES,tr);
        assertThat(parcial.get("muestrasDisponibles").asLong()).isEqualTo(5001);
        assertThat(parcial.get("muestrasAnalizadas").asInt()).isEqualTo(5000);
        assertThat(parcial.get("parcial").asBoolean()).isTrue();
        assertThat(parcial.get("estado").asText()).isEqualTo("REVISION_PARCIAL");
        assertThat(Instant.parse(de(parcial,"DETENCION_CANDIDATA").getFirst().get("hasta").asText())).as("solo dentro de las 5000 analizadas").isEqualTo(b.plusSeconds(5000));
        // Parámetros, inexistente, acceso, revocación, inmutabilidad.
        for(String q:List.of("desvioMetros=0","radioDetencionMetros=-1","detencionSegundos=NaN","huecoSegundos=1000001","precisionMetros=abc"))
            assertThat(get("/api/revision-gps/jornadas/"+jp+"/desvios?"+q,tr).statusCode()).as(q).isEqualTo(400);
        assertThat(get("/api/revision-gps/jornadas/"+UUID.randomUUID()+"/desvios",tr).statusCode()).isEqualTo(404);
        assertThat(get("/api/revision-gps/jornadas/"+jp+"/desvios",null).statusCode()).isIn(401,403);
        for(String u:List.of(operador,sinPermiso,otro)) {
            var x=get("/api/revision-gps/jornadas/"+jp+"/desvios",token(u));
            assertThat(x.statusCode()).as(u).isEqualTo(403);
            assertThat(x.body()).doesNotContain(jp.toString());
        }
        assertThat(contar("SELECT count(*) FROM concesion_permiso WHERE usuario_id=? AND permiso LIKE 'GPS_%'",uO)).as("A4 sin permisos GPS").isZero();
        assertThat(catchThrowable(() -> jdbc.update("UPDATE ruta_version SET geometria=ST_GeomFromText('LINESTRING(-60 -10,-61 -11)',4326) WHERE id=?",rv[1]))).as("RutaVersion histórica inmutable").isNotNull();
        assertThat(catchThrowable(() -> jdbc.update("UPDATE posicion_gps SET precision_metros=1 WHERE jornada_id=?",jp))).as("evidencia inmutable").isNotNull();
        assertThat(jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba',motivo_revocacion='fixture' WHERE usuario_id=? AND permiso='GPS_DESVIOS_REVISAR' AND revocado_en IS NULL",uR)).isEqualTo(1);
        assertThat(get("/api/revision-gps/jornadas/"+jp+"/desvios",tr).statusCode()).as("permiso revocado").isEqualTo(403);
    }
}
