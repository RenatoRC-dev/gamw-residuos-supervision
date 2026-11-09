package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion.RegistrarEventosEjecucion;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jornadas/{jornadaId}/ejecucion")
public class EjecucionJornadasController {
    private final RegistrarEventosEjecucion eventos;
    private final boolean desarrolloLocal;
    public EjecucionJornadasController(RegistrarEventosEjecucion eventos,Environment entorno) {
        this.eventos = eventos; this.desarrolloLocal = entorno.matchesProfiles("desarrollo-local");
    }
    @PostMapping("/inicio")
    public ResponseEntity<EventoRespuesta> inicio(@PathVariable UUID jornadaId,@Valid @RequestBody RegistrarSolicitud d,@AuthenticationPrincipal UsuarioAutenticado actor) {
        return registrar(jornadaId,d,TipoEventoEjecucion.INICIO,actor);
    }
    @PostMapping("/fin")
    public ResponseEntity<EventoRespuesta> fin(@PathVariable UUID jornadaId,@Valid @RequestBody RegistrarSolicitud d,@AuthenticationPrincipal UsuarioAutenticado actor) {
        return registrar(jornadaId,d,TipoEventoEjecucion.FIN,actor);
    }
    @GetMapping
    public LecturaRespuesta consultar(@PathVariable UUID jornadaId) {
        var lectura = eventos.consultar(jornadaId);
        return new LecturaRespuesta(jornadaId,EventoRespuesta.desde(lectura.inicio()),EventoRespuesta.desde(lectura.fin()));
    }
    private ResponseEntity<EventoRespuesta> registrar(UUID jornadaId,RegistrarSolicitud d,TipoEventoEjecucion tipo,UsuarioAutenticado actor) {
        if (actor == null && !desarrolloLocal) throw new InsufficientAuthenticationException("Se requiere identidad GAMW autenticada.");
        var resultado = actor == null
                ? eventos.registrar(jornadaId,d.eventoClienteId(),d.asignacionJornadaId(),tipo,d.capturadoEn())
                : eventos.registrarAutenticado(jornadaId,d.eventoClienteId(),d.asignacionJornadaId(),tipo,d.capturadoEn(),actor);
        return ResponseEntity.status(resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create("/api/jornadas/" + jornadaId + "/ejecucion")).body(EventoRespuesta.desde(resultado.evento()));
    }
    public record RegistrarSolicitud(@NotNull(message="eventoClienteId es obligatorio.") UUID eventoClienteId,
            @NotNull(message="asignacionJornadaId es obligatorio.") UUID asignacionJornadaId,
            @NotNull(message="capturadoEn es obligatorio.") Instant capturadoEn) {
        @JsonAnySetter public void rechazarCampo(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido para evidencia operativa."); }
    }
    public record EventoRespuesta(UUID id,UUID eventoClienteId,UUID jornadaId,UUID asignacionJornadaId,TipoEventoEjecucion tipoEvento,Instant capturadoEn,Instant recibidoEn,UUID usuarioId) {
        static EventoRespuesta desde(EventoEjecucionJornada e) {
            return e == null ? null : new EventoRespuesta(e.id(),e.eventoClienteId(),e.jornadaId(),e.asignacionJornadaId(),e.tipoEvento(),e.capturadoEn(),e.recibidoEn(),e.usuarioId());
        }
    }
    public record LecturaRespuesta(UUID jornadaId,EventoRespuesta inicio,EventoRespuesta fin) { }
}
