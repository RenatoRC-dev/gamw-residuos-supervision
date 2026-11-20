package bo.gob.warnes.gamw.residuos.modules.historial.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionMunicipal;
import bo.gob.warnes.gamw.residuos.modules.historial.persistencia.RepositorioReportesServicio;
import bo.gob.warnes.gamw.residuos.modules.historial.persistencia.RepositorioReportesServicio.Conteo;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * CU-30 / RF-24 (corte descriptivo, solo lectura). Recuentos de registros conservados con definición, fuente y fecha del último registro.
 * NO son indicadores oficiales (PENDING-11): sin porcentajes, cumplimiento, KPIs contractuales, sanciones ni exportación (formato no aprobado).
 * Autorización: la base reutiliza JORNADAS_CONSULTAR (igual que CU-29); cada fuente adicional solo se cuenta si el actor tiene el permiso
 * propio de su módulo. Sin ese permiso se informa ACCESO_NO_PERMITIDO sin cifra, para no confundir «sin acceso» con «cero». A2/A4 sin acceso (D1).
 */
@Service
@Transactional(readOnly = true)
public class ConsultarReportesServicio {
    public static final String ZONA = "America/La_Paz";
    private final AutorizacionMunicipal permisos;
    private final RepositorioReportesServicio repositorio;
    public ConsultarReportesServicio(AutorizacionMunicipal permisos, RepositorioReportesServicio repositorio) {
        this.permisos = permisos; this.repositorio = repositorio;
    }

    public record Filtros(LocalDate desde, LocalDate hasta, UUID periodoId, UUID empresaId, String zonaFechaServicio) { }
    /** estado: CON_REGISTROS | SIN_REGISTROS | ACCESO_NO_PERMITIDO | NO_CONSULTADO | NO_APLICA. cantidad solo existe si se consultó. */
    public record Recuento(String clave, String nombre, String definicion, String fuente, String permisoRequerido, String estado,
            Long cantidad, Instant ultimoRegistro, String nota) { }
    public record Reporte(Filtros filtros, Instant generadoEn, String naturaleza, List<Recuento> recuentos, List<String> limitaciones) { }

    private static final String NATURALEZA = "Reporte descriptivo de registros conservados. No es un indicador oficial (PENDING-11) ni una conclusión de cumplimiento.";
    private static final String ALCANCE = "jornadas cuya fecha de servicio (inicio programado en " + ZONA + ") está entre 'desde' y 'hasta', ambos incluidos"
        + ", con los filtros de periodo/empresa indicados";

