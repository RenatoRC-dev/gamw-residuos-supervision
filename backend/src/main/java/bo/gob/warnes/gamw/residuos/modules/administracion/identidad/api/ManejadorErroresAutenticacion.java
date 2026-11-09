package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion.CredencialesInvalidas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AutenticacionController.class)
public class ManejadorErroresAutenticacion {
    @ExceptionHandler(CredencialesInvalidas.class)
    public ProblemDetail credencialesInvalidas(CredencialesInvalidas error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail solicitudInvalida(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }
}
