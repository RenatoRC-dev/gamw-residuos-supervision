package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio;

import java.time.Instant;
import java.util.UUID;

public record RutaVersion(UUID id, UUID rutaId, int numeroVersion, GeometriaRuta geometria,
        Instant vigenteDesde, Instant vigenteHasta, String motivoCambio, Instant creadoEn) { }
