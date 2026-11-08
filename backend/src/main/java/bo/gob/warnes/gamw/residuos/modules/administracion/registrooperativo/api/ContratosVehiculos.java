package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.GestionarVehiculos.DatosVehiculo;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ContratosVehiculos {
    private ContratosVehiculos() { }
    public record CrearMarcaSolicitud(@NotBlank String nombre) { }
    public record CrearModeloSolicitud(@NotNull UUID marcaVehiculoId,@NotBlank String nombre) { }
    public record CrearTipoSolicitud(@NotBlank String codigo,@NotBlank String nombre) { }
    public record GuardarVehiculoSolicitud(@NotBlank String placa,UUID modeloVehiculoId,UUID tipoVehiculoId,@Min(1900) Integer anio,
            @DecimalMin(value="0",inclusive=false) BigDecimal capacidadCargaKg,@DecimalMin(value="0",inclusive=false) BigDecimal capacidadVolumenM3) {
        DatosVehiculo aComando() { return new DatosVehiculo(placa,modeloVehiculoId,tipoVehiculoId,anio,capacidadCargaKg,capacidadVolumenM3); }
    }
    public record CrearVinculoVehiculoSolicitud(@NotNull UUID vehiculoId,@NotNull LocalDate fechaInicio,LocalDate fechaFin,@NotNull Boolean activo) { }
    public record CerrarVinculoVehiculoSolicitud(LocalDate fechaFin,@NotNull Boolean activo) {
        @JsonAnySetter
        public void rechazarIdentidadHistorica(String campo,Object valor) {
            throw new IllegalArgumentException("La identidad del vínculo vehicular es histórica. Cierre el vínculo y cree otro para cambiar de empresa.");
        }
    }
    public record MarcaRespuesta(UUID id,String nombre,boolean activo) {
        static MarcaRespuesta desde(MarcaVehiculo m) { return new MarcaRespuesta(m.id(),m.nombre(),m.activo()); }
    }
    public record ModeloRespuesta(UUID id,UUID marcaVehiculoId,String nombre,boolean activo) {
        static ModeloRespuesta desde(ModeloVehiculo m) { return new ModeloRespuesta(m.id(),m.marcaVehiculoId(),m.nombre(),m.activo()); }
    }
    public record TipoRespuesta(UUID id,String codigo,String nombre,boolean activo) {
        static TipoRespuesta desde(TipoVehiculo t) { return new TipoRespuesta(t.id(),t.codigo(),t.nombre(),t.activo()); }
    }
    public record VehiculoRespuesta(UUID id,String placa,UUID modeloVehiculoId,UUID tipoVehiculoId,Integer anio,BigDecimal capacidadCargaKg,
            BigDecimal capacidadVolumenM3,boolean activo,Instant creadoEn,Instant actualizadoEn) {
        static VehiculoRespuesta desde(Vehiculo v) {
            return new VehiculoRespuesta(v.id(),v.placa(),v.modeloVehiculoId(),v.tipoVehiculoId(),v.anio(),v.capacidadCargaKg(),v.capacidadVolumenM3(),v.activo(),v.creadoEn(),v.actualizadoEn());
        }
    }
    public record VinculoVehiculoRespuesta(UUID id,UUID vehiculoId,UUID empresaOperadoraId,LocalDate fechaInicio,LocalDate fechaFin,
            boolean activo,Instant creadoEn,Instant actualizadoEn) {
        static VinculoVehiculoRespuesta desde(VinculoVehiculoEmpresa v) {
            return new VinculoVehiculoRespuesta(v.id(),v.vehiculoId(),v.empresaOperadoraId(),v.fechaInicio(),v.fechaFin(),v.activo(),v.creadoEn(),v.actualizadoEn());
        }
    }
}
