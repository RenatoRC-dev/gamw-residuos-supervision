package bo.gob.warnes.gamw.residuos.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import static org.assertj.core.api.Assertions.*;

/** Contextos Spring reales: errores de configuración, sin infraestructura PostgreSQL. */
class ArranqueAutenticacionTests {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class InfraestructuraWeb { }

    @Test void arranqueNormalFallaSinSecretoSinExponerSuValor() {
        new WebApplicationContextRunner().withUserConfiguration(ConfiguracionJwt.class)
                .withPropertyValues("gamw.seguridad.jwt.emisor=gamw-pruebas-arranque")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("gamw.seguridad.jwt.secreto es obligatorio fuera de desarrollo-local. Configure GAMW_JWT_SECRET con al menos 32 bytes.");
                });
    }

    @Test void arranqueNormalFallaConSecretoBlancoOCortoSinExponerlo() {
        for (String secreto : new String[]{"   ", "corto-sintetico"}) {
            new WebApplicationContextRunner().withUserConfiguration(ConfiguracionJwt.class)
                    .withPropertyValues("gamw.seguridad.jwt.emisor=gamw-pruebas-arranque",
                            "gamw.seguridad.jwt.secreto=" + secreto)
                    .run(contexto -> {
                        assertThat(contexto).hasFailed();
                        Throwable causa = contexto.getStartupFailure();
                        while (causa.getCause() != null) causa = causa.getCause();
                        assertThat(causa).isInstanceOf(IllegalStateException.class);
                        assertThat(causa.getMessage()).contains("gamw.seguridad.jwt.secreto");
                        if (!secreto.isBlank()) assertThat(causa.getMessage()).doesNotContain(secreto);
                    });
        }
    }

    @Test void desarrolloLocalNoArrancaConDireccionPublica() {
        new WebApplicationContextRunner()
                .withUserConfiguration(InfraestructuraWeb.class, SecurityConfiguration.class, SeguridadDesarrolloLocal.class)
                .withPropertyValues("spring.profiles.active=desarrollo-local", "server.address=0.0.0.0")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("desarrollo-local requiere server.address de loopback");
                });
    }
}
