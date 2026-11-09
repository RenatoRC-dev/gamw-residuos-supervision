package bo.gob.warnes.gamw.residuos.shared.security;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.api.AutenticacionController;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion.ServicioAutenticacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "gamw.seguridad.jwt.secreto=",
        "server.address=127.0.0.1", "gamw.development.web-origin=http://127.0.0.1:5173"})
@AutoConfigureMockMvc
@ActiveProfiles("desarrollo-local")
@Import(AislamientoDesarrolloLocalTests.Observacion.class)
class AislamientoDesarrolloLocalTests extends ContextoAutenticacionSinBd {
    @Autowired MockMvc mvc;
    @Autowired ConfigurableApplicationContext contexto;

    @TestConfiguration(proxyBeanMethods = false)
    @Import(ObservadorIdentidad.class)
    static class Observacion { }

    /** Endpoint solo de test para observar el principal después de los filtros reales. */
    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class ObservadorIdentidad {
        @GetMapping("/api/jornadas/prueba-aislamiento")
        java.util.Map<String, Boolean> observar() {
            var autenticacion = SecurityContextHolder.getContext().getAuthentication();
            return java.util.Map.of("identidadInstitucional", autenticacion != null
                    && autenticacion.getPrincipal() instanceof UsuarioAutenticado);
        }
    }

    @Test void arrancaSinBdSinJwtYSinUsuariosAutomaticos() {
        for (Class<?> tipo : new Class<?>[]{javax.sql.DataSource.class, org.flywaydb.core.Flyway.class,
                JwtDecoder.class, JwtEncoder.class, ConfiguracionJwt.class, ConvertidorJwtUsuario.class,
                EmisorTokensJwt.class, AutenticacionController.class, ServicioAutenticacion.class, UserDetailsService.class}) {
            assertThat(contexto.getBeansOfType(tipo)).as(tipo.getSimpleName()).isEmpty();
        }
        assertThat(ConditionEvaluationReport.get(contexto.getBeanFactory()).getExclusions())
                .contains("org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration");
    }

    @Test void cabeceraLocalPermiteSoloAccesoTecnicoSinIdentidad() throws Exception {
        mvc.perform(get("/api/jornadas/prueba-aislamiento").header("X-GAMW-Development", "gamw-desarrollo")
                .header("Origin", "http://127.0.0.1:5173"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identidadInstitucional").value(false));
        mvc.perform(get("/api/jornadas/prueba-aislamiento").header("X-GAMW-Development", "gamw-desarrollo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identidadInstitucional").value(false));
        mvc.perform(get("/api/autenticacion/actual").header("X-GAMW-Development", "gamw-desarrollo")
                .header("X-Persona-Id", "inventado").header("X-Usuario-Id", "inventado"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/autenticacion/login").contentType("application/json")
                .content("{\"nombreUsuario\":\"usuario.prueba\",\"contrasena\":\"sintetica\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.tokenAcceso").doesNotExist());
        verifyNoInteractions(usuarios);
    }

    @Test void rechazaCabeceraAusenteIncorrectaOrigenExternoYDireccionRemota() throws Exception {
        String ruta = "/api/jornadas/prueba-aislamiento";
        mvc.perform(get(ruta)).andExpect(status().isForbidden());
        mvc.perform(get(ruta).header("X-GAMW-Development", "incorrecta")).andExpect(status().isForbidden());
        mvc.perform(get(ruta).header("X-GAMW-Development", "gamw-desarrollo").header("Origin", "https://externo.example"))
                .andExpect(status().isForbidden());
        mvc.perform(get(ruta).header("X-GAMW-Development", "gamw-desarrollo")
                .with(request -> { request.setRemoteAddr("192.0.2.1"); return request; }))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/technical/health")).andExpect(status().isOk());
    }
}
