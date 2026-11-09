package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConsultarSeguimientoGpsTests {
    @Test void jornadaSinPosicionesNoInventaUltimaUbicacionNiLinea() {
        var jornada = UUID.randomUUID();
        var vista = new ConsultarSeguimientoGps(id -> List.of()).consultar(jornada);
        assertThat(vista.jornadaId()).isEqualTo(jornada);
        assertThat(vista.trayectoria().type()).isEqualTo("FeatureCollection");
        assertThat(vista.trayectoria().features()).isEmpty();
        assertThat(vista.ultimaPosicion()).isNull();
    }
    @Test void conservaOrdenLongitudLatitudYMetadatosSinConfundirRecepcionConCaptura() {
        var jornada = UUID.randomUUID();
        var t = Instant.parse("2026-11-09T04:00:00.123456Z");
        var tardia = new PosicionGps(UUID.randomUUID(), UUID.randomUUID(), jornada, UUID.randomUUID(), UUID.randomUUID(),
                t, t.plusSeconds(500), 20.12345678912345, -10.98765432198765, 3.123456789);
        var reciente = new PosicionGps(UUID.randomUUID(), UUID.randomUUID(), jornada, tardia.asignacionJornadaId(), tardia.usuarioId(),
                t.plusSeconds(30), t.plusSeconds(31), 21, -11, 4);
        var vista = new ConsultarSeguimientoGps(id -> List.of(tardia, reciente)).consultar(jornada);
        var primero = vista.trayectoria().features().getFirst();
        assertThat(primero.geometry().coordinates()).containsExactly(tardia.longitud(), tardia.latitud());
        assertThat(primero.properties().capturadoEn()).isEqualTo(t);
        assertThat(primero.properties().recibidoEn()).isEqualTo(t.plusSeconds(500));
        assertThat(primero.properties().precisionMetros()).isEqualTo(tardia.precisionMetros());
        assertThat(primero.properties().usuarioId()).isEqualTo(tardia.usuarioId());
        assertThat(vista.ultimaPosicion().id()).isEqualTo(reciente.capturaId());
        assertThatThrownBy(() -> vista.trayectoria().features().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void rechazaConsultaSinJornadaAntesDeAccederAPersistencia() {
        var consulta = new ConsultarSeguimientoGps(id -> { throw new AssertionError("No ejecutar consulta global"); });
        assertThatThrownBy(() -> consulta.consultar(null)).isInstanceOf(NullPointerException.class);
    }
}
