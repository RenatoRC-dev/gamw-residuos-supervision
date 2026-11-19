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
 * CU-17 (A3) con navegador real + REST real + PostgreSQL/PostGIS aislados y datos sintéticos.
 * Umbrales PROVISIONALES explícitos en cada prueba (no se inventan políticas PENDING-04). Las señales quedan POR_REVISAR; ausencia de GPS no se atribuye a
 * Internet, detención, fraude ni incumplimiento. Los metadatos del dispositivo no existen: se informan como NO DISPONIBLES. CU-18/19 fuera de este corte.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class IntegridadGpsNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private UUID persona(String nombres) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"IG-"+id,nombres);
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
    private UUID jornada(UUID periodo,UUID version,UUID vv,UUID vp,Instant inicio,Instant fin) {
        UUID j=UUID.randomUUID(),a=UUID.randomUUID();
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",j,periodo,version,Timestamp.from(Instant.now().plus(1,ChronoUnit.DAYS)));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",a,j,vv,vp,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        if(inicio!=null) jdbc.update("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'INICIO',?,?,NULL)",UUID.randomUUID(),UUID.randomUUID(),j,a,Timestamp.from(inicio),Timestamp.from(inicio.plusSeconds(1)));
        if(fin!=null) jdbc.update("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'FIN',?,?,NULL)",UUID.randomUUID(),UUID.randomUUID(),j,a,Timestamp.from(fin),Timestamp.from(fin.plusSeconds(1)));
        return j;
    }
    private UUID asignacionDe(UUID jornada) { return jdbc.queryForObject("SELECT id FROM asignacion_jornada WHERE jornada_id=?",UUID.class,jornada); }
    private void posicion(UUID jornada,UUID captura,UUID conductor,Instant capturado,Instant recibido,double lon,double lat,double precision) {
        jdbc.update("INSERT INTO posicion_gps VALUES (?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),?)",UUID.randomUUID(),captura,jornada,asignacionDe(jornada),conductor,Timestamp.from(capturado),Timestamp.from(recibido),lon,lat,precision);
    }
    private List<String> tipos(JsonNode v) { var l=new ArrayList<String>(); v.get("hallazgos").forEach(h -> l.add(h.get("tipo").asText())); Collections.sort(l); return l; }
    private JsonNode hallazgo(JsonNode v,String tipo) { for(var h:v.get("hallazgos")) if(h.get("tipo").asText().equals(tipo)) return h; throw new AssertionError("Falta "+tipo); }
    private JsonNode revisar(String jornada,String query,String jwt) throws Exception {
        var r=get("/api/revision-gps/jornadas/"+jornada+"/integridad?"+query,jwt);
        assertThat(r.statusCode()).as(query+" "+r.body()).isEqualTo(200);
        return json.readTree(r.body());
    }

    @Test @Timeout(value=8,unit=TimeUnit.MINUTES)
    void municipalRevisaIntegridadGpsConUmbralesExplicitosSinDictaminar() throws Exception {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID();
        String revisor="int-rev-"+sufijo,operador="int-op-"+sufijo,sinPermiso="int-sin-"+sufijo,otro="int-otro-"+sufijo;
        UUID uR=usuario(revisor),uO=usuario(operador),uN=usuario(sinPermiso),uX=usuario(otro),conductor=usuario("int-cond-"+sufijo);
        conceder(uR,"GPS_INTEGRIDAD_REVISAR");
        conceder(uX,"GPS_COINCIDENCIAS_REVISAR");   // permisos de revisión independientes
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética integridad "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        for(String p:List.of("PLANIFICACION_CONSULTAR","JORNADA_PROGRAMAR","JORNADA_ASIGNAR","JORNADA_REPROGRAMAR"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),uO,periodo,p);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"IG-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.1 -17.7,-63.1 -17.6)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        UUID veh=UUID.randomUUID(),vv=UUID.randomUUID(),vp=UUID.randomUUID();
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",veh,"INT-"+sufijo.toUpperCase());
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,veh,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,persona("Conductor integridad "+sufijo),empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);

        Instant b=Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(2,ChronoUnit.HOURS);
        // Principal: INICIO b, FIN b+1000; muestras con huecos, precisión, recepción diferida/anterior, mismo instante y salto.
        UUID jp=jornada(periodo,version,vv,vp,b,b.plusSeconds(1000));
        UUID c2=UUID.fromString("00000000-0000-4000-8000-0000000000b2"),c3=UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"),c4=UUID.randomUUID(),c5=UUID.randomUUID(),c6=UUID.randomUUID(),c1=UUID.randomUUID();
        posicion(jp,c1,conductor,b.plusSeconds(200),b.plusSeconds(205),-63.1,-17.7,10);
        posicion(jp,c2,conductor,b.plusSeconds(230),b.plusSeconds(400),-63.1,-17.7001,80);      // precisión limitada + recepción diferida (170 s)
        posicion(jp,c3,conductor,b.plusSeconds(230),b.plusSeconds(235),-63.1,-17.7046,10);      // mismo instante que c2, otra posición
        posicion(jp,c4,conductor,b.plusSeconds(300),b.plusSeconds(305),-63.1,-17.705,5);        // intervalo de 70 s
        posicion(jp,c5,conductor,b.plusSeconds(310),b.plusSeconds(315),-63.1,-17.678,5);        // salto de ~3 km en 10 s
        posicion(jp,c6,conductor,b.plusSeconds(400),b.plusSeconds(395),-63.1,-17.678,10);       // recepción anterior a captura; intervalo de 90 s
        UUID jcero=jornada(periodo,version,vv,vp,b,null);
        UUID juna=jornada(periodo,version,vv,vp,b,b.plusSeconds(20));
        posicion(juna,UUID.randomUUID(),conductor,b.plusSeconds(10),b.plusSeconds(12),-63.1,-17.7,10);
        UUID jlim=jornada(periodo,version,vv,vp,b,b.plusSeconds(180));
        posicion(jlim,UUID.randomUUID(),conductor,b.plusSeconds(60),b.plusSeconds(65),-63.1,-17.7,50);     // hueco = 60 s y precisión = 50 m: no son señal
        posicion(jlim,UUID.randomUUID(),conductor,b.plusSeconds(120),b.plusSeconds(125),-63.1,-17.7002,50);
        UUID jl=jornada(periodo,version,vv,vp,null,null);
        jdbc.update("INSERT INTO posicion_gps SELECT gen_random_uuid(),gen_random_uuid(),?,?,?,(?::timestamptz)+i*interval '1 second',(?::timestamptz)+i*interval '1 second'+interval '1 second',ST_SetSRID(ST_MakePoint(-63.1,-17.7+(i%2)*0.00001),4326),5 FROM generate_series(1,5001) i",
                jl,asignacionDe(jl),conductor,Timestamp.from(b),Timestamp.from(b));
        var esperadosPrincipal=new ArrayList<>(List.of("FIN_SIN_MUESTRAS_RECIENTES","INICIO_SIN_MUESTRAS","INTERVALO_SIN_MUESTRAS","INTERVALO_SIN_MUESTRAS","POSICIONES_DISTINTAS_MISMO_INSTANTE",
                "PRECISION_LIMITADA","RECEPCION_ANTERIOR_CAPTURA","RECEPCION_DIFERIDA","SALTO_POR_REVISAR"));
        Collections.sort(esperadosPrincipal);
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
            var payload=Map.of("clave",clave,"revisor",revisor,"operador",operador,"sinPermiso",sinPermiso,
                "j",Map.of("principal",jp,"cero",jcero,"una",juna,"limite",jlim),"esperados",Map.of("principal",esperadosPrincipal));
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-integridad-gps.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lector.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E integridad GPS: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        assertThat(contar("SELECT count(*) FROM posicion_gps")).as("revisar no edita evidencia").isEqualTo(gpsAntes);

        // ---------- REST real ----------
        String tr=token(revisor);
        JsonNode v=revisar(jp.toString(),"huecoSegundos=60",tr);
        assertThat(tipos(v)).isEqualTo(esperadosPrincipal);
        assertThat(v.get("estado").asText()).isEqualTo("REVISION_TECNICA");
        assertThat(v.get("parcial").asBoolean()).isFalse();
        assertThat(v.get("politica").asText()).contains("no certifica integridad").contains("RECALCULABLE");
        assertThat(v.get("metadatosUbicacion").asText()).contains("NO DISPONIBLES").contains("no se inventan");
        assertThat(hallazgo(v,"INICIO_SIN_MUESTRAS").get("valor").asDouble()).isEqualTo(200.0);
        assertThat(hallazgo(v,"PRECISION_LIMITADA").get("valor").asDouble()).isEqualTo(80.0);
        assertThat(hallazgo(v,"RECEPCION_DIFERIDA").get("valor").asDouble()).isEqualTo(170.0);
        assertThat(hallazgo(v,"RECEPCION_ANTERIOR_CAPTURA").get("valor").asDouble()).isEqualTo(-5.0);
        assertThat(hallazgo(v,"FIN_SIN_MUESTRAS_RECIENTES").get("valor").asDouble()).isEqualTo(600.0);
        assertThat(hallazgo(v,"SALTO_POR_REVISAR").get("valor").asDouble()).isGreaterThan(120.0);
        for(var h:v.get("hallazgos")) {
            assertThat(h.get("estado").asText()).as("ninguna señal es dictamen").isIn("POR_REVISAR");
            assertThat(h.get("detalle").asText().toLowerCase()).doesNotContain("fraude comprobado ").doesNotContain("incumpl");
        }
        List<String> orden=new ArrayList<>(); v.get("muestras").forEach(m -> orden.add(m.get("capturaId").asText()));
        assertThat(orden).as("orden cronológico por captura con desempate").containsExactly(c1.toString(),c2.toString(),c3.toString(),c4.toString(),c5.toString(),c6.toString());
        // Umbrales explícitos y límites exactos.
        assertThat(tipos(revisar(jlim.toString(),"huecoSegundos=60&precisionMetros=50",tr))).as("hueco=60 y precisión=50 no son señal").isEmpty();
        assertThat(tipos(revisar(jlim.toString(),"huecoSegundos=59.9&precisionMetros=49.9",tr))).contains("INICIO_SIN_MUESTRAS","INTERVALO_SIN_MUESTRAS","FIN_SIN_MUESTRAS_RECIENTES","PRECISION_LIMITADA");
        var insuficiente=revisar(jcero.toString(),"huecoSegundos=60",tr);
        assertThat(insuficiente.get("estado").asText()).isEqualTo("EVIDENCIA_INSUFICIENTE");
        assertThat(insuficiente.get("muestrasAnalizadas").asInt()).isZero();
        assertThat(revisar(juna.toString(),"huecoSegundos=60",tr).get("estado").asText()).isEqualTo("EVIDENCIA_INSUFICIENTE");
        // Captura tardía: el recálculo cambia los hallazgos sin tocar la evidencia original.
        var originales=jdbc.queryForList("SELECT * FROM posicion_gps WHERE jornada_id=? ORDER BY captura_id",jp);
        posicion(jp,UUID.randomUUID(),conductor,b.plusSeconds(265),Instant.now().truncatedTo(ChronoUnit.MICROS),-63.1,-17.7048,5);
        var recalculado=revisar(jp.toString(),"huecoSegundos=60",tr);
        assertThat(recalculado.get("muestrasDisponibles").asLong()).isEqualTo(7);
        assertThat(tipos(recalculado).stream().filter("INTERVALO_SIN_MUESTRAS"::equals).count()).as("el intervalo de 70 s queda dividido").isEqualTo(1);
        assertThat(tipos(recalculado).stream().filter("RECEPCION_DIFERIDA"::equals).count()).as("la tardía se informa como diferida").isEqualTo(2);
        var despues=jdbc.queryForList("SELECT * FROM posicion_gps WHERE jornada_id=? AND captura_id IN (?,?,?,?,?,?) ORDER BY captura_id",jp,c1,c2,c3,c4,c5,c6);
        assertThat(despues).as("evidencia original intacta").isEqualTo(originales);
        // Límite de 5000 muestras con resultado parcial explícito.
        var parcial=revisar(jl.toString(),"huecoSegundos=60",tr);
        assertThat(parcial.get("muestrasDisponibles").asLong()).isEqualTo(5001);
        assertThat(parcial.get("muestrasAnalizadas").asInt()).isEqualTo(5000);
        assertThat(parcial.get("parcial").asBoolean()).isTrue();
        assertThat(parcial.get("estado").asText()).isEqualTo("REVISION_PARCIAL");
        // Parámetros inválidos, tipo, inexistente.
        for(String q:List.of("huecoSegundos=0","huecoSegundos=-1","huecoSegundos=NaN","huecoSegundos=Infinity","huecoSegundos=1000001","huecoSegundos=abc","precisionMetros=0","velocidadKmh=-5","antiguedadSegundos=0"))
            assertThat(get("/api/revision-gps/jornadas/"+jp+"/integridad?"+q,tr).statusCode()).as(q).isEqualTo(400);
        assertThat(get("/api/revision-gps/jornadas/"+jp+"/xyz",tr).statusCode()).as("tipo no admitido").isEqualTo(400);
        assertThat(get("/api/revision-gps/jornadas/"+UUID.randomUUID()+"/integridad",tr).statusCode()).as("jornada inexistente").isEqualTo(404);
        assertThat(get("/api/revision-gps/jornadas/no-es-uuid/integridad",tr).statusCode()).isEqualTo(400);
        // Acceso: sin token, A4, sin permiso y permiso de OTRA revisión (independientes); revocación.
        assertThat(get("/api/revision-gps/jornadas/"+jp+"/integridad",null).statusCode()).isIn(401,403);
        for(String u:List.of(operador,sinPermiso,otro)) {
            var x=get("/api/revision-gps/jornadas/"+jp+"/integridad",token(u));
            assertThat(x.statusCode()).as(u).isEqualTo(403);
            assertThat(x.body()).doesNotContain(jp.toString());
        }
        assertThat(contar("SELECT count(*) FROM concesion_permiso WHERE usuario_id=? AND permiso LIKE 'GPS_%'",uO)).as("A4 sin permisos GPS").isZero();
        assertThat(catchThrowable(() -> jdbc.update("UPDATE posicion_gps SET precision_metros=1 WHERE jornada_id=?",jp))).as("evidencia inmutable").isNotNull();
        assertThat(jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba',motivo_revocacion='fixture' WHERE usuario_id=? AND permiso='GPS_INTEGRIDAD_REVISAR' AND revocado_en IS NULL",uR)).isEqualTo(1);
        assertThat(get("/api/revision-gps/jornadas/"+jp+"/integridad",tr).statusCode()).as("permiso revocado").isEqualTo(403);
    }
}
