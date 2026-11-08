package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioJornadas {
    void insertar(Jornada jornada);
    Optional<Jornada> buscar(UUID id,boolean bloquear);
    List<Jornada> listar();
    void sustituir(UUID jornadaId,Instant actualizadoEn);
    void insertarAsignacion(AsignacionJornada asignacion);
    Optional<AsignacionJornada> actual(UUID jornadaId);
    List<AsignacionJornada> asignaciones(UUID jornadaId);
    void cerrarAsignacion(UUID asignacionId,Instant vigenteHasta);
}
