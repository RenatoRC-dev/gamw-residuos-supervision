package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionMunicipal;
import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.dominio.RepositorioFiscalizacion;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioContextoTrayectoria;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.aplicacion.ConsultarSeguimientoGps;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.aplicacion.ConsultarTrayectoriasAutorizadas;
import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.RepositorioRuta;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@Service
@Transactional(readOnly=true)
public class GestionarFiscalizacion {
    private final RepositorioFiscalizacion repo;
    private final AutorizacionMunicipal permisos;
    private final PoliticaEvidenciasCampo politica;
    private final ObjectMapper json;
    private final RepositorioContextoTrayectoria jornadas;
    private final RepositorioRuta rutas;
    private final ConsultarSeguimientoGps gps;
    public GestionarFiscalizacion(RepositorioFiscalizacion repo,AutorizacionMunicipal permisos,PoliticaEvidenciasCampo politica,
            ObjectMapper json,RepositorioContextoTrayectoria jornadas,RepositorioRuta rutas,ConsultarSeguimientoGps gps) {
        this.repo=repo; this.permisos=permisos; this.politica=politica; this.json=json; this.jornadas=jornadas; this.rutas=rutas; this.gps=gps;
    }
    public record Adjunto(String origen,String descripcion,Instant capturadaEn) { }
    public record InspeccionEntrada(UUID solicitudId,Instant realizadaEn,String lugarDescripcion,Double latitud,Double longitud,
            String observaciones,List<Adjunto> adjuntos) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo de inspección no permitido; identidad determinada por servidor."); }
    }
    public record DecisionEntrada(UUID solicitudId,UUID resultadoId,String observaciones,List<UUID> inspeccionesReferenciadas) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo de fiscalización no permitido."); }
    }
    public record Expediente(JsonNode contexto,List<JsonNode> inspecciones,JsonNode fiscalizacion,
            List<RepositorioFiscalizacion.Categoria> resultadosHabilitados,PoliticaEvidenciasCampo.Vista politicaEvidencias,String limitaciones,
            boolean puedeRegistrarInspeccion,boolean puedeEmitirResultado) { }
    public record ListaJornadas(List<RepositorioContextoTrayectoria.Resumen> jornadas,Integer siguiente) { }
    public ListaJornadas listar(UsuarioAutenticado a,int desde) {
        permisos.exigirConsultaFiscalizacion(a);
        if(desde<0 || desde>10000000) throw error(HttpStatus.BAD_REQUEST,"Página inválida.");
        var datos=jornadas.listar(51,desde);
        return new ListaJornadas(datos.stream().limit(50).toList(),datos.size()>50?desde+50:null);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Expediente expediente(UsuarioAutenticado a,UUID jornada) {
        permisos.exigirConsultaFiscalizacion(a);
        String contexto=repo.antecedentes(jornada,Instant.now(),false).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Jornada no encontrada."));
        return new Expediente(json.readTree(contexto),repo.inspecciones(jornada).stream().map(json::readTree).toList(),
            repo.fiscalizacion(jornada).map(json::readTree).orElse(null),repo.categorias(),politica.vista(),
            "Incidencias, reclamos y acciones correctivas aún no integrados; ausencia de GPS no es evidencia de incumplimiento. Correcciones y anexos posteriores no habilitados.",
            autorizado(()->permisos.exigirRegistroInspeccion(a)),autorizado(()->permisos.exigirEmisionFiscalizacion(a)));
    }
    private boolean autorizado(Runnable comprobacion) {
        try { comprobacion.run(); return true; } catch(org.springframework.security.access.AccessDeniedException error) { return false; }
    }
    public ConsultarTrayectoriasAutorizadas.Detalle gps(UsuarioAutenticado a,UUID jornada,int desplazamiento,Instant corte) {
        permisos.exigirConsultaFiscalizacion(a);
        if(desplazamiento<0 || desplazamiento>10000000) throw error(HttpStatus.BAD_REQUEST,"Página inválida.");
        if(corte==null) corte=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(corte.isAfter(Instant.now())) throw error(HttpStatus.BAD_REQUEST,"Corte de evidencia futuro.");
        var contexto=jornadas.obtener(jornada).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Jornada no encontrada."));
        var version=rutas.versionPorId(contexto.rutaVersionId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Ruta histórica no disponible."));
        return new ConsultarTrayectoriasAutorizadas.Detalle(contexto,new ConsultarTrayectoriasAutorizadas.LineaReferencia("LineString",version.geometria().coordenadas()),gps.consultarPagina(jornada,500,desplazamiento,corte));
    }
    private String texto(String s,int max,boolean requerido) {
        if(s==null) s="";
        if(s.length()>max || (requerido && s.isBlank())) throw error(HttpStatus.BAD_REQUEST,"Texto obligatorio o longitud fuera de límites.");
        return s;
    }
    private void bloquear(UUID jornada) { if(!repo.bloquearJornada(jornada)) throw error(HttpStatus.NOT_FOUND,"Jornada no encontrada."); }
    private void reintento(RepositorioFiscalizacion.Registro r,UUID actor,String huella) {
        if(!r.usuarioId().equals(actor) || !r.huella().equals(huella)) throw error(HttpStatus.CONFLICT,"Solicitud ya utilizada con otro actor o contenido; no se sobrescribe.");
    }
    @Transactional public JsonNode inspeccionar(UsuarioAutenticado a,UUID jornada,InspeccionEntrada e,List<MultipartFile> archivos) {
        permisos.exigirRegistroInspeccion(a);
        if(e==null || e.solicitudId()==null || e.realizadaEn()==null) throw error(HttpStatus.BAD_REQUEST,"Solicitud y fecha de inspección requeridas.");
        if(!e.realizadaEn().equals(e.realizadaEn().truncatedTo(java.time.temporal.ChronoUnit.MICROS))) throw error(HttpStatus.BAD_REQUEST,"Fecha de inspección con precisión máxima de microsegundos.");
        texto(e.observaciones(),20000,true); texto(e.lugarDescripcion(),2000,false);
        if((e.latitud()==null)!=(e.longitud()==null) || (e.latitud()!=null && (!Double.isFinite(e.latitud()) || !Double.isFinite(e.longitud()) || Math.abs(e.latitud())>90 || Math.abs(e.longitud())>180)))
            throw error(HttpStatus.BAD_REQUEST,"Coordenadas opcionales válidas [longitud,latitud] requeridas en conjunto.");
        var metadatos=e.adjuntos()==null?List.<Adjunto>of():e.adjuntos();
        if(archivos.size()>3 || metadatos.size()!=archivos.size()) throw error(HttpStatus.BAD_REQUEST,"Máximo tres archivos con sus metadatos de procedencia.");
        var validos=new ArrayList<PoliticaEvidenciasCampo.Validado>();
        for(int i=0;i<archivos.size();i++) {
            var m=metadatos.get(i); if(m==null) throw error(HttpStatus.BAD_REQUEST,"Metadatos requeridos.");
            if(m.capturadaEn()!=null && !m.capturadaEn().equals(m.capturadaEn().truncatedTo(java.time.temporal.ChronoUnit.MICROS))) throw error(HttpStatus.BAD_REQUEST,"Fecha de captura con precisión máxima de microsegundos.");
            texto(m.origen(),2000,true); texto(m.descripcion(),4000,true); validos.add(politica.validar(archivos.get(i)));
        }
        String huella=PoliticaEvidenciasCampo.huella(json.writeValueAsBytes(Map.of("jornada",jornada,"datos",e,"archivos",validos.stream().map(v->List.of(v.nombre(),v.mime(),v.sha256())).toList())));
        bloquear(jornada);
        var previa=repo.inspeccionPorSolicitud(e.solicitudId());
        if(previa.isPresent()) { reintento(previa.get(),a.usuarioId(),huella); return json.readTree(repo.inspeccion(previa.get().id()).orElseThrow()); }
        UUID id=UUID.randomUUID();
        repo.insertarInspeccion(id,e.solicitudId(),jornada,a.usuarioId(),e.realizadaEn(),e.lugarDescripcion(),e.latitud(),e.longitud(),e.observaciones(),huella);
        for(int i=0;i<validos.size();i++) {
            var v=validos.get(i); var m=metadatos.get(i);
            repo.insertarArchivo(new RepositorioFiscalizacion.Archivo(UUID.randomUUID(),id,a.usuarioId(),m.capturadaEn(),v.nombre(),v.mime(),m.origen(),m.descripcion(),politica.vista().referenciaPolitica(),v.sha256(),v.bytes()));
        }
        return json.readTree(repo.inspeccion(id).orElseThrow());
    }
    @Transactional public JsonNode emitir(UsuarioAutenticado a,UUID jornada,DecisionEntrada e) {
        permisos.exigirEmisionFiscalizacion(a); permisos.exigirConsultaFiscalizacion(a);
        if(e==null || e.solicitudId()==null || e.resultadoId()==null || e.inspeccionesReferenciadas()==null
            || e.inspeccionesReferenciadas().size()>1000 || e.inspeccionesReferenciadas().stream().anyMatch(Objects::isNull)
            || new HashSet<>(e.inspeccionesReferenciadas()).size()!=e.inspeccionesReferenciadas().size()) throw error(HttpStatus.BAD_REQUEST,"Solicitud, resultado e inspecciones válidas sin duplicados requeridos.");
        String obs=texto(e.observaciones(),20000,false);
        String huella=PoliticaEvidenciasCampo.huella(json.writeValueAsBytes(Map.of("jornada",jornada,"datos",e)));
        bloquear(jornada);
        var previa=repo.fiscalizacionPorSolicitud(e.solicitudId());
        if(previa.isPresent()) { reintento(previa.get(),a.usuarioId(),huella); return json.readTree(repo.fiscalizacion(jornada).orElseThrow()); }
        if(repo.fiscalizacion(jornada).isPresent()) throw error(HttpStatus.CONFLICT,"Existe una determinación; rectificación no habilitada.");
        var categoria=repo.categoriaBloqueada(e.resultadoId()).orElseThrow(()->error(HttpStatus.CONFLICT,"Resultado no habilitado por el catálogo; no se inventan categorías."));
        if(categoria.observacionesObligatorias()) texto(obs,20000,true);
        for(UUID i:e.inspeccionesReferenciadas()) if(!repo.inspeccionPertenece(i,jornada)) throw error(HttpStatus.BAD_REQUEST,"Inspección ajena o inexistente.");
        Instant corte=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String antecedentes=repo.antecedentes(jornada,corte,true).orElseThrow();
        UUID id=UUID.randomUUID();
        repo.insertarFiscalizacion(id,e.solicitudId(),jornada,a.usuarioId(),categoria.id(),obs,corte,antecedentes,huella);
        for(UUID i:e.inspeccionesReferenciadas()) repo.vincularInspeccion(id,jornada,i);
        return json.readTree(repo.fiscalizacion(jornada).orElseThrow());
    }
    public RepositorioFiscalizacion.Archivo archivo(UsuarioAutenticado a,UUID jornada,UUID id) {
        permisos.exigirConsultaFiscalizacion(a);
        return repo.archivo(jornada,id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Evidencia no disponible."));
    }
    private static ResponseStatusException error(HttpStatus estado,String detalle) { return new ResponseStatusException(estado,detalle); }
}
