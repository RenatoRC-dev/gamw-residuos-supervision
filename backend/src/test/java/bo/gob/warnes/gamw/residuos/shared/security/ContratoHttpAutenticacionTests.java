package bo.gob.warnes.gamw.residuos.shared.security;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.Usuario;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A5-B2: aplicación y filtros reales; únicamente los repositorios son dobles. */
@SpringBootTest(properties = {"spring.config.import=", "server.address=127.0.0.1",
        "gamw.seguridad.jwt.secreto=secreto-sintetico-http-01234567890123456789012345678901234567890123456789",
        "gamw.seguridad.jwt.emisor=gamw-pruebas-http", "gamw.seguridad.jwt.duracion=PT15M"})
@AutoConfigureMockMvc
class ContratoHttpAutenticacionTests extends ContextoAutenticacionSinBd {
    private static final String SECRETO = "secreto-sintetico-http-01234567890123456789012345678901234567890123456789";
    private static final String CLAVE = "clave-sintetica-http";
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PERSONA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder contrasenas;
    @Autowired EmisorTokensJwt emisor;
    @Autowired ConfigurableApplicationContext contexto;
    private Usuario usuario;

    @BeforeEach void preparar() {
        usuario = usuario(true);
        when(usuarios.porNombreUsuario("usuario.prueba")).thenReturn(Optional.of(usuario));
        when(usuarios.porId(ID)).thenReturn(Optional.of(usuario));
    }

