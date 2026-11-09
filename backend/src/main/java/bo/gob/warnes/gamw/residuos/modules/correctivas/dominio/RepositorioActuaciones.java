package bo.gob.warnes.gamw.residuos.modules.correctivas.dominio;

import java.time.Instant;
import java.util.*;

public interface RepositorioActuaciones {
    record Incidencia(UUID id, UUID solicitudId, UUID jornadaId, UUID asignacionJornadaId, UUID usuarioId,
            Instant ocurridaEn, Instant registradaEn, String descripcion, String evidenciaDeclarada, String lugarDescripcion) { }
    record Accion(UUID id, UUID solicitudId, UUID jornadaId, UUID usuarioId, UUID incidenciaId, UUID inspeccionId,
            UUID fiscalizacionId, String descripcion, Instant creadaEn, String antecedentes) { }
    record Respuesta(UUID id, UUID accionId, UUID usuarioId, Instant comunicadaEn, Instant registradaEn,
            String descripcion, String evidenciaDeclarada, List<UUID> evidenciasReferenciadas) { }
    record Verificacion(UUID id, UUID solicitudId, UUID accionId, UUID respuestaId, UUID usuarioId, UUID resultadoId,
            String resultado, String referenciaAprobacion, String observaciones, UUID inspeccionId, Instant registradaEn) { }
    record Categoria(UUID id, String codigo, String nombre, String referenciaAprobacion, boolean observacionesObligatorias) { }
    record Solicitud(UUID id, UUID usuarioId, String huella) { }
    enum Registro { INCIDENCIA, ACCION, VERIFICACION, RESPUESTA }
    record ResumenEmpresa(UUID id,UUID jornadaId,UUID periodoServicioId,UUID empresaOperadoraId,String razonSocial,
            Instant inicioProgramado,UUID rutaVersionId,String descripcion,Instant creadaEn) { }
    List<ResumenEmpresa> accionesEmpresa(UUID usuario,int limite,int desde);
    List<UUID> evidenciasDisponibles(UUID accion);
    record EvidenciaCompartida(UUID id,UUID inspeccionId,String nombreOriginal,String tipoMime,String origen,String descripcion,
            Instant capturadaEn,Instant incorporadaEn,String sha256,int tamanoBytes) { }
    List<EvidenciaCompartida> soporteEmpresa(UUID accion);
    void responder(UUID id,UUID solicitud,UUID accion,UUID actor,UUID concesion,Instant comunicada,String descripcion,
            String evidenciaDeclarada,String huella,List<UUID> evidencias);
    Optional<Solicitud> solicitud(Registro tipo, UUID solicitud);
    boolean bloqueo(UUID jornada);
    boolean enEjecucion(UUID jornada, Instant ocurrida);
    Optional<Incidencia> incidencia(UUID id);
    List<Incidencia> incidencias(UUID jornada);
    Optional<Accion> accion(UUID id);
    List<Accion> acciones(UUID jornada);
    List<Respuesta> respuestas(UUID accion);
    List<Verificacion> verificaciones(UUID accion);
    List<Categoria> categorias();
    Optional<Categoria> categoria(UUID id);
    void insertar(Incidencia incidencia, String huella);
    void insertar(Accion accion, String huella);
    void verificar(UUID id, UUID solicitud, UUID accion, UUID jornada, UUID respuesta, UUID actor,
            UUID resultado, String observaciones, UUID inspeccion, String huella);
}
