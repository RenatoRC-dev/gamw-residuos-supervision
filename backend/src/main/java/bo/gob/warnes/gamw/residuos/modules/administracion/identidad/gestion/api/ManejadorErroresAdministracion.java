package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=AdministracionIdentidadController.class)
public class ManejadorErroresAdministracion {
    @ExceptionHandler(DataIntegrityViolationException.class) public ProblemDetail conflicto() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Nombre/concesión ya existentes o referencia incompatible. La operación no deja cambios parciales."); }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ProblemDetail invalido() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Contrato administrativo inválido; no se admiten campos adicionales ni autoridad suministrada por el cliente."); }
}
