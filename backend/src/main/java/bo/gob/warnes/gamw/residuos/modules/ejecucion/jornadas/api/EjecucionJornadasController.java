package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion.RegistrarEventosEjecucion;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jornadas/{jornadaId}/ejecucion")
public class EjecucionJornadasController {
    private final RegistrarEventosEjecucion eventos;
    public EjecucionJornadasController(RegistrarEventosEjecucion eventos) { this.eventos = eventos; }
    @PostMapping("/inicio")
    public ResponseEntity<EventoRespuesta> inicio(@PathVariable UUID jornadaId,@Valid @RequestBody RegistrarSolicitud d) {
        return registrar(jornadaId,d,TipoEventoEjecucion.INICIO);
    }
    @PostMapping("/fin")
    public ResponseEntity<EventoRespuesta> fin(@PathVariable UUID jornadaId,@Valid @RequestBody RegistrarSolicitud d) {
        return registrar(jornadaId,d,TipoEventoEjecucion.FIN);
    }
    @GetMapping
    public LecturaRespuesta consultar(@PathVariable UUID jornadaId) {
        var lectura = eventos.consultar(jornadaId);
        return new LecturaRespuesta(jornadaId,EventoRespuesta.desde(lectura.inicio()),EventoRespuesta.desde(lectura.fin()));
    }
    private ResponseEntity<EventoRespuesta> registrar(UUID jornadaId,RegistrarSolicitud d,TipoEventoEjecucion tipo) {
        var resultado = eventos.registrar(jornadaId,d.eventoClienteId(),d.asignacionJornadaId(),tipo,d.capturadoEn());
        return ResponseEntity.status(resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create("/api/jornadas/" + jornadaId + "/ejecucion")).body(EventoRespuesta.desde(resultado.evento()));
    }
    public record RegistrarSolicitud(@NotNull(message="eventoClienteId es obligatorio.") UUID eventoClienteId,
            @NotNull(message="asignacionJornadaId es obligatorio.") UUID asignacionJornadaId,
            @NotNull(message="capturadoEn es obligatorio.") Instant capturadoEn) {
        @JsonAnySetter public void rechazarCampo(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido para evidencia operativa."); }
    }
    public record EventoRespuesta(UUID id,UUID eventoClienteId,UUID jornadaId,UUID asignacionJornadaId,TipoEventoEjecucion tipoEvento,Instant capturadoEn,Instant recibidoEn) {
        static EventoRespuesta desde(EventoEjecucionJornada e) {
            return e == null ? null : new EventoRespuesta(e.id(),e.eventoClienteId(),e.jornadaId(),e.asignacionJornadaId(),e.tipoEvento(),e.capturadoEn(),e.recibidoEn());
        }
    }
    public record LecturaRespuesta(UUID jornadaId,EventoRespuesta inicio,EventoRespuesta fin) { }
}
