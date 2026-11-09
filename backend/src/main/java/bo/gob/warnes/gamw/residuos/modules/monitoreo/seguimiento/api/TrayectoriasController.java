package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.api;

import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.aplicacion.ConsultarTrayectoriasAutorizadas;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trayectorias/jornadas")
public class TrayectoriasController {
    private final ConsultarTrayectoriasAutorizadas consulta;
    public TrayectoriasController(ConsultarTrayectoriasAutorizadas consulta) { this.consulta=consulta; }
    private <T> ResponseEntity<T> privada(T datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(datos);
    }
    @GetMapping public ResponseEntity<ConsultarTrayectoriasAutorizadas.ListaJornadas> listar(
            @AuthenticationPrincipal UsuarioAutenticado actor,@RequestParam(defaultValue="50") int limite,
            @RequestParam(defaultValue="0") int desplazamiento) {
        return privada(consulta.listar(actor,limite,desplazamiento));
    }
    @GetMapping("/{jornadaId}") public ResponseEntity<ConsultarTrayectoriasAutorizadas.Detalle> obtener(
            @AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornadaId,
            @RequestParam(defaultValue="500") int limite,@RequestParam(defaultValue="0") int desplazamiento,
            @RequestParam(required=false) Instant corteRecepcion) {
        return privada(consulta.consultar(actor,jornadaId,limite,desplazamiento,corteRecepcion));
    }
}
