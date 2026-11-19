package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * CU-24 (A4) con navegador real (Playwright) + Vite + REST + PostgreSQL/PostGIS aislados y datos sintéticos.
 * La sucesora conserva periodo y versión, enlaza su antecedente y registra el motivo; no hereda recursos, eventos, GPS ni resultados.
 * Las jornadas con evidencia permanecen bloqueadas (procedimiento institucional pendiente). No se inventa política de reprogramación ni aprobación automática.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class ReprogramacionOperadorNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);
    private static final ZoneOffset BOLIVIA=ZoneOffset.ofHours(-4);

    private UUID persona(String nombres) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"RP-"+id,nombres);
        return id;
    }
    private UUID usuario(String nombre) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona("Cuenta "+nombre),nombre,codificador.encode(clave));
        return id;
    }
    private void concederPeriodo(UUID usuario,UUID periodo,String... permisos) {
        for(String p:permisos) jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,operador_local,motivo) VALUES (?,?,?,?,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),usuario,periodo,p);
    }
    private HttpResponse<String> api(String metodo,String ruta,String jwt,Object cuerpo) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(15));
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        if(cuerpo==null) b.method(metodo,HttpRequest.BodyPublishers.noBody());
        else b.header("Content-Type","application/json").method(metodo,HttpRequest.BodyPublishers.ofString(cuerpo instanceof String s?s:json.writeValueAsString(cuerpo)));
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String token(String nombre) throws Exception {
        var r=api("POST","/api/autenticacion/login",null,Map.of("nombreUsuario",nombre,"contrasena",clave));
        assertThat(r.statusCode()).isEqualTo(200);
        return json.readTree(r.body()).get("tokenAcceso").asText();
    }
    private int contar(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }
    private UUID jornada(UUID periodo,UUID version,Instant inicio) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",id,periodo,version,Timestamp.from(inicio));
        return id;
    }
    private void asignar(UUID jornada,UUID vv,UUID vp) {
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",UUID.randomUUID(),jornada,vv,vp,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
    }
    private Map<String,Object> reprogramacion(UUID solicitud,Instant inicio,String motivo) {
        var m=new LinkedHashMap<String,Object>();
        m.put("solicitudId",solicitud); m.put("inicioProgramado",inicio.toString()); m.put("motivoReprogramacion",motivo);
        return m;
    }
    private static String local(LocalDateTime l) { return l.toString().length()==16?l+":00":l.toString(); }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void operadorReprogramaPorLaInterfazConservandoLaCadenaHistoricaYBloqueandoEvidencia() throws Exception {
        UUID empresaA=UUID.randomUUID(),empresaB=UUID.randomUUID(),periodoA=UUID.randomUUID(),periodoB=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID();
        String operadorA="rep-op-"+sufijo,operadorB="rep-opb-"+sufijo,consultor="rep-con-"+sufijo;
        UUID uA=usuario(operadorA),uB=usuario(operadorB),uC=usuario(consultor);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaA,"Empresa sintética reprogramación A "+sufijo);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaB,"Empresa sintética reprogramación B "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoA,empresaA);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoB,empresaB);
        concederPeriodo(uA,periodoA,"PLANIFICACION_CONSULTAR","JORNADA_REPROGRAMAR");
        concederPeriodo(uB,periodoB,"PLANIFICACION_CONSULTAR","JORNADA_REPROGRAMAR");
        concederPeriodo(uC,periodoA,"PLANIFICACION_CONSULTAR");
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"RP-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        UUID vv=UUID.randomUUID(),vehiculo=UUID.randomUUID(),vp=UUID.randomUUID();
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"REP-"+sufijo.toUpperCase());
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresaA);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,persona("Conductor reprogramación "+sufijo),empresaA);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);

        LocalDateTime base=LocalDateTime.now(BOLIVIA).plusDays(5).withHour(7).withMinute(0).withSecond(0).withNano(0);
        Instant inicio0=base.toInstant(BOLIVIA),inicio1=base.plusDays(1).toInstant(BOLIVIA),inicio2=base.plusDays(2).toInstant(BOLIVIA);
        // J0: reprogramable (con asignación, sin evidencia). JE: con evidencia (evento de ejecución). JC: para concurrencia.
        UUID j0=jornada(periodoA,version,inicio0),je=jornada(periodoA,version,inicio0.plus(3,ChronoUnit.HOURS)),jc=jornada(periodoA,version,inicio0.plus(4,ChronoUnit.HOURS));
        asignar(j0,vv,vp);
        asignar(je,vv,vp);
        UUID asignacionEvidencia=jdbc.queryForObject("SELECT id FROM asignacion_jornada WHERE jornada_id=?",UUID.class,je);
        Instant captura=Instant.now().minusSeconds(3600);
        jdbc.update("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'INICIO',?,?,NULL)",UUID.randomUUID(),UUID.randomUUID(),je,asignacionEvidencia,Timestamp.from(captura),Timestamp.from(captura.plusSeconds(60)));
        var j0Antes=jdbc.queryForMap("SELECT periodo_servicio_id,ruta_version_id,inicio_programado,fin_programado,creado_en FROM jornada WHERE id=?",j0);
        var asignacionesJ0Antes=jdbc.queryForList("SELECT * FROM asignacion_jornada WHERE jornada_id=?",j0);
        var eventosJeAntes=jdbc.queryForList("SELECT * FROM evento_ejecucion_jornada WHERE jornada_id=?",je);

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
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-reprogramacion.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_PASSWORD",clave);
            env.put("GAMW_WEB_TEST_OPERADOR",operadorA); env.put("GAMW_WEB_TEST_OPERADOR_OTRO",operadorB); env.put("GAMW_WEB_TEST_CONSULTA",consultor);
            env.put("GAMW_WEB_TEST_J0",j0.toString()); env.put("GAMW_WEB_TEST_JE",je.toString());
            env.put("GAMW_WEB_TEST_INICIO_0",local(base)); env.put("GAMW_WEB_TEST_INICIO_1",local(base.plusDays(1))); env.put("GAMW_WEB_TEST_INICIO_2",local(base.plusDays(2)));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lector.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E reprogramación: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }

            // ---------- Cadena histórica persistida: J0 → J1 → J2 ----------
            assertThat(contar("SELECT count(*) FROM jornada WHERE periodo_servicio_id=?",periodoA)).as("J0, JE, JC + dos sucesoras (rechazo y fallo de transporte no crean jornadas)").isEqualTo(5);
            UUID j1=jdbc.queryForObject("SELECT id FROM jornada WHERE jornada_antecedente_id=?",UUID.class,j0);
            UUID j2=jdbc.queryForObject("SELECT id FROM jornada WHERE jornada_antecedente_id=?",UUID.class,j1);
            var f1=jdbc.queryForMap("SELECT periodo_servicio_id,ruta_version_id,inicio_programado,fin_programado,motivo_reprogramacion,vigente FROM jornada WHERE id=?",j1);
            var f2=jdbc.queryForMap("SELECT periodo_servicio_id,ruta_version_id,inicio_programado,fin_programado,motivo_reprogramacion,vigente FROM jornada WHERE id=?",j2);
            for(var f:List.of(f1,f2)) { assertThat(f.get("periodo_servicio_id")).as("mismo periodo").isEqualTo(periodoA); assertThat(f.get("ruta_version_id")).as("misma versión de ruta").isEqualTo(version); assertThat(f.get("fin_programado")).isNull(); }
            assertThat(((Timestamp) f1.get("inicio_programado")).toInstant()).isEqualTo(inicio1);
            assertThat(((Timestamp) f2.get("inicio_programado")).toInstant()).isEqualTo(inicio2);
            assertThat(f1.get("motivo_reprogramacion")).isEqualTo("Motivo sintético 1: reprogramación solicitada por la empresa.");
            assertThat(f2.get("motivo_reprogramacion")).isEqualTo("Motivo sintético 2: nueva reprogramación.");
            assertThat(f1.get("vigente")).as("la intermedia queda como antecedente").isEqualTo(false);
            assertThat(f2.get("vigente")).as("solo la última es vigente").isEqualTo(true);
            assertThat(jdbc.queryForObject("SELECT vigente FROM jornada WHERE id=?",Boolean.class,j0)).isFalse();
            // El antecedente original conserva sus referencias y su asignación.
            var j0Despues=jdbc.queryForMap("SELECT periodo_servicio_id,ruta_version_id,inicio_programado,fin_programado,creado_en FROM jornada WHERE id=?",j0);
            assertThat(j0Despues).as("el original no cambia (salvo quedar como antecedente)").isEqualTo(j0Antes);
            assertThat(jdbc.queryForList("SELECT * FROM asignacion_jornada WHERE jornada_id=?",j0)).as("asignaciones del original intactas").isEqualTo(asignacionesJ0Antes);
            // Sin herencia: ni recursos, ni eventos, ni GPS, ni resultados, ni acciones correctivas.
            for(UUID s:List.of(j1,j2)) {
                assertThat(contar("SELECT count(*) FROM asignacion_jornada WHERE jornada_id=?",s)).as("sin recursos heredados").isZero();
                assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",s)).isZero();
                assertThat(contar("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",s)).isZero();
                assertThat(contar("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",s)).isZero();
                assertThat(contar("SELECT count(*) FROM accion_correctiva WHERE jornada_id=?",s)).isZero();
            }
            assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",j0)+contar("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",j0)).isZero();
            // Registro de autoría por la cuenta autenticada y con su concesión de periodo.
            assertThat(contar("SELECT count(*) FROM reprogramacion_autorizada WHERE usuario_id=? AND concesion_periodo_id IS NOT NULL",uA)).isEqualTo(2);
            assertThat(contar("SELECT count(*) FROM reprogramacion_autorizada WHERE jornada_antecedente_id=? AND jornada_sucesora_id=?",j0,j1)).isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM reprogramacion_autorizada WHERE jornada_antecedente_id=? AND jornada_sucesora_id=?",j1,j2)).isEqualTo(1);
            // Referencias inmutables.
            assertThat(catchThrowable(() -> jdbc.update("UPDATE jornada SET periodo_servicio_id=? WHERE id=?",periodoB,j1))).isNotNull();
            assertThat(catchThrowable(() -> jdbc.update("UPDATE jornada SET ruta_version_id=? WHERE id=?",UUID.randomUUID(),j1))).isNotNull();
            // Evidencia del bloqueo intacta y sin sucesora.
            assertThat(jdbc.queryForList("SELECT * FROM evento_ejecucion_jornada WHERE jornada_id=?",je)).isEqualTo(eventosJeAntes);
            assertThat(contar("SELECT count(*) FROM jornada WHERE jornada_antecedente_id=?",je)).isZero();

            // ---------- REST real: motivo, fechas, actores, duplicados, conflictos, evidencia y concurrencia ----------
            String ta=token(operadorA),tb=token(operadorB),tc=token(consultor);
            String ruta2="/api/empresa/jornadas/"+j2+"/reprogramaciones";
            Instant futuro=inicio2.plus(1,ChronoUnit.DAYS);
            assertThat(api("POST",ruta2,null,reprogramacion(UUID.randomUUID(),futuro,"x")).statusCode()).as("sin autenticar: denegado por defecto").isEqualTo(403);
            assertThat(api("POST",ruta2,tb,reprogramacion(UUID.randomUUID(),futuro,"x")).statusCode()).as("operador de otro periodo").isEqualTo(403);
            assertThat(api("POST",ruta2,tc,reprogramacion(UUID.randomUUID(),futuro,"x")).statusCode()).as("consultar no habilita reprogramar").isEqualTo(403);
            assertThat(api("GET","/api/empresa/jornadas/"+j2,tb,null).statusCode()).isEqualTo(403);
            var sinMotivo=reprogramacion(UUID.randomUUID(),futuro,"x"); sinMotivo.remove("motivoReprogramacion");
            assertThat(api("POST",ruta2,ta,sinMotivo).statusCode()).as("sin motivo").isEqualTo(400);
            assertThat(api("POST",ruta2,ta,reprogramacion(UUID.randomUUID(),futuro,"   ")).statusCode()).as("motivo en blanco").isEqualTo(400);
            var sinSolicitud=reprogramacion(UUID.randomUUID(),futuro,"x"); sinSolicitud.remove("solicitudId");
            assertThat(api("POST",ruta2,ta,sinSolicitud).statusCode()).as("sin solicitud").isEqualTo(400);
            var sinInicio=reprogramacion(UUID.randomUUID(),futuro,"x"); sinInicio.remove("inicioProgramado");
            assertThat(api("POST",ruta2,ta,sinInicio).statusCode()).as("sin inicio").isEqualTo(400);
            var conPeriodo=reprogramacion(UUID.randomUUID(),futuro,"x"); conPeriodo.put("periodoServicioId",periodoB);
            assertThat(api("POST",ruta2,ta,conPeriodo).statusCode()).as("el periodo no viene del cliente").isEqualTo(400);
            var conVersion=reprogramacion(UUID.randomUUID(),futuro,"x"); conVersion.put("rutaVersionId",UUID.randomUUID());
            assertThat(api("POST",ruta2,ta,conVersion).statusCode()).as("la versión no viene del cliente").isEqualTo(400);
            var finAntes=reprogramacion(UUID.randomUUID(),futuro,"x"); finAntes.put("finProgramado",futuro.toString());
            assertThat(api("POST",ruta2,ta,finAntes).statusCode()).as("fin no posterior al inicio").isEqualTo(400);
            assertThat(api("POST",ruta2,ta,reprogramacion(UUID.randomUUID(),Instant.parse("2040-01-01T12:00:00Z"),"x")).statusCode()).as("fuera del periodo").isEqualTo(400);
            assertThat(api("POST",ruta2,ta,reprogramacion(UUID.randomUUID(),Instant.now().minus(2,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS),"x")).statusCode()).as("anterior a la concesión sin histórico").isEqualTo(403);
            var nanos=reprogramacion(UUID.randomUUID(),futuro,"x"); nanos.put("inicioProgramado","2026-12-01T12:00:00.1234567Z");
            assertThat(api("POST",ruta2,ta,nanos).statusCode()).as("precisión mayor a microsegundos").isEqualTo(400);
            assertThat(api("POST",ruta2,ta,reprogramacion(UUID.randomUUID(),futuro,"m".repeat(10001))).statusCode()).as("motivo demasiado largo").isEqualTo(400);
            // Duplicado idempotente de la solicitud ya confirmada (J1 → J2) y conflicto con otro contenido / otro actor.
            UUID solicitud12=jdbc.queryForObject("SELECT solicitud_id FROM reprogramacion_autorizada WHERE jornada_antecedente_id=?",UUID.class,j1);
            var repetida=api("POST","/api/empresa/jornadas/"+j1+"/reprogramaciones",ta,reprogramacion(solicitud12,inicio2,"Motivo sintético 2: nueva reprogramación."));
            assertThat(repetida.statusCode()).as("duplicado idempotente: %s",repetida.body()).isEqualTo(200);
            assertThat(json.readTree(repetida.body()).get("id").asText()).isEqualTo(j2.toString());
            assertThat(api("POST","/api/empresa/jornadas/"+j1+"/reprogramaciones",ta,reprogramacion(solicitud12,inicio2,"Motivo distinto")).statusCode()).as("misma solicitud, otro contenido").isEqualTo(409);
            assertThat(api("POST","/api/empresa/jornadas/"+j1+"/reprogramaciones",ta,reprogramacion(solicitud12,inicio2.plusSeconds(60),"Motivo sintético 2: nueva reprogramación.")).statusCode()).as("misma solicitud, otra fecha").isEqualTo(409);
            assertThat(api("POST","/api/empresa/jornadas/"+j1+"/reprogramaciones",tb,reprogramacion(solicitud12,inicio2,"Motivo sintético 2: nueva reprogramación.")).statusCode()).as("solicitud ajena").isEqualTo(403);
            // Un antecedente ya sustituido no admite otra sucesora (solicitud nueva).
            assertThat(api("POST","/api/empresa/jornadas/"+j0+"/reprogramaciones",ta,reprogramacion(UUID.randomUUID(),futuro,"Segunda sucesora del mismo antecedente")).statusCode()).as("antecedente ya reprogramado").isEqualTo(409);
            // Jornada con evidencia: bloqueada, sin trasladar nada.
            assertThat(api("POST","/api/empresa/jornadas/"+je+"/reprogramaciones",ta,reprogramacion(UUID.randomUUID(),futuro,"Intento sobre jornada con evidencia")).statusCode()).as("con evidencia").isEqualTo(409);
            assertThat(contar("SELECT count(*) FROM jornada WHERE jornada_antecedente_id=?",je)).isZero();
            assertThat(jdbc.queryForList("SELECT * FROM evento_ejecucion_jornada WHERE jornada_id=?",je)).isEqualTo(eventosJeAntes);
            assertThat(contar("SELECT count(*) FROM jornada WHERE periodo_servicio_id=?",periodoA)).as("ningún intento inválido crea jornada").isEqualTo(5);
            // Base compartida de la JVM: se cuentan solo las reprogramaciones de los fixtures de esta prueba.
            assertThat(contar("SELECT count(*) FROM reprogramacion_autorizada r JOIN jornada j ON j.id=r.jornada_antecedente_id WHERE j.periodo_servicio_id=?",periodoA))
                .as("ningún intento inválido registra reprogramaciones").isEqualTo(2);

            // Concurrencia: dos sucesoras distintas para el mismo antecedente → exactamente una.
            String rutaC="/api/empresa/jornadas/"+jc+"/reprogramaciones";
            var c1=CompletableFuture.supplyAsync(() -> { try { return api("POST",rutaC,ta,reprogramacion(UUID.randomUUID(),futuro,"Concurrente 1")).statusCode(); } catch(Exception e) { throw new IllegalStateException(e); } });
            var c2=CompletableFuture.supplyAsync(() -> { try { return api("POST",rutaC,ta,reprogramacion(UUID.randomUUID(),futuro.plusSeconds(3600),"Concurrente 2")).statusCode(); } catch(Exception e) { throw new IllegalStateException(e); } });
            List<Integer> estados=List.of(c1.get(30,TimeUnit.SECONDS),c2.get(30,TimeUnit.SECONDS));
            assertThat(estados).as("concurrentes distintas: una gana y la otra se rechaza sin error de servidor").containsExactlyInAnyOrder(200,409);
            assertThat(contar("SELECT count(*) FROM jornada WHERE jornada_antecedente_id=?",jc)).isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM reprogramacion_autorizada WHERE jornada_antecedente_id=?",jc)).isEqualTo(1);
            // Concurrencia con la MISMA solicitud (respuesta incierta duplicada): nunca 5xx y una sola sucesora; luego el reintento devuelve la misma.
            UUID jd=jornada(periodoA,version,inicio0.plus(5,ChronoUnit.HOURS));
            String rutaD="/api/empresa/jornadas/"+jd+"/reprogramaciones";
            UUID mismaSolicitud=UUID.randomUUID();
            var d1=CompletableFuture.supplyAsync(() -> { try { return api("POST",rutaD,ta,reprogramacion(mismaSolicitud,futuro,"Misma solicitud")).statusCode(); } catch(Exception e) { throw new IllegalStateException(e); } });
            var d2=CompletableFuture.supplyAsync(() -> { try { return api("POST",rutaD,ta,reprogramacion(mismaSolicitud,futuro,"Misma solicitud")).statusCode(); } catch(Exception e) { throw new IllegalStateException(e); } });
            List<Integer> estadosD=List.of(d1.get(30,TimeUnit.SECONDS),d2.get(30,TimeUnit.SECONDS));
            assertThat(estadosD).as("misma solicitud concurrente").allMatch(s -> s==200 || s==409);
            assertThat(estadosD).contains(200);
            assertThat(contar("SELECT count(*) FROM jornada WHERE jornada_antecedente_id=?",jd)).isEqualTo(1);
            var reintento=api("POST",rutaD,ta,reprogramacion(mismaSolicitud,futuro,"Misma solicitud"));
            assertThat(reintento.statusCode()).as("el reintento posterior es idempotente").isEqualTo(200);
            assertThat(json.readTree(reintento.body()).get("id").asText()).isEqualTo(jdbc.queryForObject("SELECT id FROM jornada WHERE jornada_antecedente_id=?",UUID.class,jd).toString());
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
    }
}
