package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;

/** CU-06 / RF-04. Un segmento histórico; su identidad no se modifica. */
public record VinculoVehiculoEmpresa(UUID id, UUID vehiculoId, UUID empresaOperadoraId, LocalDate fechaInicio, LocalDate fechaFin, boolean activo, Instant creadoEn, Instant actualizadoEn) {
    public VinculoVehiculoEmpresa {
        java.util.Objects.requireNonNull(vehiculoId, "El vehículo es obligatorio.");
        java.util.Objects.requireNonNull(empresaOperadoraId, "La empresa es obligatoria.");
        ReglasRegistroOperativo.fechas(fechaInicio,fechaFin,false);
    }
}
