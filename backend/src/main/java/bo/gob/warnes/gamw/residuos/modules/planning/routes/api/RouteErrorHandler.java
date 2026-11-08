package bo.gob.warnes.gamw.residuos.modules.planning.routes.api;

import bo.gob.warnes.gamw.residuos.modules.planning.routes.application.ManageReferenceRoutes.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = ReferenceRoutesController.class)
public class RouteErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }
    @ExceptionHandler(RouteNotFound.class)
    public ProblemDetail notFound(RouteNotFound error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }
    @ExceptionHandler(VersionConflict.class)
    public ProblemDetail conflict(VersionConflict error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "El código o la versión ya existe, o viola una restricción de integridad.");
    }
}
