package bo.gob.warnes.gamw.residuos.modules.correctivas.api;

import bo.gob.warnes.gamw.residuos.modules.correctivas.aplicacion.AtenderCorrectivasEmpresa;
import bo.gob.warnes.gamw.residuos.modules.correctivas.aplicacion.AtenderCorrectivasEmpresa.NuevaRespuesta;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresa/correctivas")
public class CorrectivasEmpresaController {
    private final AtenderCorrectivasEmpresa servicio;
    public CorrectivasEmpresaController(AtenderCorrectivasEmpresa servicio) { this.servicio=servicio; }
    private <T> ResponseEntity<T> privada(T datos) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(datos); }
    @GetMapping public ResponseEntity<?> listar(@AuthenticationPrincipal UsuarioAutenticado actor,@RequestParam(defaultValue="0") int desplazamiento) {
        return privada(servicio.listar(actor,desplazamiento));
    }
    @GetMapping("/{accion}") public ResponseEntity<?> consultar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID accion) {
        return privada(servicio.consultar(actor,accion));
    }
    @PostMapping("/{accion}/respuestas") public ResponseEntity<?> responder(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID accion,@RequestBody NuevaRespuesta datos) {
        return privada(servicio.responder(actor,accion,datos));
    }
    @GetMapping("/{accion}/evidencias/{evidencia}") public ResponseEntity<byte[]> archivo(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID accion,@PathVariable UUID evidencia) {
        var a=servicio.archivo(actor,accion,evidencia);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(a.nombre(),StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(a.mime())).contentLength(a.contenido().length).body(a.contenido());
    }
}
