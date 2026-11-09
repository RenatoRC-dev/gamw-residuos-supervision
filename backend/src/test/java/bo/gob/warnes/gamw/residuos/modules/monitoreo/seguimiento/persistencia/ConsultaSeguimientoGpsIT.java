package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.persistencia;

import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.aplicacion.ConsultarSeguimientoGps;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.aplicacion.RegistrarPosicionesGps;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion.RegistrarEventosEjecucion;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.TipoEventoEjecucion;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU-16/CU-15 parciales: consulta interna sobre evidencia sintética aceptada por servicios existentes. */
@SpringBootTest(properties = {"spring.config.import=", "debug=false", "logging.level.root=ERROR"})
@AutoConfigureMockMvc
class ConsultaSeguimientoGpsIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder contrasenas;
    @Autowired RegistrarEventosEjecucion ejecucion;
    @Autowired RegistrarPosicionesGps ingreso;
    @Autowired ConsultarSeguimientoGps consulta;
    @Autowired EmisorTokensJwt tokens;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private final UUID persona=UUID.randomUUID(), usuario=UUID.randomUUID(), empresa=UUID.randomUUID(),
            periodo=UUID.randomUUID(), ruta=UUID.randomUUID(), version=UUID.randomUUID(), vehiculo=UUID.randomUUID(),
            vv=UUID.randomUUID(), vp=UUID.randomUUID(), jornada=UUID.randomUUID(), vacia=UUID.randomUUID(), asignacion=UUID.randomUUID();
    private final Instant inicio=Instant.parse("2026-11-09T04:00:00Z");
    private UsuarioAutenticado actor;

    @BeforeEach void datosSoloEnClusterDesechable() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Sintética consulta GPS");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2027-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"CONSULTA-"+ruta,"Referencia sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"CONSULTA-"+vehiculo.toString().toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"CONSULTA-"+persona,"Persona sintética");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,persona,empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000001')",vp);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",usuario,persona,"CONSULTA-"+usuario,contrasenas.encode(UUID.randomUUID().toString()));
        for (UUID j : List.of(jornada,vacia)) jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,NULL,NULL,true,now(),now())",j,periodo,version,Timestamp.from(inicio));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacion,jornada,vv,vp,Timestamp.from(inicio.minusSeconds(60)));
        actor=new UsuarioAutenticado(usuario,persona,"CONSULTA-"+usuario);
        ejecucion.registrarAutenticado(jornada,UUID.randomUUID(),asignacion,TipoEventoEjecucion.INICIO,inicio,actor);
    }
    private RegistrarPosicionesGps.Lectura lectura(UUID captura,long segundos) {
        // Contrato sintético únicamente, no medición física ni ubicación oficial.
        return new RegistrarPosicionesGps.Lectura(captura,inicio.plusSeconds(segundos).plusNanos(123456000),
                20.12345678912345,-10.98765432198765,3.123456789123);
    }

    @Test void ordenTotalPorCapturaNoPorRecepcionYPreservaPostgisYGeojson() throws Exception {
        UUID uno=UUID.fromString("00000000-0000-4000-8000-000000000001"), dos=UUID.fromString("00000000-0000-4000-8000-000000000002"), tres=UUID.randomUUID();
        var reciente=ingreso.registrar(jornada,asignacion,List.of(lectura(tres,30)),actor).getFirst();
        Thread.sleep(5); // La recepción de las capturas antiguas ocurre realmente después.
        var tardias=ingreso.registrar(jornada,asignacion,List.of(lectura(dos,10),lectura(uno,10)),actor);
        var vista=consulta.consultar(jornada);
        assertThat(vista.trayectoria().features()).extracting(p->p.id()).containsExactly(uno,dos,tres);
        assertThat(consulta.consultar(jornada)).isEqualTo(vista);
        assertThat(vista.ultimaPosicion().id()).isEqualTo(tres);
        assertThat(tardias.getFirst().recibidoEn()).isAfter(reciente.recibidoEn());
        var punto=vista.trayectoria().features().getFirst();
        var confirmacion=tardias.getLast();
        assertThat(punto.properties().capturadoEn()).isEqualTo(confirmacion.capturadoEn());
        assertThat(punto.properties().recibidoEn()).isEqualTo(confirmacion.recibidoEn());
        assertThat(punto.properties().precisionMetros()).isEqualTo(confirmacion.precisionMetros());
        assertThat(punto.properties().usuarioId()).isEqualTo(usuario);
        assertThat(punto.properties().asignacionJornadaId()).isEqualTo(asignacion);
        var serializado=json.readTree(json.writeValueAsString(vista));
        var geometria=serializado.get("trayectoria").get("features").get(0).get("geometry");
        assertThat(geometria.get("type").asText()).isEqualTo("Point");
        assertThat(geometria.get("coordinates").get(0).asDouble()).isEqualTo(confirmacion.longitud());
        assertThat(geometria.get("coordinates").get(1).asDouble()).isEqualTo(confirmacion.latitud());
        assertThat(jdbc.queryForObject("SELECT ST_SRID(geometria) FROM posicion_gps WHERE jornada_id=? AND captura_id=?",Integer.class,jornada,uno)).isEqualTo(4326);
        assertThat(serializado.toString()).doesNotContain("cumplimiento","fraude");
        assertThat(consulta.consultar(vacia).trayectoria().features()).isEmpty();
    }
    @Test void vaciaNoUsaUbicacionDeOtraJornadaNiInventaUnaPosicion() {
        ingreso.registrar(jornada,asignacion,List.of(lectura(UUID.randomUUID(),5)),actor);
        var vista=consulta.consultar(vacia);
        assertThat(vista.jornadaId()).isEqualTo(vacia);
        assertThat(vista.trayectoria().features()).isEmpty();
        assertThat(vista.ultimaPosicion()).isNull();
    }
    @Test void sincronizacionPosteriorAFinMantieneTiempoOriginalYEvidenciaInmutable() {
        var p=lectura(UUID.randomUUID(),10);
        ejecucion.registrarAutenticado(jornada,UUID.randomUUID(),asignacion,TipoEventoEjecucion.FIN,inicio.plusSeconds(60),actor);
        ingreso.registrar(jornada,asignacion,List.of(p),actor);
        var original=consulta.consultar(jornada);
        ingreso.registrar(jornada,asignacion,List.of(p),actor);
        assertThat(consulta.consultar(jornada)).isEqualTo(original);
        assertThat(original.ultimaPosicion().properties().capturadoEn()).isEqualTo(p.capturadoEn());
        assertThat(original.ultimaPosicion().properties().recibidoEn()).isAfter(inicio.plusSeconds(60));
    }
    @Test void consultasNoSePublicanNiSeHabilitanPorJwtOCabeceraTecnica() throws Exception {
        String token=tokens.emitir(usuario,persona,actor.nombreUsuario()).token();
        for(String ruta:List.of("/api/jornadas/"+jornada+"/trayectoria", "/api/jornadas/"+jornada+"/posicion-actual", "/api/monitoreo", "/api/personas")) {
            mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mvc.perform(get(ruta).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            mvc.perform(get(ruta).header("X-GAMW-Development","gamw-desarrollo")).andExpect(status().isUnauthorized());
        }
    }
}
