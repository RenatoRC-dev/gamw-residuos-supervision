package bo.gob.warnes.gamw.residuos.shared.security;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.Usuario;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** H-05: freno técnico de fuerza bruta con filtros reales (límites bajos solo para la prueba). No es bloqueo de cuenta. */
@SpringBootTest(properties = {"spring.config.import=", "server.address=127.0.0.1",
        "gamw.seguridad.jwt.secreto=secreto-sintetico-limite-01234567890123456789012345678901234567890123456789",
        "gamw.seguridad.jwt.emisor=gamw-pruebas-limite", "gamw.seguridad.jwt.duracion=PT15M",
        "gamw.autenticacion.fallos-maximos-por-cuenta-y-origen=2", "gamw.autenticacion.fallos-maximos-por-origen=3",
        "gamw.autenticacion.ventana-segundos=600"})
@AutoConfigureMockMvc
class LimiteIntentosAccesoHttpTests extends ContextoAutenticacionSinBd {
    private static final String CLAVE = "clave-sintetica-limite";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder contrasenas;

    @BeforeEach void preparar() {
        var u = new Usuario(UUID.fromString("11111111-1111-4111-8111-111111111111"), UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "usuario.prueba", contrasenas.encode(CLAVE), true, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
        when(usuarios.porNombreUsuario("usuario.prueba")).thenReturn(Optional.of(u));
    }
    private ResultActions login(String origen, String nombre, String clave) throws Exception {
        return mvc.perform(post("/api/autenticacion/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nombreUsuario", nombre, "contrasena", clave)))
                .with(r -> { r.setRemoteAddr(origen); return r; }));
    }

    @Test void trasAgotarElCupoResponde429UniformeInclusoConClaveCorrectaYSinBloquearOtrosOrigenes() throws Exception {
        login("10.0.0.1", "usuario.prueba", "incorrecta").andExpect(status().isUnauthorized());
        login("10.0.0.1", "usuario.prueba", "incorrecta").andExpect(status().isUnauthorized());
        var existente = login("10.0.0.1", "usuario.prueba", CLAVE).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.tokenAcceso").doesNotExist()).andReturn();
        // Cuenta inexistente: mismo comportamiento y mismo mensaje (sin enumeración).
        login("10.0.0.2", "no.existe", "x").andExpect(status().isUnauthorized());
        login("10.0.0.2", "no.existe", "x").andExpect(status().isUnauthorized());
        var inexistente = login("10.0.0.2", "no.existe", "x").andExpect(status().isTooManyRequests()).andReturn();
        assertThat(json.readTree(inexistente.getResponse().getContentAsString()).get("detail"))
                .isEqualTo(json.readTree(existente.getResponse().getContentAsString()).get("detail"));
        assertThat(Long.parseLong(existente.getResponse().getHeader("Retry-After"))).isBetween(1L, 600L);
        // No es bloqueo de cuenta: desde otro origen la cuenta legítima ingresa.
        login("10.0.0.3", "usuario.prueba", CLAVE).andExpect(status().isOk()).andExpect(jsonPath("$.tokenAcceso").isNotEmpty());
    }

    @Test void unAccesoCorrectoReiniciaElParOrigenCuenta() throws Exception {
        login("10.0.1.1", "usuario.prueba", "incorrecta").andExpect(status().isUnauthorized());
        login("10.0.1.1", "usuario.prueba", CLAVE).andExpect(status().isOk());
        login("10.0.1.1", "usuario.prueba", "incorrecta").andExpect(status().isUnauthorized());
        login("10.0.1.1", "usuario.prueba", CLAVE).andExpect(status().isOk());
    }

    @Test void elOrigenTieneCupoPropioAunqueRoteNombres() throws Exception {
        for (String nombre : new String[]{"a.uno", "a.dos", "a.tres"}) login("10.0.2.1", nombre, "x").andExpect(status().isUnauthorized());
        login("10.0.2.1", "a.cuatro", "x").andExpect(status().isTooManyRequests());
        login("10.0.2.2", "a.cuatro", "x").andExpect(status().isUnauthorized());
    }
}
