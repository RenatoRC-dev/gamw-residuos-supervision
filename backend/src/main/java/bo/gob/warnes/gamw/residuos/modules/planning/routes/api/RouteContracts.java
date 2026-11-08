package bo.gob.warnes.gamw.residuos.modules.planning.routes.api;

import bo.gob.warnes.gamw.residuos.modules.planning.routes.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RouteContracts {
    private RouteContracts() { }

    public record Geometry(String type, List<List<Double>> coordinates) {
        RouteGeometry toDomain() {
            if (!"LineString".equals(type)) throw new IllegalArgumentException("La geometría debe ser LineString.");
            return new RouteGeometry(coordinates);
        }
        static Geometry from(RouteGeometry geometry) { return new Geometry("LineString", geometry.coordinates()); }
    }
    public record CreateRouteRequest(@NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 160) String name, @NotNull @Valid Geometry geometry,
            @NotNull Instant validFrom, @Size(max = 500) String changeReason) { }
    public record CreateVersionRequest(@NotNull @Valid Geometry geometry,
            @NotNull Instant validFrom, @Size(max = 500) String changeReason) { }
    public record RouteResponse(UUID id, String code, String name, boolean active, Instant createdAt, Instant updatedAt) {
        static RouteResponse from(Route route) {
            return new RouteResponse(route.id(), route.code(), route.name(), route.active(), route.createdAt(), route.updatedAt());
        }
    }
    public record VersionResponse(UUID id, UUID routeId, int versionNumber, Geometry geometry,
            Instant validFrom, Instant validTo, String changeReason, Instant createdAt) {
        static VersionResponse from(RouteVersion version) {
            return new VersionResponse(version.id(), version.routeId(), version.versionNumber(), Geometry.from(version.geometry()),
                    version.validFrom(), version.validTo(), version.changeReason(), version.createdAt());
        }
    }
    public record CreatedRouteResponse(RouteResponse route, VersionResponse version) { }
}
