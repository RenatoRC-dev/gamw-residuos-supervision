package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

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
 * CU-21 con navegador real + REST real + PostgreSQL/PostGIS aislados y JWT: revisión del expediente municipal sin dictamen (PENDING-05),
 * lectura sin escritura y denegación a un operador A4. El conductor ejecuta por la API real; la inspección se siembra como antecedente CU-20.
 * Sin categorías de resultado: esta JVM de pruebas de navegador no inserta ninguna (se comprueba antes del E2E).
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class FiscalizacionMunicipalNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private UUID persona(String etiqueta) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"FMN-"+id,etiqueta);
        return id;
    }
    private UUID usuario(UUID persona,String nombre,String... permisos) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private HttpResponse<String> api(String ruta,String jwt,Object cuerpo) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void revisaElExpedienteSinDictamenYRespetaPermisosEnElNavegador() throws Exception {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),
            vp=UUID.randomUUID(),jornada=UUID.randomUUID(),asignacion=UUID.randomUUID(),inspeccion=UUID.randomUUID();
        String razon="Empresa sintética CU-21 "+sufijo,codigo="FMN-"+sufijo,placa="FMN-"+sufijo.toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,razon);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,codigo,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,placa);
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        UUID personaConductor=persona("Conductor sintético");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,personaConductor,empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        Instant inicio=Instant.now().minus(40,ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacion,jornada,vv,vp,Timestamp.from(inicio.minus(1,ChronoUnit.HOURS)));
        String conductor="fm-cond-"+sufijo,revisor="fm-rev-"+sufijo,lector="fm-lec-"+sufijo,operador="fm-op-"+sufijo;
        usuario(personaConductor,conductor);
        usuario(persona("Revisor municipal"),revisor,"FISCALIZACION_CONSULTAR","FISCALIZACION_EMITIR","RECLAMO_CONSULTAR");
        usuario(persona("Lector municipal"),lector,"FISCALIZACION_CONSULTAR");
        UUID operadorId=usuario(persona("Operador de empresa"),operador);
        jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,'CORRECTIVA_CONSULTAR',true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),operadorId,periodo);
        UUID inspector=usuario(persona("Inspector sintético"),"fm-ins-"+sufijo,"INSPECCION_REGISTRAR");
        String observacion="Inspección sintética CU-21 "+sufijo;
        jdbc.update("INSERT INTO inspeccion(id,solicitud_id,huella_solicitud,jornada_id,inspector_usuario_id,realizada_en,observaciones) VALUES (?,?,'h',?,?,?,?)",
            inspeccion,UUID.randomUUID(),jornada,inspector,Timestamp.from(inicio.plusSeconds(600)),observacion);
        jdbc.update("""
            INSERT INTO reclamo_institucional(id,solicitud_id,huella_solicitud,canal_declarado,recibido_en,descripcion,lugar_descripcion,referencias_origen,jornada_id,usuario_id)
            VALUES (?,?,'h','Teléfono institucional (declarado)',?,'Reclamo sintético CU-21','','Registro sintético',?,?)""",
            UUID.randomUUID(),UUID.randomUUID(),Timestamp.from(Instant.now().minusSeconds(60)),jornada,inspector);
        // El conductor ejecuta por la API real: INICIO, 3 GPS, FIN.
        var login=json.readTree(api("/api/autenticacion/login",null,Map.of("nombreUsuario",conductor,"contrasena",clave)).body());
        String tc=login.get("tokenAcceso").asText();
        assertThat(api("/api/jornadas/"+jornada+"/ejecucion/inicio",tc,Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",inicio.toString())).statusCode()).isEqualTo(201);
        var lote=Map.of("asignacionJornadaId",asignacion,"posiciones",List.of(
            Map.of("capturaId",UUID.randomUUID(),"capturadoEn",inicio.plusSeconds(30).toString(),"latitud",20.1,"longitud",-10.1,"precisionMetros",5.0),
            Map.of("capturaId",UUID.randomUUID(),"capturadoEn",inicio.plusSeconds(60).toString(),"latitud",20.2,"longitud",-10.2,"precisionMetros",5.0),
            Map.of("capturaId",UUID.randomUUID(),"capturadoEn",inicio.plusSeconds(90).toString(),"latitud",20.3,"longitud",-10.3,"precisionMetros",5.0)));
        assertThat(api("/api/jornadas/"+jornada+"/ejecucion/posiciones-gps",tc,lote).statusCode()).isEqualTo(200);
        assertThat(api("/api/jornadas/"+jornada+"/ejecucion/fin",tc,Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",inicio.plusSeconds(1200).toString())).statusCode()).isEqualTo(201);
        // PENDING-05: ninguna categoría oficial. En la JVM aislada de navegador (CI) el catálogo está vacío; en un `verify` local completo
        // pueden existir fixtures sintéticas habilitadas por CadenaOperativaFiscalizacionIT (misma BD efímera): se distinguen en el E2E.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalogo_resultado_fiscalizacion WHERE operador_local<>'prueba-sintetica'",Integer.class)).as("sin catálogo oficial").isZero();
        boolean catalogoVacio=jdbc.queryForObject("SELECT count(*) FROM catalogo_resultado_fiscalizacion WHERE habilitado",Integer.class)==0;
        var evidenciaAntes=jdbc.queryForList("SELECT id,capturado_en,recibido_en FROM posicion_gps WHERE jornada_id=? ORDER BY id",jornada);

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
            payload.put("clave",clave); payload.put("revisor",revisor); payload.put("lector",lector); payload.put("operador",operador);
            payload.put("jornada",jornada.toString()); payload.put("periodo",periodo.toString()); payload.put("empresa",razon); payload.put("codigoRuta",codigo);
            payload.put("placa",placa); payload.put("vehiculo",vehiculo.toString()); payload.put("inspeccion",inspeccion.toString()); payload.put("inspector",inspector.toString());
            payload.put("observacionInspeccion",observacion); payload.put("catalogoVacio",catalogoVacio);
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-fiscalizacion-municipal.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lectorSalida=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(180,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lectorSalida.join(1000);
                String diagnostico=salida.toString(StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E fiscalización municipal: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        // Revisar no emite ni altera: sin fiscalización; GPS, eventos e inspección intactos.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();
        assertThat(jdbc.queryForList("SELECT id,capturado_en,recibido_en FROM posicion_gps WHERE jornada_id=? ORDER BY id",jornada)).isEqualTo(evidenciaAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inspeccion WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(1);
    }
}
