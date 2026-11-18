package bo.gob.warnes.gamw.residuos.modules.historial.api;

import bo.gob.warnes.gamw.residuos.modules.historial.aplicacion.ConsultarHistorialServicio;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** CU-29: consulta histórica de solo lectura. Autorización en servidor dentro del caso de uso. */
@RestController
@RequestMapping("/api/historial-servicio/jornadas")
public class HistorialServicioController {
    private final ConsultarHistorialServicio historial;
    public HistorialServicioController(ConsultarHistorialServicio historial) { this.historial = historial; }
    private <T> ResponseEntity<T> privada(T datos) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(datos); }

    @GetMapping
    public ResponseEntity<?> buscar(@AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(required = false) UUID jornadaId,@RequestParam(required = false) UUID periodoId,@RequestParam(required = false) UUID empresaId,@RequestParam(required = false) UUID rutaId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int desplazamiento) {
        return privada(historial.buscar(actor,jornadaId,periodoId,empresaId,rutaId,desde,hasta,desplazamiento));
    }
    @GetMapping("/{jornadaId}")
    public ResponseEntity<?> detalle(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornadaId) {
        return privada(historial.detalle(actor,jornadaId));
    }
}
