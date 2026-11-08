package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;

public record VinculoPersonaEmpresa(UUID id, UUID personaId, UUID empresaOperadoraId, LocalDate fechaInicio, LocalDate fechaFin, boolean activo, Instant creadoEn, Instant actualizadoEn) {
    public VinculoPersonaEmpresa {
        ReglasRegistroOperativo.fechas(fechaInicio, fechaFin, false);
    }

}
