package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion.ConflictoEjecucion;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion.EjecucionNoAutorizada;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.ReferenciaPlanificacionNoEncontrada;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=EjecucionJornadasController.class)
@Order(-3)
public class ManejadorErroresEjecucion {
    @ExceptionHandler(EjecucionNoAutorizada.class)
    public ProblemDetail noAutorizado(EjecucionNoAutorizada e) { return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,e.getMessage()); }
    @ExceptionHandler(org.springframework.security.authentication.InsufficientAuthenticationException.class)
    public ProblemDetail sinIdentidad() { return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,"Se requiere identidad GAMW autenticada."); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalido(IllegalArgumentException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage()); }
    @ExceptionHandler(ConflictoEjecucion.class)
    public ProblemDetail conflicto(ConflictoEjecucion e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage()); }
    @ExceptionHandler(ReferenciaPlanificacionNoEncontrada.class)
    public ProblemDetail noEncontrado(ReferenciaPlanificacionNoEncontrada e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage()); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail contrato() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Solo se admiten eventoClienteId, asignacionJornadaId y capturadoEn. El servidor determina identidad, recepción y tipo de evento."); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getBindingResult().getFieldErrors().stream().map(x -> x.getDefaultMessage()).findFirst().orElse("Revise el evento."));
    }
}
