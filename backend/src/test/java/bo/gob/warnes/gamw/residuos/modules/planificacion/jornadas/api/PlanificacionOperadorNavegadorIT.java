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
 * CU-11 / CU-12 (A4) con navegador real (Playwright) + Vite + REST + PostgreSQL/PostGIS aislados y datos sintéticos.
 * UI real: programar y asignar/reasignar por periodo concedido (JWT + concesiones de periodo). REST real desde Java: denegaciones, periodos inválidos,
 * recursos incompatibles, conflictos y acceso entre operadores. Empresas, periodos, rutas, vínculos y concesiones se SIEMBRAN por SQL (no por la UI).
 * Programar/asignar no implica ejecución ni cumplimiento. No se inventan calendarios de disponibilidad ni horarios oficiales. CU-24 fuera de este corte.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class PlanificacionOperadorNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private UUID persona(String nombres) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"PL-"+id,nombres);
        return id;
    }
    private UUID usuario(String nombre) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona("Cuenta "+nombre),nombre,codificador.encode(clave));
        return id;
    }
    private UUID vehiculo(String placa,boolean activo) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,?,now(),now())",id,placa,activo);
        return id;
    }
    private UUID vinculoVehiculo(UUID vehiculo,UUID empresa,String fin) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',"+(fin==null?"NULL":"DATE '"+fin+"'")+",true,now(),now())",id,vehiculo,empresa);
        return id;
    }
    private UUID vinculoPersona(UUID persona,UUID empresa) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",id,persona,empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",id);
        return id;
    }
    private void concederPeriodo(UUID usuario,UUID periodo,String... permisos) {
        for(String p:permisos) jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,operador_local,motivo) VALUES (?,?,?,?,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),usuario,periodo,p);
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
    void operadorProgramaYAsignaPorLaInterfazConAislamientoDePeriodoEIdempotencia() throws Exception {
        UUID empresaA=UUID.randomUUID(),empresaB=UUID.randomUUID(),periodoA=UUID.randomUUID(),periodoB=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID();
        String operadorA="plan-op-"+sufijo,operadorB="plan-opb-"+sufijo;
        UUID uA=usuario(operadorA),uB=usuario(operadorB);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaA,"Empresa sintética planificación A "+sufijo);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaB,"Empresa sintética planificación B "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoA,empresaA);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoB,empresaB);
        concederPeriodo(uA,periodoA,"PLANIFICACION_CONSULTAR","JORNADA_PROGRAMAR","JORNADA_ASIGNAR");
        concederPeriodo(uB,periodoB,"PLANIFICACION_CONSULTAR","JORNADA_PROGRAMAR","JORNADA_ASIGNAR");
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"PL-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        // Recursos de la empresa A (elegibles y no elegibles) y de la empresa B.
        String placa1="PLAN1-"+sufijo.toUpperCase(),placa2="PLAN2-"+sufijo.toUpperCase(),placaB="PLANB-"+sufijo.toUpperCase(),placaInactiva="PLANI-"+sufijo.toUpperCase(),placaVencida="PLANV-"+sufijo.toUpperCase();
        UUID vv1=vinculoVehiculo(vehiculo(placa1,true),empresaA,null),vv2=vinculoVehiculo(vehiculo(placa2,true),empresaA,null);
        UUID vvB=vinculoVehiculo(vehiculo(placaB,true),empresaB,null);
        vinculoVehiculo(vehiculo(placaInactiva,false),empresaA,null);            // vehículo inactivo
        vinculoVehiculo(vehiculo(placaVencida,true),empresaA,"2026-06-30");     // vínculo vencido antes de la fecha programada
        String persona1="Conductor Uno "+sufijo,persona2="Conductor Dos "+sufijo,personaB="Conductor Ajeno "+sufijo;
        UUID vp1=vinculoPersona(persona(persona1),empresaA),vp2=vinculoPersona(persona(persona2),empresaA),vpB=vinculoPersona(persona(personaB),empresaB);

        LocalDateTime local=LocalDateTime.now(ZoneOffset.ofHours(-4)).plusDays(5).withHour(7).withMinute(0).withSecond(0).withNano(0);
        Instant inicio=local.toInstant(ZoneOffset.ofHours(-4));

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
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-planificacion.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_PASSWORD",clave);
            env.put("GAMW_WEB_TEST_OPERADOR",operadorA); env.put("GAMW_WEB_TEST_OPERADOR_OTRO",operadorB);
            env.put("GAMW_WEB_TEST_PERIODO",periodoA.toString()); env.put("GAMW_WEB_TEST_VERSION",version.toString());
            env.put("GAMW_WEB_TEST_INICIO",local.toString().length()==16?local+":00":local.toString());
            env.put("GAMW_WEB_TEST_PLACA_1",placa1); env.put("GAMW_WEB_TEST_PLACA_2",placa2);
            env.put("GAMW_WEB_TEST_PERSONA_1",persona1); env.put("GAMW_WEB_TEST_PERSONA_2",persona2);
            env.put("GAMW_WEB_TEST_PLACA_OTRA_EMPRESA",placaB); env.put("GAMW_WEB_TEST_PLACA_INACTIVA",placaInactiva);
            env.put("GAMW_WEB_TEST_PLACA_VENCIDA",placaVencida); env.put("GAMW_WEB_TEST_PERSONA_OTRA_EMPRESA",personaB);
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lector.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E planificación: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }

            // ---------- Estado persistido tras la interfaz ----------
            assertThat(contar("SELECT count(*) FROM jornada WHERE periodo_servicio_id=?",periodoA)).as("envío fallido + reintento = UNA jornada").isEqualTo(1);
            var j=jdbc.queryForMap("SELECT id,ruta_version_id,inicio_programado,fin_programado,jornada_antecedente_id,vigente FROM jornada WHERE periodo_servicio_id=?",periodoA);
            UUID jornada=(UUID) j.get("id");
            assertThat(jdbc.queryForObject("SELECT p.empresa_operadora_id FROM jornada x JOIN periodo_servicio p ON p.id=x.periodo_servicio_id WHERE x.id=?",UUID.class,jornada)).as("empresa derivada del periodo").isEqualTo(empresaA);
            assertThat(j.get("ruta_version_id")).isEqualTo(version);
            assertThat(((Timestamp) j.get("inicio_programado")).toInstant()).as("inicio declarado en Bolivia (UTC−04:00)").isEqualTo(inicio);
            assertThat(j.get("fin_programado")).isNull();
            assertThat(j.get("jornada_antecedente_id")).isNull();
            assertThat(contar("SELECT count(*) FROM jornada WHERE periodo_servicio_id=?",periodoB)).isZero();
            var asignaciones=jdbc.queryForList("SELECT id,vinculo_vehiculo_empresa_id,vinculo_persona_empresa_id,vigente_desde,vigente_hasta,motivo_cambio FROM asignacion_jornada WHERE jornada_id=? ORDER BY vigente_desde,creado_en",jornada);
            assertThat(asignaciones).as("asignación inicial + reasignación").hasSize(2);
            assertThat(asignaciones.get(0).get("vinculo_vehiculo_empresa_id")).isEqualTo(vv1); assertThat(asignaciones.get(0).get("vinculo_persona_empresa_id")).isEqualTo(vp1);
            assertThat(asignaciones.get(1).get("vinculo_vehiculo_empresa_id")).isEqualTo(vv2); assertThat(asignaciones.get(1).get("vinculo_persona_empresa_id")).isEqualTo(vp2);
            assertThat(contar("SELECT count(*) FROM asignacion_jornada WHERE jornada_id=? AND vigente_hasta IS NULL",jornada)).as("una sola asignación abierta").isEqualTo(1);
            assertThat(asignaciones.get(0).get("vigente_hasta")).as("la anterior se cierra al iniciar la nueva").isEqualTo(asignaciones.get(1).get("vigente_desde"));
            assertThat(asignaciones.get(1).get("motivo_cambio")).isEqualTo("Cambio sintético de recurso por avería.");
            // Autoría: cada actuación pertenece a la cuenta autenticada (con su concesión de periodo).
            for(String op:List.of("PROGRAMAR","ASIGNAR","REASIGNAR")) assertThat(contar("SELECT count(*) FROM actuacion_planificacion_operador WHERE jornada_id=? AND operacion=? AND usuario_id=?",jornada,op,uA)).as(op).isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM actuacion_planificacion_operador WHERE jornada_id=?",jornada)).isEqualTo(3);
            // Programar y asignar NO implican ejecución, GPS, fiscalización ni acciones correctivas.
            assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",jornada)).isZero();
            assertThat(contar("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",jornada)).isZero();
            assertThat(contar("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",jornada)).isZero();
            assertThat(contar("SELECT count(*) FROM accion_correctiva WHERE jornada_id=?",jornada)).isZero();
            // Referencias inmutables.
            assertThat(catchThrowable(() -> jdbc.update("UPDATE jornada SET periodo_servicio_id=? WHERE id=?",periodoB,jornada))).isNotNull();
            assertThat(catchThrowable(() -> jdbc.update("UPDATE jornada SET inicio_programado=inicio_programado+interval '1 hour' WHERE id=?",jornada))).isNotNull();
            assertThat(catchThrowable(() -> jdbc.update("UPDATE asignacion_jornada SET vinculo_vehiculo_empresa_id=? WHERE id=?",vv2,asignaciones.get(0).get("id")))).isNotNull();

            // ---------- REST real: periodos inválidos, recursos incompatibles, conflictos y acceso entre operadores ----------
            String ta=token(operadorA),tb=token(operadorB);
            String programarA="/api/empresa/periodos/"+periodoA+"/jornadas";
            Map<String,Object> base=new LinkedHashMap<>(Map.of("solicitudId",UUID.randomUUID(),"rutaVersionId",version,"inicioProgramado",inicio.plus(1,ChronoUnit.DAYS).toString()));
            assertThat(api("POST","/api/empresa/periodos/"+periodoB+"/jornadas",ta,base).statusCode()).as("periodo de otro operador").isEqualTo(403);
            assertThat(api("POST","/api/empresa/periodos/"+UUID.randomUUID()+"/jornadas",ta,base).statusCode()).as("periodo inexistente/sin concesión").isEqualTo(403);
            assertThat(api("POST",programarA,tb,base).statusCode()).as("el otro operador no programa en el periodo A").isEqualTo(403);
            var conEmpresa=new LinkedHashMap<>(base); conEmpresa.put("empresaOperadoraId",empresaB);
            assertThat(api("POST",programarA,ta,conEmpresa).statusCode()).as("la empresa no viene del cliente").isEqualTo(400);
            var fueraDePeriodo=new LinkedHashMap<>(base); fueraDePeriodo.put("solicitudId",UUID.randomUUID()); fueraDePeriodo.put("inicioProgramado","2040-01-01T12:00:00Z");
            assertThat(api("POST",programarA,ta,fueraDePeriodo).statusCode()).as("fecha fuera del periodo").isEqualTo(400);
            var finAntes=new LinkedHashMap<>(base); finAntes.put("solicitudId",UUID.randomUUID()); finAntes.put("finProgramado",inicio.toString());
            assertThat(api("POST",programarA,ta,finAntes).statusCode()).as("fin no posterior al inicio").isEqualTo(400);
            var anteriorConcesion=new LinkedHashMap<>(base); anteriorConcesion.put("solicitudId",UUID.randomUUID()); anteriorConcesion.put("inicioProgramado",Instant.now().minus(1,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString());
            assertThat(api("POST",programarA,ta,anteriorConcesion).statusCode()).as("fecha anterior a la concesión sin histórico explícito").isEqualTo(403);
            var versionInexistente=new LinkedHashMap<>(base); versionInexistente.put("solicitudId",UUID.randomUUID()); versionInexistente.put("rutaVersionId",UUID.randomUUID());
            assertThat(api("POST",programarA,ta,versionInexistente).statusCode()).as("versión de ruta inexistente").isIn(400,404);
            // Conflicto: misma solicitud (la de la UI) con contenido distinto.
            UUID solicitudProgramacion=jdbc.queryForObject("SELECT solicitud_id FROM actuacion_planificacion_operador WHERE jornada_id=? AND operacion='PROGRAMAR'",UUID.class,jornada);
            var conflicto=new LinkedHashMap<>(base); conflicto.put("solicitudId",solicitudProgramacion); conflicto.put("inicioProgramado",inicio.plus(2,ChronoUnit.DAYS).toString());
            assertThat(api("POST",programarA,ta,conflicto).statusCode()).as("misma solicitud con otro contenido").isEqualTo(409);
            assertThat(contar("SELECT count(*) FROM jornada WHERE periodo_servicio_id=?",periodoA)).as("ningún intento inválido crea jornada").isEqualTo(1);
            // Acceso entre operadores a la jornada A.
            assertThat(api("GET","/api/empresa/jornadas/"+jornada,tb,null).statusCode()).isEqualTo(403);
            assertThat(api("GET","/api/empresa/jornadas/"+jornada+"/recursos",tb,null).statusCode()).isEqualTo(403);
            assertThat(api("POST","/api/empresa/jornadas/"+jornada+"/asignaciones",tb,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vv1,"vinculoPersonaEmpresaId",vp1)).statusCode()).isEqualTo(403);
            // Asignación: duplicado (ya hay una abierta), recursos de otra empresa, inactivos o vencidos: sin modificar el historial.
            var historiaAntes=jdbc.queryForList("SELECT id,vigente_desde,vigente_hasta,motivo_cambio FROM asignacion_jornada WHERE jornada_id=? ORDER BY vigente_desde,creado_en",jornada);
            String asignarA="/api/empresa/jornadas/"+jornada+"/asignaciones",reasignarA="/api/empresa/jornadas/"+jornada+"/reasignaciones";
            assertThat(api("POST",asignarA,ta,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vv1,"vinculoPersonaEmpresaId",vp1)).statusCode()).as("asignar con una abierta: debe reasignar").isEqualTo(409);
            assertThat(api("POST",reasignarA,ta,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vvB,"vinculoPersonaEmpresaId",vp1,"motivoCambio","x")).statusCode()).as("vehículo de otra empresa").isEqualTo(400);
            assertThat(api("POST",reasignarA,ta,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vv1,"vinculoPersonaEmpresaId",vpB,"motivoCambio","x")).statusCode()).as("persona de otra empresa").isEqualTo(400);
            UUID vvInactivo=jdbc.queryForObject("SELECT v.id FROM vinculo_vehiculo_empresa v JOIN vehiculo x ON x.id=v.vehiculo_id WHERE x.placa=?",UUID.class,placaInactiva);
            UUID vvVencido=jdbc.queryForObject("SELECT v.id FROM vinculo_vehiculo_empresa v JOIN vehiculo x ON x.id=v.vehiculo_id WHERE x.placa=?",UUID.class,placaVencida);
            assertThat(api("POST",reasignarA,ta,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vvInactivo,"vinculoPersonaEmpresaId",vp1,"motivoCambio","x")).statusCode()).as("vehículo inactivo").isEqualTo(400);
            assertThat(api("POST",reasignarA,ta,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vvVencido,"vinculoPersonaEmpresaId",vp1,"motivoCambio","x")).statusCode()).as("vínculo vencido en la fecha programada").isEqualTo(400);
            assertThat(api("POST",reasignarA,ta,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vv1,"vinculoPersonaEmpresaId",vp1)).statusCode()).as("reasignar sin motivo").isIn(400);
            assertThat(api("POST",reasignarA,ta,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",UUID.randomUUID(),"vinculoPersonaEmpresaId",vp1,"motivoCambio","x")).statusCode()).as("vínculo inexistente").isIn(400,404);
            assertThat(jdbc.queryForList("SELECT id,vigente_desde,vigente_hasta,motivo_cambio FROM asignacion_jornada WHERE jornada_id=? ORDER BY vigente_desde,creado_en",jornada)).as("el historial de asignaciones no cambió").isEqualTo(historiaAntes);
            assertThat(contar("SELECT count(*) FROM actuacion_planificacion_operador WHERE jornada_id=?",jornada)).isEqualTo(3);
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
    }
}
