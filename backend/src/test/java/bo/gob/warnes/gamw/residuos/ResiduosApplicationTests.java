package bo.gob.warnes.gamw.residuos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("smoke")
class ResiduosApplicationTests {
    @Autowired
    private ApplicationContext context;

    @Test
    void startsApplicationWithoutRequiringDatabaseForSmokeTests() {
        assertThat(context.getBean(ResiduosApplication.class)).isNotNull();
        assertThat(context.containsBean("dataSource")).isFalse();
    }
}
