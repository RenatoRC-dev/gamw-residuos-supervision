package bo.gob.warnes.gamw.residuos.modules.planning.routes.api;

import bo.gob.warnes.gamw.residuos.modules.planning.routes.application.ManageReferenceRoutes;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static bo.gob.warnes.gamw.residuos.modules.planning.routes.api.RouteContracts.*;

@RestController
@RequestMapping("/api/routes")
public class ReferenceRoutesController {
    private final ManageReferenceRoutes useCase;
    public ReferenceRoutesController(ManageReferenceRoutes useCase) { this.useCase = useCase; }

    @PostMapping
    public ResponseEntity<CreatedRouteResponse> create(@Valid @RequestBody CreateRouteRequest request) {
        var created = useCase.create(request.code(), request.name(), request.geometry().toDomain(), request.validFrom(), request.changeReason());
        return ResponseEntity.created(URI.create("/api/routes/" + created.route().id()))
                .body(new CreatedRouteResponse(RouteResponse.from(created.route()), VersionResponse.from(created.version())));
    }
    @GetMapping
    public List<RouteResponse> list() { return useCase.list().stream().map(RouteResponse::from).toList(); }
    @GetMapping("/{routeId}")
    public RouteResponse get(@PathVariable UUID routeId) { return RouteResponse.from(useCase.get(routeId)); }
    @GetMapping("/{routeId}/versions")
    public List<VersionResponse> versions(@PathVariable UUID routeId) {
        return useCase.versions(routeId).stream().map(VersionResponse::from).toList();
    }
    @GetMapping("/{routeId}/versions/{versionNumber}")
    public VersionResponse version(@PathVariable UUID routeId, @PathVariable int versionNumber) {
        return VersionResponse.from(useCase.version(routeId, versionNumber));
    }
    @PostMapping("/{routeId}/versions")
    public ResponseEntity<VersionResponse> createVersion(@PathVariable UUID routeId, @Valid @RequestBody CreateVersionRequest request) {
        var version = useCase.createVersion(routeId, request.geometry().toDomain(), request.validFrom(), request.changeReason());
        return ResponseEntity.created(URI.create("/api/routes/" + routeId + "/versions/" + version.versionNumber()))
                .body(VersionResponse.from(version));
    }
}
