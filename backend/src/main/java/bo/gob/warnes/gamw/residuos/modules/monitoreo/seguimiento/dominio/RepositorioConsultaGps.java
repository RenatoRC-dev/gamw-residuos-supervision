package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

/** Lectura interna; orden por captura original y UUID de captura como desempate estable. */
public interface RepositorioConsultaGps {
    List<PosicionGps> porJornada(UUID jornadaId);
    default List<PosicionGps> paginaPorJornada(UUID jornadaId, int limite, int desplazamiento, Instant corteRecepcion) {
        throw new UnsupportedOperationException("El adaptador no admite lectura paginada.");
    }
}
