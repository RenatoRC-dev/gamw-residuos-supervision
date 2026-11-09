package bo.gob.warnes.gamw.residuos.modules.monitoreo.posicion.dominio;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioContextoTrayectoria.Resumen;
import java.time.Instant;
import java.util.*;

public interface RepositorioMonitoreo {
    List<JornadaActiva> activas(int limite,int desplazamiento);
    record JornadaActiva(Resumen contexto,UUID vehiculoId,String placa,Instant inicioRegistrado,
            PosicionGps ultimaPosicion,String referenciaGeojson) { }
}
