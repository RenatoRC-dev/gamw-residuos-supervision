package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

/** CU-01: servidor/filtros y repositorios reales; navegador en Vite propio. Sin .env ni destinos existentes. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class AutenticacionWebIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final UUID persona=UUID.randomUUID(),personaInactiva=UUID.randomUUID(),usuario=UUID.randomUUID(),usuarioInactivo=UUID.randomUUID();
    private final String nombre="auto-web-"+usuario,nombreInactivo="auto-web-"+usuarioInactivo,clave=UUID.randomUUID().toString();

    @BeforeEach void prepararSoloFixturesAislados() {
        for(UUID p:List.of(persona,personaInactiva)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"AUTO-WEB-"+p,"Persona sintética web");
        String hash=codificador.encode(clave);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",usuario,persona,nombre,hash);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,false,now(),now())",usuarioInactivo,personaInactiva,nombreInactivo,hash);
    }
    private HttpResponse<String> solicitar(String ruta,String jwt,Object cuerpo) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(10));
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        if(cuerpo==null) b.GET();
        else b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void persistenciaAutenticacionRevocacionYCierreDeApis() throws Exception {
        var login=solicitar("/api/autenticacion/login",null,Map.of("nombreUsuario",nombre,"contrasena",clave));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var respuesta=json.readTree(login.body());
        assertThat(respuesta.get("usuarioId").asText()).isEqualTo(usuario.toString());
        assertThat(respuesta.get("personaNaturalId").asText()).isEqualTo(persona.toString());
        assertThat(respuesta.has("contrasenaHash")).isFalse();
        String token=respuesta.get("tokenAcceso").asText();
        assertThat(solicitar("/api/autenticacion/actual",token,null).statusCode()).isEqualTo(200);
        for(String ruta:List.of("/api/rutas","/api/personas","/api/vehiculos","/api/empresas-operadoras","/api/jornadas"))
            assertThat(solicitar(ruta,token,null).statusCode()).isEqualTo(403);
        var incorrecta=solicitar("/api/autenticacion/login",null,Map.of("nombreUsuario",nombre,"contrasena","incorrecta"));
        var inactiva=solicitar("/api/autenticacion/login",null,Map.of("nombreUsuario",nombreInactivo,"contrasena",clave));
        assertThat(incorrecta.statusCode()).isEqualTo(401); assertThat(inactiva.statusCode()).isEqualTo(401);
        assertThat(json.readTree(inactiva.body()).get("detail")).isEqualTo(json.readTree(incorrecta.body()).get("detail"));
        jdbc.update("UPDATE usuario SET activo=false WHERE id=?",usuario);
        assertThat(solicitar("/api/autenticacion/actual",token,null).statusCode()).isEqualTo(401);
        assertThat(solicitar("/api/autenticacion/actual","sintetico-invalido",null).statusCode()).isEqualTo(401);
    }
    @Test @Timeout(value=3,unit=TimeUnit.MINUTES)
    void navegadorCompletaCu01ContraPostgresAislado() throws Exception {
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
            long limite=System.nanoTime()+Duration.ofSeconds(25).toNanos(); boolean listo=false; int ultimoEstado=0;
            while(System.nanoTime()<limite && servidor.isAlive()) {
                try {
                    var r=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+web+"/acceso")).header("Accept","text/html").timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding());
                    ultimoEstado=r.statusCode();
                    if(r.statusCode()==200) { listo=true; break; }
                } catch(java.io.IOException error) { /* Esperar solo al proceso recién creado. */ }
                Thread.sleep(100);
            }
            assertThat(listo).as("Vite nuevo en puerto aislado; último HTTP %s",ultimoEstado).isTrue();
            var prueba=new ProcessBuilder("node","--test","e2e/autenticacion.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            prueba.environment().put("GAMW_WEB_INTEGRACION","true");
            prueba.environment().put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web);
            prueba.environment().put("GAMW_WEB_TEST_USER",nombre);
            prueba.environment().put("GAMW_WEB_TEST_INACTIVE",nombreInactivo);
            prueba.environment().put("GAMW_WEB_TEST_PASSWORD",clave);
            navegador=prueba.start();
            var salida=new java.io.ByteArrayOutputStream(); final Process proceso=navegador;
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* Validar exit code. */ } });
            assertThat(navegador.waitFor(100,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
            lector.join(1000);
            String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
            assertThat(navegador.exitValue()).as("E2E CU-01: %s",diagnostico).isZero();
            System.out.println("CU-01 Playwright: 1/1, contra servidor y PostgreSQL aislados.");
        } finally {
            if(navegador!=null) { navegador.descendants().forEach(ProcessHandle::destroy); navegador.destroy(); }
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
    }
}
