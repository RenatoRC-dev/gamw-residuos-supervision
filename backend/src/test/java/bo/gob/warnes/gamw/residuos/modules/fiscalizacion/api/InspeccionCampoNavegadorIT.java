package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.*;
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
 * CU-20 con navegador real + REST real + PostgreSQL/PostGIS aislados y JWT: registro de inspección (fecha futura rechazada, 503 sin falso
 * éxito, reintento con la misma solicitud), sin resultado de fiscalización, y consulta de solo lectura. Fotografías deshabilitadas (PENDING-09).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class InspeccionCampoNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);
    private static final ZoneOffset BOLIVIA=ZoneOffset.ofHours(-4);

    private UUID usuario(String nombre,String... permisos) {
        UUID id=UUID.randomUUID(),persona=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"FCN-"+persona,"Cuenta "+nombre);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private static String local(LocalDateTime t) { return t.truncatedTo(ChronoUnit.SECONDS).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")); }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void registraLaInspeccionConRechazoYReintentoYElLectorLaConsultaSinEscribir() throws Exception {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada=UUID.randomUUID();
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-20 "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"FCN-"+sufijo,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        String inspector="fc-ins-"+sufijo,lector="fc-lec-"+sufijo;
        UUID inspectorId=usuario(inspector,"INSPECCION_REGISTRAR","FISCALIZACION_CONSULTAR");
        usuario(lector,"FISCALIZACION_CONSULTAR");
        LocalDateTime ahora=LocalDateTime.now(BOLIVIA);
        String futura=local(ahora.plusHours(3)),valida=local(ahora.minusMinutes(20)),observacion="Observación de campo sintética CU-20 "+sufijo;

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
            payload.put("clave",clave); payload.put("inspector",inspector); payload.put("inspectorId",inspectorId.toString()); payload.put("lector",lector);
            payload.put("jornada",jornada.toString()); payload.put("fechaFutura",futura); payload.put("fechaValida",valida); payload.put("observacion",observacion);
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-inspeccion-campo.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lectorSalida=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(180,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lectorSalida.join(1000);
                String diagnostico=salida.toString(StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E inspección de campo: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        // ---------- Estado persistido ----------
        var filas=jdbc.queryForList("SELECT inspector_usuario_id,realizada_en,registrada_en,observaciones,ST_X(geometria) lon,ST_Y(geometria) lat FROM inspeccion WHERE jornada_id=?",jornada);
        assertThat(filas).as("rechazo, 503 y reintento dejan UNA inspección").hasSize(1);
        var f=filas.get(0);
        assertThat(f.get("inspector_usuario_id")).isEqualTo(inspectorId);
        assertThat(f.get("observaciones")).isEqualTo(observacion);
        Instant realizada=((Timestamp)f.get("realizada_en")).toInstant();
        assertThat(realizada).as("fecha declarada en Bolivia (UTC−04:00)").isEqualTo(LocalDateTime.parse(valida).toInstant(BOLIVIA));
        assertThat(((Timestamp)f.get("registrada_en")).toInstant()).isAfter(realizada);
        assertThat(((Number)f.get("lon")).doubleValue()).isEqualTo(-10.75);
        assertThat(((Number)f.get("lat")).doubleValue()).isEqualTo(20.25);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidencia_inspeccion e JOIN inspeccion i ON i.id=e.inspeccion_id WHERE i.jornada_id=?",Integer.class,jornada)).isZero();
    }
}
