package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import bo.gob.warnes.gamw.residuos.modules.reclamos.aplicacion.GestionarReclamos;
import bo.gob.warnes.gamw.residuos.modules.reclamos.aplicacion.GestionarReclamos.*;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/puntos-criticos")
public class PuntosCriticosController {
    private final GestionarReclamos servicio;
    public PuntosCriticosController(GestionarReclamos servicio) { this.servicio=servicio; }
    private <T> ResponseEntity<T> privada(T d) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(d); }
    @GetMapping public ResponseEntity<?> listar(@AuthenticationPrincipal UsuarioAutenticado a,@RequestParam(defaultValue="0") int desplazamiento) { return privada(servicio.listarPuntos(a,desplazamiento)); }
    @GetMapping("/{id}") public ResponseEntity<?> obtener(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id) { return privada(servicio.punto(a,id)); }
    @PostMapping public ResponseEntity<?> crear(@AuthenticationPrincipal UsuarioAutenticado a,@RequestBody NuevoPunto e) { return privada(servicio.crearPunto(a,e)); }
    @PostMapping("/{id}/observaciones") public ResponseEntity<?> observar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody NuevaObservacion e) { return privada(servicio.observar(a,id,e)); }
    @PostMapping("/{id}/validacion") public ResponseEntity<?> validar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody ConfirmarPunto e) { return privada(servicio.validar(a,id,e)); }
}
