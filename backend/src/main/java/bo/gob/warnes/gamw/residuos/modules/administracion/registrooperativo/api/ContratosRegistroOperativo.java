package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.GestionarPersonalEmpresa.DetallePersonal;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.UUID;

public final class ContratosRegistroOperativo {
    private ContratosRegistroOperativo() { }
    public record DatosPersona(@NotBlank String ci, @NotBlank String nombres, String apellidoPaterno, String apellidoMaterno, String telefono) { }
    public record DatosEmpresa(@Pattern(regexp = "(?s).*\\S.*", message = "El NIT no puede estar vacío si se proporciona.") String nit,
            @NotBlank String razonSocial, String nombreComercial) { }
    public record DatosPeriodo(@NotNull LocalDate fechaInicio, @NotNull LocalDate fechaFin) { }
    public record EstadoSolicitud(@NotNull Boolean activo) { }
    public record CrearVinculoSolicitud(@NotNull UUID personaId, @NotNull LocalDate fechaInicio, LocalDate fechaFin,
            @NotNull Boolean activo, @NotEmpty(message = "Seleccione al menos una función operativa activa.") List<@NotNull UUID> funcionOperativaIds) { }
    public record ActualizarVinculoSolicitud(@NotNull LocalDate fechaInicio, LocalDate fechaFin,
            @NotNull Boolean activo) {
        @JsonAnySetter
        public void rechazarCampoNoEditable(String campo, Object valor) {
            throw new IllegalArgumentException("Las funciones e identidad del vínculo son históricas. Cierre el vínculo y cree uno nuevo para cambiar funciones.");
        }
    }

    public record PersonaRespuesta(UUID id, String ci, String nombres, String apellidoPaterno, String apellidoMaterno, String telefono,
            boolean activo, Instant creadoEn, Instant actualizadoEn) {
        static PersonaRespuesta desde(PersonaNatural p) {
            return new PersonaRespuesta(p.id(),p.ci(),p.nombres(),p.apellidoPaterno(),p.apellidoMaterno(),p.telefono(),p.activo(),p.creadoEn(),p.actualizadoEn());
        }
    }
    public record EmpresaRespuesta(UUID id, String nit, String razonSocial, String nombreComercial, boolean activo, Instant creadoEn, Instant actualizadoEn) {
        static EmpresaRespuesta desde(EmpresaOperadora e) {
            return new EmpresaRespuesta(e.id(),e.nit(),e.razonSocial(),e.nombreComercial(),e.activo(),e.creadoEn(),e.actualizadoEn());
        }
    }
    public record PeriodoRespuesta(UUID id, UUID empresaOperadoraId, LocalDate fechaInicio, LocalDate fechaFin,
            boolean activo, Instant creadoEn, Instant actualizadoEn) {
        static PeriodoRespuesta desde(PeriodoServicio p) {
            return new PeriodoRespuesta(p.id(),p.empresaOperadoraId(),p.fechaInicio(),p.fechaFin(),p.activo(),p.creadoEn(),p.actualizadoEn());
        }
    }
    public record FuncionRespuesta(UUID id, String codigo, String nombre, boolean activo) {
        static FuncionRespuesta desde(FuncionOperativa f) { return new FuncionRespuesta(f.id(),f.codigo(),f.nombre(),f.activo()); }
    }
    public record PersonalRespuesta(UUID id, UUID personaId, UUID empresaOperadoraId, LocalDate fechaInicio, LocalDate fechaFin,
            boolean activo, Instant creadoEn, Instant actualizadoEn, PersonaRespuesta persona, List<FuncionRespuesta> funcionesOperativas) {
        static PersonalRespuesta desde(DetallePersonal d) {
            var v = d.vinculo();
            return new PersonalRespuesta(v.id(),v.personaId(),v.empresaOperadoraId(),v.fechaInicio(),v.fechaFin(),v.activo(),v.creadoEn(),v.actualizadoEn(),
                    PersonaRespuesta.desde(d.persona()),d.funciones().stream().map(FuncionRespuesta::desde).toList());
        }
    }
}
