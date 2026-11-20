package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
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
 * CU-27 con navegador real + REST real + PostgreSQL/PostGIS aislados y JWT: revisión municipal (observaciones sin resultado oficial), vínculos
 * justificados, rechazos y consulta de la historia por un lector. Datos sintéticos; el reclamo se siembra como registrado por CU-26.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class RevisionReclamosNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private UUID usuario(String nombre,String... permisos) {
        UUID id=UUID.randomUUID(),persona=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"RVN-"+persona,"Cuenta "+nombre);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void revisaVinculaYConsultaLaHistoriaDeUnReclamoEnElNavegador() throws Exception {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada1=UUID.randomUUID(),jornada2=UUID.randomUUID();
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-27 "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"RVN-"+sufijo,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada1,periodo,version);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-09T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada2,periodo,version);
        String revisor="rv-rev-"+sufijo,lector="rv-lec-"+sufijo;
        UUID registradorId=usuario("rv-reg-"+sufijo,"RECLAMO_CONSULTAR","RECLAMO_REGISTRAR");
        UUID revisorId=usuario(revisor,"RECLAMO_CONSULTAR","RECLAMO_REVISAR");
        usuario(lector,"RECLAMO_CONSULTAR");
        UUID inspeccion=UUID.randomUUID();
        jdbc.update("INSERT INTO inspeccion(id,solicitud_id,huella_solicitud,jornada_id,inspector_usuario_id,realizada_en,observaciones) VALUES (?,?,'h',?,?,?,?)",
            inspeccion,UUID.randomUUID(),jornada1,usuario("rv-ins-"+sufijo),Timestamp.from(Instant.now().minus(1,ChronoUnit.DAYS)),"Inspección sintética");
        // Reclamo registrado (CU-26) con recepción reciente: figura en la primera página (orden por recepción descendente).
        UUID reclamo=UUID.randomUUID(); Instant recibido=Instant.now().minus(1,ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("""
            INSERT INTO reclamo_institucional(id,solicitud_id,huella_solicitud,canal_declarado,recibido_en,descripcion,lugar_descripcion,referencias_origen,jornada_id,usuario_id)
            VALUES (?,?,'h','Teléfono institucional (declarado)',?,?,'Calle sintética','',NULL,?)""",
            reclamo,UUID.randomUUID(),Timestamp.from(recibido),"Vecino informa acumulación de residuos "+sufijo,registradorId);

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
            var payload=new LinkedHashMap<String,Object>();
            payload.put("clave",clave); payload.put("revisor",revisor); payload.put("lector",lector); payload.put("marca",sufijo);
            payload.put("reclamo",reclamo.toString()); payload.put("recibidoEn",recibido.toString()); payload.put("registradorId",registradorId.toString());
            payload.put("revisorId",revisorId.toString()); payload.put("jornada1",jornada1.toString()); payload.put("jornada2",jornada2.toString()); payload.put("inspeccion",inspeccion.toString());
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-revision-reclamo.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lectorSalida=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(180,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lectorSalida.join(1000);
                String diagnostico=salida.toString(StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E revisión de reclamos: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }

        // ---------- Estado persistido ----------
        var revisiones=jdbc.queryForList("SELECT usuario_id,resultado_id,observaciones FROM revision_reclamo WHERE reclamo_id=? ORDER BY registrada_en,id",reclamo);
        assertThat(revisiones).as("dos observaciones; el reintento no duplica").hasSize(2);
        for(var r:revisiones) { assertThat(r).containsEntry("usuario_id",revisorId); assertThat(r.get("resultado_id")).isNull(); }
        var relaciones=jdbc.queryForList("SELECT jornada_id,inspeccion_id,usuario_id FROM relacion_reclamo WHERE reclamo_id=? ORDER BY registrada_en,id",reclamo);
        assertThat(relaciones).hasSize(2);
        assertThat(relaciones.get(0)).containsEntry("jornada_id",jornada1).containsEntry("usuario_id",revisorId);
        assertThat(relaciones.get(1)).containsEntry("inspeccion_id",inspeccion);
        var original=jdbc.queryForMap("SELECT usuario_id,jornada_id,recibido_en,canal_declarado FROM reclamo_institucional WHERE id=?",reclamo);
        assertThat(original).containsEntry("usuario_id",registradorId).containsEntry("canal_declarado","Teléfono institucional (declarado)");
        assertThat(original.get("jornada_id")).isNull();
        assertThat(((Timestamp)original.get("recibido_en")).toInstant()).isEqualTo(recibido);
    }
}
