package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;

/** CU-06 / RF-04. Registro operativo independiente de jornadas y autenticación. */
public record Vehiculo(UUID id, String placa, UUID modeloVehiculoId, UUID tipoVehiculoId, Integer anio, BigDecimal capacidadCargaKg, BigDecimal capacidadVolumenM3, boolean activo, Instant creadoEn, Instant actualizadoEn) {
    public Vehiculo {
        placa = ReglasRegistroOperativo.requerido(placa).toUpperCase(java.util.Locale.ROOT);
        if (anio != null && anio < 1900) throw new IllegalArgumentException("El año debe ser mayor o igual a 1900.");
        if (capacidadCargaKg != null && capacidadCargaKg.signum() <= 0) throw new IllegalArgumentException("La capacidad de carga en kg debe ser mayor que cero.");
        if (capacidadVolumenM3 != null && capacidadVolumenM3.signum() <= 0) throw new IllegalArgumentException("La capacidad de volumen en m³ debe ser mayor que cero.");
    }
}
