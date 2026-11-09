package bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio;

import java.time.Instant;
import java.util.UUID;

/** Evidencia recibida, sin interpretación de cumplimiento. Coordenadas EPSG:4326. */
public record PosicionGps(UUID id,UUID capturaId,UUID jornadaId,UUID asignacionJornadaId,UUID usuarioId,
        Instant capturadoEn,Instant recibidoEn,double latitud,double longitud,double precisionMetros) { }
