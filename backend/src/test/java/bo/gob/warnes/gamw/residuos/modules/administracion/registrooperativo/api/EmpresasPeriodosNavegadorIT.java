package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
 * CU-04 / CU-05 (A1) con navegador real + REST real + PostgreSQL/PostGIS aislados, JWT y permisos municipales explícitos EMPRESAS_* y
 * PERIODOS_*. Sin política de solapamiento inventada, sin permisos A4 y sin decisión D1. Base compartida: solo se verifican los
 * identificadores sembrados aquí.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class EmpresasPeriodosNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private UUID persona(String nombres) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"EP-"+id,nombres);
        return id;
    }
    private UUID usuario(String nombre,String... permisos) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona("Cuenta "+nombre),nombre,codificador.encode(clave));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
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
    private int contar(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void administradorGestionaEmpresasYPeriodosPorLaInterfazConservandoLaHistoria() throws Exception {
        String admin="emp-adm-"+sufijo,lector="emp-lec-"+sufijo,sinPermiso="emp-sin-"+sufijo,operador="emp-op-"+sufijo;
        usuario(admin,"EMPRESAS_CONSULTAR","EMPRESAS_GESTIONAR","PERIODOS_CONSULTAR","PERIODOS_GESTIONAR");
        usuario(lector,"EMPRESAS_CONSULTAR","PERIODOS_CONSULTAR");
        usuario(sinPermiso);
        UUID uOperador=usuario(operador);
        // Empresa histórica con un periodo que ya tiene una jornada programada (15/06/2026 08:00, Bolivia).
        UUID historica=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada=UUID.randomUUID();
        String razonHistorica="Histórica sintética "+sufijo;
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",historica,razonHistorica);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2026-12-31',true,now(),now())",periodo,historica);
        for(String p:List.of("PLANIFICACION_CONSULTAR","JORNADA_PROGRAMAR"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),uOperador,periodo,p);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"EP-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.1 -17.7,-63.1 -17.6)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",jornada,periodo,version,Timestamp.from(Instant.parse("2026-06-15T12:00:00Z")));
        String nit="NIT-"+sufijo.toUpperCase(),nueva="Nueva sintética "+sufijo,duplicada="Duplicada sintética "+sufijo;

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
            var payload=Map.of("clave",clave,"admin",admin,"lector",lector,"sinPermiso",sinPermiso,"historica",razonHistorica,"nueva",nueva,"duplicada",duplicada,"nit",nit);
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-empresas-periodos.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lector2=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lector2.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E empresas/periodos: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }

        // ---------- Estado persistido ----------
        UUID nuevaId=jdbc.queryForObject("SELECT id FROM empresa_operadora WHERE razon_social=?",UUID.class,nueva);
        assertThat(jdbc.queryForMap("SELECT nit,nombre_comercial,activo FROM empresa_operadora WHERE id=?",nuevaId))
            .containsEntry("nit",nit).containsEntry("nombre_comercial","Comercial corregido").containsEntry("activo",true);
        assertThat(contar("SELECT count(*) FROM empresa_operadora WHERE razon_social=?",duplicada)).as("NIT duplicado no persistido").isZero();
        assertThat(contar("SELECT count(*) FROM empresa_operadora WHERE nit=?",nit)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM periodo_servicio WHERE empresa_operadora_id=?",nuevaId)).as("periodos coexistentes").isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM periodo_servicio WHERE empresa_operadora_id=? AND NOT activo",nuevaId)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM empresa_operadora WHERE razon_social LIKE 'Intento lector%'")).as("el lector no crea").isZero();
        // La empresa histórica sigue con su identidad, su periodo y su jornada; solo cambió el fin compatible.
        assertThat(jdbc.queryForObject("SELECT activo FROM empresa_operadora WHERE id=?",Boolean.class,historica)).isFalse();
        assertThat(jdbc.queryForMap("SELECT fecha_inicio::text AS i,fecha_fin::text AS f,empresa_operadora_id FROM periodo_servicio WHERE id=?",periodo))
            .containsEntry("i","2026-01-01").containsEntry("f","2027-06-30").containsEntry("empresa_operadora_id",historica);
        assertThat(jdbc.queryForObject("SELECT periodo_servicio_id FROM jornada WHERE id=?",UUID.class,jornada)).as("la jornada conserva su periodo").isEqualTo(periodo);

        // ---------- REST real: autorización, validación, conflicto e historia ----------
        String ta=token(admin),tl=token(lector),ts=token(sinPermiso),to=token(operador);
        String rutaPeriodo="/api/empresas-operadoras/"+historica+"/periodos/"+periodo;
        assertThat(api("PUT",rutaPeriodo,ta,Map.of("fechaInicio","2026-07-01","fechaFin","2027-06-30")).statusCode()).as("inicio posterior a la jornada: incompatible").isEqualTo(409);
        assertThat(api("PUT",rutaPeriodo,ta,Map.of("fechaInicio","2026-06-15","fechaFin","2026-06-15")).statusCode()).as("límite exacto (fecha de servicio en Bolivia): compatible").isEqualTo(200);
        assertThat(api("PUT",rutaPeriodo,ta,Map.of("fechaInicio","2026-06-16","fechaFin","2026-06-20")).statusCode()).isEqualTo(409);
        assertThat(api("PUT",rutaPeriodo,ta,Map.of("fechaInicio","2026-01-01","fechaFin","2026-12-31")).statusCode()).isEqualTo(200);
        assertThat(api("PUT",rutaPeriodo,ta,Map.of("fechaInicio","2026-12-31","fechaFin","2026-01-01")).statusCode()).as("fin anterior al inicio").isEqualTo(400);
        assertThat(api("POST","/api/empresas-operadoras/"+UUID.randomUUID()+"/periodos",ta,Map.of("fechaInicio","2026-01-01","fechaFin","2026-01-02")).statusCode()).as("empresa inexistente").isEqualTo(404);
        assertThat(api("PUT","/api/empresas-operadoras/"+nuevaId+"/periodos/"+periodo,ta,Map.of("fechaInicio","2026-01-01","fechaFin","2026-12-31")).statusCode()).as("periodo de otra empresa").isEqualTo(404);
        assertThat(api("POST","/api/empresas-operadoras",ta,Map.of("razonSocial"," ")).statusCode()).as("razón social obligatoria").isEqualTo(400);
        assertThat(api("POST","/api/empresas-operadoras",ta,Map.of("razonSocial","Otra "+sufijo,"nit",nit)).statusCode()).as("NIT único").isEqualTo(409);
        // Un identificador enviado por el cliente no se adopta: la identidad la genera el servidor.
        UUID impuesto=UUID.randomUUID();
        var conId=api("POST","/api/empresas-operadoras",ta,Map.of("razonSocial","Campo extra "+sufijo,"id",impuesto));
        assertThat(conId.statusCode()).isEqualTo(201);
        assertThat(json.readTree(conId.body()).get("id").asText()).isNotEqualTo(impuesto.toString());
        assertThat(contar("SELECT count(*) FROM empresa_operadora WHERE id=?",impuesto)).isZero();
        assertThat(api("GET","/api/empresas-operadoras/"+historica+"/periodos",ta,null).statusCode()).as("empresa inactiva: historia consultable").isEqualTo(200);
        assertThat(api("DELETE","/api/empresas-operadoras/"+nuevaId,ta,null).statusCode()).as("sin borrado físico").isIn(403,405);
        assertThat(api("GET","/api/empresas-operadoras",null,null).statusCode()).isIn(401,403);
        assertThat(api("GET","/api/empresas-operadoras",ts,null).statusCode()).isEqualTo(403);
        assertThat(api("GET","/api/empresas-operadoras",to,null).statusCode()).as("A4 sin permiso municipal").isEqualTo(403);
        assertThat(api("PATCH","/api/empresas-operadoras/"+nuevaId+"/estado",tl,Map.of("activo",false)).statusCode()).as("lector no gestiona").isEqualTo(403);
        assertThat(api("POST","/api/empresas-operadoras/"+nuevaId+"/periodos",tl,Map.of("fechaInicio","2026-01-01","fechaFin","2026-01-02")).statusCode()).isEqualTo(403);
        assertThat(api("GET","/api/empresas-operadoras/"+nuevaId+"/periodos",tl,null).statusCode()).isEqualTo(200);
        assertThat(contar("SELECT count(*) FROM periodo_servicio WHERE empresa_operadora_id=?",nuevaId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT periodo_servicio_id FROM jornada WHERE id=?",UUID.class,jornada)).isEqualTo(periodo);
    }
}
