package bo.gob.warnes.gamw.residuos.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import static org.assertj.core.api.Assertions.*;

/**
 * Fuera de los perfiles técnicos el secreto es obligatorio y debe alcanzar 32 bytes (HS256).
 * El secreto usado aquí es sintético y solo existe como prueba: nunca se versiona uno real.
 */
class ConfiguracionJwtTests {
    private static final String SECRETO_SINTETICO = "secreto-sintetico-solo-para-pruebas-1234567890";
    private static final String EMISOR_CONFIGURADO = "gamw-warnes-pruebas";

    private final ConfiguracionJwt configuracion = new ConfiguracionJwt();

    @Test
    void cierraElArranqueCuandoElSecretoFaltaOEsteEnBlanco() {
        for (String secreto : new String[]{null, "", "   "}) {
            PropiedadesJwt propiedades = new PropiedadesJwt(secreto, null, EMISOR_CONFIGURADO);
            assertThatThrownBy(() -> configuracion.claveFirmaJwt(propiedades))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("obligatorio");
        }
    }

    @Test
    void cierraElArranqueCuandoElSecretoNoAlcanza32BytesUtf8() {
        for (String secreto : new String[]{"corto", "x".repeat(31), "ñ".repeat(15)}) {
            PropiedadesJwt propiedades = new PropiedadesJwt(secreto, null, EMISOR_CONFIGURADO);
            assertThatThrownBy(() -> configuracion.claveFirmaJwt(propiedades))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("32");
        }
    }

    @Test
    void mideElSecretoEnBytesUtf8YNoEnCaracteres() {
        String secretoMultibyte = "ñ".repeat(16);
        assertThat(secretoMultibyte.getBytes(StandardCharsets.UTF_8)).hasSize(32);

        SecretKey clave = configuracion.claveFirmaJwt(new PropiedadesJwt(secretoMultibyte, null, EMISOR_CONFIGURADO));

        assertThat(clave.getEncoded()).hasSize(32);
    }

    @Test
    void creaMaterialDeFirmaHmacSha256ConSecretoSuficiente() {
        SecretKey clave = configuracion.claveFirmaJwt(
                new PropiedadesJwt(SECRETO_SINTETICO, null, EMISOR_CONFIGURADO));

        assertThat(clave.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(clave.getEncoded())
                .hasSize(SECRETO_SINTETICO.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void noSeAplicaEnLosPerfilesTecnicos() {
        Profile perfil = ConfiguracionJwt.class.getAnnotation(Profile.class);

        assertThat(perfil).isNotNull();
        assertThat(perfil.value()).containsExactly("!desarrollo-local & !smoke");
    }

    @Test
    void aplicaDuracionPorDefectoCuandoFaltaOEsInvalida() {
        for (Duration invalida : new Duration[]{null, Duration.ZERO, Duration.ofMinutes(-5)}) {
            assertThat(new PropiedadesJwt(SECRETO_SINTETICO, invalida, EMISOR_CONFIGURADO).duracion())
                    .isEqualTo(Duration.ofMinutes(30));
        }
    }

    @Test
    void conservaLaDuracionYElEmisorConfigurados() {
        PropiedadesJwt propiedades = new PropiedadesJwt(SECRETO_SINTETICO, Duration.ofMinutes(10),
                "  " + EMISOR_CONFIGURADO + "  ");

        assertThat(propiedades.duracion()).isEqualTo(Duration.ofMinutes(10));
        assertThat(propiedades.emisor()).isEqualTo(EMISOR_CONFIGURADO);
    }

    @Test
    void usaElEmisorPorDefectoCuandoNoSeConfigura() {
        assertThat(new PropiedadesJwt(SECRETO_SINTETICO, null, null).emisor()).isEqualTo("gamw-residuos");
        assertThat(new PropiedadesJwt(SECRETO_SINTETICO, null, "  ").emisor()).isEqualTo("gamw-residuos");
    }
}
