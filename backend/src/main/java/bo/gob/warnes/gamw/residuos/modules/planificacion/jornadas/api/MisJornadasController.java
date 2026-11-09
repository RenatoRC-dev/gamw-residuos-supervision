package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.ConsultarMisJornadas;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.ContextoOperativoJornada;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MisJornadasController {
    private final ConsultarMisJornadas jornadas;
    public MisJornadasController(ConsultarMisJornadas jornadas) { this.jornadas=jornadas; }

    @GetMapping("/api/mis-jornadas")
    public List<RespuestaJornadaAsignada> consultar(@AuthenticationPrincipal UsuarioAutenticado actor) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Se requiere identidad GAMW autenticada.");
        return jornadas.consultar(actor).stream().map(j -> new RespuestaJornadaAsignada(
                j.jornadaId(),j.asignacionJornadaId(),j.inicioProgramado(),j.finProgramado(),j.vigente(),j.vigenteDesde(),j.vigenteHasta(),j.contextoOperativo())).toList();
    }
    public record RespuestaJornadaAsignada(UUID jornadaId,UUID asignacionJornadaId,Instant inicioProgramado,
            Instant finProgramado,boolean vigente,Instant vigenteDesde,Instant vigenteHasta,ContextoOperativoJornada contextoOperativo) { }
}
