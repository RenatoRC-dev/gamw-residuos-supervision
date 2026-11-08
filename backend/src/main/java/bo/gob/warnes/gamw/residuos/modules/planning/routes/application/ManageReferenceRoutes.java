package bo.gob.warnes.gamw.residuos.modules.planning.routes.application;

import bo.gob.warnes.gamw.residuos.modules.planning.routes.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-09 / RF-06 / BR-04. No authority/approval workflow is assumed here. */
@Service
@Transactional(readOnly = true)
public class ManageReferenceRoutes {
    private final RouteRepository routes;

    public ManageReferenceRoutes(RouteRepository routes) { this.routes = routes; }

    @Transactional
    public CreatedRoute create(String code, String name, RouteGeometry geometry, Instant validFrom, String reason) {
        Instant now = Instant.now();
        var route = new Route(UUID.randomUUID(), code.strip(), name.strip(), true, now, now);
        routes.insert(route);
        var version = new RouteVersion(UUID.randomUUID(), route.id(), 1, geometry, validFrom, null, reason, now);
        routes.insert(version);
        return new CreatedRoute(route, version);
    }

    @Transactional
    public RouteVersion createVersion(UUID routeId, RouteGeometry geometry, Instant validFrom, String reason) {
        routes.find(routeId, true).orElseThrow(() -> new RouteNotFound("Ruta no encontrada."));
        var previous = routes.latest(routeId).orElseThrow(() -> new IllegalStateException("Ruta sin versión inicial."));
        if (!validFrom.isAfter(previous.validFrom())) {
            throw new VersionConflict("La nueva vigencia debe ser posterior a la última versión.");
        }
        Instant now = Instant.now();
        routes.closeVersion(previous.id(), validFrom);
        var next = new RouteVersion(UUID.randomUUID(), routeId, Math.addExact(previous.versionNumber(), 1),
                geometry, validFrom, null, reason, now);
        routes.insert(next);
        routes.touch(routeId, now);
        return next;
    }

    public List<Route> list() { return routes.list(); }
    public Route get(UUID id) {
        return routes.find(id, false).orElseThrow(() -> new RouteNotFound("Ruta no encontrada."));
    }
    public List<RouteVersion> versions(UUID routeId) { get(routeId); return routes.versions(routeId); }
    public RouteVersion version(UUID routeId, int number) {
        return routes.version(routeId, number).orElseThrow(() -> new RouteNotFound("Versión no encontrada."));
    }

    public record CreatedRoute(Route route, RouteVersion version) { }
    public static class RouteNotFound extends RuntimeException {
        public RouteNotFound(String message) { super(message); }
    }
    public static class VersionConflict extends RuntimeException {
        public VersionConflict(String message) { super(message); }
    }
}
