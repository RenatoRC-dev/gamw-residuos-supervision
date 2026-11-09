package bo.gob.warnes.gamw.residuos.shared.security;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.RepositorioUsuarios;
import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.RepositorioRegistroOperativo;
import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.RepositorioVehiculos;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.RepositorioEventosEjecucion;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.RepositorioJornadas;
import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.RepositorioRuta;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Fronteras sustituidas exclusivamente para probar HTTP/arranque sin tocar una base de datos. */
@Import(ContextoAutenticacionSinBd.SinInfraestructuraBd.class)
abstract class ContextoAutenticacionSinBd {
    @MockitoBean protected RepositorioUsuarios usuarios;
    @MockitoBean protected RepositorioRuta rutas;
    @MockitoBean protected RepositorioRegistroOperativo registro;
    @MockitoBean protected RepositorioVehiculos vehiculos;
    @MockitoBean protected RepositorioJornadas jornadas;
    @MockitoBean protected RepositorioEventosEjecucion eventos;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
    static class SinInfraestructuraBd { }
}
