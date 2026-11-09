package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio;

import java.time.Instant;
import java.util.UUID;

public record EventoEjecucionJornada(UUID id,UUID eventoClienteId,UUID jornadaId,UUID asignacionJornadaId,
        TipoEventoEjecucion tipoEvento,Instant capturadoEn,Instant recibidoEn,UUID usuarioId) {
    /** Compatibilidad con evidencia histórica y el flujo técnico de desarrollo-local. */
    public EventoEjecucionJornada(UUID id,UUID eventoClienteId,UUID jornadaId,UUID asignacionJornadaId,
            TipoEventoEjecucion tipoEvento,Instant capturadoEn,Instant recibidoEn) {
        this(id,eventoClienteId,jornadaId,asignacionJornadaId,tipoEvento,capturadoEn,recibidoEn,null);
    }
    public EventoEjecucionJornada {
        if (id == null || eventoClienteId == null || jornadaId == null || asignacionJornadaId == null ||
                tipoEvento == null || capturadoEn == null || recibidoEn == null) {
            throw new IllegalArgumentException("La identidad del evento, jornada, asignación, tipo y fechas son obligatorios.");
        }
        // PostgreSQL timestamptz conserva microsegundos. Rechazar evita redondeo silencioso e idempotencia ambigua.
        if (capturadoEn.getNano() % 1000 != 0) throw new IllegalArgumentException("capturadoEn admite hasta microsegundos; no se redondea la fecha declarada.");
    }
    public boolean mismoContenido(UUID jornada,UUID asignacion,TipoEventoEjecucion tipo,Instant captura) {
        return jornadaId.equals(jornada) && asignacionJornadaId.equals(asignacion) && tipoEvento == tipo && capturadoEn.equals(captura);
    }
}
