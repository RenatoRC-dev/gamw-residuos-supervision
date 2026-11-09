package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.config.NavegadorWebAislado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

/** CU-07/CU-24: navegador, servidor, filtros locales y PostgreSQL reales. No autorización institucional. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "server.address=127.0.0.1", "debug=false", "logging.level.root=ERROR"})
@ActiveProfiles("desarrollo-local")
@Timeout(180)
class RegistroPlanificacionWebIT extends IntegracionPostgresV8 {
    private static final int WEB = elegirPuerto();
    @Value("${local.server.port}") int backend;
    @Autowired JdbcTemplate jdbc;
    private static int elegirPuerto() {
        try { return NavegadorWebAislado.puertoDisponible(); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    @DynamicPropertySource static void origen(DynamicPropertyRegistry propiedades) {
        propiedades.add("gamw.development.web-origin", () -> "http://127.0.0.1:" + WEB);
    }
    @Test void responsablesHistoricosDesdeLaWeb() throws Exception {
        NavegadorWebAislado.ejecutar(backend, WEB, "registro-operativo.test.mjs");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persona_natural WHERE ci LIKE 'SINTETICA-S2A-%'", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario u JOIN persona_natural p ON p.id=u.persona_natural_id WHERE p.ci LIKE 'SINTETICA-S2A-%'", Integer.class)).isZero();
    }
    @Test void reprogramacionHistoricaDesdeLaWeb() throws Exception {
        NavegadorWebAislado.ejecutar(backend, WEB, "jornadas.test.mjs");
    }
}
