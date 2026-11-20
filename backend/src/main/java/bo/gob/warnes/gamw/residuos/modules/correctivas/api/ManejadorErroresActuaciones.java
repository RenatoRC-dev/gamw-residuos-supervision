package bo.gob.warnes.gamw.residuos.modules.correctivas.api;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion.EjecucionNoAutorizada;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.ReferenciaPlanificacionNoEncontrada;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={ActuacionesController.class,CorrectivasEmpresaController.class})
public class ManejadorErroresActuaciones {
    @ExceptionHandler(EjecucionNoAutorizada.class)
    public ProblemDetail ajeno() { return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"Asignación ajena a la persona autenticada."); }
    @ExceptionHandler(ReferenciaPlanificacionNoEncontrada.class)
    public ProblemDetail ausente() { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,"Referencia no disponible."); }
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ProblemDetail invalido() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Contrato inválido; no se admiten campos de identidad ni estado del servidor."); }
    // CU-14 E5 / CA-CU-14-06: un fallo de almacenamiento (transacción revertida) se informa como tal. Sin este manejador llegaba a /error,
    // cerrado por la seguridad, y el móvil mostraba «sin autorización» en lugar de «no confirmado; reintente la misma solicitud».
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ProblemDetail noGuardado() { return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo guardar; no hay confirmación. Reintente la misma solicitud."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflicto() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"Actuación incompatible o ya registrada. El historial se conserva."); }
}
