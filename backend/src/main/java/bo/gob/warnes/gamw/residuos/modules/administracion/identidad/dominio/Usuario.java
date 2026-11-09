package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio;

import java.time.Instant;
import java.util.UUID;

/**
 * Identidad de acceso al sistema. No es la persona del negocio: PersonaNatural sigue siéndolo.
 * No representa rol, permiso ni función operativa.
 */
public record Usuario(UUID id, UUID personaNaturalId, String nombreUsuario, String contrasenaHash,
        boolean activo, Instant creadoEn, Instant actualizadoEn) {
    public Usuario {
        if (id == null) throw new IllegalArgumentException("id es obligatorio.");
        if (personaNaturalId == null) throw new IllegalArgumentException("personaNaturalId es obligatorio.");
        nombreUsuario = requerido(nombreUsuario, "nombreUsuario");
        contrasenaHash = requerido(contrasenaHash, "contrasenaHash");
        if (!contrasenaHash.startsWith("$2")) {
            throw new IllegalArgumentException("contrasenaHash debe ser un hash BCrypt; no se admite contraseña en claro.");
        }
        if (creadoEn == null) throw new IllegalArgumentException("creadoEn es obligatorio.");
        if (actualizadoEn == null) throw new IllegalArgumentException("actualizadoEn es obligatorio.");
    }

    private static String requerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(campo + " es obligatorio.");
        return valor.strip();
    }
}
