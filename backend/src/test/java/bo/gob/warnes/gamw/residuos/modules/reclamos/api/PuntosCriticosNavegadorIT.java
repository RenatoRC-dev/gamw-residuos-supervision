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
 * CU-28 con navegador real + REST real + PostgreSQL/PostGIS aislados y JWT: registro de un candidato a punto crítico, observación, validación
 * PENDING-10 cerrada (configuración por defecto) y consulta de solo lectura. Datos y coordenadas sintéticos; sin ubicaciones municipales reales.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class PuntosCriticosNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);
    private static final double LON=-10.123456,LAT=20.654321;

    private UUID usuario(String nombre,String... permisos) {
        UUID id=UUID.randomUUID(),persona=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"PCN-"+persona,"Cuenta "+nombre);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void registraObservaYConsultaUnCandidatoSinOficializarloEnElNavegador() throws Exception {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada=UUID.randomUUID();
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-28 "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"PCN-"+sufijo,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        String registrador="pc-reg-"+sufijo,validador="pc-val-"+sufijo,lector="pc-lec-"+sufijo;
        UUID registradorId=usuario(registrador,"PUNTO_CRITICO_CONSULTAR","PUNTO_CRITICO_REGISTRAR","RECLAMO_CONSULTAR","FISCALIZACION_CONSULTAR");
        usuario(validador,"PUNTO_CRITICO_CONSULTAR","PUNTO_CRITICO_VALIDAR");
        usuario(lector,"PUNTO_CRITICO_CONSULTAR");
        UUID receptor=usuario("pc-rec-"+sufijo),inspeccion=UUID.randomUUID(),reclamo=UUID.randomUUID();
        jdbc.update("INSERT INTO inspeccion(id,solicitud_id,huella_solicitud,jornada_id,inspector_usuario_id,realizada_en,observaciones) VALUES (?,?,'h',?,?,?,?)",
            inspeccion,UUID.randomUUID(),jornada,usuario("pc-ins-"+sufijo),Timestamp.from(Instant.now().minus(1,ChronoUnit.DAYS)),"Inspección sintética");
        Instant recibido=Instant.now().minus(3,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("""
            INSERT INTO reclamo_institucional(id,solicitud_id,huella_solicitud,canal_declarado,recibido_en,descripcion,lugar_descripcion,referencias_origen,jornada_id,usuario_id)
            VALUES (?,?,'h','Teléfono institucional (declarado)',?,?,'Calle sintética','',NULL,?)""",
            reclamo,UUID.randomUUID(),Timestamp.from(recibido),"Vecino informa acumulación "+sufijo,receptor);
        var reclamoAntes=jdbc.queryForMap("SELECT * FROM reclamo_institucional WHERE id=?",reclamo);

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
            payload.put("clave",clave); payload.put("registrador",registrador); payload.put("validador",validador); payload.put("lector",lector); payload.put("marca",sufijo);
            payload.put("registradorId",registradorId.toString()); payload.put("reclamo",reclamo.toString()); payload.put("inspeccion",inspeccion.toString());
            payload.put("longitud",LON); payload.put("latitud",LAT);
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-punto-critico.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lectorSalida=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(180,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lectorSalida.join(1000);
                String diagnostico=salida.toString(StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E puntos críticos: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }

        // ---------- Estado persistido ----------
        var puntos=jdbc.queryForList("SELECT id,usuario_id,reclamo_id,inspeccion_id,ST_SRID(geometria) srid,ST_X(geometria) x,ST_Y(geometria) y FROM punto_critico WHERE denominacion=?","Candidato sintético "+sufijo);
        assertThat(puntos).as("un solo candidato: el rechazo y el reintento no duplican").hasSize(1);
        var p=puntos.get(0); UUID id=(UUID)p.get("id");
        assertThat(p).containsEntry("usuario_id",registradorId).containsEntry("reclamo_id",reclamo).containsEntry("inspeccion_id",inspeccion)
            .containsEntry("srid",4326).containsEntry("x",LON).containsEntry("y",LAT);
        var observaciones=jdbc.queryForList("SELECT usuario_id,reclamo_id,inspeccion_id FROM observacion_punto_critico WHERE punto_id=?",id);
        assertThat(observaciones).as("el reintento tras 503 no duplica").hasSize(1);
        assertThat(observaciones.get(0)).containsEntry("usuario_id",registradorId).containsEntry("reclamo_id",reclamo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM validacion_punto_critico WHERE punto_id=?",Integer.class,id)).as("PENDING-10: sin validación").isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM reclamo_institucional WHERE id=?",reclamo)).as("el reclamo vinculado conserva su procedencia").isEqualTo(reclamoAntes);
    }
}
