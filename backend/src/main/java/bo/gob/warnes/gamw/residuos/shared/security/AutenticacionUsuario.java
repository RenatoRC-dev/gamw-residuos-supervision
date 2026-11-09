package bo.gob.warnes.gamw.residuos.shared.security;

import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Autenticación Bearer cuyo principal es la identidad GAMW resuelta desde el token firmado. */
public final class AutenticacionUsuario extends AbstractAuthenticationToken {
    private final Jwt token;
    private final UsuarioAutenticado usuario;

    public AutenticacionUsuario(Jwt token, UsuarioAutenticado usuario) {
        super(List.of());
        this.token = Objects.requireNonNull(token, "token es obligatorio.");
        this.usuario = Objects.requireNonNull(usuario, "usuario es obligatorio.");
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return usuario;
    }

    public Jwt getToken() {
        return token;
    }
}
