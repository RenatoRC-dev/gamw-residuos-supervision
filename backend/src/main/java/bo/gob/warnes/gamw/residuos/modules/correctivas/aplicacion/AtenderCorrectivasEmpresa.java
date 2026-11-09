package bo.gob.warnes.gamw.residuos.modules.correctivas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionOperador;
import bo.gob.warnes.gamw.residuos.modules.correctivas.dominio.RepositorioActuaciones;
import bo.gob.warnes.gamw.residuos.modules.correctivas.dominio.RepositorioActuaciones.*;
import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.aplicacion.PoliticaEvidenciasCampo;
import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.dominio.RepositorioFiscalizacion;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@Service
@Transactional(readOnly=true)
public class AtenderCorrectivasEmpresa {
    private final AutorizacionOperador permisos;
    private final RepositorioActuaciones repo;
    private final RepositorioFiscalizacion fiscal;
    private final ObjectMapper json;
    public AtenderCorrectivasEmpresa(AutorizacionOperador permisos,RepositorioActuaciones repo,RepositorioFiscalizacion fiscal,ObjectMapper json) {
        this.permisos=permisos;this.repo=repo;this.fiscal=fiscal;this.json=json;
    }
    public record NuevaRespuesta(UUID solicitudId,Instant comunicadaEn,String observaciones,String evidenciaDeclarada,List<UUID> evidenciasReferenciadas) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no permitido: identidad, empresa, periodo y autorización proceden del servidor."); }
    }
    public record Lista(List<ResumenEmpresa> acciones,Integer siguiente) { }
    public record SolicitudEmpresa(UUID id,UUID jornadaId,UUID usuarioId,String descripcion,Instant creadaEn) { }
    public record Antecedente(String tipo,UUID id,String descripcion,String fecha) { }
    public record Detalle(SolicitudEmpresa solicitud,JsonNode contexto,Antecedente antecedente,List<Respuesta> respuestas,
            List<Verificacion> verificaciones,List<EvidenciaCompartida> evidenciasDisponibles,boolean puedeResponder) { }
    public Lista listar(UsuarioAutenticado actor,int desde) {
        permisos.exigirListado(actor);
        if(desde<0 || desde>10000000) throw error(HttpStatus.BAD_REQUEST,"Página inválida.");
        var filas=repo.accionesEmpresa(actor.usuarioId(),51,desde);
        return new Lista(filas.stream().limit(50).toList(),filas.size()>50?desde+50:null);
    }
    private ResponseStatusException error(HttpStatus estado,String detalle) { return new ResponseStatusException(estado,detalle); }
    private Accion accion(UUID id) { return repo.accion(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Actuación no disponible.")); }
    private JsonNode contexto(Accion accion) {
        var original=json.readTree(accion.antecedentes());var seguro=json.createObjectNode();
        for(String campo:List.of("periodo","empresa","ruta","rutaVersion")) seguro.set(campo,original.path(campo));
        var jornada=json.createObjectNode();
        for(String campo:List.of("id","inicioProgramado","finProgramado","rutaVersionId")) jornada.set(campo,original.path("jornada").path(campo));
        seguro.set("jornada",jornada);return seguro; // Sin GPS, asignaciones personales ni expediente municipal completo.
    }
    private Antecedente antecedente(Accion accion) {
        if(accion.incidenciaId()!=null) {
            var i=repo.incidencia(accion.incidenciaId()).orElseThrow();
            return new Antecedente("INCIDENCIA",i.id(),i.descripcion(),i.ocurridaEn().toString());
        }
        if(accion.inspeccionId()!=null) {
            var i=json.readTree(fiscal.inspeccion(accion.inspeccionId()).orElseThrow());
            return new Antecedente("INSPECCION",accion.inspeccionId(),i.path("observaciones").asText(),i.path("realizadaEn").asText());
        }
        var f=json.readTree(fiscal.fiscalizacion(accion.jornadaId()).orElseThrow());
        return new Antecedente("FISCALIZACION",accion.fiscalizacionId(),f.path("resultado").path("nombre").asText()+": "+f.path("observaciones").asText(),f.path("registradaEn").asText());
    }
    public Detalle consultar(UsuarioAutenticado actor,UUID id) {
        permisos.exigirConsulta(actor,id);var a=accion(id);
        boolean responde;
        try { permisos.exigirCapacidadRespuesta(actor,id);responde=true; } catch(AccessDeniedException e) { responde=false; }
        return new Detalle(new SolicitudEmpresa(a.id(),a.jornadaId(),a.usuarioId(),a.descripcion(),a.creadaEn()),contexto(a),antecedente(a),
            repo.respuestas(id),repo.verificaciones(id),repo.soporteEmpresa(id),responde);
    }
    public RepositorioFiscalizacion.Archivo archivo(UsuarioAutenticado actor,UUID accionId,UUID evidenciaId) {
        permisos.exigirConsulta(actor,accionId);var a=accion(accionId);
        if(!repo.evidenciasDisponibles(accionId).contains(evidenciaId)) throw new AccessDeniedException("Evidencia no compartida en esta actuación.");
        return fiscal.archivo(a.jornadaId(),evidenciaId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Archivo no disponible."));
    }
    @Transactional
    public Respuesta responder(UsuarioAutenticado actor,UUID id,NuevaRespuesta e) {
        // Ambas concesiones se bloquean hasta finalizar: una revocación no se ignora en una solicitud posterior.
        UUID concesion=permisos.exigirRespuesta(actor,id);var a=accion(id);
        if(!repo.bloqueo(a.jornadaId())) throw error(HttpStatus.NOT_FOUND,"Jornada no disponible.");
        if(e==null || e.solicitudId()==null || e.comunicadaEn()==null || e.observaciones()==null || e.observaciones().isBlank()
            || e.observaciones().length()>20000 || e.evidenciasReferenciadas()==null || e.evidenciasReferenciadas().size()>50
            || e.evidenciasReferenciadas().stream().anyMatch(Objects::isNull) || new HashSet<>(e.evidenciasReferenciadas()).size()!=e.evidenciasReferenciadas().size())
            throw error(HttpStatus.BAD_REQUEST,"Solicitud, fecha, observaciones y referencias válidas sin duplicados requeridas.");
        if(e.comunicadaEn().isAfter(Instant.now()) || !e.comunicadaEn().equals(e.comunicadaEn().truncatedTo(ChronoUnit.MICROS)))
            throw error(HttpStatus.BAD_REQUEST,"Fecha original no futura y de precisión máxima de microsegundos.");
        String declarada=e.evidenciaDeclarada()==null?"":e.evidenciaDeclarada();
        if(declarada.length()>10000) throw error(HttpStatus.BAD_REQUEST,"Soporte declarado demasiado extenso.");
        // El orden de selección no cambia el significado de las referencias históricas.
        var referencias=e.evidenciasReferenciadas().stream().sorted().toList();
        var normalizada=new NuevaRespuesta(e.solicitudId(),e.comunicadaEn(),e.observaciones(),declarada,referencias);
        String huella=PoliticaEvidenciasCampo.huella(json.writeValueAsBytes(Map.of("accion",id,"respuesta",normalizada)));
        var previa=repo.solicitud(Registro.RESPUESTA,e.solicitudId());
        if(previa.isPresent()) {
            if(!previa.get().usuarioId().equals(actor.usuarioId()) || !previa.get().huella().equals(huella))
                throw error(HttpStatus.CONFLICT,"Solicitud ya registrada por otro actor o con otro contenido.");
            return repo.respuestas(id).stream().filter(r->r.id().equals(previa.get().id())).findFirst().orElseThrow();
        }
        if(!new HashSet<>(repo.evidenciasDisponibles(id)).containsAll(referencias)) throw new AccessDeniedException("Referencia no compartida por el antecedente de esta acción.");
        UUID respuesta=UUID.randomUUID();
        repo.responder(respuesta,e.solicitudId(),id,actor.usuarioId(),concesion,e.comunicadaEn(),e.observaciones(),declarada,huella,referencias);
        return repo.respuestas(id).stream().filter(r->r.id().equals(respuesta)).findFirst().orElseThrow();
    }
}
