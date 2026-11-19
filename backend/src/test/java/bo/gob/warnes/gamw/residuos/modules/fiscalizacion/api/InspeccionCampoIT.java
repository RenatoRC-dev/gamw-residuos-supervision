package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * CU-20 / RF-17 (sin fotografías): alta de inspección de campo con JWT real y PostgreSQL/PostGIS aislado. Es observación humana, NO resultado de
 * fiscalización (RN-CU-20-01). Fotografías deshabilitadas: no se inventan políticas de retención/privacidad (PENDING-09).
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class InspeccionCampoIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    private final UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada=UUID.randomUUID();
    private UUID inspector,otroInspector,soloConsulta,conductor;

    @BeforeEach void fixtures() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-20");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"IC-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version);
        inspector=cuenta("INSPECCION_REGISTRAR","FISCALIZACION_CONSULTAR","JORNADAS_CONSULTAR");
        otroInspector=cuenta("INSPECCION_REGISTRAR","FISCALIZACION_CONSULTAR");
        soloConsulta=cuenta("FISCALIZACION_CONSULTAR");
        conductor=cuenta();
    }

    private UUID cuenta(String... permisos) {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"IC-"+persona,"Persona sintética");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"ic-"+id,codificador.encode("clave-sintetica"));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private String bearer(UUID usuario) { return "Bearer "+emisor.emitir(usuario,UUID.randomUUID(),"no-confiable").token(); }
    private Map<String,Object> inspeccion(UUID solicitud,Instant fecha) {
        var d=new LinkedHashMap<String,Object>();
        d.put("solicitudId",solicitud); d.put("realizadaEn",fecha.toString()); d.put("lugarDescripcion","Esquina sintética"); d.put("latitud",20.25); d.put("longitud",-10.75);
        d.put("observaciones","Contenedor desbordado, observación original sintética."); d.put("adjuntos",List.of());
        return d;
    }
    private int estado(UUID usuario,UUID j,Object cuerpo) throws Exception {
        var b=post("/api/fiscalizacion/jornadas/"+j+"/inspecciones").contentType("application/json").content(json.writeValueAsString(cuerpo));
        if(usuario!=null) b.header("Authorization",bearer(usuario));
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }
    private JsonNode registrar(UUID usuario,Object cuerpo,int esperado) throws Exception {
        var r=mvc.perform(post("/api/fiscalizacion/jornadas/"+jornada+"/inspecciones").contentType("application/json").content(json.writeValueAsString(cuerpo))
            .header("Authorization",bearer(usuario))).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(esperado);
        return r.getContentAsString().isBlank()?null:json.readTree(r.getContentAsString());
    }
    private int inspecciones() { return jdbc.queryForObject("SELECT count(*) FROM inspeccion WHERE jornada_id=?",Integer.class,jornada); }

    @Test void registraObservacionConAutorFechasUbicacionYSinResultadoNiSancion() throws Exception {
        Instant fecha=Instant.now().minus(2,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        UUID solicitud=UUID.randomUUID();
        var creada=registrar(inspector,inspeccion(solicitud,fecha),200);
        assertThat(creada.get("inspectorUsuarioId").asText()).as("autor desde la sesión, no del cliente").isEqualTo(inspector.toString());
        assertThat(creada.get("jornadaId").asText()).isEqualTo(jornada.toString());
        assertThat(creada.get("observaciones").asText()).isEqualTo("Contenedor desbordado, observación original sintética.");
        assertThat(Instant.parse(creada.get("realizadaEn").asText())).isEqualTo(fecha);
        assertThat(Instant.parse(creada.get("registradaEn").asText())).as("registro por el servidor, distinto de la fecha declarada").isAfter(fecha);
        assertThat(creada.get("longitud").asDouble()).isEqualTo(-10.75); // [longitud, latitud]
        assertThat(creada.get("latitud").asDouble()).isEqualTo(20.25);
        assertThat(creada.get("evidencias").size()).as("sin fotografías").isZero();
        UUID id=UUID.fromString(creada.get("id").asText());
        assertThat(jdbc.queryForObject("SELECT ST_SRID(geometria) FROM inspeccion WHERE id=?",Integer.class,id)).isEqualTo(4326);
        assertThat(jdbc.queryForObject("SELECT GeometryType(geometria) FROM inspeccion WHERE id=?",String.class,id)).isEqualTo("POINT");
        assertThat(jdbc.queryForObject("SELECT inspector_usuario_id FROM inspeccion WHERE id=?",UUID.class,id)).isEqualTo(inspector);
        // La inspección NO crea resultado de fiscalización ni acción/sanción alguna.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accion_correctiva WHERE jornada_id=?",Integer.class,jornada)).isZero();
        // Disponible como antecedente en el expediente de CU-21 (sin dictamen) y en el historial CU-29 (conteo con permiso propio).
        var expediente=json.readTree(mvc.perform(get("/api/fiscalizacion/jornadas/"+jornada).header("Authorization",bearer(inspector))).andReturn().getResponse().getContentAsString());
        assertThat(expediente.get("inspecciones").size()).isEqualTo(1);
        assertThat(expediente.get("inspecciones").get(0).get("id").asText()).isEqualTo(id.toString());
        assertThat(expediente.get("fiscalizacion").isNull()).isTrue();
        assertThat(expediente.get("politicaEvidencias").get("fotografiasHabilitadas").asBoolean()).as("fotografías deshabilitadas por defecto").isFalse();
        assertThat(expediente.get("puedeRegistrarInspeccion").asBoolean()).isTrue();
        var historial=json.readTree(mvc.perform(get("/api/historial-servicio/jornadas/"+jornada).header("Authorization",bearer(inspector))).andReturn().getResponse().getContentAsString());
        for(JsonNode e:historial.get("enlaces")) if(e.get("modulo").asText().equals("Inspecciones")) {
            assertThat(e.get("estado").asText()).isEqualTo("CON_REGISTROS"); assertThat(e.get("cantidad").asInt()).isEqualTo(1);
        }
        // Inmutabilidad: la inspección original no se puede alterar ni borrar.
        assertThatThrownBy(() -> jdbc.update("UPDATE inspeccion SET observaciones='alterada' WHERE id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inspeccion WHERE id=?",id)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT observaciones FROM inspeccion WHERE id=?",String.class,id)).startsWith("Contenedor desbordado");
        // Sin coordenadas también es válida (datos mínimos: fecha y observación).
        var minima=new LinkedHashMap<String,Object>(); minima.put("solicitudId",UUID.randomUUID()); minima.put("realizadaEn",fecha.plusSeconds(60).toString());
        minima.put("observaciones","Solo observación"); minima.put("adjuntos",List.of());
        var sinGeo=registrar(inspector,minima,200);
        assertThat(sinGeo.get("latitud").isNull()).isTrue();
        assertThat(inspecciones()).isEqualTo(2);
    }

    @Test void reintentoIdempotenteYConflictosNoSobrescriben() throws Exception {
        UUID solicitud=UUID.randomUUID(); Instant fecha=Instant.now().minus(1,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        var primera=registrar(inspector,inspeccion(solicitud,fecha),200);
        var replica=registrar(inspector,inspeccion(solicitud,fecha),200);
        assertThat(replica.get("id")).isEqualTo(primera.get("id"));
        assertThat(replica.get("registradaEn")).as("la réplica devuelve el original, no uno nuevo").isEqualTo(primera.get("registradaEn"));
        assertThat(inspecciones()).isEqualTo(1);
        var distinta=inspeccion(solicitud,fecha); distinta.put("observaciones","Contenido distinto con la misma solicitud");
        registrar(inspector,distinta,409);
        registrar(otroInspector,inspeccion(solicitud,fecha),409); // otro actor, misma solicitud
        assertThat(inspecciones()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT observaciones FROM inspeccion WHERE solicitud_id=?",String.class,solicitud)).startsWith("Contenedor desbordado");
    }

    @Test void datosInvalidosJornadaInexistenteYActoresNoAutorizadosNoRegistranNada() throws Exception {
        Instant fecha=Instant.now().minus(1,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        var latitudFuera=inspeccion(UUID.randomUUID(),fecha); latitudFuera.put("latitud",91.0);
        registrar(inspector,latitudFuera,400);
        var longitudFuera=inspeccion(UUID.randomUUID(),fecha); longitudFuera.put("longitud",-181.0);
        registrar(inspector,longitudFuera,400);
        var soloLatitud=inspeccion(UUID.randomUUID(),fecha); soloLatitud.remove("longitud"); soloLatitud.put("longitud",null);
        registrar(inspector,soloLatitud,400);
        var sinObservacion=inspeccion(UUID.randomUUID(),fecha); sinObservacion.put("observaciones"," ");
        registrar(inspector,sinObservacion,400);
        var sinFecha=inspeccion(UUID.randomUUID(),fecha); sinFecha.remove("realizadaEn");
        registrar(inspector,sinFecha,400);
        var sinSolicitud=inspeccion(UUID.randomUUID(),fecha); sinSolicitud.remove("solicitudId");
        registrar(inspector,sinSolicitud,400);
        var nanos=inspeccion(UUID.randomUUID(),fecha); nanos.put("realizadaEn",fecha.plusNanos(123).toString());
        registrar(inspector,nanos,400);
        var campoAjeno=inspeccion(UUID.randomUUID(),fecha); campoAjeno.put("inspectorUsuarioId",otroInspector);
        registrar(inspector,campoAjeno,400);
        var adjuntoSinArchivo=inspeccion(UUID.randomUUID(),fecha); adjuntoSinArchivo.put("adjuntos",List.of(Map.of("origen","cámara","descripcion","foto")));
        registrar(inspector,adjuntoSinArchivo,400);
        assertThat(inspecciones()).as("ninguna inspección inválida deja registro").isZero();
        // Jornada inexistente: 404 con permiso; sin permiso no se revela existencia (403).
        assertThat(estado(inspector,UUID.randomUUID(),inspeccion(UUID.randomUUID(),fecha))).isEqualTo(404);
        assertThat(estado(soloConsulta,UUID.randomUUID(),inspeccion(UUID.randomUUID(),fecha))).isEqualTo(403);
        // Actores no autorizados: consulta no es registro, conductor sin concesiones, sin identidad.
        registrar(soloConsulta,inspeccion(UUID.randomUUID(),fecha),403);
        registrar(conductor,inspeccion(UUID.randomUUID(),fecha),403);
        assertThat(estado(null,jornada,inspeccion(UUID.randomUUID(),fecha))).isIn(401,403);
        assertThat(inspecciones()).isZero();
        var expediente=json.readTree(mvc.perform(get("/api/fiscalizacion/jornadas/"+jornada).header("Authorization",bearer(soloConsulta))).andReturn().getResponse().getContentAsString());
        assertThat(expediente.get("puedeRegistrarInspeccion").asBoolean()).isFalse();
    }

    @Test void fotografiasPermanecenDeshabilitadasSinPoliticaAprobada() throws Exception {
        Instant fecha=Instant.now().minus(1,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        var datos=inspeccion(UUID.randomUUID(),fecha); datos.put("adjuntos",List.of(Map.of("origen","cámara sintética","descripcion","foto sintética")));
        var multipart=multipart("/api/fiscalizacion/jornadas/"+jornada+"/inspecciones")
            .file(new MockMultipartFile("datos","","application/json",json.writeValueAsBytes(datos)))
            .file(new MockMultipartFile("archivos","foto.jpg","image/jpeg",new byte[] {(byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xD9}))
            .header("Authorization",bearer(inspector));
        assertThat(mvc.perform(multipart).andReturn().getResponse().getStatus()).as("fotografías deshabilitadas: política pendiente").isEqualTo(409);
        assertThat(inspecciones()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidencia_inspeccion e JOIN inspeccion i ON i.id=e.inspeccion_id WHERE i.jornada_id=?",Integer.class,jornada)).isZero();
        // La misma observación por multipart SIN archivos (como hace la interfaz) sí se registra.
        var sinArchivos=inspeccion(UUID.randomUUID(),fecha);
        var ok=multipart("/api/fiscalizacion/jornadas/"+jornada+"/inspecciones")
            .file(new MockMultipartFile("datos","","application/json",json.writeValueAsBytes(sinArchivos))).header("Authorization",bearer(inspector));
        assertThat(mvc.perform(ok).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(inspecciones()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT registrada_en FROM inspeccion WHERE jornada_id=?",Timestamp.class,jornada)).isNotNull();
    }
}
