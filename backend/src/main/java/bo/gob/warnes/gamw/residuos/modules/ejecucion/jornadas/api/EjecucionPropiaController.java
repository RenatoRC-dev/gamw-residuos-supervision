package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion.RegistrarEventosEjecucion;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class EjecucionPropiaController {
    private final RegistrarEventosEjecucion ejecucion;
    public EjecucionPropiaController(RegistrarEventosEjecucion ejecucion) { this.ejecucion=ejecucion; }
    @GetMapping("/api/mis-jornadas/{jornada}/ejecucion")
    public ResponseEntity<RegistrarEventosEjecucion.LecturaEjecucion> consultar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada,@RequestParam UUID asignacionId) {
        ejecucion.autorizarAsignacionHistorica(jornada,asignacionId,actor);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(ejecucion.consultar(jornada));
    }
}
