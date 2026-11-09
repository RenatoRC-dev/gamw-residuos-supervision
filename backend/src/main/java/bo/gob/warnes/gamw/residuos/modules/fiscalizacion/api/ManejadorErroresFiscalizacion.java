package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.core.JacksonException;

@RestControllerAdvice(assignableTypes=FiscalizacionController.class)
public class ManejadorErroresFiscalizacion {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflicto() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Solicitud duplicada o referencia inválida. No se reemplaza ningún antecedente."); }
    @ExceptionHandler(JacksonException.class)
    public ProblemDetail contrato() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Contrato de inspección inválido; identidad y registro se determinan en servidor."); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail limite() { return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,"Adjuntos fuera de los límites permitidos; no se registró evidencia."); }
}
