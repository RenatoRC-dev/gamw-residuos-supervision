package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio;

import java.time.Instant;
import java.util.UUID;

/** Proyección propia de la persona autenticada, incluida su asignación histórica. */
public record JornadaAsignada(UUID jornadaId, UUID asignacionJornadaId, Instant inicioProgramado,
        Instant finProgramado, boolean vigente, Instant vigenteDesde, Instant vigenteHasta) { }
