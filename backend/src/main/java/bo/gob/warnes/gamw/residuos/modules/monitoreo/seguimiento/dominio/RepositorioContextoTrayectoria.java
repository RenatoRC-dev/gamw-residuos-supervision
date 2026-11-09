package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio;

import java.time.Instant;
import java.util.*;

public interface RepositorioContextoTrayectoria {
    List<Resumen> listar(int limite,int desplazamiento);
    Optional<Resumen> obtener(UUID jornadaId);
    record Resumen(UUID jornadaId,Instant inicioProgramado,Instant finProgramado,UUID periodoServicioId,
            String empresaOperadora,UUID rutaId,String codigoRuta,String nombreRuta,UUID rutaVersionId,int numeroVersion) { }
}
