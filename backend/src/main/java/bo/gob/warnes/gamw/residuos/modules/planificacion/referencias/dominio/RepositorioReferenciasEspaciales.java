package bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.dominio;

import java.time.Instant;
import java.util.*;

public interface RepositorioReferenciasEspaciales {
    record Version(UUID id,UUID referenciaId,int numeroVersion,UUID rutaVersionId,String nombre,String tipo,
            String geojson,Double radioMetros,Instant vigenteDesde,Instant vigenteHasta,String motivo) { }
    record OpcionRuta(UUID rutaVersionId,String codigo,String nombre,int numeroVersion) { }
    List<Version> listar(UUID rutaVersionId,int limite,int desplazamiento);
    List<OpcionRuta> rutas(int limite,int desplazamiento);
    Optional<Version> actualBloqueada(UUID referenciaId);
    void crearIdentidad(UUID referenciaId);
    void insertar(Version version,UUID usuarioId);
    void cerrar(UUID versionId,Instant hasta,UUID usuarioId,String motivo);
}
