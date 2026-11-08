package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.aplicacion.GestionarRutasReferencia.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = RutasReferenciaController.class)
public class ManejadorErroresRuta {
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalida(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }
    @ExceptionHandler(RutaNoEncontrada.class)
    public ProblemDetail noEncontrada(RutaNoEncontrada error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }
    @ExceptionHandler(ConflictoVersion.class)
    public ProblemDetail conflicto(ConflictoVersion error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El código o la versión ya existe, o viola una restricción de integridad.");
    }
}
