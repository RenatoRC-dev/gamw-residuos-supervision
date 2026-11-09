package bo.gob.warnes.gamw.residuos.shared.security;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.Usuario;
import java.security.Principal;
import java.util.UUID;

/**
 * Identidad autenticada mínima. No es PersonaNatural: solo la referencia su identidad de negocio.
 * No contiene roles ni permisos: FuncionOperativa no es un rol de seguridad.
 *
 * Se construye siempre desde el Usuario persistido; nunca desde claims aislados ni datos del cliente.
 */
public record UsuarioAutenticado(UUID usuarioId, UUID personaNaturalId, String nombreUsuario) implements Principal {
    public UsuarioAutenticado {
        if (usuarioId == null) throw new IllegalArgumentException("usuarioId es obligatorio.");
        if (personaNaturalId == null) throw new IllegalArgumentException("personaNaturalId es obligatorio.");
        if (nombreUsuario == null || nombreUsuario.isBlank()) throw new IllegalArgumentException("nombreUsuario es obligatorio.");
    }

    @Override
    public String getName() {
        return nombreUsuario;
    }

    static UsuarioAutenticado desde(Usuario usuario) {
        return new UsuarioAutenticado(usuario.id(), usuario.personaNaturalId(), usuario.nombreUsuario());
    }
}
