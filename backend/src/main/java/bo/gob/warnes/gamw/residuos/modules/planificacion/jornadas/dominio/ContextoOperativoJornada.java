package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio;

import java.time.LocalDate;
import java.util.UUID;

/** Proyección de lectura de relaciones existentes, sin duplicar su persistencia. CU-13. */
public record ContextoOperativoJornada(Periodo periodo, RutaReferencia ruta, VehiculoAsignado vehiculo,
        UUID vinculoPersonaEmpresaId, UUID vinculoVehiculoEmpresaId) {
    public record Periodo(UUID id, LocalDate fechaInicio, LocalDate fechaFin,
            UUID empresaOperadoraId, String razonSocial) { }
    public record RutaReferencia(UUID rutaId, String codigo, String nombre, UUID rutaVersionId, int numeroVersion) { }
    public record VehiculoAsignado(UUID id, String placa, Integer anio) { }
}
