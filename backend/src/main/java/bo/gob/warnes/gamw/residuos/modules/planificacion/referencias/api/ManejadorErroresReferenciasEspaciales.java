package bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * CU-10: un fallo de persistencia (transacción revertida) se informa como tal. Sin este manejador la excepción llegaba a /error, cerrado por
 * la seguridad, y la interfaz mostraba «permiso requerido». Los rechazos de contrato siguen siendo ResponseStatusException (400/409).
 */
@RestControllerAdvice(assignableTypes = ReferenciasEspacialesController.class)
public class ManejadorErroresReferenciasEspaciales {
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail noGuardado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo guardar la referencia espacial; la operación se revirtió y la historia anterior se conserva.");
    }
}
