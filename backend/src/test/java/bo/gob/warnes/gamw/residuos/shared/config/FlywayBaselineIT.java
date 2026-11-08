package bo.gob.warnes.gamw.residuos.shared.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in: uses only the dedicated PostgreSQL database configured by DB_* variables. */
@SpringBootTest
class FlywayBaselineIT {
    @Autowired
    private Flyway flyway;

    @Test
    void appliesTechnicalBaselineAndCanMigrateAgainWithoutChanges() {
        assertThat(flyway.info().applied())
                .anySatisfy(migration -> assertThat(migration.getVersion().getVersion()).isEqualTo("1"));
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
