package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RegistroOperativoTests {
    @Test
    void ciEsTextoSinReglasNumericasNiApellidosObligatorios() {
        for (String ci : new String[]{"12345678","12345678-1A"}) {
            var persona = new PersonaNatural(UUID.randomUUID(),ci,"Prueba",null,null,null,true,Instant.now(),Instant.now());
            assertThat(persona.ci()).isEqualTo(ci);
            assertThat(persona.apellidoPaterno()).isNull();
            assertThat(persona.apellidoMaterno()).isNull();
        }
    }
    @Test
    void validaDatosRequeridosSinInventarFormatoDeNit() {
        assertThatThrownBy(() -> new EmpresaOperadora(UUID.randomUUID(),null," ",null,true,Instant.now(),Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmpresaOperadora(UUID.randomUUID()," ","Prueba",null,true,Instant.now(),Instant.now())).isInstanceOf(IllegalArgumentException.class);
        var empresa = new EmpresaOperadora(UUID.randomUUID(),"000123-A","Prueba",null,true,Instant.now(),Instant.now());
        assertThat(empresa.nit()).isEqualTo("000123-A");
    }
    @Test
    void fechasPermitenMismoDiaYVinculoSinCierre() {
        var inicio = LocalDate.of(2026,1,1);
        ReglasRegistroOperativo.fechas(inicio,inicio,true);
        ReglasRegistroOperativo.fechas(inicio,null,false);
        assertThatThrownBy(() -> ReglasRegistroOperativo.fechas(inicio,inicio.minusDays(1),false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglasRegistroOperativo.fechas(inicio,null,true)).isInstanceOf(IllegalArgumentException.class);
    }
}
