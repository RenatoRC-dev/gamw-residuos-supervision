package bo.gob.warnes.gamw.residuos.modules.planning.routes.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence owned by CU-09, including the lock used to serialize version creation. */
public interface RouteRepository {
    void insert(Route route);
    void insert(RouteVersion version);
    List<Route> list();
    Optional<Route> find(UUID id, boolean lock);
    List<RouteVersion> versions(UUID routeId);
    Optional<RouteVersion> latest(UUID routeId);
    Optional<RouteVersion> version(UUID routeId, int number);
    void closeVersion(UUID versionId, Instant validTo);
    void touch(UUID routeId, Instant updatedAt);
}
