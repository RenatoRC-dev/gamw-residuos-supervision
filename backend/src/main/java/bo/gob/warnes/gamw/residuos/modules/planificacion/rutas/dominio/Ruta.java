package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio;

import java.time.Instant;
import java.util.UUID;

public record Ruta(UUID id, String codigo, String nombre, boolean activo, Instant creadoEn, Instant actualizadoEn) { }
