package bo.gob.warnes.gamw.residuos.shared.security;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.api.AutenticacionController;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion.ServicioAutenticacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "gamw.seguridad.jwt.secreto="})
@AutoConfigureMockMvc
@ActiveProfiles("smoke")
class ArranqueSmokeAutenticacionTests extends ContextoAutenticacionSinBd {
    @Autowired ConfigurableApplicationContext contexto;
    @Autowired MockMvc mvc;

    @Test void arrancaSinBdSinStackInstitucionalNiUsuarioPredeterminado() {
        for (Class<?> tipo : new Class<?>[]{javax.sql.DataSource.class, org.flywaydb.core.Flyway.class,
                JwtDecoder.class, JwtEncoder.class, ConfiguracionJwt.class, ConvertidorJwtUsuario.class,
                EmisorTokensJwt.class, AutenticacionController.class, ServicioAutenticacion.class, UserDetailsService.class}) {
            assertThat(contexto.getBeansOfType(tipo)).as(tipo.getSimpleName()).isEmpty();
        }
        assertThat(ConditionEvaluationReport.get(contexto.getBeanFactory()).getExclusions())
                .contains("org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration");
    }

    @Test void healthEsPublicoPeroCabeceraLocalNoAutenticaEnSmoke() throws Exception {
        mvc.perform(get("/api/technical/health")).andExpect(status().isOk());
        mvc.perform(get("/api/autenticacion/actual").header("X-GAMW-Development", "gamw-desarrollo"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jornadas").header("X-GAMW-Development", "gamw-desarrollo"))
                .andExpect(status().isUnauthorized());
    }
}
