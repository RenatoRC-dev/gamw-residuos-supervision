package bo.gob.warnes.gamw.residuos.modules.correctivas.api;

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
 * CU-23 con navegador real + REST real + PostgreSQL/PostGIS aislados y JWT. La cadena previa se crea por la API real (A5 incidencia, A3 solicitud,
 * A4 atención); el navegador verifica (409 y 503 simulados en la red para la interfaz; el 409 y el 503 reales del backend están en
 * VerificacionCorrectivaIT). Categoría de verificación SINTÉTICA de prueba (PENDING-07), nunca oficial.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class VerificacionCorrectivaNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private UUID usuario(UUID persona,String nombre,String... permisos) {
        if(persona==null) { persona=UUID.randomUUID(); jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"VCN-"+persona,"Cuenta "+nombre); }
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private String token(String nombre) throws Exception { return json.readTree(api("/api/autenticacion/login",null,Map.of("nombreUsuario",nombre,"contrasena",clave)).body()).get("tokenAcceso").asText(); }
    private HttpResponse<String> api(String ruta,String jwt,Object cuerpo) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String exigir(HttpResponse<String> r,int estado) { assertThat(r.statusCode()).as(r.body()).isEqualTo(estado); return r.body(); }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void verificaLaAtencionConRechazoCorregibleYReintentoSeguroEnElNavegador() throws Exception {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),
            persona=UUID.randomUUID(),vp=UUID.randomUUID(),jornada=UUID.randomUUID(),asignacion=UUID.randomUUID(),categoria=UUID.randomUUID();
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-23 "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"VCN-"+sufijo,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"VCN-"+sufijo.toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"VCN-"+persona,"Conductor sintético");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,persona,empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        Instant inicio=Instant.now().minus(40,ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacion,jornada,vv,vp,Timestamp.from(inicio.minus(1,ChronoUnit.HOURS)));
        String conductor="vc-cond-"+sufijo,solicitante="vc-sol-"+sufijo,verificador="vc-ver-"+sufijo,lector="vc-lec-"+sufijo,operador="vc-op-"+sufijo;
        usuario(persona,conductor);
        UUID solicitanteId=usuario(null,solicitante,"CORRECTIVA_CONSULTAR","CORRECTIVA_SOLICITAR");
        UUID verificadorId=usuario(null,verificador,"CORRECTIVA_CONSULTAR","CORRECTIVA_VERIFICAR");
        usuario(null,lector,"CORRECTIVA_CONSULTAR");
        UUID operadorId=usuario(null,operador);
        for(String p:List.of("CORRECTIVA_CONSULTAR","CORRECTIVA_RESPONDER"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),operadorId,periodo,p);
        String nombreCategoria="Categoría sintética de prueba "+sufijo;
        jdbc.update("INSERT INTO catalogo_verificacion_correctiva(id,codigo,nombre,referencia_aprobacion,observaciones_obligatorias,habilitado,operador_local) VALUES (?,?,?,'referencia sintética de prueba',true,true,'prueba-sintetica')",
            categoria,"CAT-SINT-"+categoria,nombreCategoria);
        // Cadena previa por la API REAL: A5 incidencia, A3 solicitud, A4 atención.
        String tc=token(conductor),ts=token(solicitante),top=token(operador);
        exigir(api("/api/jornadas/"+jornada+"/ejecucion/inicio",tc,Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",inicio.toString())),201);
        UUID incidencia=UUID.fromString(json.readTree(exigir(api("/api/jornadas/"+jornada+"/incidencias",tc,Map.of("solicitudId",UUID.randomUUID(),"asignacionJornadaId",asignacion,
            "ocurridaEn",inicio.plusSeconds(300).toString(),"descripcion","Contenedor bloqueado "+sufijo,"evidenciaDeclarada","","lugarDescripcion","Tramo sintético")),200)).get("id").asText());
        exigir(api("/api/jornadas/"+jornada+"/ejecucion/fin",tc,Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",inicio.plusSeconds(1200).toString())),201);
        String descripcionAccion="Retirar el bloqueo "+sufijo;
        var nueva=new LinkedHashMap<String,Object>(); nueva.put("solicitudId",UUID.randomUUID()); nueva.put("incidenciaId",incidencia); nueva.put("inspeccionId",null); nueva.put("fiscalizacionId",null); nueva.put("descripcion",descripcionAccion);
        UUID accion=UUID.fromString(json.readTree(exigir(api("/api/correctivas/jornadas/"+jornada+"/acciones",ts,nueva),200)).get("accion").get("id").asText());
        UUID respuesta=UUID.fromString(json.readTree(exigir(api("/api/empresa/correctivas/"+accion+"/respuestas",top,Map.of("solicitudId",UUID.randomUUID(),
            "comunicadaEn",Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS).toString(),"observaciones","Bloqueo retirado "+sufijo,"evidenciaDeclarada","","evidenciasReferenciadas",List.of())),200)).get("id").asText());
        var antes=jdbc.queryForMap("SELECT a.descripcion,a.creada_en,a.usuario_id,r.descripcion rd,r.comunicada_en,r.registrada_en,r.usuario_id ru FROM accion_correctiva a JOIN respuesta_correctiva r ON r.accion_id=a.id WHERE a.id=?",accion);

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
            payload.put("clave",clave); payload.put("verificador",verificador); payload.put("lector",lector); payload.put("operador",operador); payload.put("marca",sufijo);
            payload.put("jornada",jornada.toString()); payload.put("accion",accion.toString()); payload.put("respuesta",respuesta.toString()); payload.put("categoria",categoria.toString());
            payload.put("nombreCategoria",nombreCategoria); payload.put("descripcionAccion",descripcionAccion); payload.put("solicitanteId",solicitanteId.toString());
            payload.put("operadorId",operadorId.toString()); payload.put("verificadorId",verificadorId.toString());
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-verificacion-correctiva.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lectorSalida=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(180,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lectorSalida.join(1000);
                String diagnostico=salida.toString(StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E verificación correctiva: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        // ---------- Estado persistido ----------
        var v=jdbc.queryForList("SELECT usuario_id,respuesta_id,resultado_id,observaciones FROM verificacion_correctiva WHERE accion_id=?",accion);
        assertThat(v).as("409 y 503 no dejan rastro; el reintento confirma UNA verificación").hasSize(1);
        assertThat(v.get(0)).containsEntry("usuario_id",verificadorId).containsEntry("respuesta_id",respuesta).containsEntry("resultado_id",categoria);
        assertThat(jdbc.queryForMap("SELECT a.descripcion,a.creada_en,a.usuario_id,r.descripcion rd,r.comunicada_en,r.registrada_en,r.usuario_id ru FROM accion_correctiva a JOIN respuesta_correctiva r ON r.accion_id=a.id WHERE a.id=?",accion))
            .as("solicitud y atención originales intactas").isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();
    }
}
