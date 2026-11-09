package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.ReprogramarJornadasOperador;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.PlanificarJornadasOperador;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresa")
public class JornadasOperadorController {
    private final ReprogramarJornadasOperador servicio;
    private final PlanificarJornadasOperador planificacion;
    public JornadasOperadorController(ReprogramarJornadasOperador servicio,PlanificarJornadasOperador planificacion) { this.servicio=servicio;this.planificacion=planificacion; }
    private <T> ResponseEntity<T> privada(T datos) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).varyBy("Authorization").body(datos); }
    @GetMapping("/jornadas") public ResponseEntity<?> listar(@AuthenticationPrincipal UsuarioAutenticado actor,@RequestParam(defaultValue="0") int desplazamiento) { return privada(servicio.listar(actor,desplazamiento)); }
    @GetMapping("/jornadas/{jornada}") public ResponseEntity<?> consultar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada) { return privada(servicio.consultar(actor,jornada)); }
    @PostMapping("/jornadas/{jornada}/reprogramaciones") public ResponseEntity<?> reprogramar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada,@RequestBody ReprogramarJornadasOperador.Solicitud datos) {
        return privada(servicio.reprogramar(actor,jornada,datos));
    }
    @GetMapping("/periodos-servicio") public ResponseEntity<?> periodos(@AuthenticationPrincipal UsuarioAutenticado actor) { return privada(planificacion.periodos(actor)); }
    @GetMapping("/periodos/{periodo}/referencias") public ResponseEntity<?> referencias(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID periodo) { return privada(planificacion.versiones(actor,periodo)); }
    @PostMapping("/periodos/{periodo}/jornadas") public ResponseEntity<?> programar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID periodo,@RequestBody PlanificarJornadasOperador.Programar datos) { return privada(planificacion.programar(actor,periodo,datos)); }
    @GetMapping("/jornadas/{jornada}/recursos") public ResponseEntity<?> recursos(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada) { return privada(planificacion.recursos(actor,jornada)); }
    @PostMapping("/jornadas/{jornada}/asignaciones") public ResponseEntity<?> asignar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada,@RequestBody PlanificarJornadasOperador.Asignar datos) { return privada(planificacion.asignar(actor,jornada,datos,false)); }
    @PostMapping("/jornadas/{jornada}/reasignaciones") public ResponseEntity<?> reasignar(@AuthenticationPrincipal UsuarioAutenticado actor,@PathVariable UUID jornada,@RequestBody PlanificarJornadasOperador.Asignar datos) { return privada(planificacion.asignar(actor,jornada,datos,true)); }
}
