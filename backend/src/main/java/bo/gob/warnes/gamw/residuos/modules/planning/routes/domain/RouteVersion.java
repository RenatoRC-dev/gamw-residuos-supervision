package bo.gob.warnes.gamw.residuos.modules.planning.routes.domain;

import java.time.Instant;
import java.util.UUID;

public record RouteVersion(UUID id, UUID routeId, int versionNumber, RouteGeometry geometry,
        Instant validFrom, Instant validTo, String changeReason, Instant createdAt) { }
