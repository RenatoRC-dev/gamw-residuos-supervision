package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioConsultaGps;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectura interna reutilizable. La fachada CU-16 exige D1-A antes de invocarla.
 * No interpreta cumplimiento ni continuidad entre puntos.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarSeguimientoGps {
    private final RepositorioConsultaGps repositorio;
    public ConsultarSeguimientoGps(RepositorioConsultaGps repositorio) { this.repositorio = repositorio; }

    public Seguimiento consultar(UUID jornadaId) {
        Objects.requireNonNull(jornadaId, "La jornada es obligatoria.");
        var puntos = repositorio.porJornada(jornadaId).stream().map(this::punto).toList();
        // Una única lectura: una recepción tardía no reemplaza la última captura por su hora de llegada.
        return new Seguimiento(jornadaId, new ColeccionPuntos("FeatureCollection", puntos),
                puntos.isEmpty() ? null : puntos.getLast());
    }
    public Pagina consultarPagina(UUID jornadaId,int limite,int desplazamiento,Instant corteRecepcion) {
        var filas=repositorio.paginaPorJornada(Objects.requireNonNull(jornadaId),limite+1,desplazamiento,corteRecepcion);
        var puntos=filas.stream().limit(limite).map(this::punto).toList();
        return new Pagina(new ColeccionPuntos("FeatureCollection",puntos),desplazamiento,
                filas.size()>limite?desplazamiento+limite:null,corteRecepcion);
    }
    public record Pagina(ColeccionPuntos trayectoria,int desplazamiento,Integer siguiente,Instant corteRecepcion) { }
    private Punto punto(PosicionGps p) {
        return new Punto("Feature", p.capturaId(),
                new GeometriaPunto("Point", List.of(p.longitud(), p.latitud())),
                new PropiedadesPosicion(p.id(), p.capturaId(), p.jornadaId(), p.asignacionJornadaId(), p.usuarioId(),
                        p.capturadoEn(), p.recibidoEn(), p.precisionMetros()));
    }
    // GeoJSON mantiene sus nombres estándar; los atributos de negocio permanecen en español.
    // Se conservan puntos: unirlos automáticamente podría ocultar interrupciones o inventar recorrido.
    public record Seguimiento(UUID jornadaId, ColeccionPuntos trayectoria, Punto ultimaPosicion) { }
    public record ColeccionPuntos(String type, List<Punto> features) { }
    public record Punto(String type, UUID id, GeometriaPunto geometry, PropiedadesPosicion properties) { }
    public record GeometriaPunto(String type, List<Double> coordinates) { }
    public record PropiedadesPosicion(UUID id, UUID capturaId, UUID jornadaId, UUID asignacionJornadaId,
            UUID usuarioId, Instant capturadoEn, Instant recibidoEn, double precisionMetros) { }
}
