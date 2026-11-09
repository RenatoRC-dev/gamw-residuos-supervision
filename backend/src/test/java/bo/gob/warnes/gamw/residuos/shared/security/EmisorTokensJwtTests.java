package bo.gob.warnes.gamw.residuos.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import static org.assertj.core.api.Assertions.*;

/**
 * El token emitido identifica al Usuario por subject; los demás claims son descriptivos.
 * No se emiten credenciales ni roles: FuncionOperativa no es un rol de seguridad.
 * El secreto usado aquí es sintético y solo existe como prueba.
 */
class EmisorTokensJwtTests {
    private static final String SECRETO_SINTETICO = "secreto-sintetico-solo-para-pruebas-1234567890";
    private static final String EMISOR_CONFIGURADO = "https://warnes.gob.bo/gamw-pruebas";
    private static final Duration DURACION_CONFIGURADA = Duration.ofMinutes(15);
    private static final Duration TOLERANCIA = Duration.ofSeconds(10);
    private static final Set<String> CLAIMS_REGISTRADOS = Set.of("iss", "sub", "exp", "iat", "nbf", "aud", "jti");

    private final ConfiguracionJwt configuracion = new ConfiguracionJwt();
    private final PropiedadesJwt propiedades = new PropiedadesJwt(SECRETO_SINTETICO, DURACION_CONFIGURADA,
            EMISOR_CONFIGURADO);
    private final JwtEncoder codificador;
    private final JwtDecoder decodificador;
    private final EmisorTokensJwt emisor;

    EmisorTokensJwtTests() {
        var clave = configuracion.claveFirmaJwt(propiedades);
        this.codificador = configuracion.codificadorJwt(clave);
        this.decodificador = configuracion.decodificadorJwt(clave, propiedades);
        this.emisor = new EmisorTokensJwt(codificador, propiedades);
    }

    @Test
    void emiteElTokenComoIdentificacionDelUsuario() {
        UUID usuarioId = UUID.randomUUID();
        UsuarioAutenticado usuario = new UsuarioAutenticado(usuarioId, UUID.randomUUID(), "usuario.prueba");

        Jwt token = decodificar(emisor.emitir(usuario.usuarioId(), usuario.personaNaturalId(), usuario.nombreUsuario()));

        assertThat(token.getSubject()).isEqualTo(usuarioId.toString());
        assertThat(token.getClaimAsString(EmisorTokensJwt.CLAIM_USUARIO_ID)).isEqualTo(usuarioId.toString());
        assertThat(token.getClaimAsString(EmisorTokensJwt.CLAIM_PERSONA_NATURAL_ID))
                .isEqualTo(usuario.personaNaturalId().toString());
        assertThat(token.getClaimAsString(EmisorTokensJwt.CLAIM_NOMBRE_USUARIO)).isEqualTo(usuario.nombreUsuario());
    }

    @Test
    void emiteElEmisorConfiguradoYLaCabeceraHs256() {
        Jwt token = decodificar(emitirTokenDePrueba());

        assertThat(token.getIssuer()).hasToString(EMISOR_CONFIGURADO);
        assertThat(String.valueOf(token.getHeaders().get("alg"))).isEqualTo("HS256");
    }

    @Test
    void incluyeEmisionYExpiracionCoherentesConLaDuracionConfigurada() {
        EmisorTokensJwt.TokenEmitido emitido = emitirTokenDePrueba();
        Jwt token = decodificar(emitido);

        assertThat(token.getIssuedAt()).isNotNull();
        assertThat(token.getExpiresAt()).isNotNull();
        assertThat(token.getExpiresAt()).isAfter(token.getIssuedAt());
        assertThat(Duration.between(token.getExpiresAt(), emitido.expiraEn()).abs())
                .isLessThanOrEqualTo(Duration.ofSeconds(1));

        Duration vigencia = Duration.between(token.getIssuedAt(), token.getExpiresAt());
        assertThat(vigencia).isBetween(DURACION_CONFIGURADA.minus(TOLERANCIA),
                DURACION_CONFIGURADA.plus(TOLERANCIA));
    }

    @Test
    void noEmiteClaimsDeCredencialesNiDeAutorizacion() {
        Jwt token = decodificar(emitirTokenDePrueba());

        List<String> claves = token.getClaims().keySet().stream()
                .map(clave -> clave.toLowerCase(Locale.ROOT))
                .toList();

        assertThat(claves).doesNotContain("contrasena", "contrasenahash", "password", "hash");
        assertThat(claves).doesNotContain("roles", "authorities", "scope", "scp", "permissions", "permisos");
        assertThat(claves).doesNotContain("refresh_token", "refreshtoken");

        Set<String> descriptivos = claves.stream()
                .filter(clave -> !CLAIMS_REGISTRADOS.contains(clave))
                .collect(Collectors.toSet());
        assertThat(descriptivos).containsOnly("usuarioid", "personanaturalid", "nombreusuario");
    }

    @Test
    void elTokenEsValidableConElDecodificadorConfigurado() {
        EmisorTokensJwt.TokenEmitido emitido = emitirTokenDePrueba();

        assertThat(decodificador.decode(emitido.token())).isNotNull();
    }

    private EmisorTokensJwt.TokenEmitido emitirTokenDePrueba() {
        return emisor.emitir(UUID.randomUUID(), UUID.randomUUID(), "usuario.prueba");
    }

    private Jwt decodificar(EmisorTokensJwt.TokenEmitido emitido) {
        assertThat(emitido.token()).isNotBlank();
        Instant expiraEn = emitido.expiraEn();
        assertThat(expiraEn).isNotNull();
        return decodificador.decode(emitido.token());
    }
}
