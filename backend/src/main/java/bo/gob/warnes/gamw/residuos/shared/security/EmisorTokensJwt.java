package bo.gob.warnes.gamw.residuos.shared.security;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Emite el token de acceso firmado con la clave y la vigencia configuradas.
 * El subject es siempre usuario.id; los demás claims son descriptivos y no autorizan por sí solos.
 */
@Component
@Profile("!desarrollo-local & !smoke")
public class EmisorTokensJwt {
    public static final String CLAIM_USUARIO_ID = "usuarioId";
    public static final String CLAIM_PERSONA_NATURAL_ID = "personaNaturalId";
    public static final String CLAIM_NOMBRE_USUARIO = "nombreUsuario";

    private final JwtEncoder codificador;
    private final PropiedadesJwt propiedades;

    public EmisorTokensJwt(JwtEncoder codificador, PropiedadesJwt propiedades) {
        this.codificador = codificador;
        this.propiedades = propiedades;
    }

    public TokenEmitido emitir(UUID usuarioId, UUID personaNaturalId, String nombreUsuario) {
        Instant emitidoEn = Instant.now();
        Instant expiraEn = emitidoEn.plus(propiedades.duracion());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .subject(usuarioId.toString())
                .issuedAt(emitidoEn)
                .expiresAt(expiraEn)
                .claim(CLAIM_USUARIO_ID, usuarioId.toString())
                .claim(CLAIM_PERSONA_NATURAL_ID, personaNaturalId.toString())
                .claim(CLAIM_NOMBRE_USUARIO, nombreUsuario)
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenEmitido(token, expiraEn);
    }

    public record TokenEmitido(String token, Instant expiraEn) {
        @Override
        public String toString() {
            return "TokenEmitido[token=[REDACTADO], expiraEn=" + expiraEn + "]";
        }
    }
}
