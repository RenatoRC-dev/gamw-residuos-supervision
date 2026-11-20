package bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
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

/**
 * CU-10 con navegador real (MapLibre + Terra Draw sobre WebGL/SwiftShader) + REST real + PostgreSQL/PostGIS aislados y JWT. El mapa usa un
 * estilo local con solo fondo (sin teselas externas). Coordenadas, radios y rutas sintéticos.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class ReferenciasEspacialesNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private void usuario(String nombre,String... permisos) {
        UUID id=UUID.randomUUID(),persona=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"REN-"+persona,"Cuenta "+nombre);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
    }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void dibujaPersisteVersionaYCierraReferenciasSobreElMapaReal() throws Exception {
        UUID ruta=UUID.randomUUID(),version=UUID.randomUUID();
        // Código con prefijo bajo para que la versión figure en la primera página (orden por código) del selector.
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"0000-RE-"+sufijo,"Ruta sintética no oficial "+sufijo);
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.17 -17.52,-63.15 -17.50)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        String gestor="re-ges-"+sufijo,lector="re-lec-"+sufijo,sinPermiso="re-sin-"+sufijo;
        usuario(gestor,"REFERENCIA_ESPACIAL_CONSULTAR","REFERENCIA_ESPACIAL_GESTIONAR");
        usuario(lector,"REFERENCIA_ESPACIAL_CONSULTAR");
        usuario(sinPermiso,"TRAYECTORIA_JORNADA_CONSULTAR");
        String estilo="{\"version\":8,\"sources\":{},\"layers\":[{\"id\":\"fondo\",\"type\":\"background\",\"paint\":{\"background-color\":\"#f3f4f6\"}}]}";

        int web;
        try(var disponible=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { web=disponible.getLocalPort(); }
        Path directorio=Path.of("..","apps","web").toAbsolutePath().normalize();
        var vite=new ProcessBuilder("node","node_modules/vite/bin/vite.js","--host","127.0.0.1","--port",Integer.toString(web),"--strictPort")
                .directory(directorio.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        vite.environment().put("GAMW_WEB_INTEGRACION","true");
        vite.environment().put("VITE_API_BASE_URL","/api");
        vite.environment().put("BACKEND_PROXY_TARGET","http://127.0.0.1:"+puerto);
        vite.environment().put("VITE_MAP_STYLE_URL","data:application/json,"+URLEncoder.encode(estilo,StandardCharsets.UTF_8).replace("+","%20"));
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
            var payload=Map.of("clave",clave,"gestor",gestor,"lector",lector,"sinPermiso",sinPermiso,"rutaVersion",version.toString(),"sufijo",sufijo);
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-referencias.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lectorSalida=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(180,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lectorSalida.join(1000);
                String diagnostico=salida.toString(StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E referencias espaciales: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }

        // ---------- Estado persistido en PostGIS ----------
        var filas=jdbc.queryForList("""
            SELECT nombre,tipo,numero_version,vigente_hasta IS NULL AS abierta,radio_metros,ST_SRID(geometria) AS srid,GeometryType(geometria) AS g,ST_IsValid(geometria) AS valida
              FROM referencia_espacial_version WHERE ruta_version_id=? ORDER BY nombre,numero_version""",version);
        assertThat(filas).hasSize(3);
        assertThat(filas.get(0)).containsEntry("tipo","GEOCERCA").containsEntry("g","POLYGON").containsEntry("abierta",true).containsEntry("valida",true);
        assertThat(filas.get(0).get("radio_metros")).isNull();
        assertThat(filas.get(1)).containsEntry("tipo","PUNTO").containsEntry("numero_version",1).containsEntry("abierta",false).containsEntry("radio_metros",50.0);
        assertThat(filas.get(2)).containsEntry("tipo","PUNTO").containsEntry("numero_version",2).containsEntry("abierta",false).containsEntry("radio_metros",80.0);
        for(var f:filas) assertThat(f).containsEntry("srid",4326);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT referencia_id) FROM referencia_espacial_version WHERE ruta_version_id=? AND tipo='PUNTO'",Integer.class,version))
            .as("v1 y v2 comparten identidad").isEqualTo(1);
    }
}
