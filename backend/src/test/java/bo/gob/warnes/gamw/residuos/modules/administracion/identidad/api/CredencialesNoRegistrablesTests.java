package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.api;

import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion.ServicioAutenticacion;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CredencialesNoRegistrablesTests {
    @Test void tokenEmitidoNoExponeJwtEnRepresentacionParaLogs() {
        var token = new EmisorTokensJwt.TokenEmitido("jwt-sintetico-secreto", Instant.now());
        assertThat(token.toString()).contains("[REDACTADO]").doesNotContain(token.token());
        assertThat(token.token()).isEqualTo("jwt-sintetico-secreto");
    }

    @Test void sesionNoExponeJwtEnRepresentacionParaLogs() {
        var sesion = new ServicioAutenticacion.Sesion("jwt-sintetico-secreto", Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), "usuario-sintetico");
        assertThat(sesion.toString()).contains("[REDACTADO]").doesNotContain(sesion.tokenAcceso());
        assertThat(sesion.tokenAcceso()).isEqualTo("jwt-sintetico-secreto");
    }

    @Test void representacionesParaLogsNoExponenContrasenaNiBearer() {
        assertThat(new AutenticacionController.SolicitudInicioSesion("usuario-sintetico","clave-sintetica-no-log").toString())
                .doesNotContain("clave-sintetica-no-log","usuario-sintetico");
        assertThat(new AutenticacionController.RespuestaInicioSesion("bearer-sintetico-no-log",Instant.now(),UUID.randomUUID(),UUID.randomUUID(),"usuario-sintetico").toString())
                .doesNotContain("bearer-sintetico-no-log");
    }
}
