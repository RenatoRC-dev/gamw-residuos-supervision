package bo.gob.warnes.gamw.residuos.modules.monitoreo.revision.api;

import bo.gob.warnes.gamw.residuos.modules.monitoreo.revision.aplicacion.RevisarEvidenciaGps;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/revision-gps/jornadas/{jornadaId}/{tipo}")
public class RevisionGpsController {
    private final RevisarEvidenciaGps servicio;
    public RevisionGpsController(RevisarEvidenciaGps s) { servicio=s; }
    @GetMapping public ResponseEntity<RevisarEvidenciaGps.Resultado> revisar(@AuthenticationPrincipal UsuarioAutenticado actor,
      @PathVariable UUID jornadaId,@PathVariable String tipo,
      @RequestParam(defaultValue="120") double huecoSegundos,@RequestParam(defaultValue="50") double precisionMetros,
      @RequestParam(defaultValue="120") double velocidadKmh,@RequestParam(defaultValue="100") double desvioMetros,
      @RequestParam(defaultValue="25") double radioDetencionMetros,@RequestParam(defaultValue="180") double detencionSegundos,
      @RequestParam(defaultValue="120") double antiguedadSegundos) {
        var p=new RevisarEvidenciaGps.Parametros(huecoSegundos,precisionMetros,velocidadKmh,desvioMetros,radioDetencionMetros,detencionSegundos,antiguedadSegundos);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(servicio.revisar(actor,jornadaId,tipo,p));
    }
}
