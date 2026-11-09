package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion.ServicioAutenticacion;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Intercambio mínimo de credenciales y consulta de la identidad autenticada. */
@RestController
@RequestMapping("/api/autenticacion")
@Profile("!desarrollo-local & !smoke")
public class AutenticacionController {
    private final ServicioAutenticacion autenticacion;

    public AutenticacionController(ServicioAutenticacion autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/login")
    public RespuestaInicioSesion login(@Valid @RequestBody SolicitudInicioSesion solicitud) {
        ServicioAutenticacion.Sesion sesion = autenticacion.autenticar(solicitud.nombreUsuario(),
                solicitud.contrasena());
        return new RespuestaInicioSesion(sesion.tokenAcceso(), sesion.expiraEn(), sesion.usuarioId(),
                sesion.personaNaturalId(), sesion.nombreUsuario());
    }

    @GetMapping("/actual")
    public RespuestaIdentidad actual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new RespuestaIdentidad(usuario.usuarioId(), usuario.personaNaturalId(), usuario.nombreUsuario());
    }

    /** Solo credenciales: ninguna identidad de negocio enviada por el cliente es aceptada. */
    public record SolicitudInicioSesion(
            @NotBlank(message = "nombreUsuario es obligatorio.") String nombreUsuario,
            @NotBlank(message = "contrasena es obligatoria.") String contrasena) {
        @JsonAnySetter
        public void rechazarCampo(String campo, Object valor) {
            throw new IllegalArgumentException("El inicio de sesión solo admite credenciales.");
        }
        @Override public String toString() { return "SolicitudInicioSesion[credenciales ocultas]"; }
    }

    public record RespuestaInicioSesion(String tokenAcceso, Instant expiraEn, UUID usuarioId,
            UUID personaNaturalId, String nombreUsuario) {
        @Override public String toString() { return "RespuestaInicioSesion[token oculto]"; }
    }

    public record RespuestaIdentidad(UUID usuarioId, UUID personaNaturalId, String nombreUsuario) { }
}
