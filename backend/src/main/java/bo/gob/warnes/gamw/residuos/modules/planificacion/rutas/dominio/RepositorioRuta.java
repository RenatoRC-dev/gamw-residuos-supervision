package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence owned by CU-09, including the lock used to serialize version creation. */
public interface RepositorioRuta {
    void insertar(Ruta ruta);
    void insertar(RutaVersion version);
    List<Ruta> listar();
    Optional<Ruta> buscar(UUID id, boolean lock);
    List<RutaVersion> versiones(UUID rutaId);
    Optional<RutaVersion> ultima(UUID rutaId);
    Optional<RutaVersion> obtenerVersion(UUID rutaId, int numeroVersion);
    Optional<RutaVersion> versionPorId(UUID id);
    void cerrarVersion(UUID versionId, Instant vigenteHasta);
    void actualizarFecha(UUID rutaId, Instant actualizadoEn);
}
