package bo.gob.warnes.gamw.residuos.shared.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness only. Does not certify database readiness or service execution. */
@RestController
public class HealthController {
    @GetMapping("/api/technical/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }

    public record HealthResponse(String status) { }
}
