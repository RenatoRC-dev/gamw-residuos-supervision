package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.aplicacion.GestionarFiscalizacion;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.aplicacion.ConsultarTrayectoriasAutorizadas;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@RestController
@RequestMapping("/api/fiscalizacion/jornadas")
public class FiscalizacionController {
    private final GestionarFiscalizacion servicio;
    private final ObjectMapper json;
    public FiscalizacionController(GestionarFiscalizacion servicio,ObjectMapper json) { this.servicio=servicio; this.json=json; }
    private <T> ResponseEntity<T> privada(T datos) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(datos); }
    @GetMapping public ResponseEntity<GestionarFiscalizacion.ListaJornadas> listar(@AuthenticationPrincipal UsuarioAutenticado a,@RequestParam(defaultValue="0") int desplazamiento) { return privada(servicio.listar(a,desplazamiento)); }
    @GetMapping("/{id}") public ResponseEntity<GestionarFiscalizacion.Expediente> consultar(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id) { return privada(servicio.expediente(a,id)); }
    @GetMapping("/{id}/gps") public ResponseEntity<ConsultarTrayectoriasAutorizadas.Detalle> gps(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,
            @RequestParam(defaultValue="0") int desplazamiento,@RequestParam(required=false) Instant corteRecepcion) { return privada(servicio.gps(a,id,desplazamiento,corteRecepcion)); }
    @PostMapping(value="/{id}/inspecciones",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> inspeccion(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody GestionarFiscalizacion.InspeccionEntrada e) { return privada(servicio.inspeccionar(a,id,e,List.of())); }
    @PostMapping(value="/{id}/inspecciones",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JsonNode> inspeccionConArchivos(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestPart("datos") String datos,
            @RequestPart(value="archivos",required=false) List<MultipartFile> archivos) {
        if(datos.length()>65536) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Metadatos demasiado grandes.");
        return privada(servicio.inspeccionar(a,id,json.readValue(datos,GestionarFiscalizacion.InspeccionEntrada.class),archivos==null?List.of():archivos));
    }
    @PostMapping("/{id}/resultado") public ResponseEntity<JsonNode> emitir(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID id,@RequestBody GestionarFiscalizacion.DecisionEntrada e) { return privada(servicio.emitir(a,id,e)); }
    @GetMapping("/{jornada}/evidencias/{archivo}") public ResponseEntity<byte[]> archivo(@AuthenticationPrincipal UsuarioAutenticado a,@PathVariable UUID jornada,@PathVariable UUID archivo) {
        var e=servicio.archivo(a,jornada,archivo);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(e.nombre(),StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(e.mime())).contentLength(e.contenido().length).body(e.contenido());
    }
}
