package bo.gob.warnes.gamw.residuos.modules.correctivas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.correctivas.dominio.RepositorioActuaciones;
import bo.gob.warnes.gamw.residuos.modules.correctivas.dominio.RepositorioActuaciones.*;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionMunicipal;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion.RegistrarEventosEjecucion;
import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.dominio.RepositorioFiscalizacion;
import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.aplicacion.PoliticaEvidenciasCampo;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioContextoTrayectoria;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@Service
@Transactional(readOnly=true)
public class GestionarActuaciones {
    private final RepositorioActuaciones repo;
    private final RepositorioFiscalizacion fiscal;
    private final RepositorioContextoTrayectoria jornadas;
    private final AutorizacionMunicipal permisos;
    private final RegistrarEventosEjecucion ejecucion;
    private final ObjectMapper json;
    public GestionarActuaciones(RepositorioActuaciones repo,RepositorioFiscalizacion fiscal,RepositorioContextoTrayectoria jornadas,
            AutorizacionMunicipal permisos,RegistrarEventosEjecucion ejecucion,ObjectMapper json) {
        this.repo=repo;this.fiscal=fiscal;this.jornadas=jornadas;this.permisos=permisos;this.ejecucion=ejecucion;this.json=json;
    }
    public record NuevaIncidencia(UUID solicitudId,UUID asignacionJornadaId,Instant ocurridaEn,String descripcion,String evidenciaDeclarada,String lugarDescripcion) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido; el servidor determina al registrante."); }
    }
    public record NuevaAccion(UUID solicitudId,UUID incidenciaId,UUID inspeccionId,UUID fiscalizacionId,String descripcion) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo de acción no permitido."); }
    }
    public record NuevaVerificacion(UUID solicitudId,UUID respuestaId,UUID resultadoId,String observaciones,UUID inspeccionId) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo de verificación no permitido."); }
    }
    public record Historia(Accion accion,List<Respuesta> respuestas,List<Verificacion> verificaciones) { }
    public record Expediente(JsonNode contexto,List<Incidencia> incidencias,List<JsonNode> inspecciones,JsonNode fiscalizacion,
        List<Historia> acciones,List<Categoria> resultados,boolean puedeSolicitar,boolean puedeVerificar,String limitaciones) { }
    public record ListaJornadas(List<RepositorioContextoTrayectoria.Resumen> jornadas,Integer siguiente) { }
    private ResponseStatusException error(HttpStatus estado,String mensaje) { return new ResponseStatusException(estado,mensaje); }
    private void requerido(Object valor) { if(valor==null) throw error(HttpStatus.BAD_REQUEST,"Falta referencia obligatoria."); }
    private String texto(String texto,int max,boolean obligatorio) {
        String s=texto==null?"":texto;
        if(s.length()>max || (obligatorio && s.isBlank())) throw error(HttpStatus.BAD_REQUEST,"Texto obligatorio o demasiado extenso.");
        return s;
    }
    private void bloquear(UUID jornada) { if(!repo.bloqueo(jornada)) throw error(HttpStatus.NOT_FOUND,"Jornada no encontrada."); }
    private String huella(UUID contexto,Object datos) { return PoliticaEvidenciasCampo.huella(json.writeValueAsBytes(Map.of("contexto",contexto,"datos",datos))); }
    private Optional<UUID> reintento(Registro tipo,UUID solicitud,UsuarioAutenticado actor,String huella) {
        return repo.solicitud(tipo,solicitud).map(e->{
            if(!e.usuarioId().equals(actor.usuarioId()) || !e.huella().equals(huella)) throw error(HttpStatus.CONFLICT,"Solicitud ya usada con otro actor o contenido.");
            return e.id();
        });
    }
    public List<Incidencia> propias(UsuarioAutenticado actor,UUID jornada,UUID asignacion) {
        ejecucion.autorizarAsignacionHistorica(jornada,asignacion,actor);
        return repo.incidencias(jornada).stream().filter(e->e.asignacionJornadaId().equals(asignacion) && e.usuarioId().equals(actor.usuarioId())).toList();
    }
    @Transactional
    public Incidencia registrarIncidencia(UsuarioAutenticado actor,UUID jornada,NuevaIncidencia e) {
        requerido(e);requerido(e.solicitudId());requerido(e.asignacionJornadaId());requerido(e.ocurridaEn());
        ejecucion.autorizarCaptura(jornada,e.asignacionJornadaId(),e.ocurridaEn(),actor);
        String descripcion=texto(e.descripcion(),20000,true),evidencia=texto(e.evidenciaDeclarada(),10000,false),lugar=texto(e.lugarDescripcion(),2000,false);
        if(!e.ocurridaEn().equals(e.ocurridaEn().truncatedTo(ChronoUnit.MICROS)) || e.ocurridaEn().isAfter(Instant.now()))
            throw error(HttpStatus.BAD_REQUEST,"Fecha original no futura, con precisión máxima de microsegundos.");
        String huella=huella(jornada,e);bloquear(jornada);
        var anterior=reintento(Registro.INCIDENCIA,e.solicitudId(),actor,huella);
        if(anterior.isPresent()) return repo.incidencia(anterior.get()).orElseThrow();
        // Registro directo durante ejecución. Registro tardío/offline pendiente de decisión institucional.
        if(!repo.enEjecucion(jornada,e.ocurridaEn())) throw error(HttpStatus.CONFLICT,"Se requiere INICIO sincronizado y jornada sin FIN; registro tardío/offline no habilitado.");
        var nuevo=new Incidencia(UUID.randomUUID(),e.solicitudId(),jornada,e.asignacionJornadaId(),actor.usuarioId(),e.ocurridaEn(),null,descripcion,evidencia,lugar);
        repo.insertar(nuevo,huella);return repo.incidencia(nuevo.id()).orElseThrow();
    }
    public ListaJornadas listar(UsuarioAutenticado actor,int desde) {
        permisos.exigirConsultaCorrectiva(actor);
        if(desde<0 || desde>10000000) throw error(HttpStatus.BAD_REQUEST,"Página inválida.");
        var lista=jornadas.listar(51,desde);return new ListaJornadas(lista.stream().limit(50).toList(),lista.size()>50?desde+50:null);
    }
    private boolean autorizado(Runnable accion) {
        try { accion.run(); return true; } catch(org.springframework.security.access.AccessDeniedException e) { return false; }
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Expediente expediente(UsuarioAutenticado actor,UUID jornada) {
        permisos.exigirConsultaCorrectiva(actor);
        var contexto=fiscal.antecedentes(jornada,Instant.now(),false).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Jornada no encontrada."));
        return new Expediente(json.readTree(contexto),repo.incidencias(jornada),fiscal.inspecciones(jornada).stream().map(json::readTree).toList(),
            fiscal.fiscalizacion(jornada).map(json::readTree).orElse(null),repo.acciones(jornada).stream().map(a->new Historia(a,repo.respuestas(a.id()),repo.verificaciones(a.id()))).toList(),
            repo.categorias(),autorizado(()->permisos.exigirSolicitudCorrectiva(actor)),autorizado(()->permisos.exigirVerificacionCorrectiva(actor)),
            "A4: consulta/respuesta solo con concesiones explícitas por periodo en /api/empresa/correctivas. Sin plazos, estados, cierres ni sanciones automáticos. Adjuntos nuevos y rectificaciones no habilitados; evidencia documental mediante referencias históricas.");
    }
    @Transactional
    public Historia solicitar(UsuarioAutenticado actor,UUID jornada,NuevaAccion e) {
        permisos.exigirConsultaCorrectiva(actor);permisos.exigirSolicitudCorrectiva(actor);
        requerido(e);requerido(e.solicitudId());String descripcion=texto(e.descripcion(),20000,true);
        if((e.incidenciaId()==null?0:1)+(e.inspeccionId()==null?0:1)+(e.fiscalizacionId()==null?0:1)!=1)
            throw error(HttpStatus.BAD_REQUEST,"Seleccione exactamente un antecedente existente.");
        String huella=huella(jornada,e);bloquear(jornada);
        var anterior=reintento(Registro.ACCION,e.solicitudId(),actor,huella);
        if(anterior.isPresent()) { var a=repo.accion(anterior.get()).orElseThrow();return new Historia(a,repo.respuestas(a.id()),repo.verificaciones(a.id())); }
        boolean valido=e.incidenciaId()!=null?repo.incidencia(e.incidenciaId()).map(i->i.jornadaId().equals(jornada)).orElse(false):
            e.inspeccionId()!=null?fiscal.inspeccionPertenece(e.inspeccionId(),jornada):
            fiscal.fiscalizacion(jornada).map(json::readTree).map(f->e.fiscalizacionId().toString().equals(f.path("id").asText())).orElse(false);
        if(!valido) throw error(HttpStatus.BAD_REQUEST,"El antecedente debe existir y pertenecer a esta jornada.");
        String contexto=fiscal.antecedentes(jornada,Instant.now(),true).orElseThrow();
        var a=new Accion(UUID.randomUUID(),e.solicitudId(),jornada,actor.usuarioId(),e.incidenciaId(),e.inspeccionId(),e.fiscalizacionId(),descripcion,null,contexto);
        repo.insertar(a,huella);return new Historia(repo.accion(a.id()).orElseThrow(),List.of(),List.of());
    }
    @Transactional
    public List<Verificacion> verificar(UsuarioAutenticado actor,UUID accionId,NuevaVerificacion e) {
        permisos.exigirConsultaCorrectiva(actor);permisos.exigirVerificacionCorrectiva(actor);
        requerido(e);requerido(e.solicitudId());requerido(e.respuestaId());requerido(e.resultadoId());
        var accion=repo.accion(accionId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Acción no encontrada."));
        bloquear(accion.jornadaId());String huella=huella(accionId,e);
        if(reintento(Registro.VERIFICACION,e.solicitudId(),actor,huella).isPresent()) return repo.verificaciones(accionId);
        var categoria=repo.categoria(e.resultadoId()).orElseThrow(()->error(HttpStatus.CONFLICT,"Resultado no habilitado por catálogo aprobado."));
        String observaciones=texto(e.observaciones(),20000,categoria.observacionesObligatorias());
        if(repo.respuestas(accionId).stream().noneMatch(r->r.id().equals(e.respuestaId()))) throw error(HttpStatus.CONFLICT,"Debe existir atención empresarial de esta acción para verificarla.");
        if(repo.verificaciones(accionId).stream().anyMatch(v->v.respuestaId().equals(e.respuestaId()))) throw error(HttpStatus.CONFLICT,"Atención ya evaluada; rectificación pendiente de política.");
        if(e.inspeccionId()!=null && !fiscal.inspeccionPertenece(e.inspeccionId(),accion.jornadaId())) throw error(HttpStatus.BAD_REQUEST,"Inspección ajena a la jornada.");
        repo.verificar(UUID.randomUUID(),e.solicitudId(),accionId,accion.jornadaId(),e.respuestaId(),actor.usuarioId(),e.resultadoId(),observaciones,e.inspeccionId(),huella);
        return repo.verificaciones(accionId); // Sin cierre ni transición implícita.
    }
}
