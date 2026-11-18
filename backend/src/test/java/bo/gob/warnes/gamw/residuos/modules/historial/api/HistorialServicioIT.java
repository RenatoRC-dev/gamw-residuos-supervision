package bo.gob.warnes.gamw.residuos.modules.historial.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * CU-29 por HTTP real: autorización municipal (JORNADAS_CONSULTAR), filtros, lista vacía, rangos inválidos,
 * cadena de reprogramación y asignaciones históricas. Datos sintéticos con UUID propios; no se eliminan
 * registros históricos (inmutables). La jornada histórica se construye solo por los servicios autorizados existentes.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class HistorialServicioIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString();
    private final UUID empresaA=UUID.randomUUID(),empresaB=UUID.randomUUID(),periodoA=UUID.randomUUID(),periodoB=UUID.randomUUID(),ruta=UUID.randomUUID(),
        rv1=UUID.randomUUID(),vehiculo1=UUID.randomUUID(),vehiculo2=UUID.randomUUID(),persona1=UUID.randomUUID(),persona2=UUID.randomUUID(),
        vv1=UUID.randomUUID(),vv2=UUID.randomUUID(),vp1=UUID.randomUUID(),vp2=UUID.randomUUID();
    private final Instant inicio1=Instant.now().plus(10,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private final Instant inicio2=Instant.now().plus(20,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private final Instant inicioB=Instant.now().plus(30,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private UUID j1,j2,jB; private String placa1,placa2;

    record Resp(int estado,String cuerpo) { }
    private record Cuenta(UUID id,String nombre) { }

    @BeforeEach void fixtures() throws Exception {
        for(UUID e:List.of(empresaA,empresaB)) jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",e,"SINTETICA-CU29-"+e);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoA,empresaA);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodoB,empresaB);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"SINTETICA-CU29-"+ruta,"Ruta sintética CU-29");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",rv1,ruta);
        placa1="SINTETICA-"+vehiculo1.toString().toUpperCase(Locale.ROOT); placa2="SINTETICA-"+vehiculo2.toString().toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo1,placa1);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo2,placa2);
        for(UUID p:List.of(persona1,persona2)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"SINTETICA-"+p,"Persona sintética CU29");
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv1,vehiculo1,empresaA);
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv2,vehiculo2,empresaA);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp1,persona1,empresaA);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp2,persona2,empresaA);
        for(UUID vp:List.of(vp1,vp2)) jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);

        // Historia construida únicamente con las rutas autorizadas existentes: A4 programa, asigna, reasigna y reprograma.
        var a4=usuario(); String t4=token(a4);
        for(String p:List.of("JORNADA_PROGRAMAR","JORNADA_ASIGNAR","JORNADA_REPROGRAMAR")) operador(a4,periodoA,p);
        var prog=enviar("POST","/api/empresa/periodos/"+periodoA+"/jornadas",t4,Map.of("solicitudId",UUID.randomUUID(),"rutaVersionId",rv1,"inicioProgramado",inicio1.toString()));
        assertThat(prog.estado()).isEqualTo(200);
        j1=UUID.fromString(json.readTree(prog.cuerpo()).get("id").asText());
        assertThat(enviar("POST","/api/empresa/jornadas/"+j1+"/asignaciones",t4,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vv1,"vinculoPersonaEmpresaId",vp1)).estado()).isEqualTo(200);
        assertThat(enviar("POST","/api/empresa/jornadas/"+j1+"/reasignaciones",t4,Map.of("solicitudId",UUID.randomUUID(),"vinculoVehiculoEmpresaId",vv2,"vinculoPersonaEmpresaId",vp2,"motivoCambio","Cambio sintético CU-29")).estado()).isEqualTo(200);
        var rep=enviar("POST","/api/empresa/jornadas/"+j1+"/reprogramaciones",t4,Map.of("solicitudId",UUID.randomUUID(),"inicioProgramado",inicio2.toString(),"motivoReprogramacion","Reprogramación sintética CU-29"));
        assertThat(rep.estado()).isEqualTo(200);
        j2=UUID.fromString(json.readTree(rep.cuerpo()).get("id").asText());
        var mun=usuario(); String tm=token(mun); municipal(mun,"JORNADAS_PROGRAMAR");
        var b=enviar("POST","/api/jornadas",tm,Map.of("periodoServicioId",periodoB,"rutaVersionId",rv1,"inicioProgramado",inicioB.toString()));
        assertThat(b.estado()).isEqualTo(201);
        jB=UUID.fromString(json.readTree(b.cuerpo()).get("jornada").get("id").asText());
    }

    private Cuenta usuario() {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID(); String nombre="cu29-"+id;
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"CU29-"+persona,"Persona sintética CU29");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        return new Cuenta(id,nombre);
    }
    private String token(Cuenta c) throws Exception {
        var r=enviar("POST","/api/autenticacion/login",null,Map.of("nombreUsuario",c.nombre(),"contrasena",clave));
        assertThat(r.estado()).isEqualTo(200);
        return json.readTree(r.cuerpo()).get("tokenAcceso").asText();
    }
    private void municipal(Cuenta c,String permiso) {
        jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),c.id(),permiso);
    }
    private void operador(Cuenta c,UUID periodo,String permiso) {
        jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,operador_local,motivo) VALUES (?,?,?,?,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),c.id(),periodo,permiso);
    }
    private Resp enviar(String metodo,String ruta,String jwt,Object cuerpo) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(10));
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        if(cuerpo==null) b.method(metodo,HttpRequest.BodyPublishers.noBody());
        else b.header("Content-Type","application/json").method(metodo,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(),r.body());
    }
    private String consultor() throws Exception { var c=usuario(); municipal(c,"JORNADAS_CONSULTAR"); return token(c); }
    private static final String BASE="/api/historial-servicio/jornadas";
    private static LocalDate fechaServicio(Instant i) { return i.atZone(ZoneId.of("America/La_Paz")).toLocalDate(); }
    private List<String> ids(Resp r) {
        var l=new ArrayList<String>(); for(JsonNode n:json.readTree(r.cuerpo()).get("jornadas")) l.add(n.get("id").asText()); return l;
    }

    @Test void autorizacionServidorSinAmpliarAcceso() throws Exception {
        assertThat(enviar("GET",BASE,null,null).estado()).isEqualTo(401);
        var sin=usuario(); String ts=token(sin);
        assertThat(enviar("GET",BASE,ts,null).estado()).isEqualTo(403);
        assertThat(enviar("GET",BASE+"/"+j1,ts,null).estado()).isEqualTo(403);
        var solo=usuario(); municipal(solo,"JORNADAS_PROGRAMAR"); // otra concesión municipal no habilita consulta histórica
        assertThat(enviar("GET",BASE,token(solo),null).estado()).isEqualTo(403);
        var a4=usuario(); operador(a4,periodoA,"JORNADA_REPROGRAMAR"); // concesión empresarial no es acceso municipal
        assertThat(enviar("GET",BASE+"/"+j1,token(a4),null).estado()).isEqualTo(403);
        var rev=usuario(); municipal(rev,"JORNADAS_CONSULTAR"); String tr=token(rev);
        assertThat(enviar("GET",BASE,tr,null).estado()).isEqualTo(200);
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba',motivo_revocacion='fixture' WHERE usuario_id=? AND permiso='JORNADAS_CONSULTAR' AND revocado_en IS NULL",rev.id());
        assertThat(enviar("GET",BASE,tr,null).estado()).isEqualTo(403);
        assertThat(enviar("GET",BASE+"/"+j1,tr,null).estado()).isEqualTo(403);
    }

    @Test void filtrosPaginacionVacioYRangosInvalidos() throws Exception {
        String t=consultor();
        var porPeriodo=enviar("GET",BASE+"?periodoId="+periodoA,t,null);
        assertThat(porPeriodo.estado()).isEqualTo(200);
        assertThat(ids(porPeriodo)).containsExactly(j2.toString(),j1.toString()); // más reciente primero
        assertThat(json.readTree(porPeriodo.cuerpo()).get("siguiente").isNull()).isTrue();
        var porEmpresaB=enviar("GET",BASE+"?empresaId="+empresaB,t,null);
        assertThat(ids(porEmpresaB)).containsExactly(jB.toString());
        // Misma ruta, empresas distintas: cada jornada conserva la identidad de su empresa/periodo.
        var vistaB=json.readTree(porEmpresaB.cuerpo()).get("jornadas").get(0);
        assertThat(vistaB.get("empresa").get("id").asText()).isEqualTo(empresaB.toString());
        assertThat(vistaB.get("periodo").get("id").asText()).isEqualTo(periodoB.toString());
        var porFecha=enviar("GET",BASE+"?periodoId="+periodoA+"&desde="+fechaServicio(inicio2)+"&hasta="+fechaServicio(inicio2),t,null);
        assertThat(ids(porFecha)).containsExactly(j2.toString());
        var vacia=enviar("GET",BASE+"?periodoId="+UUID.randomUUID(),t,null);
        assertThat(vacia.estado()).isEqualTo(200);
        assertThat(ids(vacia)).isEmpty();
        assertThat(enviar("GET",BASE+"?desde=2030-01-02&hasta=2030-01-01",t,null).estado()).isEqualTo(400);
        assertThat(enviar("GET",BASE+"?periodoId=no-es-uuid",t,null).estado()).isEqualTo(400);
        assertThat(enviar("GET",BASE+"?desde=31-12-2030",t,null).estado()).isEqualTo(400);
        assertThat(enviar("GET",BASE+"?desplazamiento=-1",t,null).estado()).isEqualTo(400);
        assertThat(enviar("GET",BASE+"/"+UUID.randomUUID(),t,null).estado()).isEqualTo(404);
    }

    @Test void continuidadEntreOperadoresFiltroRutaJornadaYCambiosEnDatosVigentes() throws Exception {
        String t=consultor();
        var porRuta=enviar("GET",BASE+"?rutaId="+ruta,t,null);
        assertThat(porRuta.estado()).isEqualTo(200);
        assertThat(ids(porRuta)).containsExactlyInAnyOrder(j1.toString(),j2.toString(),jB.toString());
        var empresaPorJornada=new HashMap<String,String>();
        for(JsonNode n:json.readTree(porRuta.cuerpo()).get("jornadas")) empresaPorJornada.put(n.get("id").asText(),n.get("empresa").get("id").asText());
        assertThat(empresaPorJornada.get(j1.toString())).isEqualTo(empresaA.toString());
        assertThat(empresaPorJornada.get(jB.toString())).isEqualTo(empresaB.toString());
        assertThat(ids(enviar("GET",BASE+"?jornadaId="+jB,t,null))).containsExactly(jB.toString());
        assertThat(ids(enviar("GET",BASE+"?rutaId="+UUID.randomUUID(),t,null))).isEmpty();
        // Un cambio en el dato vigente de la empresa no reasigna jornadas ni asignaciones: se conservan las identidades históricas.
        jdbc.update("UPDATE empresa_operadora SET razon_social=? WHERE id=?","RENOMBRADA-"+empresaA,empresaA);
        var d=json.readTree(enviar("GET",BASE+"/"+j1,t,null).cuerpo());
        assertThat(d.get("jornada").get("empresa").get("id").asText()).isEqualTo(empresaA.toString());
        assertThat(d.get("jornada").get("periodo").get("id").asText()).isEqualTo(periodoA.toString());
        assertThat(d.get("jornada").get("ruta").get("versionId").asText()).isEqualTo(rv1.toString());
        assertThat(d.get("asignaciones").get(0).get("vinculoVehiculoEmpresaId").asText()).isEqualTo(vv1.toString());
        assertThat(d.get("asignaciones").get(1).get("vinculoPersonaEmpresaId").asText()).isEqualTo(vp2.toString());
        assertThat(ids(enviar("GET",BASE+"?empresaId="+empresaB,t,null))).containsExactly(jB.toString());
    }

    @Test void detalleConservaCadenaAsignacionesYValoresOriginalesSinEscribir() throws Exception {
        String t=consultor();
        int jornadasAntes=jdbc.queryForObject("SELECT count(*) FROM jornada",Integer.class),asignacionesAntes=jdbc.queryForObject("SELECT count(*) FROM asignacion_jornada",Integer.class);
        var d1=enviar("GET",BASE+"/"+j1,t,null);
        assertThat(d1.estado()).isEqualTo(200);
        var n1=json.readTree(d1.cuerpo());
        var jornada=n1.get("jornada");
        assertThat(jornada.get("vigente").asBoolean()).isFalse();
        assertThat(jornada.get("sucesoraId").asText()).isEqualTo(j2.toString());
        assertThat(jornada.get("empresa").get("id").asText()).isEqualTo(empresaA.toString());
        assertThat(jornada.get("periodo").get("id").asText()).isEqualTo(periodoA.toString());
        assertThat(jornada.get("ruta").get("versionId").asText()).isEqualTo(rv1.toString());
        assertThat(jornada.get("ruta").get("numeroVersion").asInt()).isEqualTo(1);
        var cadena=n1.get("cadena");
        assertThat(cadena.size()).isEqualTo(2);
        assertThat(cadena.get(0).get("id").asText()).isEqualTo(j1.toString());
        assertThat(cadena.get(1).get("id").asText()).isEqualTo(j2.toString());
        assertThat(cadena.get(1).get("antecedenteId").asText()).isEqualTo(j1.toString());
        assertThat(cadena.get(1).get("motivoReprogramacion").asText()).isEqualTo("Reprogramación sintética CU-29");
        var asignaciones=n1.get("asignaciones");
        assertThat(asignaciones.size()).isEqualTo(2);
        assertThat(asignaciones.get(0).get("placa").asText()).isEqualTo(placa1);
        assertThat(asignaciones.get(0).get("vigenteHasta").isNull()).isFalse(); // segmento cerrado por la reasignación
        assertThat(asignaciones.get(1).get("placa").asText()).isEqualTo(placa2);
        assertThat(asignaciones.get(1).get("vigenteHasta").isNull()).isTrue();
        assertThat(asignaciones.get(1).get("motivoCambio").asText()).isEqualTo("Cambio sintético CU-29");
        assertThat(asignaciones.get(0).get("vigenteHasta")).isEqualTo(asignaciones.get(1).get("vigenteDesde"));
        assertThat(n1.get("inconsistencias").toString()).doesNotContain("otra empresa");
        var enlaces=n1.get("enlaces").toString();
        assertThat(enlaces).contains("/trayectorias?jornada="+j1,"/revision-gps?jornada="+j1,"/fiscalizacion?jornada="+j1,"/correctivas?jornada="+j1);
        // Evidencia: sin permiso propio no se sabe si existe («sin acceso» ≠ «sin registros»); ejecución no se consulta.
        for(JsonNode e:n1.get("enlaces")) {
            String modulo=e.get("modulo").asText(),estado=e.get("estado").asText();
            assertThat(estado).as(modulo).isEqualTo(modulo.startsWith("Eventos de ejecución")||modulo.startsWith("Revisión técnica")?"NO_CONSULTADO":"ACCESO_NO_PERMITIDO");
            assertThat(e.get("cantidad").isNull()).isTrue();
        }
        var amplio=usuario(); for(String p:List.of("JORNADAS_CONSULTAR","TRAYECTORIA_JORNADA_CONSULTAR","FISCALIZACION_CONSULTAR","CORRECTIVA_CONSULTAR","RECLAMO_CONSULTAR")) municipal(amplio,p);
        var ev=json.readTree(enviar("GET",BASE+"/"+j1,token(amplio),null).cuerpo()).get("enlaces");
        for(JsonNode e:ev) if(!e.get("modulo").asText().startsWith("Eventos de ejecución")&&!e.get("modulo").asText().startsWith("Revisión técnica")) {
            assertThat(e.get("estado").asText()).isEqualTo("SIN_REGISTROS"); assertThat(e.get("cantidad").asInt()).isZero();
        }
        // La sucesora no hereda asignaciones y lo declara; la cadena es la misma desde cualquier extremo.
        var n2=json.readTree(enviar("GET",BASE+"/"+j2,t,null).cuerpo());
        assertThat(n2.get("asignaciones").size()).isZero();
        assertThat(n2.get("inconsistencias").toString()).contains("no registra asignaciones");
        assertThat(n2.get("jornada").get("antecedenteId").asText()).isEqualTo(j1.toString());
        assertThat(n2.get("cadena").size()).isEqualTo(2);
        // Solo lectura: ningún registro creado ni modificado.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jornada",Integer.class)).isEqualTo(jornadasAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asignacion_jornada",Integer.class)).isEqualTo(asignacionesAntes);
        // Los métodos de escritura no existen sobre la consulta histórica.
        assertThat(enviar("POST",BASE,t,Map.of()).estado()).isIn(403,405);
        assertThat(enviar("DELETE",BASE+"/"+j1,t,null).estado()).isIn(403,405);
    }
}
