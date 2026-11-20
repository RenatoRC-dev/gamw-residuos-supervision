package bo.gob.warnes.gamw.residuos.modules.historial.api;

import bo.gob.warnes.gamw.residuos.modules.historial.aplicacion.ConsultarReportesServicio;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** CU-30: reporte descriptivo de solo lectura. Autorización en servidor dentro del caso de uso. Sin exportación (formato no aprobado). */
@RestController
@RequestMapping("/api/reportes-servicio")
public class ReportesServicioController {
    private final ConsultarReportesServicio reportes;
    public ReportesServicioController(ConsultarReportesServicio reportes) { this.reportes = reportes; }

    @GetMapping("/resumen")
    public ResponseEntity<?> resumen(@AuthenticationPrincipal UsuarioAutenticado actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID periodoId, @RequestParam(required = false) UUID empresaId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization")
            .body(reportes.resumen(actor, desde, hasta, periodoId, empresaId));
    }
}
