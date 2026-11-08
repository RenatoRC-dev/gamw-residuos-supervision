package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;

public record PeriodoServicio(UUID id, UUID empresaOperadoraId, LocalDate fechaInicio, LocalDate fechaFin, boolean activo, Instant creadoEn, Instant actualizadoEn) {
    public PeriodoServicio {
        ReglasRegistroOperativo.fechas(fechaInicio, fechaFin, true);
    }

}
