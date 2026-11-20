package bo.gob.warnes.gamw.residuos.modules.historial.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
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

/** CU-30 con navegador real + REST real + PostgreSQL/PostGIS aislados y JWT. Reutiliza el fixture de ReportesServicioIT. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class ReportesServicioNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final String clave=UUID.randomUUID().toString();

    @Test @Timeout(value=5,unit=TimeUnit.MINUTES)
    void reporteDescriptivoPorLaInterfazConFiltrosPermisosYSinEscrituras() throws Exception {
        var f=ReportesServicioIT.sembrar(jdbc,codificador,clave);
        String s=f.empresa().toString().substring(0,8);
        String huella="SELECT (SELECT count(*) FROM jornada)||'|'||(SELECT count(*) FROM posicion_gps)||'|'||(SELECT count(*) FROM incidencia_operativa)||'|'||(SELECT count(*) FROM inspeccion)||'|'||(SELECT count(*) FROM reclamo_institucional)";
        String antes=jdbc.queryForObject(huella,String.class);
        int web;
        try(var disponible=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { web=disponible.getLocalPort(); }
        Path directorio=Path.of("..","apps","web").toAbsolutePath().normalize();
        var vite=new ProcessBuilder("node","node_modules/vite/bin/vite.js","--host","127.0.0.1","--port",Integer.toString(web),"--strictPort")
                .directory(directorio.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        vite.environment().put("GAMW_WEB_INTEGRACION","true");
        vite.environment().put("VITE_API_BASE_URL","/api");
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
            var payload=Map.of("clave",clave,"completo","rp-adm-"+s,"basico","rp-bas-"+s,"sinPermiso","rp-sin-"+s,
                    "empresa",f.empresa().toString(),"empresaVacia",f.empresaVacia().toString());
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-reportes.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lector.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E reportes: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        assertThat(jdbc.queryForObject(huella,String.class)).as("CA-30-07: la generación por la interfaz no escribe").isEqualTo(antes);
    }
}
