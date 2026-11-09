package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU-13 paso 3 y RN-CU-13-05/06: identidad real, proyección histórica y PostgreSQL aislado. */
@SpringBootTest(properties={"spring.config.import=", "debug=false", "logging.level.root=ERROR"})
@AutoConfigureMockMvc
class ContextoMisJornadasIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmisorTokensJwt tokens;
    @Autowired PasswordEncoder claves;
    @Autowired ObjectMapper json;
    private final UUID personaA=UUID.randomUUID(),personaB=UUID.randomUUID(),usuarioA=UUID.randomUUID(),usuarioB=UUID.randomUUID(),
            empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version1=UUID.randomUUID(),version2=UUID.randomUUID(),
            vehiculo1=UUID.randomUUID(),vehiculo2=UUID.randomUUID(),vv1=UUID.randomUUID(),vv2=UUID.randomUUID(),
            vpA=UUID.randomUUID(),vpB=UUID.randomUUID(),jornada=UUID.randomUUID(),asignacion=UUID.randomUUID();
    @BeforeEach void prepararSoloDatosSinteticos() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU13");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2026-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"CU13-"+ruta,"Referencia sintética histórica");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z','2026-06-01T00:00:00Z',NULL,now())",version1,ruta);
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,2,ST_GeomFromText('LINESTRING(-12 22,-13 23)',4326),'2026-06-01T00:00:00Z',NULL,'Cambio sintético',now())",version2,ruta);
        for(UUID v:List.of(vehiculo1,vehiculo2)) jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",v,"CU13-"+v.toString().toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv1,vehiculo1,empresa);
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv2,vehiculo2,empresa);
        for(UUID p:List.of(personaA,personaB)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"CU13-"+p,"Persona sintética");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpA,personaA,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpB,personaB,empresa);
        for(UUID vp:List.of(vpA,vpB)) jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);
        String hash=claves.encode(UUID.randomUUID().toString());
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",usuarioA,personaA,"CU13-"+usuarioA,hash);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",usuarioB,personaB,"CU13-"+usuarioB,hash);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-05-10T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornada,periodo,version1);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,'2026-05-10T11:00:00Z',NULL,NULL,now())",asignacion,jornada,vv1,vpA);
    }
    private String token(UUID usuario) { return tokens.emitir(usuario,UUID.randomUUID(),"claim-no-confiable").token(); }
    private JsonNode consultar(UUID usuario) throws Exception {
        return json.readTree(mvc.perform(get("/api/mis-jornadas").header("Authorization","Bearer "+token(usuario)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void devuelveContextoOperativoExactoSinSustituirPorUltimaVersion() throws Exception {
        var datos=consultar(usuarioA); assertThat(datos.size()).isEqualTo(1);
        var j=datos.get(0); var c=j.get("contextoOperativo");
        assertThat(j.get("jornadaId").asText()).isEqualTo(jornada.toString());
        assertThat(j.get("inicioProgramado").asText()).isEqualTo("2026-05-10T12:00:00Z");
        assertThat(c.get("ruta").get("rutaId").asText()).isEqualTo(ruta.toString());
        assertThat(c.get("ruta").get("codigo").asText()).isEqualTo("CU13-"+ruta);
        assertThat(c.get("ruta").get("nombre").asText()).isEqualTo("Referencia sintética histórica");
        assertThat(c.get("ruta").get("rutaVersionId").asText()).isEqualTo(version1.toString());
        assertThat(c.get("ruta").get("numeroVersion").asInt()).isEqualTo(1);
        assertThat(c.get("vehiculo").get("id").asText()).isEqualTo(vehiculo1.toString());
        assertThat(c.get("vehiculo").get("placa").asText()).isEqualTo("CU13-"+vehiculo1.toString().toUpperCase(Locale.ROOT));
        assertThat(c.get("periodo").get("id").asText()).isEqualTo(periodo.toString());
        assertThat(c.get("periodo").get("fechaInicio").asText()).isEqualTo("2026-01-01");
        assertThat(c.get("periodo").get("fechaFin").asText()).isEqualTo("2026-12-31");
        assertThat(c.get("periodo").get("empresaOperadoraId").asText()).isEqualTo(empresa.toString());
        assertThat(c.get("periodo").get("razonSocial").asText()).isEqualTo("Empresa sintética CU13");
        assertThat(c.get("vinculoPersonaEmpresaId").asText()).isEqualTo(vpA.toString());
        assertThat(c.get("vinculoVehiculoEmpresaId").asText()).isEqualTo(vv1.toString());
    }
    @Test void conservaVehiculoHistoricoAnteReasignacionYDatosInactivos() throws Exception {
        jdbc.update("UPDATE asignacion_jornada SET vigente_hasta='2026-05-10T13:00:00Z' WHERE id=?",asignacion);
        UUID nueva=UUID.randomUUID();
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,'2026-05-10T13:00:00Z',NULL,'Reasignación sintética',now())",nueva,jornada,vv2,vpB);
        jdbc.update("UPDATE vehiculo SET activo=false WHERE id=?",vehiculo1);
        jdbc.update("UPDATE periodo_servicio SET activo=false WHERE id=?",periodo);
        jdbc.update("UPDATE ruta SET activo=false WHERE id=?",ruta);
        var antes=jdbc.queryForMap("SELECT * FROM asignacion_jornada WHERE id=?",asignacion);
        var a=consultar(usuarioA).get(0); var b=consultar(usuarioB).get(0);
        assertThat(a.get("vigenteHasta").asText()).isEqualTo("2026-05-10T13:00:00Z");
        assertThat(a.get("contextoOperativo").get("vehiculo").get("id").asText()).isEqualTo(vehiculo1.toString());
        assertThat(b.get("asignacionJornadaId").asText()).isEqualTo(nueva.toString());
        assertThat(b.get("contextoOperativo").get("vehiculo").get("id").asText()).isEqualTo(vehiculo2.toString());
        assertThat(b.get("contextoOperativo").get("ruta").get("rutaVersionId").asText()).isEqualTo(version1.toString());
        assertThat(jdbc.queryForMap("SELECT * FROM asignacion_jornada WHERE id=?",asignacion)).isEqualTo(antes);
    }
    @Test void conservaDatosOpcionalesAusentesYSinAsignacionesDevuelveVacio() throws Exception {
        var j=consultar(usuarioA).get(0);
        assertThat(j.get("finProgramado").isNull()).isTrue();
        assertThat(j.get("contextoOperativo").get("vehiculo").get("anio").isNull()).isTrue();
        assertThat(consultar(usuarioB).isEmpty()).isTrue();
        assertThatThrownBy(()->jdbc.update("DELETE FROM vehiculo WHERE id=?",vehiculo1))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(consultar(usuarioA).get(0)).isEqualTo(j);
    }
    @Test void rechazaAccesoAjenoYSuplantacionSinAbrirApisGenerales() throws Exception {
        var respuesta=mvc.perform(get("/api/mis-jornadas").header("Authorization","Bearer "+token(usuarioB))
                .param("personaId",personaA.toString()).param("jornadaId",jornada.toString()).header("X-Persona-Id",personaA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(respuesta).isEmpty()).isTrue();
        mvc.perform(get("/api/mis-jornadas")).andExpect(status().isUnauthorized());
        for(String api:List.of("/api/jornadas/"+jornada,"/api/vehiculos/"+vehiculo1,"/api/rutas/"+ruta,"/api/periodos-servicio"))
            mvc.perform(get(api).header("Authorization","Bearer "+token(usuarioB))).andExpect(status().isForbidden());
    }
}
