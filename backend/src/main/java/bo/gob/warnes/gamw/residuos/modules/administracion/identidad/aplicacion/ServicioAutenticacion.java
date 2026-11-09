package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.RepositorioUsuarios;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.Usuario;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Autenticación mínima de identidad de acceso. Verifica BCrypt contra el Usuario persistido
 * y solo entonces emite el token. La persona del negocio se deriva del Usuario, no del token.
 */
@Service
@Profile("!desarrollo-local & !smoke")
public class ServicioAutenticacion {
    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder codificadorContrasenas;
    private final EmisorTokensJwt emisor;

    public ServicioAutenticacion(RepositorioUsuarios usuarios, PasswordEncoder codificadorContrasenas,
            EmisorTokensJwt emisor) {
        this.usuarios = usuarios;
        this.codificadorContrasenas = codificadorContrasenas;
        this.emisor = emisor;
    }

    public Sesion autenticar(String nombreUsuario, String contrasena) {
        if (nombreUsuario == null || nombreUsuario.isBlank() || contrasena == null || contrasena.isBlank()) {
            throw new CredencialesInvalidas();
        }
        Usuario usuario = usuarios.porNombreUsuario(nombreUsuario)
                .filter(Usuario::activo)
                .orElseThrow(CredencialesInvalidas::new);
        if (!codificadorContrasenas.matches(contrasena, usuario.contrasenaHash())) {
            throw new CredencialesInvalidas();
        }
        EmisorTokensJwt.TokenEmitido token = emisor.emitir(usuario.id(), usuario.personaNaturalId(),
                usuario.nombreUsuario());
        return new Sesion(token.token(), token.expiraEn(), usuario.id(), usuario.personaNaturalId(),
                usuario.nombreUsuario());
    }

    /** Resultado del intercambio de credenciales. Nunca incluye el hash de la contraseña. */
    public record Sesion(String tokenAcceso, Instant expiraEn, UUID usuarioId, UUID personaNaturalId,
            String nombreUsuario) {
        @Override
        public String toString() {
            return "Sesion[tokenAcceso=[REDACTADO], expiraEn=" + expiraEn + "]";
        }
    }
}
