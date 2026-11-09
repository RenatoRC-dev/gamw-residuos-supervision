package bo.gob.warnes.gamw.residuos.shared.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in: uses only the dedicated PostgreSQL database configured by DB_* variables. */
@SpringBootTest
class FlywayBaselineIT extends IntegracionPostgresV8 {
    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void usesTheUnprivilegedApplicationConnection() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("gamw_app_dev");
        assertThat(jdbc.queryForMap("""
                SELECT rolcanlogin, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls
                FROM pg_roles WHERE rolname = current_user
                """))
                .containsEntry("rolcanlogin", true)
                .containsEntry("rolsuper", false)
                .containsEntry("rolcreatedb", false)
                .containsEntry("rolcreaterole", false)
                .containsEntry("rolreplication", false)
                .containsEntry("rolbypassrls", false);
    }

    @Test
    void appliesTechnicalBaselineAndCanMigrateAgainWithoutChanges() {
        assertThat(flyway.info().applied())
                .anySatisfy(migration -> assertThat(migration.getVersion().getVersion()).isEqualTo("1"));
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
