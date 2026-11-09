package bo.gob.warnes.gamw.residuos.shared.security;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.RepositorioUsuarios;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.Usuario;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Traduce un token validado a la identidad GAMW autenticada.
 * El token solo aporta el subject: la identidad y la persona se resuelven desde el Usuario persistido.
 */
@Component
@Profile("!desarrollo-local & !smoke")
public class ConvertidorJwtUsuario implements Converter<Jwt, AbstractAuthenticationToken> {
    private final RepositorioUsuarios usuarios;

    public ConvertidorJwtUsuario(RepositorioUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID usuarioId;
        try {
            usuarioId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException error) {
            throw new InvalidBearerTokenException("El token no identifica un Usuario válido de GAMW.");
        }
        Usuario usuario = usuarios.porId(usuarioId)
                .filter(Usuario::activo)
                .orElseThrow(() -> new InvalidBearerTokenException("El token no identifica un Usuario activo de GAMW."));
        return new AutenticacionUsuario(jwt, UsuarioAutenticado.desde(usuario));
    }
}
