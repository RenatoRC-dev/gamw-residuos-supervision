package bo.gob.warnes.gamw.residuos.modules.monitoreo.revision.dominio;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.dominio.RepositorioReferenciasEspaciales.Version;
import java.time.Instant;
import java.util.*;

public interface RepositorioRevisionGps {
    record Muestra(PosicionGps posicion,double distanciaRutaMetros,Double distanciaAnteriorMetros,
            long total,Instant ultimaCaptura,Instant ultimaRecepcion) { }
    record Coincidencia(Version referencia,long muestras,long observadas,long precisas,long cercanasInciertas,
            Instant primera,Instant ultima,Double distanciaMinimaMetros) { }
    List<Muestra> muestras(UUID jornada,Instant corte,int limite);
    List<Coincidencia> coincidencias(UUID jornada,Instant corte,double precisionMaxima);
}
