package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import bo.gob.warnes.gamw.residuos.modules.reclamos.aplicacion.GestionarReclamos;
import bo.gob.warnes.gamw.residuos.modules.reclamos.aplicacion.GestionarReclamos.*;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/reclamos-institucionales")
public class ReclamosController {
    private final GestionarReclamos servicio;
    public ReclamosController(GestionarReclamos servicio) { this.servicio=servicio; }
    private <T> ResponseEntity<T> privada(T datos) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(datos); }
    @GetMapping("/opciones") public ResponseEntity<?> opciones(@AuthenticationPrincipal UsuarioAutenticado a) { return privada(servicio.opciones(a)); }
    @GetMapping public ResponseEntity<?> listar(@AuthenticationPrincipal UsuarioAutenticado a,@RequestParam(defaultValue="0") int desplazamiento) { return privada(servicio.listarReclamos(a,desplazamiento)); }
    @GetMapping("/{id}") public ResponseEntity<?> consultar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id) { return privada(servicio.reclamo(a,id)); }
    @PostMapping public ResponseEntity<?> registrar(@AuthenticationPrincipal UsuarioAutenticado a,@RequestBody NuevoReclamo e) { return privada(servicio.registrar(a,e)); }
    @PostMapping("/{id}/revisiones") public ResponseEntity<?> revisar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody NuevaRevision e) { return privada(servicio.revisar(a,id,e)); }
    @PostMapping("/{id}/relaciones") public ResponseEntity<?> relacionar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody NuevaRelacion e) { return privada(servicio.relacionar(a,id,e)); }
    @PostMapping(value="/{id}/anexos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> anexar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestParam String origen,@RequestParam String descripcion,
        @RequestParam(required=false) Instant capturadoEn,@RequestPart MultipartFile archivo) { return privada(servicio.anexar(a,id,origen,descripcion,capturadoEn,archivo)); }
    @GetMapping("/{id}/anexos/{anexo}") public ResponseEntity<byte[]> descargar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@PathVariable UUID anexo) {
        var archivo=servicio.archivo(a,id,anexo);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(archivo.nombre(),StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(archivo.mime())).contentLength(archivo.contenido().length).body(archivo.contenido());
    }
}
