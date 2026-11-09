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

/** CU-11/CU-12: autorización contextual sobre las reglas y persistencia ya existentes. */
@Service
@Transactional(readOnly=true)
public class PlanificarJornadasOperador {
    private final AutorizacionOperador permisos;
    private final RepositorioReprogramacionesOperador auditoria;
    private final RepositorioJornadas repo;
    private final ReferenciasJornadas referencias;
    private final GestionarJornadas jornadas;
    private final ReprogramarJornadasOperador lectura;
    private final ObjectMapper json;
    public PlanificarJornadasOperador(AutorizacionOperador permisos,RepositorioReprogramacionesOperador auditoria,RepositorioJornadas repo,
        ReferenciasJornadas referencias,GestionarJornadas jornadas,ReprogramarJornadasOperador lectura,ObjectMapper json) {
        this.permisos=permisos;this.auditoria=auditoria;this.repo=repo;this.referencias=referencias;this.jornadas=jornadas;this.lectura=lectura;this.json=json;
    }
    public record Programar(UUID solicitudId,UUID rutaVersionId,Instant inicioProgramado,Instant finProgramado) {
        @JsonAnySetter public void desconocido(String c,Object v) { throw new IllegalArgumentException("Empresa/periodo/actor se determinan en servidor y por concesión, no en el cuerpo."); }
    }
    public record Asignar(UUID solicitudId,UUID vinculoVehiculoEmpresaId,UUID vinculoPersonaEmpresaId,String motivoCambio) {
        @JsonAnySetter public void desconocido(String c,Object v) { throw new IllegalArgumentException("Campo no autorizado en asignación."); }
    }
    public List<RepositorioReprogramacionesOperador.Periodo> periodos(UsuarioAutenticado a) { permisos.exigirListadoJornadas(a);return auditoria.periodos(a.usuarioId()); }
    public List<RepositorioReprogramacionesOperador.VersionOpcion> versiones(UsuarioAutenticado a,UUID periodo) {
        permisos.exigirProgramacionPeriodo(a,periodo,false);return auditoria.versionesProgramables();
    }
    public RepositorioReprogramacionesOperador.Recursos recursos(UsuarioAutenticado a,UUID jornada) {
        permisos.exigirJornadaOperativa(a,jornada,"JORNADA_ASIGNAR",false);
        var j=repo.buscar(jornada,false).orElseThrow(()->new ReferenciaPlanificacionNoEncontrada("Jornada no encontrada."));
        return auditoria.recursos(jornada,referencias.fechaServicio(j.inicioProgramado()));
    }
    private String huella(UUID contexto,Object e,String tipo) { return PoliticaEvidenciasCampo.huella(json.writeValueAsBytes(Map.of("contexto",contexto,"datos",e,"tipo",tipo))); }
    private Optional<UUID> previa(UUID solicitud,UUID actor,String huella,String tipo) {
        return auditoria.operacion(solicitud).map(p->{
            if(!p.usuarioId().equals(actor) || !p.huella().equals(huella) || !p.operacion().equals(tipo)) throw new ConflictoPlanificacion("Solicitud ya utilizada con otro actor, contenido u operación.");return p.jornadaId();
        });
    }
    @Transactional public ReprogramarJornadasOperador.Plan programar(UsuarioAutenticado a,UUID periodo,Programar e) {
        UUID concesion=permisos.exigirProgramacionPeriodo(a,periodo,true);
        if(e==null || e.solicitudId()==null || e.rutaVersionId()==null || e.inicioProgramado()==null) throw new IllegalArgumentException("Solicitud, versión e inicio obligatorios.");
        if(!e.inicioProgramado().equals(e.inicioProgramado().truncatedTo(ChronoUnit.MICROS)) || (e.finProgramado()!=null && !e.finProgramado().equals(e.finProgramado().truncatedTo(ChronoUnit.MICROS)))) throw new IllegalArgumentException("Precisión máxima de microsegundos.");
        if(!auditoria.bloquearPeriodo(periodo)) throw new ReferenciaPlanificacionNoEncontrada("Periodo no encontrado.");
        String h=huella(periodo,e,"PROGRAMAR");var anterior=previa(e.solicitudId(),a.usuarioId(),h,"PROGRAMAR");if(anterior.isPresent()) return lectura.consultar(a,anterior.get());
        permisos.exigirFechaSucesora(concesion,e.inicioProgramado());
        var nueva=jornadas.crear(new GestionarJornadas.Programacion(periodo,e.rutaVersionId(),e.inicioProgramado(),e.finProgramado()));
        auditoria.registrarOperacion(e.solicitudId(),nueva.jornada().id(),null,a.usuarioId(),concesion,"PROGRAMAR",h);return lectura.consultar(a,nueva.jornada().id());
    }
    @Transactional public ReprogramarJornadasOperador.Plan asignar(UsuarioAutenticado a,UUID jornada,Asignar e,boolean reasignar) {
        UUID concesion=permisos.exigirJornadaOperativa(a,jornada,"JORNADA_ASIGNAR",true);
        if(e==null || e.solicitudId()==null || e.vinculoVehiculoEmpresaId()==null || e.vinculoPersonaEmpresaId()==null || (e.motivoCambio()!=null && e.motivoCambio().length()>10000)) throw new IllegalArgumentException("Solicitud y vínculos obligatorios; motivo acotado.");
        repo.buscar(jornada,true).orElseThrow(()->new ReferenciaPlanificacionNoEncontrada("Jornada no encontrada."));
        String tipo=reasignar?"REASIGNAR":"ASIGNAR",h=huella(jornada,e,tipo);var anterior=previa(e.solicitudId(),a.usuarioId(),h,tipo);
        if(anterior.isPresent()) return lectura.consultar(a,anterior.get());
        var nueva=reasignar?jornadas.reasignar(jornada,e.vinculoVehiculoEmpresaId(),e.vinculoPersonaEmpresaId(),e.motivoCambio()):jornadas.asignar(jornada,e.vinculoVehiculoEmpresaId(),e.vinculoPersonaEmpresaId());
        auditoria.registrarOperacion(e.solicitudId(),jornada,nueva.asignacion().id(),a.usuarioId(),concesion,tipo,h);return lectura.consultar(a,jornada);
    }
}