    public Reporte resumen(UsuarioAutenticado actor, LocalDate desde, LocalDate hasta, UUID periodo, UUID empresa) {
        permisos.exigirConsultaHistorialServicio(actor);
        if (desde == null || hasta == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indique 'desde' y 'hasta' (AAAA-MM-DD).");
        if (desde.isAfter(hasta)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rango de fechas es contradictorio: 'desde' no puede ser posterior a 'hasta'.");
        // Un filtro inexistente o incompatible no se presenta como «cero registros».
        if (empresa != null && !repositorio.empresaExiste(empresa)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa operadora no encontrada.");
        if (periodo != null && !repositorio.periodoExiste(periodo, null)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Periodo de servicio no encontrado.");
        if (periodo != null && empresa != null && !repositorio.periodoExiste(periodo, empresa))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El periodo indicado no pertenece a la empresa indicada.");

        var r = new ArrayList<Recuento>();
        r.add(consultado("jornadas", "Jornadas programadas", "Número de " + ALCANCE + "; incluye las sustituidas por reprogramación.",
            "jornada", "JORNADAS_CONSULTAR", () -> repositorio.jornadas(desde, hasta, periodo, empresa), null));
        r.add(consultado("jornadasSustituidas", "Jornadas sustituidas por reprogramación", "Número de " + ALCANCE + " marcadas como no vigentes por una reprogramación.",
            "jornada (vigente = false)", "JORNADAS_CONSULTAR", () -> repositorio.jornadasSustituidas(desde, hasta, periodo, empresa), null));
        r.add(consultado("jornadasConAsignacion", "Jornadas con asignación registrada", "Número de " + ALCANCE + " con al menos un registro de asignación (vigente o histórico).",
            "asignacion_jornada", "JORNADAS_CONSULTAR", () -> repositorio.jornadasConAsignacion(desde, hasta, periodo, empresa), null));
        r.add(new Recuento("eventosEjecucion", "Eventos de ejecución (INICIO/FIN)", "No se calcula.", "evento_ejecucion_jornada", null, "NO_CONSULTADO", null, null,
            "No existe un permiso municipal aprobado para consultar eventos de ejecución (igual que CU-29); no se verificó si hay registros."));
        boolean gps = permisos.tiene(actor, "TRAYECTORIA_JORNADA_CONSULTAR");
        String notaGps = "La presencia de posiciones GPS es evidencia técnica; no prueba recolección ni equivale a cumplimiento.";
        r.add(siPermite(gps, "posicionesGps", "Posiciones GPS recibidas", "Número de posiciones GPS registradas para las " + ALCANCE + ".",
            "posicion_gps", "TRAYECTORIA_JORNADA_CONSULTAR", () -> repositorio.posicionesGps(desde, hasta, periodo, empresa), notaGps));
        r.add(siPermite(gps, "jornadasConGps", "Jornadas con al menos una posición GPS", "Número de " + ALCANCE + " con una o más posiciones GPS registradas.",
            "posicion_gps", "TRAYECTORIA_JORNADA_CONSULTAR", () -> repositorio.jornadasConGps(desde, hasta, periodo, empresa), notaGps));
        r.add(siPermite(permisos.tiene(actor, "CORRECTIVA_CONSULTAR"), "incidencias", "Incidencias operativas registradas",
            "Número de incidencias registradas en las " + ALCANCE + ".", "incidencia_operativa", "CORRECTIVA_CONSULTAR",
            () -> repositorio.incidencias(desde, hasta, periodo, empresa), "Una incidencia es un hecho declarado; no implica incumplimiento ni sanción."));
        r.add(siPermite(permisos.tiene(actor, "FISCALIZACION_CONSULTAR"), "inspecciones", "Inspecciones de campo registradas",
            "Número de inspecciones registradas en las " + ALCANCE + ".", "inspeccion", "FISCALIZACION_CONSULTAR",
            () -> repositorio.inspecciones(desde, hasta, periodo, empresa), "El resultado de fiscalización lo decide un fiscalizador municipal; aquí no se resume."));
        boolean reclamos = permisos.tiene(actor, "RECLAMO_CONSULTAR");
        r.add(siPermite(reclamos, "reclamosVinculados", "Reclamos vinculados directamente a jornadas",
            "Número de reclamos registrados con referencia directa a una de las " + ALCANCE + ".", "reclamo_institucional (jornada_id)", "RECLAMO_CONSULTAR",
            () -> repositorio.reclamosVinculados(desde, hasta, periodo, empresa), null));
        if (periodo != null || empresa != null) {
            r.add(new Recuento("reclamosRecibidos", "Reclamos recibidos en el rango", "No aplica con filtro de periodo o empresa.", "reclamo_institucional", "RECLAMO_CONSULTAR",
                "NO_APLICA", null, null, "Un reclamo puede no referenciar jornada ni empresa; con estos filtros no se puede atribuir sin inventar una relación."));
        } else {
            r.add(siPermite(reclamos, "reclamosRecibidos", "Reclamos recibidos en el rango",
                "Número de reclamos cuya fecha de recepción declarada (" + ZONA + ") está entre 'desde' y 'hasta', con o sin jornada.", "reclamo_institucional (recibido_en)",
                "RECLAMO_CONSULTAR", () -> repositorio.reclamosRecibidos(desde, hasta), null));
        }
        return new Reporte(new Filtros(desde, hasta, periodo, empresa, ZONA), Instant.now().truncatedTo(ChronoUnit.MICROS), NATURALEZA, r, List.of(
            "Recuentos descriptivos: sin porcentajes, cumplimiento, KPIs contractuales ni sanciones. Las fórmulas e indicadores oficiales siguen pendientes (PENDING-11).",
            "La ausencia de registros no implica incumplimiento; «acceso no permitido» significa que no se sabe si existen registros.",
            "Sin exportación: no existe un formato aprobado. Responsables municipales (A2) y supervisores de empresa (A4) sin acceso hasta resolver D1.",
            "«Último registro» es la fecha más reciente de registro o recepción en la fuente dentro del alcance; no indica una carga programada."));
    }

    private Recuento consultado(String clave, String nombre, String definicion, String fuente, String permiso, Supplier<Conteo> conteo, String nota) {
        var c = conteo.get();
        return new Recuento(clave, nombre, definicion, fuente, permiso, c.cantidad() > 0 ? "CON_REGISTROS" : "SIN_REGISTROS", c.cantidad(), c.ultimoRegistro(),
            nota != null ? nota : c.cantidad() > 0 ? null : "Cero registros conservados para este alcance (dato consultado, no faltante).");
    }
    private Recuento siPermite(boolean permitido, String clave, String nombre, String definicion, String fuente, String permiso, Supplier<Conteo> conteo, String nota) {
        if (!permitido) return new Recuento(clave, nombre, definicion, fuente, permiso, "ACCESO_NO_PERMITIDO", null, null,
            "Su cuenta no tiene el permiso propio de este módulo: no se sabe si existen registros.");
        return consultado(clave, nombre, definicion, fuente, permiso, conteo, nota);
    }
}
