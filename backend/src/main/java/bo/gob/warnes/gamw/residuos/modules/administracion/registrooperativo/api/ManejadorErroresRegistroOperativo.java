package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.RegistroNoEncontrado;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice(assignableTypes = {PersonasController.class, EmpresasOperadorasController.class})
@Order(-1)
public class ManejadorErroresRegistroOperativo {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail contratoInvalido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "JSON inválido o campos no admitidos. Al actualizar un vínculo solo se admiten fechaInicio (sin cambios), fechaFin y activo. Las funciones son históricas: cierre el vínculo y cree uno nuevo para cambiarlas.");
    }
    @ExceptionHandler(RegistroNoEncontrado.class)
    public ProblemDetail noEncontrado(RegistroNoEncontrado error) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,error.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail datosInvalidos(IllegalArgumentException error) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,error.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflicto() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"El CI o NIT ya existe, o los datos incumplen una restricción de integridad.");
    }
}
