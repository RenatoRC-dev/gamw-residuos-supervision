package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** H-05: ventana deslizante con reloj controlado (sin esperas reales). */
class LimitadorIntentosAccesoTests {
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-19T12:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zona) { return this; }
        @Override public Instant instant() { return ahora.get(); }
    };

    @Test void bloqueaAlAgotarElCupoYSeLiberaAlVencerLaVentana() {
        var l = new LimitadorIntentosAcceso(2, 5, Duration.ofSeconds(300), reloj);
        l.registrarFallo("o", "u"); l.verificar("o", "u");
        l.registrarFallo("o", "u");
        var error = catchThrowableOfType(LimitadorIntentosAcceso.DemasiadosIntentos.class, () -> l.verificar("o", "u"));
        assertThat(error.reintentarEnSegundos()).isEqualTo(300);
        ahora.set(ahora.get().plusSeconds(299));
        assertThatThrownBy(() -> l.verificar("o", "u")).isInstanceOf(LimitadorIntentosAcceso.DemasiadosIntentos.class);
        ahora.set(ahora.get().plusSeconds(1));
        assertThatCode(() -> l.verificar("o", "u")).as("ventana vencida: sin bloqueo permanente").doesNotThrowAnyException();
    }

    @Test void rechazaLimitesIncoherentes() {
        assertThatThrownBy(() -> new LimitadorIntentosAcceso(0, 5, Duration.ofSeconds(1), reloj)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitadorIntentosAcceso(5, 2, Duration.ofSeconds(1), reloj)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitadorIntentosAcceso(1, 2, Duration.ZERO, reloj)).isInstanceOf(IllegalArgumentException.class);
    }
}
