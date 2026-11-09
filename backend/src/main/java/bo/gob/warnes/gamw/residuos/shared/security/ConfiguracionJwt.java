package bo.gob.warnes.gamw.residuos.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Firma y validación de tokens con el stack estándar de Spring Security (Nimbus/Jose).
 * No se implementa criptografía propia.
 *
 * Fuera de desarrollo-local el secreto es obligatorio: si falta, está vacío o no alcanza
 * los 32 bytes exigidos por HS256, el arranque falla cerrado. No existe clave efímera.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesJwt.class)
@Profile("!desarrollo-local & !smoke")
public class ConfiguracionJwt {
    private static final int BYTES_MINIMOS = 32;

    @Bean
    SecretKey claveFirmaJwt(PropiedadesJwt propiedades) {
        String secreto = propiedades.secreto();
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException("gamw.seguridad.jwt.secreto es obligatorio fuera de desarrollo-local. "
                    + "Configure GAMW_JWT_SECRET con al menos " + BYTES_MINIMOS + " bytes.");
        }
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < BYTES_MINIMOS) {
            throw new IllegalStateException("gamw.seguridad.jwt.secreto debe tener al menos " + BYTES_MINIMOS
                    + " bytes para firmar con HS256.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder decodificadorJwt(SecretKey claveFirmaJwt, PropiedadesJwt propiedades) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(claveFirmaJwt)
                .macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator tiempo = new JwtTimestampValidator();
        tiempo.setAllowEmptyExpiryClaim(false);
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(propiedades.emisor()), tiempo));
        return decodificador;
    }

    @Bean
    JwtEncoder codificadorJwt(SecretKey claveFirmaJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveFirmaJwt));
    }
}
