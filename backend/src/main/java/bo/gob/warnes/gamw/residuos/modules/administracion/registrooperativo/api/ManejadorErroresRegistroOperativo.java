package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.RegistroNoEncontrado;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = {PersonasController.class, EmpresasOperadorasController.class, VehiculosController.class, VinculosVehiculoEmpresaController.class})
@Order(-1)
public class ManejadorErroresRegistroOperativo {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail contratoInvalido(HttpServletRequest solicitud) {
        if (solicitud.getRequestURI().contains("/vehiculos")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "JSON vehicular inválido o campos no admitidos. Al actualizar un vínculo vehicular solo se admiten fechaFin y activo; vehiculoId, empresaOperadoraId y fechaInicio son históricos. Cierre el vínculo y cree otro.");
        }
        // H3 (auditoría F1): la explicación sobre vínculos solo corresponde al recurso de personal.
        if (solicitud.getRequestURI().contains("/personal")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "JSON inválido o campos no admitidos. Al actualizar un vínculo solo se admiten fechaInicio (sin cambios), fechaFin y activo. Las funciones son históricas: cierre el vínculo y cree uno nuevo para cambiarlas.");
        }
        if (solicitud.getRequestURI().contains("/periodos")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "JSON de periodo inválido. Indique fechaInicio y fechaFin en formato AAAA-MM-DD; la empresa se toma de la ruta.");
        }
        if (solicitud.getRequestURI().contains("/empresas-operadoras")) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "JSON de empresa inválido. Se admiten razonSocial, nit y nombreComercial (o activo al cambiar el estado).");
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "JSON inválido o campos no admitidos para este registro.");
    }
    @ExceptionHandler(RegistroNoEncontrado.class)
    public ProblemDetail noEncontrado(RegistroNoEncontrado error) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,error.getMessage()); }
    @ExceptionHandler(bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.ModificacionHistoricaIncompatible.class)
    public ProblemDetail historiaIncompatible(RuntimeException error) { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,error.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail datosInvalidos(IllegalArgumentException error) { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,error.getMessage()); }
    // CA-CU-04-07 / CA-CU-05-07: un fallo de persistencia (transacción revertida) se informa como tal. Sin este manejador, la excepción
    // llegaba a /error, que la cadena de seguridad cierra (denyAll), y el cliente recibía un 403 engañoso de «sin permiso».
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ProblemDetail falloPersistencia() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"No se pudo guardar; la operación se revirtió y los datos anteriores se conservan. Reintente.");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflicto() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"El identificador (CI, NIT, placa o catálogo) ya existe, o los datos incumplen una restricción de integridad.");
    }
}
