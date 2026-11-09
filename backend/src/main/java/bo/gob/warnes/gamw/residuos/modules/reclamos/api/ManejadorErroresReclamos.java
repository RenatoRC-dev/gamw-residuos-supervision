package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={ReclamosController.class,PuntosCriticosController.class})
public class ManejadorErroresReclamos {
    @ExceptionHandler(DataIntegrityViolationException.class) public ProblemDetail conflicto() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Referencia incompatible, relación repetida o decisión ya registrada. El historial previo permanece intacto."); }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ProblemDetail invalido() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Contrato inválido; no se admiten identidad, autoridad o estado suministrados por el cliente."); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ProblemDetail grande() { return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,"Anexo demasiado grande; reclamo original conservado."); }
}
