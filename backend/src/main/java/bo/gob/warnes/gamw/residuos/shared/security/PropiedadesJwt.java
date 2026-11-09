package bo.gob.warnes.gamw.residuos.shared.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración técnica del token de acceso. El secreto proviene de configuración/entorno:
 * nunca se versiona un secreto real. La vigencia del token es configurable.
 */
@ConfigurationProperties(prefix = "gamw.seguridad.jwt")
public record PropiedadesJwt(String secreto, Duration duracion, String emisor) {
    private static final Duration DURACION_POR_DEFECTO = Duration.ofMinutes(30);
    private static final String EMISOR_POR_DEFECTO = "gamw-residuos";

    public PropiedadesJwt {
        emisor = emisor == null || emisor.isBlank() ? EMISOR_POR_DEFECTO : emisor.strip();
        duracion = duracion == null || duracion.isZero() || duracion.isNegative() ? DURACION_POR_DEFECTO : duracion;
    }
}