    private Usuario usuario(boolean activo) {
        return new Usuario(ID, PERSONA, "usuario.prueba", contrasenas.encode(CLAVE), activo,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private String token() { return emisor.emitir(ID, PERSONA, "usuario.prueba").token(); }

    @Test void arrancaSinBdNiUsuariosAutomaticosConStackReal() {
        assertThat(contexto.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
        assertThat(contexto.getBeansOfType(org.flywaydb.core.Flyway.class)).isEmpty();
        assertThat(contexto.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(ConditionEvaluationReport.get(contexto.getBeanFactory()).getExclusions())
                .contains("org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration");
        assertThat(contexto.getBean(ConfiguracionJwt.class)).isNotNull();
        assertThat(contexto.getBean(ConvertidorJwtUsuario.class)).isNotNull();
        assertThat(contexto.getBean(org.springframework.security.oauth2.jwt.JwtDecoder.class)).isNotNull();
        assertThat(contexto.getBean(org.springframework.security.oauth2.jwt.JwtEncoder.class)).isNotNull();
    }

    @Test void loginYActualRecorrenFiltrosRealesSinExponerCredenciales() throws Exception {
        var respuesta = mvc.perform(post("/api/autenticacion/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("nombreUsuario", "usuario.prueba", "contrasena", CLAVE))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(ID.toString()))
                .andExpect(jsonPath("$.personaNaturalId").value(PERSONA.toString()))
                .andExpect(jsonPath("$.nombreUsuario").value("usuario.prueba"))
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.contrasena").doesNotExist()).andReturn();
        var cuerpo = json.readTree(respuesta.getResponse().getContentAsString());
        assertThat(cuerpo.size()).isEqualTo(5);
        assertThat(Instant.parse(cuerpo.get("expiraEn").asText())).isAfter(Instant.now());
        assertThat(respuesta.getResponse().getContentAsString()).doesNotContain(CLAVE, usuario.contrasenaHash(), SECRETO);
        assertThat(respuesta.getRequest().getSession(false)).isNull();
        mvc.perform(get("/api/autenticacion/actual").header("Authorization", "Bearer " + cuerpo.get("tokenAcceso").asText()))
                .andExpect(status().isOk()).andExpect(content().json(json.writeValueAsString(java.util.Map.of(
                        "usuarioId", ID, "personaNaturalId", PERSONA, "nombreUsuario", "usuario.prueba"))))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist());
        assertThat(json.readTree(mvc.perform(get("/api/autenticacion/actual").header("Authorization", "Bearer " + token()))
                .andReturn().getResponse().getContentAsString()).size()).isEqualTo(3);
    }

    @Test void credencialesInvalidasTienenElMismoErrorGenerico() throws Exception {
        String incorrecta = falloLogin("usuario.prueba", "incorrecta");
        String inexistente = falloLogin("desconocido", CLAVE);
        when(usuarios.porNombreUsuario("usuario.prueba")).thenReturn(Optional.of(usuario(false)));
        String inactivo = falloLogin("usuario.prueba", CLAVE);
        assertThat(json.readTree(incorrecta).get("detail").asText()).isEqualTo("Credenciales inválidas.");
        assertThat(json.readTree(inexistente).get("detail")).isEqualTo(json.readTree(incorrecta).get("detail"));
        assertThat(json.readTree(inactivo).get("detail")).isEqualTo(json.readTree(incorrecta).get("detail"));
    }

    private String falloLogin(String nombre, String clave) throws Exception {
        var respuesta = mvc.perform(post("/api/autenticacion/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("nombreUsuario", nombre, "contrasena", clave))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.tokenAcceso").doesNotExist()).andReturn();
        assertThat(respuesta.getResponse().getContentAsString()).doesNotContain(CLAVE, usuario.contrasenaHash(), SECRETO, "stackTrace");
        return respuesta.getResponse().getContentAsString();
    }

    @Test void rechazaSolicitudesDeLoginInvalidasSinConsultarUsuarios() throws Exception {
        clearInvocations(usuarios);
        for (String solicitud : new String[]{"{}", "{\"nombreUsuario\":\"usuario.prueba\"}",
                "{\"nombreUsuario\":\" \",\"contrasena\":\"x\"}",
                "{\"nombreUsuario\":\"usuario.prueba\",\"contrasena\":\" \"}", "{malformado",
                "{\"nombreUsuario\":\"usuario.prueba\",\"contrasena\":\"x\",\"usuarioId\":\"inventado\"}"}) {
            var resultado = mvc.perform(post("/api/autenticacion/login").contentType(MediaType.APPLICATION_JSON).content(solicitud))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.tokenAcceso").doesNotExist()).andReturn();
            assertThat(resultado.getResponse().getContentAsString()).doesNotContain(SECRETO, usuario.contrasenaHash(), "stackTrace");
        }
        verifyNoInteractions(usuarios);
    }

    @Test void rechazaJwtSinExpYVariantesCriptograficasOTemporalesInvalidas() throws Exception {
        Instant ahora = Instant.now();
        String[] invalidos = {
                firmado(ID.toString(), null, null, "gamw-pruebas-http", SECRETO, JWSAlgorithm.HS256),
                firmado(ID.toString(), ahora.minusSeconds(300), null, "gamw-pruebas-http", SECRETO, JWSAlgorithm.HS256),
                firmado(ID.toString(), ahora.plusSeconds(900), ahora.plusSeconds(300), "gamw-pruebas-http", SECRETO, JWSAlgorithm.HS256),
                firmado(ID.toString(), ahora.plusSeconds(900), null, "emisor-incorrecto", SECRETO, JWSAlgorithm.HS256),
                firmado(ID.toString(), ahora.plusSeconds(900), null, "gamw-pruebas-http", "otra-clave-sintetica-01234567890123456789012345678901234567890123456789", JWSAlgorithm.HS256),
                firmado(ID.toString(), ahora.plusSeconds(900), null, "gamw-pruebas-http", SECRETO, JWSAlgorithm.HS512),
                "token.malformado", "eyJhbGciOiJub25lIn0.eyJzdWIiOiJpbnZlbnRhZG8ifQ."};
        clearInvocations(usuarios);
        for (String invalido : invalidos) {
            mvc.perform(get("/api/autenticacion/actual").header("Authorization", "Bearer " + invalido))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(usuarios);
    }

    @Test void rechazaSujetoInvalidoUsuarioInexistenteOInactivo() throws Exception {
        for (String sujeto : new String[]{null, "no-uuid", UUID.randomUUID().toString()}) {
            mvc.perform(get("/api/autenticacion/actual").header("Authorization", "Bearer " + firmado(sujeto,
                    Instant.now().plusSeconds(900), null, "gamw-pruebas-http", SECRETO, JWSAlgorithm.HS256)))
                    .andExpect(status().isUnauthorized());
        }
        String vigente = token();
        when(usuarios.porId(ID)).thenReturn(Optional.of(usuario(false)));
        mvc.perform(get("/api/autenticacion/actual").header("Authorization", "Bearer " + vigente))
                .andExpect(status().isUnauthorized());
    }

    @Test void identidadPersistidaPrevaleceYSeRevalidaEnCadaPeticion() throws Exception {
        String firmado = firmado(ID.toString(), Instant.now().plusSeconds(900), null, "gamw-pruebas-http", SECRETO, JWSAlgorithm.HS256);
        mvc.perform(get("/api/autenticacion/actual").header("Authorization", "Bearer " + firmado)
                .header("X-Persona-Id", UUID.randomUUID()).header("X-Usuario-Id", UUID.randomUUID()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personaNaturalId").value(PERSONA.toString()))
                .andExpect(jsonPath("$.usuarioId").value(ID.toString())).andExpect(jsonPath("$.nombreUsuario").value("usuario.prueba"));
        when(usuarios.porId(ID)).thenReturn(Optional.of(usuario(false)));
        mvc.perform(get("/api/autenticacion/actual").header("Authorization", "Bearer " + firmado))
                .andExpect(status().isUnauthorized());
    }

    @Test void cabecerasTecnicasNoAutenticanYNegocioSigueCerrado() throws Exception {
        mvc.perform(get("/api/autenticacion/actual").header("X-GAMW-Development", "gamw-desarrollo")
                .header("X-Persona-Id", PERSONA).header("X-Usuario-Id", ID)).andExpect(status().isUnauthorized());
        String valido = token();
        for (String ruta : new String[]{"/api/rutas", "/api/personas", "/api/empresas-operadoras", "/api/vehiculos",
                "/api/jornadas", "/api/periodos-servicio", "/api/jornadas/" + ID + "/ejecucion"}) {
            mvc.perform(get(ruta).header("Authorization", "Bearer " + valido)).andExpect(status().isForbidden());
            mvc.perform(get(ruta).header("X-GAMW-Development", "gamw-desarrollo")).andExpect(status().isUnauthorized());
        }
        // Mis jornadas es la excepción contextual nueva; no habilita el listado general.
        mvc.perform(get("/api/mis-jornadas").header("Authorization", "Bearer " + valido))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/mis-jornadas").header("X-GAMW-Development", "gamw-desarrollo"))
                .andExpect(status().isUnauthorized());
        verify(jornadas).asignadasAPersona(PERSONA);
        // C1 verifica la autorización contextual de INICIO/FIN en EjecucionAutenticadaIT.
        for (String ruta : new String[]{"/api/personas"}) {
            mvc.perform(post(ruta).with(csrf()).header("Authorization", "Bearer " + valido))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void mantieneHealthPublicoSinSesionNiFormLoginNiBasic() throws Exception {
        mvc.perform(get("/api/technical/health")).andExpect(status().isOk());
        mvc.perform(get("/api/autenticacion/actual")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/autenticacion/actual").header("Authorization", "Basic dXN1YXJpbzpjbGF2ZQ=="))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/login").param("username", "usuario.prueba").param("password", CLAVE).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/technical/health")).andExpect(status().isForbidden());
        var actual = mvc.perform(get("/api/autenticacion/actual").header("Authorization", "Bearer " + token())).andReturn();
        assertThat(actual.getRequest().getSession(false)).isNull();
    }

    private String firmado(String sujeto, Instant expira, Instant desde, String emisor, String secreto, JWSAlgorithm algoritmo) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(emisor).subject(sujeto).issueTime(Date.from(Instant.now().minusSeconds(600)))
                .claim("personaNaturalId", UUID.randomUUID().toString()).claim("nombreUsuario", "no-confiable");
        if (expira != null) claims.expirationTime(Date.from(expira));
        if (desde != null) claims.notBeforeTime(Date.from(desde));
        var jwt = new SignedJWT(new JWSHeader.Builder(algoritmo).type(JOSEObjectType.JWT).build(), claims.build());
        jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
