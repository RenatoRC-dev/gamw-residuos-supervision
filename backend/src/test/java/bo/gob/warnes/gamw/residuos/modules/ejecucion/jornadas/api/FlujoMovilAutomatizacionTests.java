package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Preflight del arnés, sin dispositivo, instalación ni conexión a una base de datos. */
class FlujoMovilAutomatizacionTests {
    @Test void rechazaApkDeLaAplicacionNormalAntesDeInstalar() {
        assertThatIllegalStateException().isThrownBy(() -> FlujoMovilIT.comprobarApkIntegracion(
                "package: name='bo.gob.warnes.gamw.gamw_mobile' versionCode='1'"));
    }
    @Test void aceptaSoloElIdentificadorAisladoExacto() {
        assertThatCode(() -> FlujoMovilIT.comprobarApkIntegracion(
                "package: name='bo.gob.warnes.gamw.gamw_mobile.it' versionCode='1'")).doesNotThrowAnyException();
        assertThatIllegalStateException().isThrownBy(() -> FlujoMovilIT.comprobarApkIntegracion(
                "package: name='bo.gob.warnes.gamw.gamw_mobile.it.otro' versionCode='1'"));
    }
    @Test void ausenciaDeMetadatosFallaCerrado() {
        assertThatIllegalStateException().isThrownBy(() -> FlujoMovilIT.comprobarApkIntegracion("salida sin paquete"));
    }
}
