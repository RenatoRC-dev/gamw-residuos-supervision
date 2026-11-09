package bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.aplicacion.GestionarReferenciasEspaciales;
import bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.dominio.RepositorioReferenciasEspaciales;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/referencias-espaciales")
public class ReferenciasEspacialesController {
    private final GestionarReferenciasEspaciales servicio;
    public ReferenciasEspacialesController(GestionarReferenciasEspaciales s) { servicio=s; }
    @GetMapping public ResponseEntity<GestionarReferenciasEspaciales.Pagina> listar(@AuthenticationPrincipal UsuarioAutenticado a,
            @RequestParam(required=false) UUID rutaVersionId,@RequestParam(defaultValue="0") int desplazamiento) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(servicio.listar(a,rutaVersionId,desplazamiento));
    }
    @GetMapping("/versiones-ruta") public List<RepositorioReferenciasEspaciales.OpcionRuta> rutas(@AuthenticationPrincipal UsuarioAutenticado a,@RequestParam(defaultValue="0") int desplazamiento) { return servicio.rutas(a,desplazamiento); }
    @PostMapping public GestionarReferenciasEspaciales.Vista crear(@AuthenticationPrincipal UsuarioAutenticado a,@RequestBody GestionarReferenciasEspaciales.Entrada e) { return servicio.guardar(a,null,e); }
    @PostMapping("/{id}/versiones") public GestionarReferenciasEspaciales.Vista versionar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody GestionarReferenciasEspaciales.Entrada e) { return servicio.guardar(a,id,e); }
    public record Cierre(String motivo) { }
    @PostMapping("/{id}/cierre") public ResponseEntity<Void> cerrar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody Cierre e) { servicio.cerrar(a,id,e.motivo()); return ResponseEntity.noContent().build(); }
}
