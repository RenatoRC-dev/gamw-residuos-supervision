package bo.gob.warnes.gamw.residuos.modules.monitoreo.posicion.api;

import bo.gob.warnes.gamw.residuos.modules.monitoreo.posicion.aplicacion.ConsultarMonitoreo;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monitoreo/jornadas")
public class MonitoreoController {
    private final ConsultarMonitoreo consulta;
    public MonitoreoController(ConsultarMonitoreo consulta) { this.consulta=consulta; }
    @GetMapping public ResponseEntity<ConsultarMonitoreo.Vista> consultar(@AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(defaultValue="50") int limite,@RequestParam(defaultValue="0") int desplazamiento) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization")
                .body(consulta.consultar(actor,limite,desplazamiento));
    }
}
