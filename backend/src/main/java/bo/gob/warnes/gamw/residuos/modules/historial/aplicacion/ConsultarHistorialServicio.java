package bo.gob.warnes.gamw.residuos.modules.historial.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionMunicipal;
import bo.gob.warnes.gamw.residuos.modules.historial.persistencia.RepositorioHistorialServicio;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-29 (primer corte, solo lectura). Autorización: reutiliza JORNADAS_CONSULTAR, la concesión municipal que ya
 * expone esos mismos datos (jornada, periodo, empresa, versión de ruta y asignaciones) por /api/jornadas.
 * No crea permisos ni política de acceso histórico (D1/RN-CU-29-05 siguen PENDING). No consulta ni resume GPS,
 * ejecución, inspecciones, incidencias ni reclamos: cada vista existente exige su propio permiso.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarHistorialServicio {
    public static final int PAGINA = 50;
    private final AutorizacionMunicipal permisos;
    private final RepositorioHistorialServicio repositorio;
    public ConsultarHistorialServicio(AutorizacionMunicipal permisos,RepositorioHistorialServicio repositorio) { this.permisos = permisos; this.repositorio = repositorio; }

    public record Periodo(UUID id,LocalDate fechaInicio,LocalDate fechaFin) { }
    public record Empresa(UUID id,String razonSocial) { }
    public record RutaVersionHistorica(UUID rutaId,String codigo,String nombre,UUID versionId,int numeroVersion,Instant vigenteDesde,Instant vigenteHasta) { }
    public record ResumenJornada(UUID id,Instant inicioProgramado,Instant finProgramado,boolean vigente,UUID antecedenteId,UUID sucesoraId,String motivoReprogramacion,
            Periodo periodo,Empresa empresa,RutaVersionHistorica ruta,int cantidadAsignaciones) { }
    public record AsignacionHistorica(UUID id,Instant vigenteDesde,Instant vigenteHasta,String motivoCambio,UUID vinculoVehiculoEmpresaId,UUID vinculoPersonaEmpresaId,
            String placa,String responsable,UUID empresaVehiculoId,UUID empresaPersonaId) { }
    public record Enlace(String modulo,String ruta,String nota) { }
    public record Pagina(List<ResumenJornada> jornadas,Integer siguiente) { }
    public record Detalle(ResumenJornada jornada,List<ResumenJornada> cadena,List<AsignacionHistorica> asignaciones,List<String> inconsistencias,List<Enlace> enlaces) { }

    public Pagina buscar(UsuarioAutenticado actor,UUID periodo,UUID empresa,UUID ruta,LocalDate desde,LocalDate hasta,int desplazamiento) {
        permisos.exigirConsultaHistorialServicio(actor);
        if (desde != null && hasta != null && desde.isAfter(hasta)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El rango de fechas es contradictorio: 'desde' no puede ser posterior a 'hasta'.");
        if (desplazamiento < 0 || desplazamiento > 10_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Desplazamiento inválido.");
        var filas = repositorio.buscar(periodo,empresa,ruta,desde,hasta,PAGINA + 1,desplazamiento);
        return new Pagina(filas.stream().limit(PAGINA).toList(),filas.size() > PAGINA ? desplazamiento + PAGINA : null);
    }

    public Detalle detalle(UsuarioAutenticado actor,UUID id) {
        permisos.exigirConsultaHistorialServicio(actor);
        var jornada = repositorio.jornada(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Jornada no encontrada."));
        var asignaciones = repositorio.asignaciones(id);
        var cadena = repositorio.cadena(id);
        var avisos = new ArrayList<String>();
        for (var a : asignaciones) {
            if (!a.empresaVehiculoId().equals(jornada.empresa().id()) || !a.empresaPersonaId().equals(jornada.empresa().id()))
                avisos.add("La asignación " + a.id() + " referencia un vínculo de otra empresa distinta de la del periodo; se muestra tal como fue registrada.");
        }
        if (jornada.antecedenteId() != null && cadena.stream().noneMatch(c -> c.id().equals(jornada.antecedenteId())))
            avisos.add("El antecedente declarado no pudo recuperarse; la cadena está incompleta.");
        if (asignaciones.isEmpty()) avisos.add("La jornada no registra asignaciones de recursos.");
        String nota = "Requiere su propio permiso municipal; esta consulta no confirma si existen registros.";
        var enlaces = List.of(
            new Enlace("Trayectoria GPS","/trayectorias?jornada=" + id,nota),
            new Enlace("Revisión de evidencia GPS","/revision-gps?jornada=" + id,nota),
            new Enlace("Inspección y fiscalización","/fiscalizacion?jornada=" + id,nota),
            new Enlace("Incidencias y acciones correctivas","/correctivas?jornada=" + id,nota));
        return new Detalle(jornada,cadena,asignaciones,avisos,enlaces);
    }
}
