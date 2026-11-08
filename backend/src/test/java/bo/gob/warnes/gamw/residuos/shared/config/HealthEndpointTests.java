package bo.gob.warnes.gamw.residuos.shared.config;

import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.RepositorioRuta;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("smoke")
class HealthEndpointTests {
    @MockitoBean
    private RepositorioRuta rutas;
    @MockitoBean
    private bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.RepositorioRegistroOperativo registro;
    @MockitoBean
    private bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.RepositorioVehiculos vehiculos;
    @Autowired
    private MockMvc mvc;

    @Test
    void exposesOnlyMinimalPublicLiveness() throws Exception {
        mvc.perform(get("/api/technical/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void deniesUnconfiguredPrivatePaths() throws Exception {
        mvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesBusinessEndpointsWithoutDevelopmentProfile() throws Exception {
        for (String ruta : new String[]{"/api/rutas", "/api/personas", "/api/empresas-operadoras",
                "/api/empresas-operadoras/funciones-operativas", "/api/vehiculos", "/api/vehiculos/marcas", "/api/vehiculos/modelos", "/api/vehiculos/tipos"}) {
            mvc.perform(get(ruta).header("X-GAMW-Development", "gamw-desarrollo"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void retainsCsrfProtectionForUnsafeRequests() throws Exception {
        mvc.perform(post("/api/technical/health"))
                .andExpect(status().isForbidden());
    }
}
