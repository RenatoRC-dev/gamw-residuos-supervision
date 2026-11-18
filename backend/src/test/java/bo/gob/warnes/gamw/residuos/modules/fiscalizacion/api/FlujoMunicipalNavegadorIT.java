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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Navegador real (Playwright) + Vite + backend REST + PostgreSQL/PostGIS aislados, datos sintéticos, sin respuestas de negocio simuladas.
 *
 * Qué hace el NAVEGADOR (UI real): trayectoria, expediente, historial, solicitud de acción correctiva y verificación humana, y denegaciones.
 * Qué hace la API REST real desde Java (NO la interfaz): la planificación (jornada, asignación, periodos, concesiones) se siembra por SQL;
 * el conductor (A5) ejecuta por REST (INICIO, GPS desordenado con hueco, reintento duplicado, incidencia, FIN); el operador de empresa responde
 * la acción por REST y se ejercitan por REST las transiciones inválidas y las denegaciones. La categoría de verificación es una FIXTURE SINTÉTICA
 * de prueba, no una categoría oficial (PENDING-05/aprobación institucional).
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
    private void concederPeriodo(UUID usuario,UUID periodo,String... permisos) {
        // incluye_historico=true: la jornada sintética está programada antes de la concesión; sin esa bandera la política exige fechas posteriores.
        for(String p:permisos) jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),usuario,periodo,p);
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
    private UUID categoriaVerificacion(boolean habilitada,String nombre) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo_verificacion_correctiva(id,codigo,nombre,referencia_aprobacion,observaciones_obligatorias,habilitado,operador_local) VALUES (?,?,?,'referencia sintética de prueba',false,?,'prueba-sintetica')",
            id,"CAT-SINT-"+id,nombre,habilitada);
        return id;
    }

    @Test @Timeout(value=8,unit=TimeUnit.MINUTES)
    void flujoMunicipalCompletoConNavegadorRealYApisReales() throws Exception {
        UUID empresa=UUID.randomUUID(),empresaB=UUID.randomUUID(),periodo=UUID.randomUUID(),periodoB=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),
            vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),vp=UUID.randomUUID(),jornada=UUID.randomUUID(),asignacion=UUID.randomUUID();
        UUID personaConductor=persona("Conductor sintético");
        String sufijo=UUID.randomUUID().toString();
        String conductor="flujo-cond-"+sufijo,fiscal="flujo-fiscal-"+sufijo,supervisor="flujo-sup-"+sufijo,fiscalCorrectivo="flujo-fc-"+sufijo,
            soloSolicita="flujo-solo-"+sufijo,operador="flujo-op-"+sufijo,operadorOtro="flujo-opb-"+sufijo;
        usuario(personaConductor,conductor);
        UUID uFiscal=usuario(persona("Fiscalizador sintético"),fiscal);
        usuario(persona("Supervisor sin concesiones"),supervisor);
        UUID uFc=usuario(persona("Fiscalizador correctivo"),fiscalCorrectivo);
        UUID uSolo=usuario(persona("Solicitante sin verificación"),soloSolicita);
        UUID uOp=usuario(persona("Operador periodo A"),operador);
        UUID uOpB=usuario(persona("Operador otro periodo"),operadorOtro);
        conceder(uFiscal,"TRAYECTORIA_JORNADA_CONSULTAR","FISCALIZACION_CONSULTAR","JORNADAS_CONSULTAR"); // sin CORRECTIVA_/RECLAMO_/EMITIR
        conceder(uFc,"CORRECTIVA_CONSULTAR","CORRECTIVA_SOLICITAR","CORRECTIVA_VERIFICAR");
        conceder(uSolo,"CORRECTIVA_CONSULTAR","CORRECTIVA_SOLICITAR"); // puede solicitar, NO verificar
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética flujo municipal");
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaB,"Otra empresa sintética");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoB,empresaB);
        concederPeriodo(uOp,periodo,"CORRECTIVA_CONSULTAR","CORRECTIVA_RESPONDER");
        concederPeriodo(uOpB,periodoB,"CORRECTIVA_CONSULTAR","CORRECTIVA_RESPONDER");
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"FM-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"FM-"+vehiculo.toString().toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,personaConductor,empresa);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacion,jornada,vv,vp,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        String nombreCategoria="Categoría sintética de prueba "+sufijo;
        UUID categoria=categoriaVerificacion(true,nombreCategoria),categoriaRetirada=categoriaVerificacion(false,"Retirada sintética "+sufijo);

        // ---- El conductor ejecuta por la API REAL: INICIO, GPS desordenado con hueco, reintento duplicado, incidencia y FIN ----
        String tc=token(conductor);
        Instant c0=Instant.now().minus(30,ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        assertThat(api("POST","/api/jornadas/"+jornada+"/ejecucion/inicio",tc,Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",c0.toString())).statusCode()).isEqualTo(201);
        var lote=Map.of("asignacionJornadaId",asignacion,"posiciones",List.of(
            Map.of("capturaId",UUID.randomUUID(),"capturadoEn",c0.plusSeconds(300).toString(),"latitud",20.30,"longitud",-10.90,"precisionMetros",9.5),
            Map.of("capturaId",UUID.randomUUID(),"capturadoEn",c0.plusSeconds(1).toString(),"latitud",20.10,"longitud",-10.70,"precisionMetros",5.5),
            Map.of("capturaId",UUID.randomUUID(),"capturadoEn",c0.plusSeconds(2).toString(),"latitud",20.20,"longitud",-10.80,"precisionMetros",7.5)));
        assertThat(api("POST","/api/jornadas/"+jornada+"/ejecucion/posiciones-gps",tc,lote).statusCode()).isEqualTo(200);
        assertThat(api("POST","/api/jornadas/"+jornada+"/ejecucion/posiciones-gps",tc,lote).statusCode()).as("reintento duplicado idempotente").isEqualTo(200);
        String descripcionIncidencia="Incidencia sintética: contenedor bloqueado "+sufijo;
        var incidencia=new LinkedHashMap<String,Object>(Map.of("solicitudId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"ocurridaEn",c0.plusSeconds(120).toString(),
            "descripcion",descripcionIncidencia,"evidenciaDeclarada","","lugarDescripcion","Punto sintético"));
        assertThat(api("POST","/api/jornadas/"+jornada+"/incidencias",tc,incidencia).statusCode()).isEqualTo(200);
        assertThat(api("POST","/api/jornadas/"+jornada+"/incidencias",tc,incidencia).statusCode()).as("incidencia idempotente por solicitudId").isEqualTo(200);
        assertThat(api("POST","/api/jornadas/"+jornada+"/ejecucion/fin",tc,Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",c0.plusSeconds(400).toString())).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM incidencia_operativa WHERE jornada_id=?",Integer.class,jornada)).as("sin duplicar la incidencia").isEqualTo(1);
        // Transición inválida: tras FIN no se admite una nueva incidencia (registro tardío no habilitado).
        var tardia=new LinkedHashMap<>(incidencia); tardia.put("solicitudId",UUID.randomUUID()); tardia.put("ocurridaEn",c0.plusSeconds(500).toString());
        assertThat(api("POST","/api/jornadas/"+jornada+"/incidencias",tc,tardia).statusCode()).as("incidencia posterior a FIN").isEqualTo(409);

        // ---- Navegador real ----
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
                    var r=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+web+"/acceso")).header("Accept","text/html").timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding());
                    if(r.statusCode()==200) { listo=true; break; }
                } catch(java.io.IOException error) { /* arranque del proceso propio */ }
                Thread.sleep(100);
            }
            assertThat(listo).as("Vite nuevo en puerto aislado").isTrue();
            Map<String,String> env=new LinkedHashMap<>();
            env.put("GAMW_WEB_INTEGRACION","true");
            env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web);
            env.put("GAMW_WEB_TEST_FISCAL",fiscal);
            env.put("GAMW_WEB_TEST_SUPERVISOR",supervisor);
            env.put("GAMW_WEB_TEST_PASSWORD",clave);
            env.put("GAMW_WEB_TEST_JORNADA",jornada.toString());
            env.put("GAMW_WEB_TEST_FISCAL_CORRECTIVO",fiscalCorrectivo);
            env.put("GAMW_WEB_TEST_SOLO_SOLICITA",soloSolicita);
            env.put("GAMW_WEB_TEST_CATEGORIA",nombreCategoria);
            env.put("GAMW_WEB_TEST_INCIDENCIA",descripcionIncidencia);

            // 1) Trayectoria, expediente, historial y denegaciones (fiscalizador / cuenta sin concesiones)
            nodo(directorio,"e2e/flujo-municipal.test.mjs",env,Map.of());

            // 2) Fiscalizador solicita la acción correctiva en la UI
            nodo(directorio,"e2e/flujo-correctivo.test.mjs",env,Map.of("GAMW_WEB_TEST_FASE","1"));
            String tfc=token(fiscalCorrectivo);
            JsonNode expediente=json.readTree(api("GET","/api/correctivas/jornadas/"+jornada,tfc,null).body());
            assertThat(expediente.get("acciones").size()).isEqualTo(1);
            UUID accion=UUID.fromString(expediente.get("acciones").get(0).get("accion").get("id").asText());
            assertThat(expediente.get("acciones").get(0).get("accion").get("usuarioId").asText()).isEqualTo(uFc.toString());

            // 3) Separación de roles y transiciones inválidas por la API real, ANTES de la atención de la empresa
            String ts=token(soloSolicita),top=token(operador),topb=token(operadorOtro);
            Object atencion=new LinkedHashMap<>(Map.of("solicitudId",UUID.randomUUID(),"comunicadaEn",Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS).toString(),
                "observaciones","Atención sintética de la empresa","evidenciaDeclarada","","evidenciasReferenciadas",List.of()));
            Object verificacionSinAtencion=Map.of("solicitudId",UUID.randomUUID(),"respuestaId",UUID.randomUUID(),"resultadoId",categoria,"observaciones","x");
            assertThat(api("POST","/api/correctivas/acciones/"+accion+"/verificaciones",tfc,verificacionSinAtencion).statusCode()).as("verificar sin atención previa").isEqualTo(409);
            assertThat(api("POST","/api/correctivas/acciones/"+accion+"/verificaciones",ts,verificacionSinAtencion).statusCode()).as("solicitante sin CORRECTIVA_VERIFICAR").isEqualTo(403);
            assertThat(api("POST","/api/empresa/correctivas/"+accion+"/respuestas",tfc,atencion).statusCode()).as("un fiscalizador municipal no responde como empresa").isEqualTo(403);
            assertThat(api("POST","/api/empresa/correctivas/"+accion+"/respuestas",topb,atencion).statusCode()).as("operador de otro periodo/empresa").isEqualTo(403);
            assertThat(api("GET","/api/empresa/correctivas/"+accion,topb,null).statusCode()).isIn(403,404);
            assertThat(json.readTree(api("GET","/api/empresa/correctivas",topb,null).body()).get("acciones").size()).as("el otro periodo no ve la acción").isZero();
            assertThat(json.readTree(api("GET","/api/empresa/correctivas",top,null).body()).get("acciones").size()).isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM respuesta_correctiva WHERE accion_id=?",accion)).isZero();

            // 4) El operador del periodo responde (REST, NO por la UI); reintento idempotente y conflicto por contenido distinto
            var respuesta=api("POST","/api/empresa/correctivas/"+accion+"/respuestas",top,atencion);
            assertThat(respuesta.statusCode()).isEqualTo(200);
            assertThat(api("POST","/api/empresa/correctivas/"+accion+"/respuestas",top,atencion).statusCode()).as("respuesta idempotente").isEqualTo(200);
            var otraObservacion=new LinkedHashMap<>((Map<String,Object>) atencion); otraObservacion.put("observaciones","Contenido distinto con el mismo solicitudId");
            assertThat(api("POST","/api/empresa/correctivas/"+accion+"/respuestas",top,otraObservacion).statusCode()).isEqualTo(409);
            assertThat(contar("SELECT count(*) FROM respuesta_correctiva WHERE accion_id=?",accion)).isEqualTo(1);
            UUID respuestaId=UUID.fromString(json.readTree(respuesta.body()).get("id").asText());
            assertThat(api("POST","/api/correctivas/acciones/"+accion+"/verificaciones",tfc,Map.of("solicitudId",UUID.randomUUID(),"respuestaId",respuestaId,"resultadoId",categoriaRetirada,"observaciones","x")).statusCode())
                .as("categoría retirada no aprobada").isEqualTo(409);
            assertThat(api("POST","/api/correctivas/acciones/"+accion+"/verificaciones",tfc,Map.of("solicitudId",UUID.randomUUID(),"respuestaId",respuestaId,"resultadoId",UUID.randomUUID(),"observaciones","x")).statusCode())
                .as("categoría inventada").isEqualTo(409);
            assertThat(contar("SELECT count(*) FROM verificacion_correctiva WHERE accion_id=?",accion)).isZero();

            // 5) Fiscalizador verifica en la UI; cuenta solo-solicita ve el historial pero no puede verificar
            nodo(directorio,"e2e/flujo-correctivo.test.mjs",env,Map.of("GAMW_WEB_TEST_FASE","2"));
            assertThat(contar("SELECT count(*) FROM verificacion_correctiva WHERE accion_id=? AND usuario_id=? AND resultado_id=?",accion,uFc,categoria)).isEqualTo(1);
            // Segunda verificación de la misma atención: conflicto; el historial anterior se conserva.
            assertThat(api("POST","/api/correctivas/acciones/"+accion+"/verificaciones",tfc,Map.of("solicitudId",UUID.randomUUID(),"respuestaId",respuestaId,"resultadoId",categoria,"observaciones","otra")).statusCode()).isEqualTo(409);
            assertThat(contar("SELECT count(*) FROM verificacion_correctiva WHERE accion_id=?",accion)).isEqualTo(1);
            // Evidencia inmutable
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update("UPDATE verificacion_correctiva SET observaciones='alterada' WHERE accion_id=?",accion))).isNotNull();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update("DELETE FROM respuesta_correctiva WHERE accion_id=?",accion))).isNotNull();
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        // El GPS, las incidencias y las acciones no crearon ningún dictamen ni modificaron evidencia.
        assertThat(contar("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",jornada)).isZero();
        assertThat(contar("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",jornada)).isEqualTo(3);
        assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",jornada)).isEqualTo(2);
    }

    private int contar(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }

    private void nodo(Path directorio,String archivo,Map<String,String> base,Map<String,String> extra) throws Exception {
        var prueba=new ProcessBuilder("node","--test",archivo).directory(directorio.toFile()).redirectErrorStream(true);
        prueba.environment().putAll(base); prueba.environment().putAll(extra);
        Process proceso=prueba.start();
        var salida=new java.io.ByteArrayOutputStream();
        var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
        try {
            assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web "+archivo).isTrue();
            lector.join(1000);
            String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
            assertThat(proceso.exitValue()).as("E2E %s %s: %s",archivo,extra,diagnostico).isZero();
        } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
    }
}
