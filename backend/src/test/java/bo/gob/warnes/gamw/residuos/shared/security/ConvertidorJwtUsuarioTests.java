package bo.gob.warnes.gamw.residuos.shared.security;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.RepositorioUsuarios;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.Usuario;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import static org.assertj.core.api.Assertions.*;

/**
 * El token solo aporta el subject: la identidad autenticada se resuelve desde el Usuario persistido.
 * Los claims descriptivos nunca sustituyen los datos persistidos.
 */
class ConvertidorJwtUsuarioTests {
    private static final String HASH_SINTETICO = "$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLM";

    private final RepositorioUsuariosFalso repositorio = new RepositorioUsuariosFalso();
    private final ConvertidorJwtUsuario convertidor = new ConvertidorJwtUsuario(repositorio);

    @Test
    void resuelveLaIdentidadDesdeElUsuarioActivoPersistido() {
        Usuario usuario = usuarioActivo();
        repositorio.registrar(usuario);

        AbstractAuthenticationToken autenticacion = convertidor.convert(tokenConSujeto(usuario.id().toString()));

        assertThat(autenticacion).isInstanceOf(AutenticacionUsuario.class);
        assertThat(autenticacion.isAuthenticated()).isTrue();
        assertThat(autenticacion.getPrincipal()).isInstanceOf(UsuarioAutenticado.class);
        UsuarioAutenticado principal = (UsuarioAutenticado) autenticacion.getPrincipal();
        assertThat(principal.usuarioId()).isEqualTo(usuario.id());
        assertThat(principal.personaNaturalId()).isEqualTo(usuario.personaNaturalId());
        assertThat(principal.nombreUsuario()).isEqualTo(usuario.nombreUsuario());
    }

    @Test
    void ignoraLosClaimsDescriptivosDelTokenFrenteAlUsuarioPersistido() {
        Usuario usuario = usuarioActivo();
        repositorio.registrar(usuario);
        UUID personaNaturalDelToken = UUID.randomUUID();
        Jwt token = Jwt.withTokenValue("token-sintetico")
                .header("alg", "HS256")
                .subject(usuario.id().toString())
                .claim(EmisorTokensJwt.CLAIM_USUARIO_ID, usuario.id().toString())
                .claim(EmisorTokensJwt.CLAIM_PERSONA_NATURAL_ID, personaNaturalDelToken.toString())
                .claim(EmisorTokensJwt.CLAIM_NOMBRE_USUARIO, "otro.nombre")
                .build();

        UsuarioAutenticado principal = (UsuarioAutenticado) convertidor.convert(token).getPrincipal();

        assertThat(personaNaturalDelToken).isNotEqualTo(usuario.personaNaturalId());
        assertThat(principal.personaNaturalId()).isEqualTo(usuario.personaNaturalId());
        assertThat(principal.nombreUsuario()).isEqualTo(usuario.nombreUsuario());
    }

    @Test
    void buscaElUsuarioPorElIdentificadorObtenidoDelSujeto() {
        Usuario usuario = usuarioActivo();
        repositorio.registrar(usuario);

        convertidor.convert(tokenConSujeto(usuario.id().toString()));

        assertThat(repositorio.consultasPorId()).containsExactly(usuario.id());
    }

    @Test
    void rechazaUnSujetoQueNoEsUuid() {
        repositorio.registrar(usuarioActivo());

        assertThatThrownBy(() -> convertidor.convert(tokenConSujeto("no-es-un-uuid")))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThat(repositorio.consultasPorId()).isEmpty();
    }

    @Test
    void rechazaUnTokenSinSujeto() {
        repositorio.registrar(usuarioActivo());

        Jwt sinSujeto = Jwt.withTokenValue("token-sintetico").header("alg", "HS256").claim("iss", "https://warnes.gob.bo/gamw-pruebas").build();

        assertThatThrownBy(() -> convertidor.convert(sinSujeto))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThat(repositorio.consultasPorId()).isEmpty();
    }

    @Test
    void rechazaUnUsuarioInexistente() {
        assertThatThrownBy(() -> convertidor.convert(tokenConSujeto(UUID.randomUUID().toString())))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void rechazaUnUsuarioInactivo() {
        Usuario inactivo = usuario(UUID.randomUUID(), UUID.randomUUID(), "usuario.inactivo", false);
        repositorio.registrar(inactivo);

        assertThatThrownBy(() -> convertidor.convert(tokenConSujeto(inactivo.id().toString())))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    private static Jwt tokenConSujeto(String sujeto) {
        return Jwt.withTokenValue("token-sintetico").header("alg", "HS256").subject(sujeto).build();
    }

    private static Usuario usuarioActivo() {
        return usuario(UUID.randomUUID(), UUID.randomUUID(), "usuario.activo", true);
    }

    private static Usuario usuario(UUID id, UUID personaNaturalId, String nombreUsuario, boolean activo) {
        Instant ahora = Instant.parse("2026-01-01T12:00:00Z");
        return new Usuario(id, personaNaturalId, nombreUsuario, HASH_SINTETICO, activo, ahora, ahora);
    }

    /** Doble de prueba en memoria: sin base de datos y registrando las consultas recibidas. */
    private static final class RepositorioUsuariosFalso implements RepositorioUsuarios {
        private final List<Usuario> usuarios = new ArrayList<>();
        private final List<UUID> consultasPorId = new ArrayList<>();

        void registrar(Usuario usuario) {
            usuarios.add(usuario);
        }

        List<UUID> consultasPorId() {
            return List.copyOf(consultasPorId);
        }

        @Override
        public Optional<Usuario> porNombreUsuario(String nombreUsuario) {
            return usuarios.stream().filter(u -> u.nombreUsuario().equals(nombreUsuario)).findFirst();
        }

        @Override
        public Optional<Usuario> porId(UUID id) {
            consultasPorId.add(id);
            return usuarios.stream().filter(u -> u.id().equals(id)).findFirst();
        }
    }
}
