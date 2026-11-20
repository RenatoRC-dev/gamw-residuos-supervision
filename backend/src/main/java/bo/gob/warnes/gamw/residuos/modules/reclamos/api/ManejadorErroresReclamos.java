package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={ReclamosController.class,PuntosCriticosController.class})
public class ManejadorErroresReclamos {
    @ExceptionHandler(DataIntegrityViolationException.class) public ProblemDetail conflicto() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Referencia incompatible, relación repetida o decisión ya registrada. El historial previo permanece intacto."); }
    /** Fallo de persistencia no clasificado: la transacción se revirtió; no se confirma la actuación ni se informa como falta de permiso. */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class) public ProblemDetail persistencia() { return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo guardar; la operación se revirtió y el historial previo permanece intacto. Reintente con la misma solicitud."); }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ProblemDetail invalido() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Contrato inválido; no se admiten identidad, autoridad o estado suministrados por el cliente."); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ProblemDetail grande() { return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,"Anexo demasiado grande; reclamo original conservado."); }
}
