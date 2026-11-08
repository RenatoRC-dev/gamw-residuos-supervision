package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio;

import java.time.Instant;
import java.util.UUID;

public record Jornada(UUID id,UUID periodoServicioId,UUID rutaVersionId,Instant inicioProgramado,Instant finProgramado,
        UUID jornadaAntecedenteId,String motivoReprogramacion,boolean vigente,Instant creadoEn,Instant actualizadoEn) {
    public Jornada {
        if (id == null || periodoServicioId == null || rutaVersionId == null || inicioProgramado == null) throw new IllegalArgumentException("Periodo, versión de ruta e inicio programado son obligatorios.");
        if (finProgramado != null && !finProgramado.isAfter(inicioProgramado)) throw new IllegalArgumentException("El fin programado debe ser posterior al inicio.");
        if (id.equals(jornadaAntecedenteId)) throw new IllegalArgumentException("Una jornada no puede ser su propio antecedente.");
        if (jornadaAntecedenteId == null) {
            if (motivoReprogramacion != null && !motivoReprogramacion.isBlank()) throw new IllegalArgumentException("Una jornada original no tiene motivo de reprogramación.");
            motivoReprogramacion = null;
        } else {
            motivoReprogramacion = motivoObligatorio(motivoReprogramacion);
        }
    }
    public static String motivoObligatorio(String motivo) {
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("El motivo del cambio es obligatorio.");
        return motivo.strip();
    }
}
