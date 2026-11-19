package bo.gob.warnes.gamw.residuos.shared.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * INT-04 (solo pruebas): la aplicación se conecta con la identidad de EJECUCIÓN no propietaria (concesiones de db/privilegios)
 * y Flyway con la identidad de MIGRACIÓN propietaria — el equivalente a DB_USERNAME + SPRING_FLYWAY_USER en despliegue.
 * Se usa con {@code @Import} sobre la clase de prueba (las propiedades dinámicas de la clase base no se pueden sobrescribir).
 */
@TestConfiguration(proxyBeanMethods = false)
public class EjecucionNoPropietaria {
    @Bean @Primary
    DataSource dataSource() {
        var postgres = PostgresV8Aislado.obtener();
        var datos = new HikariDataSource();
        datos.setJdbcUrl(postgres.urlLimpia());
        datos.setUsername(postgres.usuarioEjecucion());
        datos.setPassword(postgres.claveEjecucion());
        // Cada contexto con credenciales propias abre su pool: se acota para no agotar las conexiones del clúster efímero compartido.
        datos.setMaximumPoolSize(3);
        datos.setMinimumIdle(0);
        datos.setPoolName("ejecucion-no-propietaria");
        return datos;
    }
    @Bean
    FlywayConfigurationCustomizer flywayConIdentidadDeMigracion() {
        var postgres = PostgresV8Aislado.obtener();
        return configuracion -> configuracion.dataSource(postgres.urlLimpia(), postgres.usuario(), postgres.clave());
    }
}
