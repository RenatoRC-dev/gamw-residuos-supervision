package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.GestionarJornadas.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class ContratosJornadas {
    private ContratosJornadas() { }
    public record ProgramarSolicitud(@NotNull(message="Seleccione un periodo de servicio.") UUID periodoServicioId,
            @NotNull(message="Seleccione una versión de ruta.") UUID rutaVersionId,
            @NotNull(message="Indique el inicio programado.") Instant inicioProgramado,Instant finProgramado) {
        Programacion comando() { return new Programacion(periodoServicioId,rutaVersionId,inicioProgramado,finProgramado); }
        @JsonAnySetter
        public void rechazarCampo(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido en el contrato de planificación."); }
    }
    public record ReprogramarSolicitud(@NotNull(message="Seleccione un periodo de servicio.") UUID periodoServicioId,
            @NotNull(message="Seleccione una versión de ruta.") UUID rutaVersionId,
            @NotNull(message="Indique el inicio programado.") Instant inicioProgramado,Instant finProgramado,
            @NotBlank(message="El motivo de reprogramación es obligatorio.") String motivoReprogramacion) {
        Programacion comando() { return new Programacion(periodoServicioId,rutaVersionId,inicioProgramado,finProgramado); }
        @JsonAnySetter
        public void rechazarCampo(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido en el contrato de planificación."); }
    }
    public record AsignarSolicitud(@NotNull(message="Seleccione un vínculo vehicular.") UUID vinculoVehiculoEmpresaId,
            @NotNull(message="Seleccione un vínculo de responsable.") UUID vinculoPersonaEmpresaId) {
        @JsonAnySetter
        public void rechazarCampo(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido en el contrato de planificación."); }
    }
    public record ReasignarSolicitud(@NotNull(message="Seleccione un vínculo vehicular.") UUID vinculoVehiculoEmpresaId,
            @NotNull(message="Seleccione un vínculo de responsable.") UUID vinculoPersonaEmpresaId,
            @NotBlank(message="El motivo de reasignación es obligatorio.") String motivoCambio) {
        @JsonAnySetter
        public void rechazarCampo(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido en el contrato de planificación."); }
    }
    public record JornadaRespuesta(UUID id,UUID periodoServicioId,UUID rutaVersionId,Instant inicioProgramado,Instant finProgramado,
            UUID jornadaAntecedenteId,String motivoReprogramacion,boolean vigente,Instant creadoEn,Instant actualizadoEn) {
        static JornadaRespuesta desde(Jornada j) { return new JornadaRespuesta(j.id(),j.periodoServicioId(),j.rutaVersionId(),j.inicioProgramado(),j.finProgramado(),
                j.jornadaAntecedenteId(),j.motivoReprogramacion(),j.vigente(),j.creadoEn(),j.actualizadoEn()); }
    }
    public record EmpresaResumen(UUID id,String razonSocial,boolean activo) { }
    public record PeriodoResumen(UUID id,UUID empresaOperadoraId,LocalDate fechaInicio,LocalDate fechaFin,boolean activo) { }
    public record VersionResumen(UUID id,UUID rutaId,String nombreRuta,int numeroVersion,Instant vigenteDesde,Instant vigenteHasta) { }
    public record VehiculoResumen(UUID id,String placa) { }
    public record PersonaResumen(UUID id,String ci,String nombres,String apellidoPaterno,String apellidoMaterno) { }
    public record AsignacionRespuesta(UUID id,UUID jornadaId,UUID vinculoVehiculoEmpresaId,UUID vinculoPersonaEmpresaId,
            Instant vigenteDesde,Instant vigenteHasta,String motivoCambio,Instant creadoEn,VehiculoResumen vehiculo,PersonaResumen responsable) {
        static AsignacionRespuesta desde(VistaAsignacion vista) {
            var a = vista.asignacion(); var p = vista.persona();
            return new AsignacionRespuesta(a.id(),a.jornadaId(),a.vinculoVehiculoEmpresaId(),a.vinculoPersonaEmpresaId(),a.vigenteDesde(),a.vigenteHasta(),a.motivoCambio(),a.creadoEn(),
                    new VehiculoResumen(vista.vehiculo().id(),vista.vehiculo().placa()),new PersonaResumen(p.id(),p.ci(),p.nombres(),p.apellidoPaterno(),p.apellidoMaterno()));
        }
    }
    public record DetalleJornadaRespuesta(JornadaRespuesta jornada,PeriodoResumen periodoServicio,EmpresaResumen empresaOperadora,
            VersionResumen rutaVersion,AsignacionRespuesta asignacionActual,LocalDate fechaServicio) {
        static DetalleJornadaRespuesta desde(VistaJornada v) {
            var p = v.periodo(); var e = v.empresa(); var r = v.version();
            return new DetalleJornadaRespuesta(JornadaRespuesta.desde(v.jornada()),new PeriodoResumen(p.id(),p.empresaOperadoraId(),p.fechaInicio(),p.fechaFin(),p.activo()),
                    new EmpresaResumen(e.id(),e.razonSocial(),e.activo()),new VersionResumen(r.id(),r.rutaId(),v.ruta().nombre(),r.numeroVersion(),r.vigenteDesde(),r.vigenteHasta()),
                    v.asignacionActual() == null ? null : AsignacionRespuesta.desde(v.asignacionActual()),v.fechaServicio());
        }
    }
    public record PeriodoOpcion(UUID id,UUID empresaOperadoraId,String razonSocial,LocalDate fechaInicio,LocalDate fechaFin,boolean activo,boolean empresaActiva) { }
}
