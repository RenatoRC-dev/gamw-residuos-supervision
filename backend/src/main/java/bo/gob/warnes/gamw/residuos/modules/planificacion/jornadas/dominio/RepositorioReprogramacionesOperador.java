package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio;

import java.util.*;

public interface RepositorioReprogramacionesOperador {
    record Actuacion(UUID usuarioId,UUID antecedenteId,UUID sucesoraId,String huella) { }
    record Periodo(UUID id,UUID empresaId,String empresa,java.time.LocalDate fechaInicio,java.time.LocalDate fechaFin,boolean puedeProgramar) { }
    record Candidato(UUID vinculoId,UUID recursoId,String nombre) { }
    record Recursos(List<Candidato> vehiculos,List<Candidato> personas) { }
    record VersionOpcion(UUID id,UUID rutaId,String ruta,int numeroVersion,java.time.Instant vigenteDesde,java.time.Instant vigenteHasta) { }
    record RegistroPlan(UUID usuarioId,UUID jornadaId,UUID asignacionId,String operacion,String huella) { }
    List<Periodo> periodos(UUID actor);
    boolean bloquearPeriodo(UUID periodo);
    Recursos recursos(UUID jornada,java.time.LocalDate fecha);
    List<VersionOpcion> versionesProgramables();
    Optional<RegistroPlan> operacion(UUID solicitud);
    void registrarOperacion(UUID solicitud,UUID jornada,UUID asignacion,UUID actor,UUID concesion,String operacion,String huella);
    List<UUID> listar(UUID usuario,int limite,int desde);
    Optional<UUID> sucesora(UUID jornada);
    boolean tieneEvidencia(UUID jornada);
    Optional<Actuacion> solicitud(UUID id);
    void registrar(UUID solicitud,UUID anterior,UUID siguiente,UUID usuario,UUID concesion,String huella);
}
