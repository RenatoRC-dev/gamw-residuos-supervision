package bo.gob.warnes.gamw.residuos.modules.planning.routes.domain;

import java.time.Instant;
import java.util.UUID;

public record Route(UUID id, String code, String name, boolean active, Instant createdAt, Instant updatedAt) { }
