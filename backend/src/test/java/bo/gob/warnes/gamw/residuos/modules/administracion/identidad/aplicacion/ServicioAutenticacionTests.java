package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.RepositorioUsuarios;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.Usuario;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Autenticación mínima contra el Usuario persistido. No requiere PostgreSQL: el repositorio
 * se sustituye en su frontera. El hash nunca forma parte del resultado.
 */
class ServicioAutenticacionTests {
    private static final Instant AHORA = Instant.parse("2026-11-08T10:00:00Z");
    private static final String CLAVE_CORRECTA = "clave-sintetica-de-prueba";
    private static final String CLAVE_INCORRECTA = "clave-sintetica-incorrecta";
    private static final String NOMBRE_EXACTO = "Conductor.Warnes";

    private final RepositorioUsuarios usuarios = mock(RepositorioUsuarios.class);
    private final EmisorTokensJwt emisor = mock(EmisorTokensJwt.class);
    private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder();
    private final ServicioAutenticacion servicio = new ServicioAutenticacion(usuarios, codificador, emisor);

    private Usuario usuario(boolean activo) {
        return new Usuario(UUID.fromString("11111111-1111-4111-8111-111111111111"),
                UUID.fromString("22222222-2222-4222-8222-222222222222"), NOMBRE_EXACTO,
                codificador.encode(CLAVE_CORRECTA), activo, AHORA, AHORA);
    }

    @Test
    void autenticaCredencialesVigentesYDevuelveIdentidadPersistida() {
        Usuario persistido = usuario(true);
        when(usuarios.porNombreUsuario(NOMBRE_EXACTO)).thenReturn(Optional.of(persistido));
        Instant expiraEn = AHORA.plusSeconds(1800);
        when(emisor.emitir(persistido.id(), persistido.personaNaturalId(), persistido.nombreUsuario()))
                .thenReturn(new EmisorTokensJwt.TokenEmitido("token-firmado-de-prueba", expiraEn));

        ServicioAutenticacion.Sesion sesion = servicio.autenticar(NOMBRE_EXACTO, CLAVE_CORRECTA);

        assertThat(sesion.tokenAcceso()).isEqualTo("token-firmado-de-prueba");
        assertThat(sesion.expiraEn()).isEqualTo(expiraEn);
        assertThat(sesion.usuarioId()).isEqualTo(persistido.id());
        assertThat(sesion.personaNaturalId()).isEqualTo(persistido.personaNaturalId());
        assertThat(sesion.nombreUsuario()).isEqualTo(persistido.nombreUsuario());
    }

    @Test
    void buscaElNombreDeUsuarioExactamenteSinNormalizarMayusculas() {
        when(usuarios.porNombreUsuario(NOMBRE_EXACTO)).thenReturn(Optional.of(usuario(true)));
        when(emisor.emitir(any(), any(), any()))
                .thenReturn(new EmisorTokensJwt.TokenEmitido("token-firmado-de-prueba", AHORA));

        servicio.autenticar(NOMBRE_EXACTO, CLAVE_CORRECTA);

        verify(usuarios).porNombreUsuario(NOMBRE_EXACTO);
        verify(usuarios, never()).porNombreUsuario(NOMBRE_EXACTO.toLowerCase());

        assertThatThrownBy(() -> servicio.autenticar(NOMBRE_EXACTO.toLowerCase(), CLAVE_CORRECTA))
                .isInstanceOf(CredencialesInvalidas.class);
    }

    @Test
    void rechazaContrasenaIncorrecta() {
        when(usuarios.porNombreUsuario(NOMBRE_EXACTO)).thenReturn(Optional.of(usuario(true)));

        assertThatThrownBy(() -> servicio.autenticar(NOMBRE_EXACTO, CLAVE_INCORRECTA))
                .isInstanceOf(CredencialesInvalidas.class);
        verifyNoInteractions(emisor);
    }

    @Test
    void rechazaUsuarioInexistenteConElMismoError() {
        when(usuarios.porNombreUsuario(NOMBRE_EXACTO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.autenticar(NOMBRE_EXACTO, CLAVE_CORRECTA))
                .isInstanceOf(CredencialesInvalidas.class);
        verifyNoInteractions(emisor);
    }

    @Test
    void rechazaUsuarioInactivoConElMismoError() {
        when(usuarios.porNombreUsuario(NOMBRE_EXACTO)).thenReturn(Optional.of(usuario(false)));

        assertThatThrownBy(() -> servicio.autenticar(NOMBRE_EXACTO, CLAVE_CORRECTA))
                .isInstanceOf(CredencialesInvalidas.class);
        verifyNoInteractions(emisor);
    }

    @Test
    void rechazaCredencialesVaciasSinConsultarElRepositorio() {
        assertThatThrownBy(() -> servicio.autenticar(" ", CLAVE_CORRECTA))
                .isInstanceOf(CredencialesInvalidas.class);
        assertThatThrownBy(() -> servicio.autenticar(NOMBRE_EXACTO, null))
                .isInstanceOf(CredencialesInvalidas.class);
        verifyNoInteractions(usuarios);
    }

    @Test
    void laSesionNuncaExponeElHashDeLaContrasena() {
        Usuario persistido = usuario(true);
        when(usuarios.porNombreUsuario(NOMBRE_EXACTO)).thenReturn(Optional.of(persistido));
        when(emisor.emitir(any(), any(), any()))
                .thenReturn(new EmisorTokensJwt.TokenEmitido("token-firmado-de-prueba", AHORA));

        ServicioAutenticacion.Sesion sesion = servicio.autenticar(NOMBRE_EXACTO, CLAVE_CORRECTA);

        assertThat(Arrays.stream(ServicioAutenticacion.Sesion.class.getRecordComponents())
                .map(RecordComponent::getName)).doesNotContain("contrasenaHash");
        assertThat(sesion.toString()).doesNotContain("$2").doesNotContain(CLAVE_CORRECTA);
    }
}
