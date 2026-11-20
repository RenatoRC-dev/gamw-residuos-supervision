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
    /** Fallo de persistencia no clasificado: la transacción se revirtió; no se confirma la actuación ni se informa como falta de permiso. */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ProblemDetail persistencia() { return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo consultar o guardar; no se registró ninguna actuación y los antecedentes permanecen intactos. Reintente."); }
    @ExceptionHandler(JacksonException.class)
    public ProblemDetail contrato() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Contrato de inspección inválido; identidad y registro se determinan en servidor."); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail limite() { return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,"Adjuntos fuera de los límites permitidos; no se registró evidencia."); }
}
