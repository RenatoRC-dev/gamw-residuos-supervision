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
 * CU-06 / CU-07 (A1) con navegador real + REST real + PostgreSQL/PostGIS aislados, JWT y permisos municipales explícitos VEHICULOS_*,
 * PERSONAS_* y EMPRESAS_CONSULTAR. Verifica también: revocación efectiva, A4 sin escritura administrativa pero con su flujo de recursos y
 * asignación por periodo intacto, y que ningún cierre de vínculo deja fuera una jornada ya asignada. Sin D1, catálogos ni políticas
 * inventadas. Base compartida: solo se verifican los identificadores sembrados aquí.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class VehiculosPersonalNavegadorIT extends IntegracionPostgresV8 {
    private static final String CONDUCTOR="00000000-0000-4000-8000-000000000001",SUPERVISOR="00000000-0000-4000-8000-000000000002";
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString(),sufijo=UUID.randomUUID().toString().substring(0,8);

    private UUID persona(String ci,String nombres) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",id,ci,nombres);
        return id;
    }
    private UUID usuario(String nombre,String... permisos) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona("VPU-"+UUID.randomUUID(),"Cuenta "+nombre),nombre,codificador.encode(clave));
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
    private static Map<String,Object> vinculoPersonal(UUID persona,String inicio,String fin,List<String> funciones) {
        var m=new HashMap<String,Object>(); m.put("personaId",persona); m.put("fechaInicio",inicio); m.put("fechaFin",fin); m.put("activo",true); m.put("funcionOperativaIds",funciones);
        return m;
    }
    private static Map<String,Object> cierre(String inicio,String fin,boolean activo) {
        var m=new HashMap<String,Object>(); if(inicio!=null) m.put("fechaInicio",inicio); m.put("fechaFin",fin); m.put("activo",activo);
        return m;
    }

    @Test @Timeout(value=6,unit=TimeUnit.MINUTES)
    void administradorGestionaVehiculosYPersonalConservandoLaHistoria() throws Exception {
        String admin="vp-adm-"+sufijo,lector="vp-lec-"+sufijo,operador="vp-op-"+sufijo,revocado="vp-rev-"+sufijo;
        usuario(admin,"EMPRESAS_CONSULTAR","PERSONAS_CONSULTAR","PERSONAS_GESTIONAR","VEHICULOS_CONSULTAR","VEHICULOS_GESTIONAR");
        usuario(lector,"EMPRESAS_CONSULTAR","PERSONAS_CONSULTAR","VEHICULOS_CONSULTAR");
        UUID uRevocado=usuario(revocado,"VEHICULOS_CONSULTAR","VEHICULOS_GESTIONAR");
        UUID uOperador=usuario(operador);
        // Dos empresas; en A, una jornada (15/06/2026 08:00 Bolivia) ya asignada con un vehículo y una persona vinculados.
        UUID empresaA=UUID.randomUUID(),empresaB=UUID.randomUUID(),periodoA=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada=UUID.randomUUID();
        String razonA="VP-A sintética "+sufijo,razonB="VP-B sintética "+sufijo;
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaA,razonA);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaB,razonB);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2026-12-31',true,now(),now())",periodoA,empresaA);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"VP-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.1 -17.7,-63.1 -17.6)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",jornada,periodoA,version,Timestamp.from(Instant.parse("2026-06-15T12:00:00Z")));
        String ciAsignado="VPA-"+sufijo,placaAsignada=("VPA"+sufijo).toUpperCase();
        UUID personaAsignada=persona(ciAsignado,"Conductor asignado "+sufijo),vehiculoAsignado=UUID.randomUUID(),vpAsignado=UUID.randomUUID(),vvAsignado=UUID.randomUUID(),asignacion=UUID.randomUUID();
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpAsignado,personaAsignada,empresaA);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,?::uuid)",vpAsignado,CONDUCTOR);
        jdbc.update("INSERT INTO vehiculo(id,placa,activo,creado_en,actualizado_en) VALUES (?,?,true,now(),now())",vehiculoAsignado,placaAsignada);
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa(id,vehiculo_id,empresa_operadora_id,fecha_inicio,fecha_fin,activo,creado_en,actualizado_en) VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vvAsignado,vehiculoAsignado,empresaA);
        jdbc.update("INSERT INTO asignacion_jornada(id,jornada_id,vinculo_vehiculo_empresa_id,vinculo_persona_empresa_id,vigente_desde,vigente_hasta,motivo_cambio,creado_en) VALUES (?,?,?,?,now(),NULL,NULL,now())",asignacion,jornada,vvAsignado,vpAsignado);
        var asignacionAntes=jdbc.queryForMap("SELECT * FROM asignacion_jornada WHERE id=?",asignacion);
        String ci="VPCI-"+sufijo,nombres="Persona VP "+sufijo,placa=("VPN"+sufijo).toUpperCase();

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
            var payload=Map.of("clave",clave,"admin",admin,"lector",lector,"empresaA",razonA,"empresaB",razonB,"ci",ci,"nombres",nombres,
                    "ciAsignado",ciAsignado,"placa",placa,"placaAsignada",placaAsignada);
            var prueba=new ProcessBuilder("node","--test","e2e/flujo-vehiculos-personal.test.mjs").directory(directorio.toFile()).redirectErrorStream(true);
            var env=prueba.environment();
            env.put("GAMW_WEB_INTEGRACION","true"); env.put("GAMW_WEB_TEST_BASE","http://127.0.0.1:"+web); env.put("GAMW_WEB_TEST_DATOS",json.writeValueAsString(payload));
            Process proceso=prueba.start();
            var salida=new java.io.ByteArrayOutputStream();
            var lectorSalida=Thread.ofVirtual().start(() -> { try { proceso.getInputStream().transferTo(salida); } catch(java.io.IOException error) { /* validar exit code */ } });
            try {
                assertThat(proceso.waitFor(180,TimeUnit.SECONDS)).as("Tiempo del E2E web").isTrue();
                lectorSalida.join(1000);
                String diagnostico=salida.toString(java.nio.charset.StandardCharsets.UTF_8).replace(clave,"[REDACTADO]");
                assertThat(proceso.exitValue()).as("E2E vehículos/personal: %s",diagnostico).isZero();
            } finally { proceso.descendants().forEach(ProcessHandle::destroy); proceso.destroy(); }
        } finally {
            servidor.descendants().forEach(ProcessHandle::destroy); servidor.destroy();
        }

        // ---------- Estado persistido por la interfaz ----------
        UUID personaNueva=jdbc.queryForObject("SELECT id FROM persona_natural WHERE ci=?",UUID.class,ci);
        assertThat(contar("SELECT count(*) FROM persona_natural WHERE ci=?",ci)).as("CI único").isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM usuario WHERE persona_natural_id=?",personaNueva)).as("CA-07-01: persona sin cuenta").isZero();
        assertThat(contar("SELECT count(*) FROM persona_natural WHERE ci=?",ci+"-LECTOR")).as("el lector no crea").isZero();
        var segmentosA=jdbc.queryForList("SELECT id,fecha_inicio::text AS i,fecha_fin::text AS f,activo FROM vinculo_persona_empresa WHERE persona_id=? AND empresa_operadora_id=? ORDER BY fecha_inicio",personaNueva,empresaA);
        assertThat(segmentosA).hasSize(2);
        assertThat(segmentosA.get(0)).containsEntry("i","2026-01-01").containsEntry("f","2026-03-31").containsEntry("activo",false);
        assertThat(segmentosA.get(1)).containsEntry("i","2026-04-01").containsEntry("activo",true);
        assertThat(jdbc.queryForList("SELECT funcion_operativa_id::text FROM vinculo_persona_funcion WHERE vinculo_persona_empresa_id=?",String.class,segmentosA.get(0).get("id")))
            .as("CA-07-05: el segmento anterior conserva su función").containsExactly(CONDUCTOR);
        assertThat(jdbc.queryForList("SELECT funcion_operativa_id::text FROM vinculo_persona_funcion WHERE vinculo_persona_empresa_id=?",String.class,segmentosA.get(1).get("id"))).containsExactly(SUPERVISOR);
        assertThat(contar("SELECT count(*) FROM vinculo_persona_empresa WHERE persona_id=? AND empresa_operadora_id=?",personaNueva,empresaB)).as("CA-07-06: misma persona en otra empresa").isEqualTo(1);
        UUID vehiculoNuevo=jdbc.queryForObject("SELECT id FROM vehiculo WHERE placa=?",UUID.class,placa);
        assertThat(contar("SELECT count(*) FROM vehiculo WHERE placa=? OR placa=?",placa,placa+"L")).as("placa única; el lector no crea").isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT empresa_operadora_id::text||' '||fecha_inicio||' '||coalesce(fecha_fin::text,'abierto')||' '||activo FROM vinculo_vehiculo_empresa WHERE vehiculo_id=? ORDER BY fecha_inicio",String.class,vehiculoNuevo))
            .as("CA-06-07: segmentos A (cerrado), B y regreso a A (nuevo)")
            .containsExactly(empresaA+" 2026-01-01 2026-03-31 false",empresaB+" 2026-04-01 2026-06-30 true",empresaA+" 2026-07-01 abierto true");
        assertThat(jdbc.queryForMap("SELECT * FROM asignacion_jornada WHERE id=?",asignacion)).as("CA-06-04/CA-07-08: asignación intacta").isEqualTo(asignacionAntes);
        assertThat(jdbc.queryForObject("SELECT fecha_fin FROM vinculo_vehiculo_empresa WHERE id=?",Object.class,vvAsignado)).as("cierre incompatible no aplicado").isNull();
        assertThat(jdbc.queryForObject("SELECT fecha_fin FROM vinculo_persona_empresa WHERE id=?",Object.class,vpAsignado)).isNull();

        // ---------- REST real: validación, referencias, ámbito de empresa, historia ----------
        String ta=token(admin),tl=token(lector),to=token(operador);
        String vinculosA="/api/empresas-operadoras/"+empresaA+"/vehiculos",personalA="/api/empresas-operadoras/"+empresaA+"/personal";
        assertThat(api("POST","/api/vehiculos",ta,Map.of("placa",placa)).statusCode()).as("E1 placa repetida").isEqualTo(409);
        assertThat(api("POST","/api/personas",ta,Map.of("ci",ci,"nombres","Otro")).statusCode()).as("E1 CI repetido").isEqualTo(409);
        assertThat(api("POST",vinculosA,ta,Map.of("vehiculoId",UUID.randomUUID(),"fechaInicio","2026-01-01","activo",true)).statusCode()).as("CA-06-06 vehículo inexistente").isEqualTo(404);
        assertThat(api("POST","/api/empresas-operadoras/"+UUID.randomUUID()+"/vehiculos",ta,Map.of("vehiculoId",vehiculoNuevo,"fechaInicio","2026-01-01","activo",true)).statusCode()).as("empresa inexistente").isEqualTo(404);
        assertThat(api("POST",vinculosA,ta,Map.of("vehiculoId",vehiculoNuevo,"fechaInicio","2026-05-01","fechaFin","2026-04-01","activo",true)).statusCode()).as("fechas incoherentes").isEqualTo(400);
        assertThat(api("PUT","/api/empresas-operadoras/"+empresaB+"/vehiculos/"+vvAsignado,ta,cierre(null,"2026-12-31",true)).statusCode()).as("E3 vínculo de otra empresa").isEqualTo(404);
        assertThat(api("PUT","/api/empresas-operadoras/"+empresaB+"/personal/"+vpAsignado,ta,cierre("2026-01-01","2026-12-31",true)).statusCode()).isEqualTo(404);
        // Límite exacto del cierre frente a la jornada asignada (fecha de servicio en Bolivia = 15/06).
        assertThat(api("PUT",vinculosA+"/"+vvAsignado,ta,cierre(null,"2026-06-14",true)).statusCode()).as("E4").isEqualTo(409);
        assertThat(api("PUT",vinculosA+"/"+vvAsignado,ta,cierre(null,"2026-06-15",true)).statusCode()).isEqualTo(200);
        assertThat(api("PUT",vinculosA+"/"+vvAsignado,ta,cierre(null,null,true)).statusCode()).as("reapertura").isEqualTo(200);
        assertThat(api("PUT",personalA+"/"+vpAsignado,ta,cierre("2026-01-01","2026-06-14",true)).statusCode()).as("CU-07 E5").isEqualTo(409);
        assertThat(api("PUT",personalA+"/"+vpAsignado,ta,cierre("2026-01-01","2026-06-15",true)).statusCode()).isEqualTo(200);
        assertThat(api("PUT",personalA+"/"+vpAsignado,ta,cierre("2026-01-02","2026-06-15",true)).statusCode()).as("inicio del segmento inmutable").isEqualTo(400);
        // CU-07 E2/E3: funciones vacías, repetidas, inexistentes o inactivas; persona o empresa inexistentes.
        int personalAntes=contar("SELECT count(*) FROM vinculo_persona_empresa WHERE persona_id=?",personaNueva);
        assertThat(api("POST",personalA,ta,vinculoPersonal(personaNueva,"2027-01-01",null,List.of())).statusCode()).isEqualTo(400);
        assertThat(api("POST",personalA,ta,vinculoPersonal(personaNueva,"2027-01-01",null,List.of(CONDUCTOR,CONDUCTOR))).statusCode()).isEqualTo(400);
        assertThat(api("POST",personalA,ta,vinculoPersonal(personaNueva,"2027-01-01",null,List.of(UUID.randomUUID().toString()))).statusCode()).isEqualTo(400);
        UUID inactiva=UUID.randomUUID();
        jdbc.update("INSERT INTO funcion_operativa(id,codigo,nombre,activo) VALUES (?,?,?,false)",inactiva,"SINTETICA-"+inactiva,"Función sintética inactiva");
        try {
            assertThat(api("POST",personalA,ta,vinculoPersonal(personaNueva,"2027-01-01",null,List.of(inactiva.toString()))).statusCode()).isEqualTo(400);
        } finally { jdbc.update("DELETE FROM funcion_operativa WHERE id=?",inactiva); }
        assertThat(contar("SELECT count(*) FROM vinculo_persona_empresa WHERE persona_id=?",personaNueva)).as("ningún vínculo con funciones inválidas").isEqualTo(personalAntes);
        assertThat(api("POST",personalA,ta,vinculoPersonal(UUID.randomUUID(),"2027-01-01",null,List.of(CONDUCTOR))).statusCode()).isEqualTo(404);
        assertThat(api("POST","/api/empresas-operadoras/"+UUID.randomUUID()+"/personal",ta,vinculoPersonal(personaNueva,"2027-01-01",null,List.of(CONDUCTOR))).statusCode()).isEqualTo(404);
        assertThat(api("DELETE","/api/vehiculos/"+vehiculoNuevo,ta,null).statusCode()).as("sin borrado físico").isIn(403,405);
        assertThat(api("DELETE","/api/personas/"+personaNueva,ta,null).statusCode()).isIn(403,405);

        // ---------- Autorización: lector, A4 y revocación ----------
        assertThat(api("GET","/api/vehiculos",tl,null).statusCode()).isEqualTo(200);
        assertThat(api("GET",personalA,tl,null).statusCode()).isEqualTo(200);
        assertThat(api("POST","/api/vehiculos",tl,Map.of("placa",placa+"X")).statusCode()).isEqualTo(403);
        assertThat(api("POST",personalA,tl,vinculoPersonal(personaNueva,"2027-01-01",null,List.of(CONDUCTOR))).statusCode()).isEqualTo(403);
        assertThat(api("PUT",vinculosA+"/"+vvAsignado,tl,cierre(null,"2026-12-31",true)).statusCode()).isEqualTo(403);
        // A4 (concesión por periodo): sin escritura ni lectura administrativa global de vehículos o personas...
        // incluye_historico: la jornada sintética (agosto 2026) es anterior al otorgamiento; campo existente de la concesión, no un permiso nuevo.
        for(String p:List.of("PLANIFICACION_CONSULTAR","JORNADA_ASIGNAR"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),uOperador,periodoA,p);
        assertThat(api("GET","/api/vehiculos",to,null).statusCode()).isEqualTo(403);
        assertThat(api("POST","/api/vehiculos",to,Map.of("placa",placa+"A4")).statusCode()).isEqualTo(403);
        assertThat(api("POST",vinculosA,to,Map.of("vehiculoId",vehiculoNuevo,"fechaInicio","2026-01-01","activo",true)).statusCode()).isEqualTo(403);
        assertThat(api("POST",personalA,to,vinculoPersonal(personaNueva,"2027-01-01",null,List.of(CONDUCTOR))).statusCode()).isEqualTo(403);
        assertThat(api("PUT",personalA+"/"+vpAsignado,to,cierre("2026-01-01","2026-12-31",true)).statusCode()).isEqualTo(403);
        // ...pero su flujo existente intacto: ve como recursos elegibles los vínculos creados por la interfaz y asigna con ellos.
        UUID jornadaAgosto=UUID.randomUUID();
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",jornadaAgosto,periodoA,version,Timestamp.from(Instant.parse("2026-08-10T12:00:00Z")));
        UUID vvNuevoA=jdbc.queryForObject("SELECT id FROM vinculo_vehiculo_empresa WHERE vehiculo_id=? AND empresa_operadora_id=? AND fecha_inicio=DATE '2026-07-01'",UUID.class,vehiculoNuevo,empresaA);
        UUID vpNuevoA=(UUID)segmentosA.get(1).get("id");
        var recursos=api("GET","/api/empresa/jornadas/"+jornadaAgosto+"/recursos",to,null);
        assertThat(recursos.statusCode()).isEqualTo(200);
        assertThat(recursos.body()).contains(vvNuevoA.toString(),vpNuevoA.toString()).doesNotContain(segmentosA.get(0).get("id").toString());
        var asignar=api("POST","/api/empresa/jornadas/"+jornadaAgosto+"/asignaciones",to,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vvNuevoA,"vinculoPersonaEmpresaId",vpNuevoA));
        assertThat(asignar.statusCode()).isIn(200,201);
        // CA-07-08 sobre datos creados por la interfaz: cerrar el segmento antes de la jornada asignada es incompatible.
        assertThat(api("PUT",personalA+"/"+vpNuevoA,ta,cierre("2026-04-01","2026-07-31",true)).statusCode()).isEqualTo(409);
        assertThat(api("PUT",vinculosA+"/"+vvNuevoA,ta,cierre(null,"2026-07-31",true)).statusCode()).isEqualTo(409);
        assertThat(api("PUT",vinculosA+"/"+vvNuevoA,ta,cierre(null,"2026-08-10",false)).statusCode()).as("cierre compatible").isEqualTo(200);
        // Revocación efectiva sobre el mismo token, sin reiniciar sesión.
        String tr=token(revocado);
        assertThat(api("POST","/api/vehiculos",tr,Map.of("placa",placa+"R1")).statusCode()).isEqualTo(201);
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='VEHICULOS_GESTIONAR' AND revocado_en IS NULL",uRevocado);
        assertThat(api("POST","/api/vehiculos",tr,Map.of("placa",placa+"R2")).statusCode()).as("concesión revocada").isEqualTo(403);
        assertThat(api("GET","/api/vehiculos",tr,null).statusCode()).as("la consulta no revocada sigue").isEqualTo(200);
        assertThat(contar("SELECT count(*) FROM vehiculo WHERE placa=?",placa+"R2")).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM asignacion_jornada WHERE id=?",asignacion)).isEqualTo(asignacionAntes);
    }
}
