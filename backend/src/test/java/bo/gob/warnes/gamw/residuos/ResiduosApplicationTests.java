package bo.gob.warnes.gamw.residuos;

import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.RepositorioRuta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("smoke")
class ResiduosApplicationTests {
    @MockitoBean private org.springframework.jdbc.core.JdbcTemplate consultasSql;
    @MockitoBean
    private RepositorioRuta rutas;
    @MockitoBean
    private bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.RepositorioRegistroOperativo registro;
    @MockitoBean
    private bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.RepositorioVehiculos vehiculos;
    @MockitoBean
    private bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.RepositorioJornadas jornadas;
    @MockitoBean
    private bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.RepositorioEventosEjecucion eventos;
    @MockitoBean
    private bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.RepositorioPosicionesGps posicionesGps;
    @Autowired
    private ApplicationContext context;

    @Test
    void startsApplicationWithoutRequiringDatabaseForSmokeTests() {
        assertThat(context.getBean(ResiduosApplication.class)).isNotNull();
        assertThat(context.containsBean("dataSource")).isFalse();
    }
}
