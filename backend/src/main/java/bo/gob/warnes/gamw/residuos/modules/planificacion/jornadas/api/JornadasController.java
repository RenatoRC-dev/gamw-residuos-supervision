package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.GestionarJornadas;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api.ContratosJornadas.*;

@RestController
@RequestMapping("/api/jornadas")
public class JornadasController {
    private final GestionarJornadas jornadas;
    public JornadasController(GestionarJornadas jornadas) { this.jornadas = jornadas; }
    @PostMapping
    public ResponseEntity<DetalleJornadaRespuesta> crear(@Valid @RequestBody ProgramarSolicitud d) {
        var nueva = jornadas.crear(d.comando());
        return ResponseEntity.created(URI.create("/api/jornadas/" + nueva.jornada().id())).body(DetalleJornadaRespuesta.desde(nueva));
    }
    @GetMapping
    public List<DetalleJornadaRespuesta> listar() { return jornadas.listar().stream().map(DetalleJornadaRespuesta::desde).toList(); }
    @GetMapping("/{jornadaId}")
    public DetalleJornadaRespuesta obtener(@PathVariable UUID jornadaId) { return DetalleJornadaRespuesta.desde(jornadas.obtener(jornadaId)); }
    @PostMapping("/{jornadaId}/reprogramaciones")
    public ResponseEntity<DetalleJornadaRespuesta> reprogramar(@PathVariable UUID jornadaId,@Valid @RequestBody ReprogramarSolicitud d) {
        var nueva = jornadas.reprogramar(jornadaId,d.comando(),d.motivoReprogramacion());
        return ResponseEntity.created(URI.create("/api/jornadas/" + nueva.jornada().id())).body(DetalleJornadaRespuesta.desde(nueva));
    }
    @PostMapping("/{jornadaId}/asignaciones")
    public ResponseEntity<AsignacionRespuesta> asignar(@PathVariable UUID jornadaId,@Valid @RequestBody AsignarSolicitud d) {
        return ResponseEntity.created(URI.create("/api/jornadas/" + jornadaId + "/asignaciones"))
                .body(AsignacionRespuesta.desde(jornadas.asignar(jornadaId,d.vinculoVehiculoEmpresaId(),d.vinculoPersonaEmpresaId())));
    }
    @GetMapping("/{jornadaId}/asignaciones")
    public List<AsignacionRespuesta> historia(@PathVariable UUID jornadaId) { return jornadas.historial(jornadaId).stream().map(AsignacionRespuesta::desde).toList(); }
    @PostMapping("/{jornadaId}/reasignaciones")
    public ResponseEntity<AsignacionRespuesta> reasignar(@PathVariable UUID jornadaId,@Valid @RequestBody ReasignarSolicitud d) {
        return ResponseEntity.created(URI.create("/api/jornadas/" + jornadaId + "/asignaciones"))
                .body(AsignacionRespuesta.desde(jornadas.reasignar(jornadaId,d.vinculoVehiculoEmpresaId(),d.vinculoPersonaEmpresaId(),d.motivoCambio())));
    }
}
