package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionOperador;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.aplicacion.PoliticaEvidenciasCampo;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly=true)
public class ReprogramarJornadasOperador {
    private final AutorizacionOperador permisos;
    private final RepositorioReprogramacionesOperador historial;
    private final RepositorioJornadas repositorio;
    private final GestionarJornadas jornadas;
    private final ObjectMapper json;
    public ReprogramarJornadasOperador(AutorizacionOperador permisos,RepositorioReprogramacionesOperador historial,RepositorioJornadas repositorio,GestionarJornadas jornadas,ObjectMapper json) {
        this.permisos=permisos;this.historial=historial;this.repositorio=repositorio;this.jornadas=jornadas;this.json=json;
    }
    public record Solicitud(UUID solicitudId,Instant inicioProgramado,Instant finProgramado,String motivoReprogramacion) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Solo fechas y motivo: periodo, versión e identidad proceden del servidor."); }
    }
    public record Asignacion(UUID id,UUID vinculoVehiculoEmpresaId,UUID vinculoPersonaEmpresaId,String placa,String responsable,Instant vigenteDesde,Instant vigenteHasta) { }
    public record Plan(UUID id,UUID periodoServicioId,String empresa,UUID rutaVersionId,String ruta,int numeroVersion,Instant inicioProgramado,Instant finProgramado,
        UUID antecedenteId,UUID sucesoraId,String motivo,boolean vigente,List<Asignacion> asignaciones,boolean puedeReprogramar,String estadoAsignacion,boolean puedeAsignar,String limitacion) { }
    public record Lista(List<Plan> jornadas,Integer siguiente) { }
    public Lista listar(UsuarioAutenticado actor,int desde) {
        permisos.exigirListadoJornadas(actor);
        if(desde<0 || desde>10000000) throw new IllegalArgumentException("Página inválida.");
        var ids=historial.listar(actor.usuarioId(),51,desde);
        return new Lista(ids.stream().limit(50).map(id->consultar(actor,id)).toList(),ids.size()>50?desde+50:null);
    }
    public Plan consultar(UsuarioAutenticado actor,UUID id) {
        permisos.exigirConsultaPlan(actor,id);
        var vista=jornadas.obtener(id);var j=vista.jornada();
        var asignaciones=jornadas.historial(id).stream().map(v->new Asignacion(v.asignacion().id(),v.asignacion().vinculoVehiculoEmpresaId(),v.asignacion().vinculoPersonaEmpresaId(),
            v.vehiculo().placa(),v.persona().nombres()+" "+Objects.toString(v.persona().apellidoPaterno(),"")+" "+Objects.toString(v.persona().apellidoMaterno(),""),v.asignacion().vigenteDesde(),v.asignacion().vigenteHasta())).toList();
        boolean reprogramar=autorizado(()->permisos.exigirJornada(actor,id,false)),asignar=autorizado(()->permisos.exigirJornadaOperativa(actor,id,"JORNADA_ASIGNAR",false));
        return new Plan(j.id(),j.periodoServicioId(),vista.empresa().razonSocial(),j.rutaVersionId(),vista.ruta().nombre(),vista.version().numeroVersion(),j.inicioProgramado(),j.finProgramado(),
            j.jornadaAntecedenteId(),historial.sucesora(id).orElse(null),j.motivoReprogramacion(),j.vigente(),asignaciones,reprogramar && j.vigente() && !historial.tieneEvidencia(id),vista.asignacionActual()==null?"SIN_ASIGNACIONES":"CON_ASIGNACION",asignar && j.vigente(),
            "Mismo periodo y versión de ruta; sin copia automática de recursos. Jornadas ejecutadas o fiscalizadas bloqueadas hasta aprobar su procedimiento institucional.");
    }
    private boolean autorizado(Runnable accion) { try { accion.run();return true; } catch(org.springframework.security.access.AccessDeniedException e) { return false; } }
    @Transactional
    public Plan reprogramar(UsuarioAutenticado actor,UUID id,Solicitud e) {
        UUID concesion=permisos.exigirJornada(actor,id,true);
        if(e==null || e.solicitudId()==null || e.inicioProgramado()==null || e.motivoReprogramacion()==null || e.motivoReprogramacion().isBlank() || e.motivoReprogramacion().length()>10000)
            throw new IllegalArgumentException("Solicitud, inicio y motivo son obligatorios (máximo 10000 caracteres).");
        if(!e.inicioProgramado().equals(e.inicioProgramado().truncatedTo(ChronoUnit.MICROS)) || (e.finProgramado()!=null && !e.finProgramado().equals(e.finProgramado().truncatedTo(ChronoUnit.MICROS))))
            throw new IllegalArgumentException("Precisión temporal máxima de microsegundos.");
        var anterior=repositorio.buscar(id,true).orElseThrow(()->new ReferenciaPlanificacionNoEncontrada("Jornada no encontrada."));
        String huella=PoliticaEvidenciasCampo.huella(json.writeValueAsBytes(Map.of("antecedente",id,"solicitud",e)));
        var previa=historial.solicitud(e.solicitudId());
        if(previa.isPresent()) {
            var p=previa.get();
            if(!p.usuarioId().equals(actor.usuarioId()) || !p.antecedenteId().equals(id) || !p.huella().equals(huella)) throw new ConflictoPlanificacion("Solicitud ya usada por otro actor o con otro contenido.");
            return consultar(actor,p.sucesoraId());
        }
        if(historial.tieneEvidencia(id)) throw new ConflictoPlanificacion("Reprogramación de jornada ejecutada o fiscalizada no habilitada: procedimiento institucional pendiente. No se traslada su evidencia.");
        permisos.exigirFechaSucesora(concesion,e.inicioProgramado());
        var nueva=jornadas.reprogramar(id,new GestionarJornadas.Programacion(anterior.periodoServicioId(),anterior.rutaVersionId(),e.inicioProgramado(),e.finProgramado()),e.motivoReprogramacion());
        historial.registrar(e.solicitudId(),id,nueva.jornada().id(),actor.usuarioId(),concesion,huella);
        return consultar(actor,nueva.jornada().id());
    }
}
