package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Autorización operativa V19 por HTTP real (servidor, filtros, JWT y PostgreSQL aislado), sin perfil desarrollo-local.
 * Solo datos sintéticos con UUID propios. Las concesiones, actuaciones y jornadas son inmutables por diseño (V15/V19):
 * esta clase no las elimina; el aislamiento lo da el UUID único de cada fixture.
 *
 * NO cubierto (bloqueo documentado, sin afirmaciones falsas):
 * - Concurrencia de solicitudId idéntico en paralelo: requiere sincronización determinista de dos transacciones
 *   con bloqueo FOR UPDATE/SHARE; la idempotencia secuencial sí se verifica. La concurrencia de dominio ya la cubre JornadasIT.
 * - Upgrade poblado V18→V19 y delegación (A1→A4) por la CLI: no son verificables por HTTP en esta clase.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class AutorizacionOperativaV19IT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString();
    private final UUID empresaA=UUID.randomUUID(),empresaB=UUID.randomUUID(),periodoA=UUID.randomUUID(),periodoB=UUID.randomUUID(),
        ruta=UUID.randomUUID(),versionRuta=UUID.randomUUID(),
        vehiculoA=UUID.randomUUID(),vehiculoB=UUID.randomUUID(),personaA=UUID.randomUUID(),personaB=UUID.randomUUID(),
        vvA=UUID.randomUUID(),vvB=UUID.randomUUID(),vpA=UUID.randomUUID(),vpB=UUID.randomUUID();
    private final Instant inicio=Instant.now().plus(10,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    record Resp(int estado,String cuerpo) { }

    @BeforeEach void fixturesSinteticos() {
        for(UUID e:List.of(empresaA,empresaB)) jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",e,"SINTETICA-V19-"+e);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoA,empresaA);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoB,empresaB);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"SINTETICA-V19-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",versionRuta,ruta);
        for(UUID v:List.of(vehiculoA,vehiculoB)) jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",v,"SINTETICA-"+v.toString().toUpperCase(Locale.ROOT));
        for(UUID p:List.of(personaA,personaB)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"SINTETICA-"+p,"Persona sintética");
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vvA,vehiculoA,empresaA);
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vvB,vehiculoB,empresaB);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpA,personaA,empresaA);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpB,personaB,empresaB);
        for(UUID vp:List.of(vpA,vpB)) jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);
    }

    // ---------- utilidades ----------
    private record Cuenta(UUID id,String nombre) { }
    private Cuenta usuario() {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID(); String nombre="v19-"+id;
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"V19-"+persona,"Persona sintética V19");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        return new Cuenta(id,nombre);
    }
    private String token(Cuenta c) throws Exception {
        var r=enviar("POST","/api/autenticacion/login",null,Map.of("nombreUsuario",c.nombre(),"contrasena",clave));
        assertThat(r.estado()).isEqualTo(200);
        return json.readTree(r.cuerpo()).get("tokenAcceso").asText();
    }
    private void municipal(Cuenta c,String permiso) {
        jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",
                UUID.randomUUID(),c.id(),permiso);
    }
    private void operador(Cuenta c,UUID periodo,String permiso) {
        jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,operador_local,motivo) VALUES (?,?,?,?,'prueba-sintetica','fixture sintético')",
                UUID.randomUUID(),c.id(),periodo,permiso);
    }
    private void revocarMunicipal(Cuenta c,String permiso) {
        assertThat(jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba',motivo_revocacion='fixture' WHERE usuario_id=? AND permiso=? AND revocado_en IS NULL",c.id(),permiso)).isEqualTo(1);
    }
    private void revocarOperador(Cuenta c,UUID periodo,String permiso) {
        assertThat(jdbc.update("UPDATE concesion_periodo_operador SET revocado_en=clock_timestamp(),revocado_por_local='prueba',motivo_revocacion='fixture' WHERE usuario_id=? AND periodo_servicio_id=? AND permiso=? AND revocado_en IS NULL",c.id(),periodo,permiso)).isEqualTo(1);
    }
    private UUID jornadaSql(UUID periodo) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",id,periodo,versionRuta,Timestamp.from(inicio.plusSeconds(3600)));
        return id;
    }
    private Resp enviar(String metodo,String ruta,String jwt,Object cuerpo) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(10));
        if(jwt!=null) b.header("Authorization","Bearer "+jwt); // nunca cabecera/cookie CSRF ni X-GAMW-Development
        if(cuerpo==null) b.method(metodo,HttpRequest.BodyPublishers.noBody());
        else b.header("Content-Type","application/json").method(metodo,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(),r.body());
    }
    private Map<String,Object> programacionMunicipal(UUID periodo,Instant desde) {
        return new HashMap<>(Map.of("periodoServicioId",periodo,"rutaVersionId",versionRuta,"inicioProgramado",desde.toString()));
    }
    private Map<String,Object> programar(UUID solicitud,Instant desde) {
        return new HashMap<>(Map.of("solicitudId",solicitud,"rutaVersionId",versionRuta,"inicioProgramado",desde.toString()));
    }
    private Map<String,Object> vinculos(UUID v,UUID p) { return new HashMap<>(Map.of("vinculoVehiculoEmpresaId",v,"vinculoPersonaEmpresaId",p)); }
    private Map<String,Object> asignar(UUID solicitud,UUID v,UUID p) { var m=vinculos(v,p); m.put("solicitudId",solicitud); return m; }
    private int contar(String sql,Object... a) { return jdbc.queryForObject(sql,Integer.class,a); }
    private int jornadas(UUID periodo) { return contar("SELECT count(*) FROM jornada WHERE periodo_servicio_id=?",periodo); }

    // ---------- 1 ----------
    @Test void anonimosInvalidosYSinConcesionSonRechazados() throws Exception {
        assertThat(enviar("GET","/api/jornadas",null,null).estado()).isEqualTo(401);
        assertThat(enviar("GET","/api/empresa/periodos-servicio",null,null).estado()).isEqualTo(401);
        // Sin Bearer, CSRF actúa antes de la autenticación: 401 o 403, nunca éxito.
        assertThat(enviar("POST","/api/jornadas",null,programacionMunicipal(periodoA,inicio)).estado()).isIn(401,403);
        assertThat(enviar("GET","/api/jornadas","token-sintetico-invalido",null).estado()).isEqualTo(401);
        var sin=usuario(); String t=token(sin);
        assertThat(enviar("GET","/api/jornadas",t,null).estado()).isEqualTo(403);
        assertThat(enviar("POST","/api/jornadas",t,programacionMunicipal(periodoA,inicio)).estado()).isEqualTo(403);
        assertThat(enviar("GET","/api/empresa/periodos-servicio",t,null).estado()).isEqualTo(403);
        assertThat(enviar("POST","/api/empresa/periodos/"+periodoA+"/jornadas",t,programar(UUID.randomUUID(),inicio)).estado()).isEqualTo(403);
        assertThat(enviar("GET","/api/no-existe-sintetico",t,null).estado()).isIn(401,403); // denyAll
        assertThat(jornadas(periodoA)).isZero();
    }

    // ---------- 2 y 3 ----------
    @Test void bearerSinCsrfEscribeSoloConLaConcesionMunicipalPersistidaExacta() throws Exception {
        var a1=usuario(); String t=token(a1);
        municipal(a1,"JORNADAS_PROGRAMAR");
        var creada=enviar("POST","/api/jornadas",t,programacionMunicipal(periodoA,inicio)); // sin token CSRF
        assertThat(creada.estado()).isEqualTo(201);
        UUID jornada=UUID.fromString(json.readTree(creada.cuerpo()).get("jornada").get("id").asText());
        // PROGRAMAR no concede CONSULTAR ni ASIGNAR; la concesión se evalúa contra la tabla en cada petición.
        assertThat(enviar("GET","/api/jornadas",t,null).estado()).isEqualTo(403);
        assertThat(enviar("POST","/api/jornadas/"+jornada+"/asignaciones",t,vinculos(vvA,vpA)).estado()).isEqualTo(403);
        assertThat(contar("SELECT count(*) FROM asignacion_jornada WHERE jornada_id=?",jornada)).isZero();
        municipal(a1,"JORNADAS_CONSULTAR");
        assertThat(enviar("GET","/api/jornadas/"+jornada,t,null).estado()).isEqualTo(200);
        // Permiso de otro módulo no abre jornadas, y viceversa.
        assertThat(enviar("GET","/api/personas",t,null).estado()).isEqualTo(403);
        municipal(a1,"JORNADAS_ASIGNAR");
        assertThat(enviar("POST","/api/jornadas/"+jornada+"/asignaciones",t,vinculos(vvA,vpA)).estado()).isEqualTo(201);
        // Reprogramación municipal por /api/jornadas está cerrada aunque existan los demás permisos.
        var rep=programacionMunicipal(periodoA,inicio.plusSeconds(7200)); rep.put("motivoReprogramacion","Prueba sintética");
        assertThat(enviar("POST","/api/jornadas/"+jornada+"/reprogramaciones",t,rep).estado()).isEqualTo(403);
    }

    // ---------- 4 ----------
    @Test void revocarConcesionBloqueaElMismoJwtYDesactivarUsuarioLoInvalida() throws Exception {
        var a1=usuario(); String t=token(a1);
        municipal(a1,"JORNADAS_CONSULTAR");
        assertThat(enviar("GET","/api/jornadas",t,null).estado()).isEqualTo(200);
        revocarMunicipal(a1,"JORNADAS_CONSULTAR");
        assertThat(enviar("GET","/api/jornadas",t,null).estado()).isEqualTo(403);
        assertThat(contar("SELECT count(*) FROM concesion_permiso WHERE usuario_id=? AND revocado_en IS NOT NULL",a1.id())).isEqualTo(1); // historia conservada

        var a4=usuario(); String t4=token(a4);
        operador(a4,periodoA,"PLANIFICACION_CONSULTAR");
        assertThat(enviar("GET","/api/empresa/periodos-servicio",t4,null).estado()).isEqualTo(200);
        revocarOperador(a4,periodoA,"PLANIFICACION_CONSULTAR");
        assertThat(enviar("GET","/api/empresa/periodos-servicio",t4,null).estado()).isEqualTo(403);

        var ex=usuario(); String te=token(ex);
        municipal(ex,"JORNADAS_CONSULTAR");
        assertThat(enviar("GET","/api/jornadas",te,null).estado()).isEqualTo(200);
        jdbc.update("UPDATE usuario SET activo=false WHERE id=?",ex.id());
        assertThat(enviar("GET","/api/jornadas",te,null).estado()).isEqualTo(401);
    }

    // ---------- 5 ----------
    @Test void operadorA4SoloVeYActuaEnElPeriodoConcedido() throws Exception {
        var a4=usuario(); String t=token(a4);
        operador(a4,periodoA,"JORNADA_PROGRAMAR"); operador(a4,periodoA,"JORNADA_ASIGNAR");
        UUID jornadaB=jornadaSql(periodoB);
        var periodos=enviar("GET","/api/empresa/periodos-servicio",t,null);
        assertThat(periodos.estado()).isEqualTo(200);
        assertThat(periodos.cuerpo()).contains(periodoA.toString()).doesNotContain(periodoB.toString());
        assertThat(enviar("GET","/api/empresa/periodos/"+periodoB+"/referencias",t,null).estado()).isEqualTo(403);
        assertThat(enviar("GET","/api/empresa/periodos/"+periodoA+"/referencias",t,null).estado()).isEqualTo(200);
        assertThat(enviar("POST","/api/empresa/periodos/"+periodoB+"/jornadas",t,programar(UUID.randomUUID(),inicio)).estado()).isEqualTo(403);
        assertThat(jornadas(periodoB)).isEqualTo(1); // solo la jornada fixture
        var propia=enviar("POST","/api/empresa/periodos/"+periodoA+"/jornadas",t,programar(UUID.randomUUID(),inicio));
        assertThat(propia.estado()).isEqualTo(200);
        UUID jornadaA=UUID.fromString(json.readTree(propia.cuerpo()).get("id").asText());
        assertThat(json.readTree(propia.cuerpo()).get("periodoServicioId").asText()).isEqualTo(periodoA.toString());
        var lista=enviar("GET","/api/empresa/jornadas",t,null);
        assertThat(lista.estado()).isEqualTo(200);
        assertThat(lista.cuerpo()).contains(jornadaA.toString()).doesNotContain(jornadaB.toString());
        assertThat(enviar("GET","/api/empresa/jornadas/"+jornadaB,t,null).estado()).isEqualTo(403);
        assertThat(enviar("GET","/api/empresa/jornadas/"+jornadaB+"/recursos",t,null).estado()).isEqualTo(403);
        assertThat(enviar("POST","/api/empresa/jornadas/"+jornadaB+"/asignaciones",t,asignar(UUID.randomUUID(),vvB,vpB)).estado()).isEqualTo(403);
        assertThat(contar("SELECT count(*) FROM asignacion_jornada WHERE jornada_id=?",jornadaB)).isZero();
        // El ámbito empresarial no sustituye la concesión municipal.
        assertThat(enviar("GET","/api/jornadas",t,null).estado()).isEqualTo(403);
        // Cuerpo con empresa/periodo/actor: se determinan en servidor, no se aceptan.
        var extra=programar(UUID.randomUUID(),inicio.plusSeconds(60)); extra.put("empresaOperadoraId",empresaB);
        assertThat(enviar("POST","/api/empresa/periodos/"+periodoA+"/jornadas",t,extra).estado()).isEqualTo(400);
    }

    // ---------- 6 ----------
    @Test void asignarRecursosDeOtraEmpresaSeRechazaSinTocarLaHistoria() throws Exception {
        var a4=usuario(); String t=token(a4);
        operador(a4,periodoA,"JORNADA_PROGRAMAR"); operador(a4,periodoA,"JORNADA_ASIGNAR");
        UUID jornada=UUID.fromString(json.readTree(enviar("POST","/api/empresa/periodos/"+periodoA+"/jornadas",t,programar(UUID.randomUUID(),inicio)).cuerpo()).get("id").asText());
        int actuaciones=contar("SELECT count(*) FROM actuacion_planificacion_operador WHERE jornada_id=?",jornada);
        for(var par:List.of(List.of(vvB,vpA),List.of(vvA,vpB),List.of(vvB,vpB))) {
            assertThat(enviar("POST","/api/empresa/jornadas/"+jornada+"/asignaciones",t,asignar(UUID.randomUUID(),par.get(0),par.get(1))).estado()).isEqualTo(400);
        }
        assertThat(contar("SELECT count(*) FROM asignacion_jornada WHERE jornada_id=?",jornada)).isZero();
        assertThat(contar("SELECT count(*) FROM actuacion_planificacion_operador WHERE jornada_id=?",jornada)).isEqualTo(actuaciones);
        var buena=enviar("POST","/api/empresa/jornadas/"+jornada+"/asignaciones",t,asignar(UUID.randomUUID(),vvA,vpA));
        assertThat(buena.estado()).isEqualTo(200);
        var antes=jdbc.queryForList("SELECT id,vinculo_vehiculo_empresa_id,vinculo_persona_empresa_id,vigente_desde,vigente_hasta,motivo_cambio FROM asignacion_jornada WHERE jornada_id=? ORDER BY vigente_desde,id",jornada);
        var rea=asignar(UUID.randomUUID(),vvB,vpA); rea.put("motivoCambio","Prueba sintética");
        assertThat(enviar("POST","/api/empresa/jornadas/"+jornada+"/reasignaciones",t,rea).estado()).isEqualTo(400);
        var despues=jdbc.queryForList("SELECT id,vinculo_vehiculo_empresa_id,vinculo_persona_empresa_id,vigente_desde,vigente_hasta,motivo_cambio FROM asignacion_jornada WHERE jornada_id=? ORDER BY vigente_desde,id",jornada);
        assertThat(despues).isEqualTo(antes).hasSize(1);
        assertThat(despues.getFirst().get("vigente_hasta")).isNull();
        // Los recursos elegibles ofrecidos pertenecen solo a la empresa derivada del periodo.
        var recursos=enviar("GET","/api/empresa/jornadas/"+jornada+"/recursos",t,null);
        assertThat(recursos.estado()).isEqualTo(200);
        assertThat(recursos.cuerpo()).contains(vvA.toString(),vpA.toString()).doesNotContain(vvB.toString(),vpB.toString());
    }

    // ---------- 7 ----------
    @Test void programacionYAsignacionConservanAutorizacionEIdempotencia() throws Exception {
        var a4=usuario(); String t=token(a4);
        operador(a4,periodoA,"JORNADA_PROGRAMAR"); operador(a4,periodoA,"JORNADA_ASIGNAR");
        UUID solicitud=UUID.randomUUID(); String ruta="/api/empresa/periodos/"+periodoA+"/jornadas";
        var primera=enviar("POST",ruta,t,programar(solicitud,inicio));
        var replica=enviar("POST",ruta,t,programar(solicitud,inicio));
        assertThat(primera.estado()).isEqualTo(200); assertThat(replica.estado()).isEqualTo(200);
        UUID jornada=UUID.fromString(json.readTree(primera.cuerpo()).get("id").asText());
        assertThat(json.readTree(replica.cuerpo()).get("id").asText()).isEqualTo(jornada.toString());
        assertThat(jornadas(periodoA)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM actuacion_planificacion_operador WHERE solicitud_id=? AND usuario_id=? AND operacion='PROGRAMAR'",solicitud,a4.id())).isEqualTo(1);
        // Misma solicitud con otro contenido, otro actor u otra operación: 409; nada nuevo.
        assertThat(enviar("POST",ruta,t,programar(solicitud,inicio.plusSeconds(86400))).estado()).isEqualTo(409);
        var otro=usuario(); String to=token(otro);
        operador(otro,periodoA,"JORNADA_PROGRAMAR");
        assertThat(enviar("POST",ruta,to,programar(solicitud,inicio)).estado()).isEqualTo(409);
        assertThat(enviar("POST","/api/empresa/jornadas/"+jornada+"/asignaciones",t,asignar(solicitud,vvA,vpA)).estado()).isEqualTo(409);
        assertThat(jornadas(periodoA)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM asignacion_jornada WHERE jornada_id=?",jornada)).isZero();
        // Fecha anterior al otorgamiento sin autorización histórica: 403 sin crear jornada.
        assertThat(enviar("POST",ruta,t,programar(UUID.randomUUID(),Instant.now().minus(2,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))).estado()).isEqualTo(403);
        assertThat(jornadas(periodoA)).isEqualTo(1);
        // Asignación idempotente por solicitudId.
        UUID sa=UUID.randomUUID();
        assertThat(enviar("POST","/api/empresa/jornadas/"+jornada+"/asignaciones",t,asignar(sa,vvA,vpA)).estado()).isEqualTo(200);
        assertThat(enviar("POST","/api/empresa/jornadas/"+jornada+"/asignaciones",t,asignar(sa,vvA,vpA)).estado()).isEqualTo(200);
        assertThat(contar("SELECT count(*) FROM asignacion_jornada WHERE jornada_id=?",jornada)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM actuacion_planificacion_operador WHERE solicitud_id=?",sa)).isEqualTo(1);
        assertThat(enviar("POST","/api/empresa/jornadas/"+jornada+"/asignaciones",t,asignar(sa,vvA,vpB)).estado()).isIn(400,409); // contenido distinto o vínculo ajeno
        // Tras revocar ASIGNAR, ni siquiera la réplica con el mismo solicitudId pasa: la autorización va primero.
        revocarOperador(a4,periodoA,"JORNADA_ASIGNAR");
        assertThat(enviar("POST","/api/empresa/jornadas/"+jornada+"/asignaciones",t,asignar(sa,vvA,vpA)).estado()).isEqualTo(403);
        assertThat(contar("SELECT count(*) FROM asignacion_jornada WHERE jornada_id=?",jornada)).isEqualTo(1);
        // La actuación registrada es inmutable.
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
            () -> jdbc.update("UPDATE actuacion_planificacion_operador SET operacion='ASIGNAR' WHERE solicitud_id=?",solicitud));
    }
}
