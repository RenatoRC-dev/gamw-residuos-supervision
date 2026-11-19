package bo.gob.warnes.gamw.residuos.modules.monitoreo.posicion.api;

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
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * CU-15 (A3) con navegador real + REST real + PostgreSQL/PostGIS aislados y datos sintéticos.
 * Última posición por capturado_en (no por recibido_en), desempate estable, jornadas sin GPS/antiguas/cerradas, respaldo tabular, red y sesión.
 * La ausencia de GPS no se interpreta. A4 no recibe permisos de monitoreo (D1 pendiente). Los marcadores WebGL no se afirman por DOM.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR","gamw.monitoreo.antiguedad-segundos=600"})
class MonitoreoMunicipalNavegadorIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private UUID persona(String nombres) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,"MO-"+id,nombres);
        return id;
    }
    private UUID usuario(String nombre) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona("Cuenta "+nombre),nombre,codificador.encode(clave));
        return id;
    }
    private HttpResponse<String> get(String ruta,String jwt) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(15)).GET();
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String token(String nombre) throws Exception {
        var r=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+"/api/autenticacion/login")).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("nombreUsuario",nombre,"contrasena",clave)))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        return json.readTree(r.body()).get("tokenAcceso").asText();
    }
    private int contar(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }
    private UUID asignacion(UUID jornada,UUID vv,UUID vp) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",id,jornada,vv,vp,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        return id;
    }
    private UUID jornadaIniciada(UUID periodo,UUID version,UUID vv,UUID vp,Instant inicio,boolean conInicio,boolean conFin) {
        UUID j=UUID.randomUUID();
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",j,periodo,version,Timestamp.from(Instant.now().plus(1,ChronoUnit.DAYS)));
        UUID a=asignacion(j,vv,vp);
        if(conInicio) jdbc.update("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'INICIO',?,?,NULL)",UUID.randomUUID(),UUID.randomUUID(),j,a,Timestamp.from(inicio),Timestamp.from(inicio.plusSeconds(60)));
        if(conFin) jdbc.update("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'FIN',?,?,NULL)",UUID.randomUUID(),UUID.randomUUID(),j,a,Timestamp.from(inicio.plusSeconds(1800)),Timestamp.from(inicio.plusSeconds(1860)));
        return j;
    }
    private void posicion(UUID jornada,UUID captura,UUID conductor,Instant capturado,Instant recibido,double lon,double lat,double precision) {
        UUID a=jdbc.queryForObject("SELECT id FROM asignacion_jornada WHERE jornada_id=?",UUID.class,jornada);
        jdbc.update("INSERT INTO posicion_gps VALUES (?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(?,?),4326),?)",UUID.randomUUID(),captura,jornada,a,conductor,Timestamp.from(capturado),Timestamp.from(recibido),lon,lat,precision);
    }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void municipalMonitoreaUltimasPosicionesPorCapturaConRespaldoTabularYSinInterpretarAusencias() throws Exception {
        UUID empresaA=UUID.randomUUID(),empresaB=UUID.randomUUID(),periodoA=UUID.randomUUID(),periodoB=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID();
        String municipal="mon-mun-"+sufijo,operador="mon-op-"+sufijo,sinPermiso="mon-sin-"+sufijo;
        UUID uM=usuario(municipal),uO=usuario(operador),uN=usuario(sinPermiso),conductor=usuario("mon-cond-"+sufijo);
        jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,'MONITOREO_JORNADA_CONSULTAR','MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),uM);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaA,"Empresa sintética monitoreo A "+sufijo);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaB,"Empresa sintética monitoreo B "+sufijo);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoA,empresaA);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoB,empresaB);
        for(String p:List.of("PLANIFICACION_CONSULTAR","JORNADA_PROGRAMAR","JORNADA_ASIGNAR","JORNADA_REPROGRAMAR"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),uO,periodoA,p);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"MO-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.2 -17.8,-63.3 -17.9)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        UUID[] vv=new UUID[2],vp=new UUID[2],empresas={empresaA,empresaB};
        for(int i=0;i<2;i++) {
            UUID veh=UUID.randomUUID(); vv[i]=UUID.randomUUID(); vp[i]=UUID.randomUUID();
            jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",veh,"MON"+i+"-"+sufijo.toUpperCase());
            jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv[i],veh,empresas[i]);
            jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp[i],persona("Conductor monitoreo "+i+" "+sufijo),empresas[i]);
            jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp[i]);
        }
        Instant ahora=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID ja=jornadaIniciada(periodoA,version,vv[0],vp[0],ahora.minus(60,ChronoUnit.MINUTES),true,false);
        UUID jt=jornadaIniciada(periodoA,version,vv[0],vp[0],ahora.minus(65,ChronoUnit.MINUTES),true,false);
        UUID js=jornadaIniciada(periodoA,version,vv[0],vp[0],ahora.minus(70,ChronoUnit.MINUTES),true,false);
        UUID jn=jornadaIniciada(periodoA,version,vv[0],vp[0],ahora.minus(75,ChronoUnit.MINUTES),true,false);
        UUID jb=jornadaIniciada(periodoB,version,vv[1],vp[1],ahora.minus(80,ChronoUnit.MINUTES),true,false);
        UUID jcerrada=jornadaIniciada(periodoA,version,vv[0],vp[0],ahora.minus(5,ChronoUnit.HOURS),true,true);
        UUID jsinInicio=jornadaIniciada(periodoA,version,vv[0],vp[0],ahora,false,false);
        UUID capA=UUID.randomUUID(),capTardia=UUID.randomUUID(),capBaja=UUID.fromString("00000000-0000-4000-8000-000000000001"),capAlta=UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
        posicion(ja,capA,conductor,ahora.minusSeconds(10),ahora.minusSeconds(8),-63.17,-17.78,12.5);
        posicion(ja,capTardia,conductor,ahora.minusSeconds(200),ahora.minusSeconds(1),-63.2,-17.8,99);   // recibida después, capturada antes
        posicion(jt,capBaja,conductor,ahora.minusSeconds(30),ahora.minusSeconds(20),-63.25,-17.85,5);
        posicion(jt,capAlta,conductor,ahora.minusSeconds(30),ahora.minusSeconds(19),-63.3,-17.9,7);       // empate de captura: gana captura_id mayor
        posicion(js,UUID.randomUUID(),conductor,ahora.minusSeconds(3600),ahora.minusSeconds(3595),-63.35,-17.95,8);
        posicion(jb,UUID.randomUUID(),conductor,ahora.minusSeconds(20),ahora.minusSeconds(15),-63.4,-17.95,3);
        posicion(jcerrada,UUID.randomUUID(),conductor,ahora.minus(5,ChronoUnit.HOURS).plusSeconds(120),ahora.minus(5,ChronoUnit.HOURS).plusSeconds(125),-63.5,-17.5,4);
        int gpsAntes=contar("SELECT count(*) FROM posicion_gps"),eventosAntes=contar("SELECT count(*) FROM evento_ejecucion_jornada");

        // ---------- REST real ----------
        String tm=token(municipal);
        var r=get("/api/monitoreo/jornadas",tm);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        JsonNode v=json.readTree(r.body());
        assertThat(v.get("umbralAntiguedadSegundos").asLong()).isEqualTo(600);
        // La base es compartida por la JVM: otras pruebas pueden dejar jornadas activas legítimas. Se verifican SOLO los identificadores sembrados aquí.
        Map<String,JsonNode> porId=new LinkedHashMap<>();
        for(int desde=0,siguiente=0;siguiente>=0;) {
            var pagina=json.readTree(get("/api/monitoreo/jornadas?limite=100&desplazamiento="+desde,tm).body());
            pagina.get("jornadas").forEach(j -> porId.put(j.get("contexto").get("jornadaId").asText(),j));
            siguiente=pagina.get("siguiente").isNull()?-1:pagina.get("siguiente").asInt(); desde=siguiente;
        }
        assertThat(porId.keySet()).as("las cinco jornadas con INICIO y sin FIN sembradas").contains(ja.toString(),jt.toString(),js.toString(),jn.toString(),jb.toString());
        assertThat(porId.keySet()).as("la cerrada y la no iniciada quedan excluidas").doesNotContain(jcerrada.toString(),jsinInicio.toString());
        List<String> orden=new ArrayList<>(porId.keySet());
        assertThat(orden.indexOf(ja.toString())).as("orden por INICIO registrado descendente").isLessThan(orden.indexOf(jt.toString()));
        assertThat(orden.indexOf(jt.toString())).isLessThan(orden.indexOf(js.toString()));
        assertThat(orden.indexOf(js.toString())).isLessThan(orden.indexOf(jn.toString()));
        assertThat(orden.indexOf(jn.toString())).isLessThan(orden.indexOf(jb.toString()));
        var pa=porId.get(ja.toString()).get("ultimaPosicion");
        assertThat(pa.get("capturaId").asText()).as("última por captura, no por recepción").isEqualTo(capA.toString());
        assertThat(Instant.parse(pa.get("capturadoEn").asText())).isEqualTo(ahora.minusSeconds(10));
        assertThat(Instant.parse(pa.get("recibidoEn").asText())).isEqualTo(ahora.minusSeconds(8));
        assertThat(pa.get("longitud").asDouble()).isEqualTo(-63.17); assertThat(pa.get("latitud").asDouble()).isEqualTo(-17.78); assertThat(pa.get("precisionMetros").asDouble()).isEqualTo(12.5);
        assertThat(porId.get(jt.toString()).get("ultimaPosicion").get("capturaId").asText()).as("desempate estable").isEqualTo(capAlta.toString());
        assertThat(Instant.parse(porId.get(js.toString()).get("ultimaPosicion").get("capturadoEn").asText())).isEqualTo(ahora.minusSeconds(3600));
        assertThat(porId.get(jn.toString()).get("ultimaPosicion").isNull()).as("sin GPS: no se fabrica posición").isTrue();
        assertThat(porId.get(ja.toString()).get("rutaReferencia").get("type").asText()).isEqualTo("LineString");
        // Paginación y límites.
        assertThat(get("/api/monitoreo/jornadas?limite=0",tm).statusCode()).isEqualTo(400);
        assertThat(get("/api/monitoreo/jornadas?limite=101",tm).statusCode()).isEqualTo(400);
        assertThat(get("/api/monitoreo/jornadas?desplazamiento=-1",tm).statusCode()).isEqualTo(400);
        // Paginación de a 2 recorrida hasta el final: sin repetidos ni omisiones respecto del listado completo, en el mismo orden.
        List<String> paginadas=new ArrayList<>();
        int desde=0;
        while(true) {
            var pagina=json.readTree(get("/api/monitoreo/jornadas?limite=2&desplazamiento="+desde,tm).body());
            assertThat(pagina.get("jornadas").size()).isLessThanOrEqualTo(2);
            pagina.get("jornadas").forEach(j -> paginadas.add(j.get("contexto").get("jornadaId").asText()));
            if(pagina.get("siguiente").isNull()) break;
            assertThat(pagina.get("siguiente").asInt()).isEqualTo(desde+2);
            desde=pagina.get("siguiente").asInt();
        }
        assertThat(paginadas).as("recorrido paginado = listado completo").containsExactlyElementsOf(orden);
        // Acceso: sin token, token inválido, sin permiso y A4 (D1: no se concede monitoreo).
        assertThat(get("/api/monitoreo/jornadas",null).statusCode()).isIn(401,403);
        assertThat(get("/api/monitoreo/jornadas","no.es.un.token").statusCode()).isIn(401,403);
        for(String u:List.of(sinPermiso,operador)) {
            var x=get("/api/monitoreo/jornadas",token(u));
            assertThat(x.statusCode()).as(u).isEqualTo(403);
            assertThat(x.body()).as("sin exponer datos").doesNotContain(ja.toString()).doesNotContain(jb.toString());
        }
        assertThat(contar("SELECT count(*) FROM concesion_permiso WHERE usuario_id=? AND permiso LIKE 'MONITOREO%'",uO)).as("A4 sin permisos de monitoreo").isZero();
        assertThat(catchThrowable(() -> jdbc.update("UPDATE posicion_gps SET precision_metros=1 WHERE jornada_id=?",ja))).as("evidencia inmutable").isNotNull();

        // ---------- Navegador ----------
        int web;
        try(var disponible=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { web=disponible.getLocalPort(); }
        Path directorio=Path.of("..","apps","web").toAbsolutePath().normalize();
        var vite=new ProcessBuilder("node","node_modules/vite/bin/vite.js","--host","127.0.0.1","--port",Integer.toString(web),"--strictPort")
                .directory(directorio.toFile()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        vite.environment().put("GAMW_WEB_INTEGRACION","true");
        vite.environment().put("VITE_API_BASE_URL","/api");
        vite.environment().put("VITE_MONITOREO_POLL_SEGUNDOS","5");
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
            var datos=Map.of("clave",clave,"municipal",municipal,"operador",operador,"sinPermiso",sinPermiso,
                "jornadas",Map.of("a",ja,"t",jt,"s",js,"n",jn,"b",jb,"cerrada",jcerrada,"sinInicio",jsinInicio),
                "a",Map.of("cap",ahora.minusSeconds(10).toString(),"rec",ahora.minusSeconds(8).toString(),"lon","-63.17","lat","-17.78","prec","12.5","tardiaLon","-63.2","tardiaLat","-17.8"),
                "t",Map.of("lon","-63.3","lat","-17.9","prec","7"),"b",Map.of("lon","-63.4","lat","-17.95","prec","3"));
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-monitoreo.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(datos));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lector=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(150,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lector.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E monitoreo: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }
        // Consultar no modifica evidencia; la revocación corta el acceso.
        assertThat(contar("SELECT count(*) FROM posicion_gps")).isEqualTo(gpsAntes);
        assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada")).isEqualTo(eventosAntes);
        assertThat(jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba',motivo_revocacion='fixture' WHERE usuario_id=? AND permiso='MONITOREO_JORNADA_CONSULTAR' AND revocado_en IS NULL",uM)).isEqualTo(1);
        assertThat(get("/api/monitoreo/jornadas",tm).statusCode()).as("permiso revocado").isEqualTo(403);
    }
}
