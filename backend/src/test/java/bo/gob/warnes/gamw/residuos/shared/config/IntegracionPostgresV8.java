package bo.gob.warnes.gamw.residuos.shared.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Configuración de test sin fallback a DB_* ni importación de .env. */
public abstract class IntegracionPostgresV8 {
    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry propiedades) {
        var postgres = PostgresV8Aislado.obtener();
        propiedades.add("spring.config.import", () -> "");
        propiedades.add("spring.datasource.url", postgres::urlLimpia);
        propiedades.add("spring.datasource.username", postgres::usuario);
        propiedades.add("spring.datasource.password", postgres::clave);
        propiedades.add("gamw.seguridad.jwt.secreto", () -> "jwt-sintetico-integracion-v8-01234567890123456789");
    }
}
