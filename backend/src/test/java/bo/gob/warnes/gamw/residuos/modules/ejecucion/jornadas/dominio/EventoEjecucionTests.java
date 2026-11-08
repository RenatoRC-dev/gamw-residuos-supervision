package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EventoEjecucionTests {
    @Test void eventosValidosConservanIdentidadYAmbasFechas() {
        var captura = Instant.parse("2026-11-10T11:02:00.123456Z");
        for (var tipo : TipoEventoEjecucion.values()) {
            var e = new EventoEjecucionJornada(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),tipo,captura,captura.plusSeconds(7200));
            assertThat(e.capturadoEn()).isEqualTo(captura);
            assertThat(e.recibidoEn()).isAfter(captura);
            assertThat(e.mismoContenido(e.jornadaId(),e.asignacionJornadaId(),tipo,captura)).isTrue();
            assertThat(e.mismoContenido(e.jornadaId(),e.asignacionJornadaId(),tipo,captura.plusSeconds(1))).isFalse();
        }
        assertThat(TipoEventoEjecucion.values()).containsExactly(TipoEventoEjecucion.INICIO,TipoEventoEjecucion.FIN);
        assertThatThrownBy(() -> TipoEventoEjecucion.valueOf("OTRO")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void camposObligatoriosNoAdmitenNull() {
        for (int campo=0; campo<7; campo++) {
            final int c = campo;
            assertThatThrownBy(() -> new EventoEjecucionJornada(c==0?null:UUID.randomUUID(),c==1?null:UUID.randomUUID(),
                    c==2?null:UUID.randomUUID(),c==3?null:UUID.randomUUID(),c==4?null:TipoEventoEjecucion.INICIO,
                    c==5?null:Instant.EPOCH,c==6?null:Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void rechazaPrecisionNoRepresentableSinRedondearCaptura() {
        assertThatThrownBy(() -> new EventoEjecucionJornada(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                TipoEventoEjecucion.INICIO,Instant.parse("2026-11-10T11:02:00.123456789Z"),Instant.now())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no se redondea");
    }
}
