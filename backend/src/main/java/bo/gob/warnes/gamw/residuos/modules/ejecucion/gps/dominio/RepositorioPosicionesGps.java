package bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio;

import java.util.Optional;
import java.util.UUID;

public interface RepositorioPosicionesGps {
    Optional<PosicionGps> buscar(UUID jornada,UUID captura);
    PosicionGps insertar(PosicionGps posicion);
}
