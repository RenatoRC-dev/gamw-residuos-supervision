package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.RepositorioJornadas;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotenciaEjecucionTests {
    @Test void reintentoSeResuelveAntesDeRevalidarPlanificacionHistorica() {
        var repositorio = mock(RepositorioEventosEjecucion.class);
        var jornadas = mock(RepositorioJornadas.class);
        var e = new EventoEjecucionJornada(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                TipoEventoEjecucion.INICIO,Instant.EPOCH,Instant.EPOCH.plusSeconds(3600));
        when(repositorio.porCliente(e.eventoClienteId())).thenReturn(Optional.of(e));
        var servicio = new RegistrarEventosEjecucion(repositorio,jornadas);
        var resultado = servicio.registrar(e.jornadaId(),e.eventoClienteId(),e.asignacionJornadaId(),e.tipoEvento(),e.capturadoEn());
        assertThat(resultado.creado()).isFalse(); assertThat(resultado.evento()).isSameAs(e);
        verifyNoInteractions(jornadas);
        assertThatThrownBy(() -> servicio.registrar(e.jornadaId(),e.eventoClienteId(),e.asignacionJornadaId(),TipoEventoEjecucion.FIN,e.capturadoEn()))
                .isInstanceOf(ConflictoEjecucion.class);
        verify(repositorio,never()).insertarSiAusente(any());
    }
}
