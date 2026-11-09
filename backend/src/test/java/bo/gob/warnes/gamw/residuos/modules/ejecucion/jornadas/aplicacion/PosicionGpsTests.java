package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.aplicacion.RegistrarPosicionesGps.Lectura;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PosicionGpsTests {
    @Test void rechazaNoFinitosSinInventarNiCorregirCoordenadas() {
        for(double valor:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertThatIllegalArgumentException().isThrownBy(()->new Lectura(UUID.randomUUID(),Instant.EPOCH,valor,0.0,1.0).validar());
            assertThatIllegalArgumentException().isThrownBy(()->new Lectura(UUID.randomUUID(),Instant.EPOCH,0.0,valor,1.0).validar());
            assertThatIllegalArgumentException().isThrownBy(()->new Lectura(UUID.randomUUID(),Instant.EPOCH,0.0,0.0,valor).validar());
        }
    }
    @Test void rechazaPrecisionTemporalNoRepresentableSinTruncarLaEvidencia() {
        assertThatIllegalArgumentException().isThrownBy(()->new Lectura(UUID.randomUUID(),Instant.parse("2026-01-01T00:00:00.000000001Z"),0.0,0.0,0.0).validar());
        assertThatCode(()->new Lectura(UUID.randomUUID(),Instant.parse("2026-01-01T00:00:00.123456Z"),0.0,0.0,0.0).validar()).doesNotThrowAnyException();
    }
}
