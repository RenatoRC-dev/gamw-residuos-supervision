package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

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
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Navegador real (Playwright) + Vite + backend REST + PostgreSQL/PostGIS aislados, datos sintéticos, sin respuestas de negocio simuladas.
 * El conductor (A5) ejecuta la jornada por la API real (INICIO, GPS desordenado con hueco temporal, reintento duplicado, FIN) y el navegador
 * recorre trayectoria → expediente → historial con un fiscalizador y comprueba las denegaciones de una cuenta sin concesiones.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class FlujoMunicipalNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString();

    private UUID usuario(UUID persona,String nombre) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        return id;
    }
    private UUID persona(String etiqueta) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"FM-"+id,etiqueta);
        return id;
    }
    private void conceder(UUID usuario,String... permisos) {
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),usuario,p);
    }
    private HttpResponse<String> api(String metodo,String ruta,String jwt,Object cuerpo) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(10));
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        if(cuerpo==null) b.method(metodo,HttpRequest.BodyPublishers.noBody());
        else b.header("Content-Type","application/json").method(metodo,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String token(String nombre) throws Exception {
        var r=api("POST","/api/autenticacion/login",null,Map.of("nombreUsuario",nombre,"contrasena",clave));
        assertThat(r.statusCode()).isEqualTo(200);
        return json.readTree(r.body()).get("tokenAcceso").asText();
    }

    @Test @Timeout(value=5,unit=TimeUnit.MINUTES)
    void fiscalizadorRecorreTrayectoriaExpedienteEHistorialYUnaCuentaSinPermisosEsDenegada() throws Exception {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),
            vp=UUID.randomUUID(),jornada=UUID.randomUUID(),asignacion=UUID.randomUUID();
        UUID personaConductor=persona("Conductor sintético");
        String conductor="flujo-cond-"+UUID.randomUUID(),fiscal="flujo-fiscal-"+UUID.randomUUID(),supervisor="flujo-sup-"+UUID.randomUUID();
        usuario(personaConductor,conductor);
        UUID uFiscal=usuario(persona("Fiscalizador sintético"),fiscal);
        usuario(persona("Supervisor sin concesiones"),supervisor);
        conceder(uFiscal,"TRAYECTORIA_JORNADA_CONSULTAR","FISCALIZACION_CONSULTAR","JORNADAS_CONSULTAR"); // sin CORRECTIVA_/RECLAMO_/EMITIR
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética flujo municipal");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"FM-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"FM-"+vehiculo.toString().toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,personaConductor,empresa);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacion,jornada,vv,vp,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));

        // ---- El conductor ejecuta por la API REAL: INICIO, GPS (desordenado, con hueco de ~5 min), reintento duplicado y FIN ----
        String tc=token(conductor);
        Instant c0=Instant.now().minus(30,ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        assertThat(api("POST","/api/jornadas/"+jornada+"/ejecucion/inicio",tc,Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",c0.toString())).statusCode()).isEqualTo(201);
        var lote=Map.of("asignacionJornadaId",asignacion,"posiciones",List.of(
            Map.of("capturaId",UUID.randomUUID(),"capturadoEn",c0.plusSeconds(300).toString(),"latitud",20.30,"longitud",-10.90,"precisionMetros",9.5),
            Map.of("capturaId",UUID.randomUUID(),"capturadoEn",c0.plusSeconds(1).toString(),"latitud",20.10,"longitud",-10.70,"precisionMetros",5.5),
            Map.of("capturaId",UUID.randomUUID(),"capturadoEn",c0.plusSeconds(2).toString(),"latitud",20.20,"longitud",-10.80,"precisionMetros",7.5)));
        assertThat(api("POST","/api/jornadas/"+jornada+"/ejecucion/posiciones-gps",tc,lote).statusCode()).isEqualTo(200);
        assertThat(api("POST","/api/jornadas/"+jornada+"/ejecucion/posiciones-gps",tc,lote).statusCode()).as("reintento duplicado idempotente").isEqualTo(200);
        assertThat(api("POST","/api/jornadas/"+jornada+"/ejecucion/fin",tc,Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",c0.plusSeconds(400).toString())).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(3);

        // ---- Navegador real ----
        int web;
        try(var disponible=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { web=disponible.getLocalPort(); }
        Path directorio=Path.of("..","apps","web").toAbsolutePath().normalize();
        var vite=new ProcessBuilder("node","node_modules/vite/bin/vite.js","--host","127.0.0.1","--port",Integer.toString(web),"--strictPort")
                .directory(directorio.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        vite.environment().put("GAMW_WEB_INTEGRACION","true");
        vite.environment().put("VITE_API_BASE_URL","/api");
        vite.environment().put("BACKEND_PROXY_TARGET","http://127.0.0.1:"+puerto);
        Process servidor=vite.start(); Process navegador=null;
        try {
            long limite=System.nanoTime()+Duration.ofSeconds(25).toNanos(); boolean listo=false;
            while(System.nanoTime()<limite && servidor.isAlive()) {
                try {
                    var r=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+web+"/acceso")).header("Accept","text/html").timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding());
                    if(r.statusCode()==200) { listo=true; break; }
                } catch(java.io.IOException error) { /* arranque del proceso propio */ }
                Thread.sleep(100);
            }
            assertThat(listo).as("Vite nuevo en puerto aislado").isTrue();
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-municipal.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            prueba.environment().put("GAMW_WEB_INTEGRACION","true");
            prueba.environment().put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web);
            prueba.environment().put("GAMW_WEB_TEST_FISCAL",fiscal);
            prueba.environment().put("GAMW_WEB_TEST_SUPERVISOR",supervisor);
            prueba.environment().put("GAMW_WEB_TEST_PASSWORD",clave);
            prueba.environment().put("GAMW_WEB_TEST_JORNADA",jornada.toString());
            navegador=prueba.start();
            var salida=new java.io.ByteArrayOutputStream(); final Process proceso=navegador;
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            assertThat(navegador.waitFor(140,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
            lector.join(1000);
            String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
            assertThat(navegador.exitValue()).as("E2E flujo municipal: %s",diagnostico).isZero();
        } finally {
            if(navegador!=null) { navegador.descendants().forEach(ProcessHandle::destroy); navegador.destroy(); }
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        // El GPS y la consulta no crearon ningún dictamen ni modificaron evidencia.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(2);
    }
}
