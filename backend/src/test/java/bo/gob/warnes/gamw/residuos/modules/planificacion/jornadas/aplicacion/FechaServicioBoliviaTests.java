package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.RepositorioRuta;
import java.time.Instant;
import java.time.LocalDate;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Cambia temporalmente la zona global de la JVM y siempre la restaura. */
@Isolated
class FechaServicioBoliviaTests {
    @Test
    void fronteraDeFechaBolivianaEsIndependienteDeLaZonaDelServidor() {
        var referencias = new ReferenciasJornadas(mock(RepositorioRegistroOperativo.class),
                mock(RepositorioVehiculos.class),mock(RepositorioRuta.class));
        var zonaAnterior = TimeZone.getDefault();
        try {
            for (String zonaServidor : new String[]{"UTC","Asia/Tokyo","Pacific/Honolulu","America/La_Paz"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zonaServidor));
                assertThat(referencias.fechaServicio(Instant.parse("2026-11-09T02:30:00Z")))
                        .as("02:30 UTC con servidor en %s",zonaServidor).isEqualTo(LocalDate.of(2026,11,8));
                assertThat(referencias.fechaServicio(Instant.parse("2026-11-09T04:30:00Z")))
                        .as("04:30 UTC con servidor en %s",zonaServidor).isEqualTo(LocalDate.of(2026,11,9));
                assertThat(referencias.fechaServicio(Instant.parse("2026-11-09T03:59:59Z")))
                        .as("Antes de medianoche en Bolivia con servidor en %s",zonaServidor).isEqualTo(LocalDate.of(2026,11,8));
                assertThat(referencias.fechaServicio(Instant.parse("2026-11-09T04:00:00Z")))
                        .as("Medianoche en Bolivia con servidor en %s",zonaServidor).isEqualTo(LocalDate.of(2026,11,9));
            }
        } finally {
            TimeZone.setDefault(zonaAnterior);
        }
    }
}
