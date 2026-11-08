package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio;

import java.util.*;

public interface RepositorioEventosEjecucion {
    Optional<EventoEjecucionJornada> porCliente(UUID eventoClienteId);
    List<EventoEjecucionJornada> porJornada(UUID jornadaId);
    Optional<EventoEjecucionJornada> insertarSiAusente(EventoEjecucionJornada evento);
}
