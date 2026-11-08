package bo.gob.warnes.gamw.residuos.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SeguridadDesarrolloLocalTests {
    @Test
    void rechazaEscuchaEnTodasLasInterfaces() {
        assertThatThrownBy(() -> new SeguridadDesarrolloLocal().filtroDesarrolloLocal(mock(HttpSecurity.class),"0.0.0.0","http://127.0.0.1:5173"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("loopback");
    }
    @Test
    void rechazaOrigenWebNoLocalORutasDentroDelOrigen() {
        for (String origen : new String[]{"https://externo.example","http://127.0.0.1:5173/ruta","/relativo"}) {
            assertThatThrownBy(() -> new SeguridadDesarrolloLocal().filtroDesarrolloLocal(mock(HttpSecurity.class),"127.0.0.1",origen))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("origen local");
        }
    }
}
