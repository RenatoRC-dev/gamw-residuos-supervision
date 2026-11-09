package bo.gob.warnes.gamw.residuos.modules.reclamos.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.reclamos.dominio.RepositorioReclamos;
import bo.gob.warnes.gamw.residuos.modules.reclamos.dominio.RepositorioReclamos.*;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionMunicipal;
import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.aplicacion.PoliticaEvidenciasCampo;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly=true)
public class GestionarReclamos {
    private final RepositorioReclamos repo;
    private final AutorizacionMunicipal permisos;
    private final PoliticaEvidenciasCampo archivos;
    private final ObjectMapper json;
    private final boolean validacionPuntos;
    private final String politicaPuntos;
    private final boolean anexosHabilitados;
    private final String politicaAnexos;
    public GestionarReclamos(RepositorioReclamos repo,AutorizacionMunicipal permisos,PoliticaEvidenciasCampo archivos,ObjectMapper json,
            @Value("${gamw.puntos-criticos.validacion-habilitada:false}") boolean validacionPuntos,
            @Value("${gamw.puntos-criticos.referencia-politica:}") String politicaPuntos,
            @Value("${gamw.reclamos.anexos-habilitados:false}") boolean anexosHabilitados,
            @Value("${gamw.reclamos.referencia-politica-anexos:}") String politicaAnexos) {
        if(validacionPuntos && politicaPuntos.isBlank()) throw new IllegalStateException("Validar puntos críticos requiere política explícita.");
        if(anexosHabilitados && politicaAnexos.isBlank()) throw new IllegalStateException("Anexos de reclamos requieren política explícita de conservación/acceso y datos personales.");
        this.repo=repo;this.permisos=permisos;this.archivos=archivos;this.json=json;this.validacionPuntos=validacionPuntos;this.politicaPuntos=politicaPuntos;this.anexosHabilitados=anexosHabilitados;this.politicaAnexos=politicaAnexos;
    }
    public record NuevoReclamo(UUID solicitudId,String canalDeclarado,Instant recibidoEn,String descripcion,String lugarDescripcion,String referenciasOrigen,Double longitud,Double latitud,UUID jornadaId) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Campo de origen no permitido; el autor procede del principal."); }
    }
    public record NuevaRevision(UUID solicitudId,UUID resultadoId,String observaciones) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Campo de revisión no permitido."); }
    }
    public record NuevaRelacion(Tipo tipo,UUID referenciaId,String fundamento) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Campo de relación no permitido."); }
    }
    public record NuevoPunto(UUID solicitudId,String denominacion,String descripcion,Double longitud,Double latitud,String sustentoMunicipal,UUID reclamoId,UUID inspeccionId) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("No se admite validación ni identidad desde el registro de origen."); }
    }
    public record NuevaObservacion(UUID solicitudId,Instant observadaEn,String descripcion,UUID reclamoId,UUID inspeccionId) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Campo de observación no permitido."); }
    }
    public record ConfirmarPunto(Boolean confirmaValidacionMunicipal,String fundamento) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Campo de validación no permitido."); }
    }
    public record Pagina<T>(List<T> registros,Integer siguiente) { }
    public record DetalleReclamo(Reclamo reclamo,List<Revision> revisiones,List<Relacion> relaciones,List<Anexo> anexos) { }
    public record DetallePunto(Punto punto,List<Observacion> observaciones,Validacion validacion) { }
    public record Opciones(boolean puedeConsultarReclamos,boolean puedeRegistrarReclamos,boolean puedeRevisarReclamos,boolean puedeConsultarPuntos,
        boolean puedeRegistrarPuntos,boolean puedeValidarPuntos,boolean politicaPuntosHabilitada,String referenciaPoliticaPuntos,
        List<Categoria> resultados,PoliticaEvidenciasCampo.Vista politicaAnexos,boolean anexosHabilitados,String referenciaPoliticaAnexos,String pendientes) { }
    private ResponseStatusException error(HttpStatus e,String m) { return new ResponseStatusException(e,m); }
    private void requerido(Object v) { if(v==null) throw error(HttpStatus.BAD_REQUEST,"Dato obligatorio ausente."); }
    private String texto(String s,int max,boolean requerido) { if(s==null)s="";if(s.length()>max || (requerido && s.isBlank())) throw error(HttpStatus.BAD_REQUEST,"Texto obligatorio o longitud inválida.");return s; }
    private void instante(Instant t) { requerido(t);if(t.isAfter(Instant.now()) || !t.equals(t.truncatedTo(ChronoUnit.MICROS))) throw error(HttpStatus.BAD_REQUEST,"Fecha declarada no futura y con precisión máxima de microsegundos."); }
    private void coordenadas(Double lon,Double lat,boolean obligatorias) {
        if((lon==null)!=(lat==null) || (obligatorias && lon==null) || (lon!=null && (!Double.isFinite(lon) || !Double.isFinite(lat) || Math.abs(lon)>180 || Math.abs(lat)>90))) throw error(HttpStatus.BAD_REQUEST,"Coordenadas válidas [longitud,latitud], en conjunto.");
    }
    private boolean puede(Runnable r) { try { r.run();return true; } catch(AccessDeniedException e) { return false; } }
    private <T> Pagina<T> pagina(List<T> datos,int desde) { return new Pagina<>(datos.stream().limit(50).toList(),datos.size()>50?desde+50:null); }
    private void pagina(int desde) { if(desde<0 || desde>10000000) throw error(HttpStatus.BAD_REQUEST,"Página inválida."); }
    private void bloquearReclamo(UUID id) { if(!repo.bloquearReclamo(id)) throw error(HttpStatus.NOT_FOUND,"Reclamo no encontrado."); }
    private void bloquearPunto(UUID id) { if(!repo.bloquearPunto(id)) throw error(HttpStatus.NOT_FOUND,"Punto no encontrado."); }
    private String huella(UUID padre,Object datos) { return PoliticaEvidenciasCampo.huella(json.writeValueAsBytes(Arrays.asList(padre,datos))); }
    private Optional<UUID> anterior(Registro tipo,UUID solicitud,UsuarioAutenticado a,String huella) {
        return repo.solicitud(tipo,solicitud).map(r->{if(!r.usuarioId().equals(a.usuarioId()) || !r.huella().equals(huella)) throw error(HttpStatus.CONFLICT,"Solicitud utilizada por otro actor o con contenido diferente.");return r.id();});
    }
    public Opciones opciones(UsuarioAutenticado a) {
        boolean r=puede(()->permisos.exigirConsultaReclamo(a)),p=puede(()->permisos.exigirConsultaPunto(a));
        if(!r && !p) throw new AccessDeniedException("Consulta municipal específica requerida.");
        return new Opciones(r,r && puede(()->permisos.exigirRegistroReclamo(a)),r && puede(()->permisos.exigirRevisionReclamo(a)),p,
            p && puede(()->permisos.exigirRegistroPunto(a)),p && puede(()->permisos.exigirValidacionPunto(a)),validacionPuntos,politicaPuntos,
            r?repo.categorias():List.of(),archivos.vista(),anexosHabilitados && archivos.vista().fotografiasHabilitadas(),politicaAnexos,"Catálogos finales, datos personales, duplicados entre canales, SLA, cierre/rectificación/retiro pendientes. Sin portal CU-25 ni acceso A4 directo; atención empresarial puede consultarse en correctivas vinculadas bajo su autorización independiente. Sin validación automática ni zonas rojas.");
    }
    public Pagina<Reclamo> listarReclamos(UsuarioAutenticado a,int desde) { permisos.exigirConsultaReclamo(a);pagina(desde);return pagina(repo.reclamos(51,desde),desde); }
    public DetalleReclamo reclamo(UsuarioAutenticado a,UUID id) {
        permisos.exigirConsultaReclamo(a);return new DetalleReclamo(repo.reclamo(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Reclamo no encontrado.")),repo.revisiones(id),repo.relaciones(id),repo.anexos(id));
    }
    @Transactional public DetalleReclamo registrar(UsuarioAutenticado a,NuevoReclamo e) {
        permisos.exigirConsultaReclamo(a);permisos.exigirRegistroReclamo(a);requerido(e);requerido(e.solicitudId());instante(e.recibidoEn());
        texto(e.canalDeclarado(),200,true);texto(e.descripcion(),20000,true);coordenadas(e.longitud(),e.latitud(),false);
        String lugar=texto(e.lugarDescripcion(),2000,false),origen=texto(e.referenciasOrigen(),10000,false);
        if(e.jornadaId()!=null && origen.isBlank()) throw error(HttpStatus.BAD_REQUEST,"Fundamente la relación con jornada en las referencias de origen.");
        if(e.jornadaId()!=null && !repo.existe(Tipo.JORNADA,e.jornadaId())) throw error(HttpStatus.BAD_REQUEST,"Jornada inexistente; no se inventa relación.");
        String h=huella(null,e);var previa=anterior(Registro.RECLAMO,e.solicitudId(),a,h);if(previa.isPresent()) return reclamo(a,previa.get());
        UUID id=UUID.randomUUID();repo.registrar(id,e.solicitudId(),h,e.canalDeclarado(),e.recibidoEn(),e.descripcion(),lugar,origen,e.longitud(),e.latitud(),e.jornadaId(),a.usuarioId());return reclamo(a,id);
    }
    @Transactional public DetalleReclamo revisar(UsuarioAutenticado a,UUID id,NuevaRevision e) {
        permisos.exigirConsultaReclamo(a);permisos.exigirRevisionReclamo(a);requerido(e);requerido(e.solicitudId());texto(e.observaciones(),20000,true);bloquearReclamo(id);
        String h=huella(id,e);if(anterior(Registro.REVISION,e.solicitudId(),a,h).isPresent()) return reclamo(a,id);
        if(e.resultadoId()!=null) {
            if(repo.tieneDecision(id)) throw error(HttpStatus.CONFLICT,"Ya existe valoración final; rectificación no habilitada.");
            repo.categoria(e.resultadoId()).orElseThrow(()->error(HttpStatus.CONFLICT,"Resultado no habilitado por catálogo aprobado."));
        }
        repo.revisar(UUID.randomUUID(),e.solicitudId(),h,id,a.usuarioId(),e.resultadoId(),e.observaciones());return reclamo(a,id);
    }
    @Transactional public DetalleReclamo relacionar(UsuarioAutenticado a,UUID id,NuevaRelacion e) {
        permisos.exigirConsultaReclamo(a);permisos.exigirRevisionReclamo(a);requerido(e);requerido(e.tipo());requerido(e.referenciaId());texto(e.fundamento(),10000,true);bloquearReclamo(id);
        if(!repo.existe(e.tipo(),e.referenciaId())) throw error(HttpStatus.BAD_REQUEST,"Antecedente inexistente.");
        var relaciones=repo.relaciones(id);
        var existente=relaciones.stream().filter(r->r.tipo()==e.tipo() && r.referenciaId().equals(e.referenciaId())).findFirst();
        if(existente.isPresent()) {
            var original=existente.get();
            if(original.fundamento().equals(e.fundamento()) && original.usuarioId().equals(a.usuarioId())) return reclamo(a,id);
            throw error(HttpStatus.CONFLICT,"El destino ya está vinculado con otra actuación original. No se reemplaza el fundamento/autor; justificaciones adicionales requieren procedimiento aprobado.");
        }
        var jornadas=new HashSet<UUID>();
        repo.reclamo(id).map(Reclamo::jornadaId).ifPresent(jornadas::add);
        for(var r:relaciones) repo.jornadaAntecedente(r.tipo(),r.referenciaId()).ifPresent(jornadas::add);
        UUID destino=repo.jornadaAntecedente(e.tipo(),e.referenciaId()).orElseThrow(()->error(HttpStatus.BAD_REQUEST,"No se pudo determinar jornada del antecedente."));
        if(jornadas.stream().anyMatch(j->!j.equals(destino))) throw error(HttpStatus.CONFLICT,"Enlace interjornada pendiente de política institucional explícita. No se presume prohibición definitiva ni se altera el historial ya registrado.");
        repo.relacionar(UUID.randomUUID(),id,e.tipo(),e.referenciaId(),e.fundamento(),a.usuarioId());return reclamo(a,id);
    }
    @Transactional public DetalleReclamo anexar(UsuarioAutenticado a,UUID id,String origen,String descripcion,Instant captura,MultipartFile archivo) {
        permisos.exigirConsultaReclamo(a);permisos.exigirRegistroReclamo(a);texto(origen,2000,true);texto(descripcion,4000,true);if(captura!=null && !captura.equals(captura.truncatedTo(ChronoUnit.MICROS))) throw error(HttpStatus.BAD_REQUEST,"Captura con precisión máxima de microsegundos.");
        if(!anexosHabilitados) throw error(HttpStatus.CONFLICT,"Política de anexos y datos personales de reclamos pendiente.");
        bloquearReclamo(id);var v=archivos.validar(archivo);
        if(repo.anexos(id).stream().anyMatch(x->x.sha256().equals(v.sha256()) && x.origen().equals(origen) && x.descripcion().equals(descripcion) && Objects.equals(x.capturadoEn(),captura))) return reclamo(a,id);
        repo.anexar(UUID.randomUUID(),id,a.usuarioId(),origen,descripcion,captura,politicaAnexos,v.nombre(),v.mime(),v.sha256(),v.bytes());return reclamo(a,id);
    }
    public Archivo archivo(UsuarioAutenticado a,UUID id,UUID anexo) { permisos.exigirConsultaReclamo(a);return repo.archivo(id,anexo).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Anexo no disponible.")); }
    public Pagina<Punto> listarPuntos(UsuarioAutenticado a,int desde) { permisos.exigirConsultaPunto(a);pagina(desde);return pagina(repo.puntos(51,desde),desde); }
    public DetallePunto punto(UsuarioAutenticado a,UUID id) { permisos.exigirConsultaPunto(a);return new DetallePunto(repo.punto(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Punto no encontrado.")),repo.observaciones(id),repo.validacion(id).orElse(null)); }
    private void fuentes(UsuarioAutenticado a,UUID reclamo,UUID inspeccion) {
        if(reclamo!=null) { permisos.exigirConsultaReclamo(a);if(repo.reclamo(reclamo).isEmpty()) throw error(HttpStatus.BAD_REQUEST,"Reclamo de sustento inexistente."); }
        if(inspeccion!=null) { permisos.exigirConsultaFiscalizacion(a);if(!repo.existe(Tipo.INSPECCION,inspeccion)) throw error(HttpStatus.BAD_REQUEST,"Inspección inexistente."); }
    }
    @Transactional public DetallePunto crearPunto(UsuarioAutenticado a,NuevoPunto e) {
        permisos.exigirConsultaPunto(a);permisos.exigirRegistroPunto(a);requerido(e);requerido(e.solicitudId());texto(e.denominacion(),200,true);texto(e.descripcion(),20000,true);texto(e.sustentoMunicipal(),10000,true);
        coordenadas(e.longitud(),e.latitud(),true);fuentes(a,e.reclamoId(),e.inspeccionId());
        String h=huella(null,e);var previa=anterior(Registro.PUNTO,e.solicitudId(),a,h);if(previa.isPresent()) return punto(a,previa.get());
        UUID id=UUID.randomUUID();repo.crearPunto(id,e.solicitudId(),h,e.denominacion(),e.descripcion(),e.longitud(),e.latitud(),e.sustentoMunicipal(),e.reclamoId(),e.inspeccionId(),a.usuarioId());return punto(a,id);
    }
    @Transactional public DetallePunto observar(UsuarioAutenticado a,UUID id,NuevaObservacion e) {
        permisos.exigirConsultaPunto(a);permisos.exigirRegistroPunto(a);requerido(e);requerido(e.solicitudId());instante(e.observadaEn());texto(e.descripcion(),20000,true);fuentes(a,e.reclamoId(),e.inspeccionId());bloquearPunto(id);
        String h=huella(id,e);if(anterior(Registro.OBSERVACION,e.solicitudId(),a,h).isPresent()) return punto(a,id);
        repo.observar(e.solicitudId(),h,id,a.usuarioId(),e.observadaEn(),e.descripcion(),e.inspeccionId(),e.reclamoId());return punto(a,id);
    }
    @Transactional public DetallePunto validar(UsuarioAutenticado a,UUID id,ConfirmarPunto e) {
        permisos.exigirConsultaPunto(a);permisos.exigirValidacionPunto(a);
        if(!validacionPuntos) throw error(HttpStatus.CONFLICT,"Política de validación municipal de puntos críticos pendiente de habilitación.");
        requerido(e);if(!Boolean.TRUE.equals(e.confirmaValidacionMunicipal())) throw error(HttpStatus.BAD_REQUEST,"Confirmación humana explícita requerida.");texto(e.fundamento(),10000,true);bloquearPunto(id);
        var previa=repo.validacion(id);
        if(previa.isPresent()) {
            if(previa.get().usuarioId().equals(a.usuarioId()) && previa.get().fundamento().equals(e.fundamento())) return punto(a,id);
            throw error(HttpStatus.CONFLICT,"Validación ya registrada; rectificación/retiro no habilitados.");
        }
        repo.validar(id,a.usuarioId(),e.fundamento(),politicaPuntos);return punto(a,id); // No modifica ni valida automáticamente el reclamo de origen.
    }
}
