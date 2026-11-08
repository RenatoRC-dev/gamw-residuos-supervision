package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-11/CU-12, RF-07/RF-08; fundamento parcial RF-20. Sin ejecución ni estados institucionales. */
@Service
@Transactional(readOnly = true)
public class GestionarJornadas {
    private final RepositorioJornadas repositorio;
    private final ReferenciasJornadas referencias;
    public GestionarJornadas(RepositorioJornadas repositorio,ReferenciasJornadas referencias) {
        this.repositorio = repositorio; this.referencias = referencias;
    }
    public List<VistaJornada> listar() { return repositorio.listar().stream().map(this::vista).toList(); }
    public VistaJornada obtener(UUID id) { return vista(buscar(id,false)); }
    private Jornada buscar(UUID id,boolean bloquear) {
        return repositorio.buscar(id,bloquear).orElseThrow(() -> new ReferenciaPlanificacionNoEncontrada("Jornada no encontrada."));
    }
    private void exigirVigente(Jornada jornada) {
        if (!jornada.vigente()) throw new ConflictoPlanificacion("La jornada ya fue reprogramada. Consulte y opere sobre su sucesora vigente.");
    }
    @Transactional
    public VistaJornada crear(Programacion datos) {
        referencias.validarProgramacion(datos.periodoServicioId(),datos.rutaVersionId(),datos.inicioProgramado());
        var ahora = ahora();
        var jornada = datos.jornada(UUID.randomUUID(),null,null,ahora);
        repositorio.insertar(jornada);
        return vista(jornada);
    }
    @Transactional
    public VistaJornada reprogramar(UUID id,Programacion datos,String motivo) {
        var anterior = buscar(id,true);
        exigirVigente(anterior);
        motivo = Jornada.motivoObligatorio(motivo);
        referencias.validarProgramacion(datos.periodoServicioId(),datos.rutaVersionId(),datos.inicioProgramado());
        var ahora = ahora();
        var siguiente = datos.jornada(UUID.randomUUID(),anterior.id(),motivo,ahora);
        repositorio.sustituir(anterior.id(),ahora);
        repositorio.insertar(siguiente);
        // No se copia ni se altera la asignación del antecedente.
        return vista(siguiente);
    }
    @Transactional
    public VistaAsignacion asignar(UUID id,UUID vehiculo,UUID persona) {
        var jornada = buscar(id,true);
        exigirVigente(jornada);
        if (repositorio.actual(id).isPresent()) throw new ConflictoPlanificacion("La jornada ya tiene asignación abierta. Utilice Reasignar recursos.");
        referencias.validarRecursos(jornada.periodoServicioId(),jornada.inicioProgramado(),vehiculo,persona);
        var asignacion = AsignacionJornada.inicial(UUID.randomUUID(),id,vehiculo,persona,ahora());
        repositorio.insertarAsignacion(asignacion);
        return vistaAsignacion(asignacion);
    }
    @Transactional
    public VistaAsignacion reasignar(UUID id,UUID vehiculo,UUID persona,String motivo) {
        var jornada = buscar(id,true);
        exigirVigente(jornada);
        motivo = Jornada.motivoObligatorio(motivo);
        var anterior = repositorio.actual(id).orElseThrow(() -> new ConflictoPlanificacion("La jornada no tiene una asignación abierta para sustituir."));
        referencias.validarRecursos(jornada.periodoServicioId(),jornada.inicioProgramado(),vehiculo,persona);
        var ahora = ahora();
        if (!ahora.isAfter(anterior.vigenteDesde())) ahora = anterior.vigenteDesde().plus(1,ChronoUnit.MICROS);
        var siguiente = AsignacionJornada.reasignacion(UUID.randomUUID(),id,vehiculo,persona,ahora,motivo);
        repositorio.cerrarAsignacion(anterior.id(),ahora);
        repositorio.insertarAsignacion(siguiente);
        return vistaAsignacion(siguiente);
    }
    public List<VistaAsignacion> historial(UUID id) {
        buscar(id,false);
        return repositorio.asignaciones(id).stream().map(this::vistaAsignacion).toList();
    }
    private VistaAsignacion vistaAsignacion(AsignacionJornada a) {
        return new VistaAsignacion(a,referencias.vehiculo(referencias.vinculoVehiculo(a.vinculoVehiculoEmpresaId()).vehiculoId()),
                referencias.persona(referencias.vinculoPersona(a.vinculoPersonaEmpresaId()).personaId()));
    }
    private VistaJornada vista(Jornada j) {
        var periodo = referencias.periodo(j.periodoServicioId());
        var version = referencias.version(j.rutaVersionId());
        return new VistaJornada(j,periodo,referencias.empresa(periodo.empresaOperadoraId()),version,referencias.ruta(version.rutaId()),
                repositorio.actual(j.id()).map(this::vistaAsignacion).orElse(null),referencias.fechaServicio(j.inicioProgramado()));
    }
    private Instant ahora() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    public record Programacion(UUID periodoServicioId,UUID rutaVersionId,Instant inicioProgramado,Instant finProgramado) {
        Jornada jornada(UUID id,UUID antecedente,String motivo,Instant ahora) {
            return new Jornada(id,periodoServicioId,rutaVersionId,inicioProgramado,finProgramado,antecedente,motivo,true,ahora,ahora);
        }
    }
    public record VistaAsignacion(AsignacionJornada asignacion,Vehiculo vehiculo,PersonaNatural persona) { }
    public record VistaJornada(Jornada jornada,PeriodoServicio periodo,EmpresaOperadora empresa,RutaVersion version,Ruta ruta,VistaAsignacion asignacionActual,LocalDate fechaServicio) { }
}
