package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecturas de las entidades publicadas, sin otra representación persistente de empresas, recursos o rutas. */
@Service
@Transactional(readOnly = true)
public class ReferenciasJornadas {
    private static final ZoneId ZONA_OPERATIVA = ZoneId.of("America/La_Paz");
    private final RepositorioRegistroOperativo registro;
    private final RepositorioVehiculos vehiculos;
    private final RepositorioRuta rutas;
    public ReferenciasJornadas(RepositorioRegistroOperativo registro,RepositorioVehiculos vehiculos,RepositorioRuta rutas) {
        this.registro = registro; this.vehiculos = vehiculos; this.rutas = rutas;
    }
    public PeriodoServicio periodo(UUID id) { return registro.periodoPorId(id).orElseThrow(() -> falta("Periodo de servicio")); }
    public EmpresaOperadora empresa(UUID id) { return registro.empresa(id,false).orElseThrow(() -> falta("Empresa operadora")); }
    public RutaVersion version(UUID id) { return rutas.versionPorId(id).orElseThrow(() -> falta("Versión de ruta")); }
    public Ruta ruta(UUID id) { return rutas.buscar(id,false).orElseThrow(() -> falta("Ruta")); }
    public VinculoVehiculoEmpresa vinculoVehiculo(UUID id) { return vehiculos.vinculoPorId(id).orElseThrow(() -> falta("Vínculo de vehículo")); }
    public VinculoPersonaEmpresa vinculoPersona(UUID id) { return registro.vinculoPorId(id).orElseThrow(() -> falta("Vínculo de persona")); }
    public Vehiculo vehiculo(UUID id) { return vehiculos.vehiculo(id,false).orElseThrow(() -> falta("Vehículo")); }
    public PersonaNatural persona(UUID id) { return registro.persona(id,false).orElseThrow(() -> falta("Persona")); }
    public List<PeriodoServicio> periodos() { return registro.todosPeriodos(); }
    public LocalDate fechaServicio(Instant inicio) { return inicio.atZone(ZONA_OPERATIVA).toLocalDate(); }
    public void validarProgramacion(UUID periodoId,UUID versionId,Instant inicio) {
        if (inicio == null) throw new IllegalArgumentException("El inicio programado es obligatorio.");
        var periodo = periodo(periodoId);
        var empresa = empresa(periodo.empresaOperadoraId());
        if (!periodo.activo() || !empresa.activo()) throw new IllegalArgumentException("El periodo y su empresa deben estar activos para programar.");
        validarFecha(fechaServicio(inicio),periodo.fechaInicio(),periodo.fechaFin(),"La jornada está fuera del periodo de servicio.");
        var version = version(versionId);
        if (inicio.isBefore(version.vigenteDesde()) || (version.vigenteHasta() != null && inicio.isAfter(version.vigenteHasta()))) {
            throw new IllegalArgumentException("La versión de ruta no es temporalmente válida para el inicio programado.");
        }
    }
    public void validarRecursos(UUID periodoId,Instant inicio,UUID vehiculoId,UUID personaId) {
        var periodo = periodo(periodoId);
        if (!periodo.activo() || !empresa(periodo.empresaOperadoraId()).activo()) throw new IllegalArgumentException("El periodo y la empresa deben estar activos para asignar.");
        var v = vinculoVehiculo(vehiculoId);
        var p = vinculoPersona(personaId);
        if (!v.empresaOperadoraId().equals(periodo.empresaOperadoraId()) || !p.empresaOperadoraId().equals(periodo.empresaOperadoraId())) {
            throw new IllegalArgumentException("Ambos vínculos deben pertenecer a la empresa derivada del periodo de servicio.");
        }
        var fecha = fechaServicio(inicio);
        validarFecha(fecha,v.fechaInicio(),v.fechaFin(),"El vínculo del vehículo no cubre la fecha programada.");
        validarFecha(fecha,p.fechaInicio(),p.fechaFin(),"El vínculo de la persona no cubre la fecha programada.");
        if (!v.activo() || !p.activo() || !vehiculo(v.vehiculoId()).activo() || !persona(p.personaId()).activo()) {
            throw new IllegalArgumentException("El vehículo, la persona y ambos vínculos deben estar activos para una nueva asignación.");
        }
        // No se exige CONDUCTOR: esa obligación institucional no está confirmada.
    }
    private void validarFecha(LocalDate fecha,LocalDate desde,LocalDate hasta,String mensaje) {
        if (fecha.isBefore(desde) || (hasta != null && fecha.isAfter(hasta))) throw new IllegalArgumentException(mensaje);
    }
    private ReferenciaPlanificacionNoEncontrada falta(String tipo) { return new ReferenciaPlanificacionNoEncontrada(tipo + " no encontrado/a."); }
}
