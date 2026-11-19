package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.api;

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
 * CU-16 (A3) con navegador real + REST real + PostgreSQL/PostGIS aislados y datos sintéticos.
 * Orden por captura con desempate, recepción tardía, corte de recepción estable, paginación, referencia histórica de ruta, línea inferida apagada
 * por defecto. Los intervalos sin muestras no acreditan recorrido. A4 sin acceso (D1 pendiente). Marcadores WebGL no afirmados por DOM.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class TrayectoriaMunicipalNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private UUID persona(String nombres) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"TR-"+id,nombres);
        return id;
    }
    private UUID usuario(String nombre) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona("Cuenta "+nombre),nombre,codificador.encode(clave));
        return id;
    }
    private HttpResponse<String> get(String ruta,String jwt) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(15)).GET();
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
    private UUID jornada(UUID periodo,UUID version,UUID vv,UUID vp) {
        UUID j=UUID.randomUUID();
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",j,periodo,version,Timestamp.from(Instant.now().plus(1,ChronoUnit.DAYS)));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",UUID.randomUUID(),j,vv,vp,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        return j;
    }
    private UUID asignacionDe(UUID jornada) { return jdbc.queryForObject("SELECT id FROM asignacion_jornada WHERE jornada_id=?",UUID.class,jornada); }
    private void posicion(UUID jornada,UUID captura,UUID conductor,Instant capturado,Instant recibido,double lon,double lat,double precision) {
        jdbc.update("INSERT INTO posicion_gps VALUES (?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),?)",UUID.randomUUID(),captura,jornada,asignacionDe(jornada),conductor,Timestamp.from(capturado),Timestamp.from(recibido),lon,lat,precision);
    }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void municipalConsultaTrayectoriasConOrdenPorCapturaPaginacionYReferenciaHistorica() throws Exception {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),v1=UUID.randomUUID(),v2=UUID.randomUUID();
        String municipal="tra-mun-"+sufijo,operador="tra-op-"+sufijo,sinPermiso="tra-sin-"+sufijo;
        UUID uM=usuario(municipal),uO=usuario(operador),uN=usuario(sinPermiso),conductor=usuario("tra-cond-"+sufijo);
        jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,'TRAYECTORIA_JORNADA_CONSULTAR','MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),uM);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética trayectoria "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        for(String p:List.of("PLANIFICACION_CONSULTAR","JORNADA_PROGRAMAR","JORNADA_ASIGNAR","JORNADA_REPROGRAMAR"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),uO,periodo,p);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"TR-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.1 -17.7,-63.2 -17.8)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",v1,ruta);
        UUID veh=UUID.randomUUID(),vv=UUID.randomUUID(),vp=UUID.randomUUID();
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",veh,"TRA-"+sufijo.toUpperCase());
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,veh,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,persona("Conductor trayectoria "+sufijo),empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);

        Instant base=Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(2,ChronoUnit.HOURS);
        UUID jcero=jornada(periodo,v1,vv,vp),juna=jornada(periodo,v1,vv,vp),jvarias=jornada(periodo,v1,vv,vp),jpag=jornada(periodo,v1,vv,vp);
        // Cambio posterior de la ruta: nueva versión 2; las jornadas anteriores conservan la 1.
        jdbc.update("UPDATE ruta_version SET vigente_hasta='2026-06-01T00:00:00Z' WHERE id=?",v1);   // solo una versión abierta por ruta
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,2,ST_GeomFromText('LINESTRING(-64.1 -18.7,-64.2 -18.8,-64.3 -18.9)',4326),'2026-06-01T00:00:00Z',NULL,NULL,now())",v2,ruta);
        UUID jv2=jornada(periodo,v2,vv,vp);
        posicion(juna,UUID.randomUUID(),conductor,base,base.plusSeconds(5),-63.15,-17.75,9);
        posicion(jv2,UUID.randomUUID(),conductor,base,base.plusSeconds(5),-64.15,-18.75,9);
        UUID c1=UUID.randomUUID(),c2=UUID.randomUUID(),c3a=UUID.fromString("00000000-0000-4000-8000-0000000000a1"),c3b=UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
        posicion(jvarias,c3b,conductor,base.plusSeconds(120),base.plusSeconds(130),-63.13,-17.73,7);
        posicion(jvarias,c1,conductor,base,base.plusSeconds(300),-63.1,-17.7,5);          // sincronización tardía: capturada primero, recibida última
        posicion(jvarias,c2,conductor,base.plusSeconds(60),base.plusSeconds(70),-63.11,-17.71,6);
        posicion(jvarias,c3a,conductor,base.plusSeconds(120),base.plusSeconds(130),-63.12,-17.72,6.5);   // empate de captura con c3b
        jdbc.update("INSERT INTO posicion_gps SELECT gen_random_uuid(),gen_random_uuid(),?,?,?,(?::timestamptz)+i*interval '1 second',(?::timestamptz)+i*interval '1 second'+interval '1 second',ST_SetSRID(ST_MakePoint(-63.0-i*0.0001,-17.0),4326),10 FROM generate_series(1,505) i",
                jpag,asignacionDe(jpag),conductor,Timestamp.from(base),Timestamp.from(base));
        List<Map<String,Object>> orden=new ArrayList<>();
        for(var o:List.of(new Object[]{c1,base,base.plusSeconds(300),-63.1,-17.7,5.0},new Object[]{c2,base.plusSeconds(60),base.plusSeconds(70),-63.11,-17.71,6.0},
                new Object[]{c3a,base.plusSeconds(120),base.plusSeconds(130),-63.12,-17.72,6.5},new Object[]{c3b,base.plusSeconds(120),base.plusSeconds(130),-63.13,-17.73,7.0}))
            orden.add(Map.of("capturaId",o[0].toString(),"capturadoEn",o[1].toString(),"recibidoEn",o[2].toString(),"lon",o[3],"lat",o[4],"precision",o[5]));
        int gpsAntes=contar("SELECT count(*) FROM posicion_gps"),eventosAntes=contar("SELECT count(*) FROM evento_ejecucion_jornada");
        assertThat(contar("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",jpag)).isEqualTo(505);

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
            var payload=Map.of("clave",clave,"municipal",municipal,"operador",operador,"sinPermiso",sinPermiso,
                "j",Map.of("cero",jcero,"una",juna,"varias",jvarias,"paginada",jpag,"v2",jv2),
                "orden",orden,"asignacionVarias",asignacionDe(jvarias).toString(),"versionOriginal",v1.toString(),"versionNueva",v2.toString());
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-trayectoria.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lector.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E trayectoria: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        assertThat(contar("SELECT count(*) FROM posicion_gps")).as("consultar no edita evidencia").isEqualTo(gpsAntes);
        assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada")).isEqualTo(eventosAntes);

        // ---------- REST real ----------
        String tm=token(municipal);
        var r=get("/api/trayectorias/jornadas/"+jvarias,tm);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode v=json.readTree(r.body());
        List<String> ids=new ArrayList<>(); v.get("seguimiento").get("trayectoria").get("features").forEach(f -> ids.add(f.get("properties").get("capturaId").asText()));
        assertThat(ids).as("orden por captura, empate por captura_id").containsExactly(c1.toString(),c2.toString(),c3a.toString(),c3b.toString());
        var primero=v.get("seguimiento").get("trayectoria").get("features").get(0);
        assertThat(primero.get("geometry").get("coordinates").get(0).asDouble()).as("[longitud, latitud]").isEqualTo(-63.1);
        assertThat(primero.get("geometry").get("coordinates").get(1).asDouble()).isEqualTo(-17.7);
        assertThat(Instant.parse(primero.get("properties").get("recibidoEn").asText())).isEqualTo(base.plusSeconds(300));
        assertThat(primero.get("properties").get("asignacionJornadaId").asText()).isEqualTo(asignacionDe(jvarias).toString());
        assertThat(v.get("rutaReferencia").get("coordinates")).as("referencia histórica v1").hasSize(2);
        assertThat(v.get("contexto").get("rutaVersionId").asText()).isEqualTo(v1.toString());
        assertThat(json.readTree(get("/api/trayectorias/jornadas/"+jv2,tm).body()).get("rutaReferencia").get("coordinates")).as("v2 conserva la suya").hasSize(3);
        assertThat(json.readTree(get("/api/trayectorias/jornadas/"+jcero,tm).body()).get("seguimiento").get("trayectoria").get("features")).isEmpty();
        // Paginación, límites y corte de recepción estable.
        var pag1=json.readTree(get("/api/trayectorias/jornadas/"+jvarias+"?limite=2&desplazamiento=0",tm).body()).get("seguimiento");
        assertThat(pag1.get("siguiente").asInt()).isEqualTo(2);
        String corte=pag1.get("corteRecepcion").asText();
        UUID tardia=UUID.randomUUID();
        posicion(jvarias,tardia,conductor,base.minusSeconds(100),Instant.now().truncatedTo(ChronoUnit.MICROS),-63.0,-17.6,11);   // llega después del corte
        var pag2=json.readTree(get("/api/trayectorias/jornadas/"+jvarias+"?limite=2&desplazamiento=2&corteRecepcion="+corte,tm).body()).get("seguimiento");
        List<String> ids2=new ArrayList<>(); pag2.get("trayectoria").get("features").forEach(f -> ids2.add(f.get("properties").get("capturaId").asText()));
        assertThat(ids2).as("corte estable: la recepción posterior no desplaza páginas").containsExactly(c3a.toString(),c3b.toString());
        assertThat(pag2.get("siguiente").isNull()).isTrue();
        var fresca=json.readTree(get("/api/trayectorias/jornadas/"+jvarias,tm).body()).get("seguimiento").get("trayectoria").get("features");
        assertThat(fresca).hasSize(5);
        assertThat(fresca.get(0).get("properties").get("capturaId").asText()).as("la tardía se ordena por su captura, no por su recepción").isEqualTo(tardia.toString());
        for(String q:List.of("?limite=0","?limite=1001","?desplazamiento=-1","?corteRecepcion=no-es-fecha","?corteRecepcion="+Instant.now().plus(1,ChronoUnit.DAYS)))
            assertThat(get("/api/trayectorias/jornadas/"+jvarias+q,tm).statusCode()).as(q).isEqualTo(400);
        assertThat(get("/api/trayectorias/jornadas/"+UUID.randomUUID(),tm).statusCode()).as("jornada inexistente").isEqualTo(404);
        assertThat(get("/api/trayectorias/jornadas/no-es-uuid",tm).statusCode()).isEqualTo(400);
        assertThat(get("/api/trayectorias/jornadas?limite=0",tm).statusCode()).isEqualTo(400);
        // Acceso (D1: A4 sin permiso), sin exposición y revocación.
        assertThat(get("/api/trayectorias/jornadas/"+jvarias,null).statusCode()).isIn(401,403);
        for(String u:List.of(operador,sinPermiso)) {
            var x=get("/api/trayectorias/jornadas/"+jvarias,token(u));
            assertThat(x.statusCode()).as(u).isEqualTo(403);
            assertThat(x.body()).doesNotContain(jvarias.toString());
            assertThat(get("/api/trayectorias/jornadas/"+UUID.randomUUID(),token(u)).statusCode()).as("403 antes de revelar existencia").isEqualTo(403);
        }
        assertThat(contar("SELECT count(*) FROM concesion_permiso WHERE usuario_id=? AND permiso LIKE 'TRAYECTORIA%'",uO)).isZero();
        assertThat(catchThrowable(() -> jdbc.update("UPDATE posicion_gps SET precision_metros=1 WHERE jornada_id=?",jvarias))).as("evidencia inmutable").isNotNull();
        assertThat(contar("SELECT count(*) FROM posicion_gps")).isEqualTo(gpsAntes+1);
        assertThat(jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba',motivo_revocacion='fixture' WHERE usuario_id=? AND permiso='TRAYECTORIA_JORNADA_CONSULTAR' AND revocado_en IS NULL",uM)).isEqualTo(1);
        assertThat(get("/api/trayectorias/jornadas/"+jvarias,tm).statusCode()).as("permiso revocado").isEqualTo(403);
    }
}
