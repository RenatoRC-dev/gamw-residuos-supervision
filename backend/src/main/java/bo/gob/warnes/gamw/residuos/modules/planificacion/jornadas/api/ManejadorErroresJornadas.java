package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.*;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={JornadasController.class,PeriodosPlanificacionController.class,JornadasOperadorController.class})
@Order(-2)
public class ManejadorErroresJornadas {
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalido(IllegalArgumentException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage()); }
    @ExceptionHandler(ReferenciaPlanificacionNoEncontrada.class)
    public ProblemDetail noEncontrado(ReferenciaPlanificacionNoEncontrada e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage()); }
    @ExceptionHandler(ConflictoPlanificacion.class)
    public ProblemDetail conflicto(ConflictoPlanificacion e) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"El cambio incumple la integridad histórica o ya existe una asignación abierta."); }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail contrato() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"JSON o campos no admitidos. La empresa se deriva del periodo; los recursos se asignan por vínculos históricos. Use las acciones explícitas de planificación."); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage()).findFirst().orElse("Revise los datos de planificación."));
    }
}
