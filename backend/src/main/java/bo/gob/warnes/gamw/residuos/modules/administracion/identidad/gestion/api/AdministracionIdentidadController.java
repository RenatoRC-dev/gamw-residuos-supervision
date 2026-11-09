package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.aplicacion.AdministrarIdentidad;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.aplicacion.AdministrarIdentidad.*;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/administracion/identidad")
public class AdministracionIdentidadController {
    private final AdministrarIdentidad servicio;
    public AdministracionIdentidadController(AdministrarIdentidad servicio) { this.servicio=servicio; }
    private <T> ResponseEntity<T> privada(T datos) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(datos); }
    @GetMapping("/usuarios") public ResponseEntity<?> listar(@AuthenticationPrincipal UsuarioAutenticado a,@RequestParam(defaultValue="0") int desplazamiento) { return privada(servicio.listar(a,desplazamiento)); }
    @GetMapping("/usuarios/{id}") public ResponseEntity<?> consultar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id) { return privada(servicio.consultar(a,id)); }
    @GetMapping("/personas") public ResponseEntity<?> personas(@AuthenticationPrincipal UsuarioAutenticado a,@RequestParam String ci) { return privada(servicio.personas(a,ci)); }
    @GetMapping("/opciones") public ResponseEntity<?> opciones(@AuthenticationPrincipal UsuarioAutenticado a) { return privada(servicio.opciones(a)); }
    @PostMapping("/usuarios") public ResponseEntity<?> crear(@AuthenticationPrincipal UsuarioAutenticado a,@RequestBody NuevaCuenta e) { return privada(servicio.crear(a,e)); }
    @PutMapping("/usuarios/{id}") public ResponseEntity<?> actualizar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody NombreCuenta e) { return privada(servicio.actualizar(a,id,e)); }
    @PatchMapping("/usuarios/{id}/estado") public ResponseEntity<?> estado(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody EstadoCuenta e) { return privada(servicio.estado(a,id,e)); }
    @GetMapping("/usuarios/{id}/permisos") public ResponseEntity<?> permisos(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestParam(defaultValue="0") int desplazamiento) { return privada(servicio.concesiones(a,id,desplazamiento)); }
    @PostMapping("/usuarios/{id}/permisos") public ResponseEntity<?> otorgar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody Otorgar e) { return privada(servicio.otorgar(a,id,e)); }
    @PostMapping("/usuarios/{id}/permisos/{concesion}/revocacion") public ResponseEntity<?> revocar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@PathVariable UUID concesion,@RequestBody Revocar e) { return privada(servicio.revocar(a,id,concesion,e)); }
    @GetMapping("/usuarios/{id}/actuaciones") public ResponseEntity<?> actuaciones(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestParam(defaultValue="0") int desplazamiento) { return privada(servicio.actuaciones(a,id,desplazamiento)); }
}
