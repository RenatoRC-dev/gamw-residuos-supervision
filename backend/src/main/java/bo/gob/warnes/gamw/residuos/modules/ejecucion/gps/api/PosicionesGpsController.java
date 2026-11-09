package bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.api;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.aplicacion.RegistrarPosicionesGps;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jornadas/{jornadaId}/ejecucion/posiciones-gps")
public class PosicionesGpsController {
    private final RegistrarPosicionesGps servicio;
    public PosicionesGpsController(RegistrarPosicionesGps servicio) { this.servicio=servicio; }
    public record Solicitud(UUID asignacionJornadaId,List<RegistrarPosicionesGps.Lectura> posiciones) {
        @JsonAnySetter public void rechazarCampo(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido."); }
    }
    @PostMapping
    public List<RegistrarPosicionesGps.Confirmacion> registrar(@PathVariable UUID jornadaId,@RequestBody Solicitud solicitud,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        if(actor==null) throw new InsufficientAuthenticationException("Se requiere identidad GAMW autenticada.");
        return servicio.registrar(jornadaId,solicitud.asignacionJornadaId(),solicitud.posiciones(),actor);
    }
}
