package bo.gob.warnes.gamw.residuos.modules.correctivas.api;

import bo.gob.warnes.gamw.residuos.modules.correctivas.aplicacion.GestionarActuaciones;
import bo.gob.warnes.gamw.residuos.modules.correctivas.aplicacion.GestionarActuaciones.*;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ActuacionesController {
    private final GestionarActuaciones servicio;
    public ActuacionesController(GestionarActuaciones servicio) { this.servicio=servicio; }
    private <T> ResponseEntity<T> respuesta(T datos) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Vary","Authorization").body(datos); }
    @GetMapping("/api/jornadas/{jornada}/incidencias-propias")
    public ResponseEntity<?> propias(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada,@RequestParam UUID asignacionId) {
        return respuesta(servicio.propias(actor,jornada,asignacionId));
    }
    @PostMapping("/api/jornadas/{jornada}/incidencias")
    public ResponseEntity<?> registrar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada,@RequestBody NuevaIncidencia datos) {
        return respuesta(servicio.registrarIncidencia(actor,jornada,datos));
    }
    @GetMapping("/api/correctivas/jornadas")
    public ResponseEntity<?> listar(@AuthenticationPrincipal UsuarioAutenticado actor,@RequestParam(defaultValue="0") int desplazamiento) {
        return respuesta(servicio.listar(actor,desplazamiento));
    }
    @GetMapping("/api/correctivas/jornadas/{jornada}")
    public ResponseEntity<?> expediente(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada) { return respuesta(servicio.expediente(actor,jornada)); }
    @PostMapping("/api/correctivas/jornadas/{jornada}/acciones")
    public ResponseEntity<?> solicitar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada,@RequestBody NuevaAccion datos) { return respuesta(servicio.solicitar(actor,jornada,datos)); }
    @PostMapping("/api/correctivas/acciones/{accion}/verificaciones")
    public ResponseEntity<?> verificar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID accion,@RequestBody NuevaVerificacion datos) { return respuesta(servicio.verificar(actor,accion,datos)); }
    // Respuesta A4 en CorrectivasEmpresaController; no utiliza permisos municipales.
}
