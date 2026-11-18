package bo.gob.warnes.gamw.residuos.modules.bitacora.api;

import bo.gob.warnes.gamw.residuos.modules.bitacora.aplicacion.ConsultarBitacora;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** CU-31: únicamente GET. No hay verbos de escritura ni borrado; los eventos son inmutables. */
@RestController
@RequestMapping("/api/bitacora/eventos")
public class BitacoraController {
    private final ConsultarBitacora bitacora;
    public BitacoraController(ConsultarBitacora bitacora) { this.bitacora = bitacora; }
    private <T> ResponseEntity<T> privada(T datos) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(datos); }

    @GetMapping
    public ResponseEntity<?> buscar(@AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID actorId,@RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidadTipo,@RequestParam(required = false) UUID entidadId,
            @RequestParam(defaultValue = "0") int desplazamiento) {
        return privada(bitacora.buscar(actor,desde,hasta,actorId,accion,entidadTipo,entidadId,desplazamiento));
    }
    @GetMapping("/{eventoId}")
    public ResponseEntity<?> detalle(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable String eventoId) {
        return privada(bitacora.detalle(actor,eventoId));
    }
}
