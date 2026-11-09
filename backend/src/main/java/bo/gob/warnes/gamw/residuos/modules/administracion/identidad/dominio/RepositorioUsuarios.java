package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio;

import java.util.Optional;
import java.util.UUID;

/** Contrato mínimo de acceso a Usuario: identidad de acceso, no administración de cuentas. */
public interface RepositorioUsuarios {
    Optional<Usuario> porNombreUsuario(String nombreUsuario);

    Optional<Usuario> porId(UUID id);
}
