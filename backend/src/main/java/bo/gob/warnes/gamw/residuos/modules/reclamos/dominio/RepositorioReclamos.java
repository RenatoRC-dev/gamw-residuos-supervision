package bo.gob.warnes.gamw.residuos.modules.reclamos.dominio;

import java.time.Instant;
import java.util.*;

public interface RepositorioReclamos {
    enum Tipo { JORNADA, INCIDENCIA, ACCION_CORRECTIVA, INSPECCION }
    enum Registro { RECLAMO, REVISION, PUNTO, OBSERVACION }
    record Solicitud(UUID id,UUID usuarioId,String huella) { }
    record Reclamo(UUID id,String canalDeclarado,Instant recibidoEn,String descripcion,String lugarDescripcion,String referenciasOrigen,
        Double longitud,Double latitud,UUID jornadaId,UUID usuarioId,Instant registradoEn,boolean validadoMunicipalmente) { }
    record Categoria(UUID id,String codigo,String nombre,String referenciaAprobacion,boolean esValidacion) { }
    record Revision(UUID id,UUID usuarioId,UUID resultadoId,String resultado,String referenciaAprobacion,Boolean esValidacion,String observaciones,Instant registradaEn) { }
    record Relacion(UUID id,Tipo tipo,UUID referenciaId,String fundamento,UUID usuarioId,Instant registradaEn) { }
    record Anexo(UUID id,UUID usuarioId,String origen,String descripcion,Instant capturadoEn,Instant registradoEn,String nombreOriginal,String tipoMime,String sha256,int tamanoBytes,String referenciaPolitica) { }
    record Archivo(String nombre,String mime,byte[] contenido) { }
    record Punto(UUID id,String denominacion,String descripcion,double longitud,double latitud,String sustentoMunicipal,UUID reclamoId,UUID inspeccionId,
        UUID usuarioId,Instant registradoEn,boolean validadoMunicipalmente) { }
    record Observacion(UUID id,UUID usuarioId,Instant observadaEn,String descripcion,UUID inspeccionId,UUID reclamoId,Instant registradaEn) { }
    record Validacion(UUID usuarioId,String fundamento,String referenciaPolitica,Instant registradaEn) { }
    Optional<Solicitud> solicitud(Registro tipo,UUID solicitud);
    boolean bloquearReclamo(UUID id);
    boolean bloquearPunto(UUID id);
    boolean existe(Tipo tipo,UUID id);
    Optional<UUID> jornadaAntecedente(Tipo tipo,UUID id);
    List<Reclamo> reclamos(int limite,int desde);
    Optional<Reclamo> reclamo(UUID id);
    List<Revision> revisiones(UUID id);
    List<Relacion> relaciones(UUID id);
    List<Categoria> categorias();
    Optional<Categoria> categoria(UUID id);
    boolean tieneDecision(UUID id);
    List<Anexo> anexos(UUID id);
    Optional<Archivo> archivo(UUID reclamo,UUID anexo);
    void registrar(UUID id,UUID solicitud,String huella,String canal,Instant recibido,String descripcion,String lugar,String origen,Double lon,Double lat,UUID jornada,UUID actor);
    void revisar(UUID id,UUID solicitud,String huella,UUID reclamo,UUID actor,UUID resultado,String observaciones);
    void relacionar(UUID id,UUID reclamo,Tipo tipo,UUID referencia,String fundamento,UUID actor);
    void anexar(UUID id,UUID reclamo,UUID actor,String origen,String descripcion,Instant captura,String politica,String nombre,String mime,String sha,byte[] contenido);
    List<Punto> puntos(int limite,int desde);
    Optional<Punto> punto(UUID id);
    List<Observacion> observaciones(UUID id);
    Optional<Validacion> validacion(UUID id);
    void crearPunto(UUID id,UUID solicitud,String huella,String nombre,String descripcion,double lon,double lat,String sustento,UUID reclamo,UUID inspeccion,UUID actor);
    void observar(UUID solicitud,String huella,UUID punto,UUID actor,Instant fecha,String descripcion,UUID inspeccion,UUID reclamo);
    void validar(UUID punto,UUID actor,String fundamento,String politica);
}
