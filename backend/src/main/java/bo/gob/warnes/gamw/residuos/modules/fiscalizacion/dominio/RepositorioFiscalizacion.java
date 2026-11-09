package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.dominio;

import java.time.Instant;
import java.util.*;

public interface RepositorioFiscalizacion {
    record Registro(UUID id,UUID usuarioId,String huella,String json) { }
    record Archivo(UUID id,UUID inspeccionId,UUID autorId,Instant capturadaEn,String nombre,String mime,String origen,
            String descripcion,String politica,String sha256,byte[] contenido) { }
    record Categoria(UUID id,String codigo,String nombre,String referenciaAprobacion,boolean observacionesObligatorias) { }
    boolean bloquearJornada(UUID id);
    Optional<String> antecedentes(UUID jornada,Instant corte,boolean incluirIdsGps);
    List<String> inspecciones(UUID jornada);
    Optional<String> fiscalizacion(UUID jornada);
    Optional<Registro> inspeccionPorSolicitud(UUID solicitud);
    Optional<Registro> fiscalizacionPorSolicitud(UUID solicitud);
    List<Categoria> categorias();
    Optional<Categoria> categoriaBloqueada(UUID id);
    void insertarInspeccion(UUID id,UUID solicitud,UUID jornada,UUID actor,Instant realizada,String lugar,Double latitud,Double longitud,String observaciones,String huella);
    void insertarArchivo(Archivo archivo);
    Optional<Archivo> archivo(UUID jornada,UUID id);
    Optional<String> inspeccion(UUID id);
    void insertarFiscalizacion(UUID id,UUID solicitud,UUID jornada,UUID actor,UUID categoria,String observaciones,Instant corte,String antecedentes,String huella);
    boolean inspeccionPertenece(UUID inspeccion,UUID jornada);
    void vincularInspeccion(UUID fiscalizacion,UUID jornada,UUID inspeccion);
}
