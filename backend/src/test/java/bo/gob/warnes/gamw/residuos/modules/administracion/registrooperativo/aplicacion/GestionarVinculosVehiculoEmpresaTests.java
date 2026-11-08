package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestionarVinculosVehiculoEmpresaTests {
    @Test
    void cierrePreservaIdentidadYNoActualizaVehiculo() {
        var repositorio = mock(RepositorioVehiculos.class);
        var vehiculos = mock(GestionarVehiculos.class);
        var empresas = mock(GestionarEmpresasOperadoras.class);
        var anterior = new VinculoVehiculoEmpresa(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),LocalDate.of(2026,1,1),null,true,Instant.now(),Instant.now());
        when(repositorio.vinculo(anterior.empresaOperadoraId(),anterior.id())).thenReturn(Optional.of(anterior));
        var casoUso = new GestionarVinculosVehiculoEmpresa(repositorio,vehiculos,empresas);
        var cerrado = casoUso.cerrar(anterior.empresaOperadoraId(),anterior.id(),LocalDate.of(2026,5,31),false);
        assertThat(cerrado.id()).isEqualTo(anterior.id());
        assertThat(cerrado.vehiculoId()).isEqualTo(anterior.vehiculoId());
        assertThat(cerrado.empresaOperadoraId()).isEqualTo(anterior.empresaOperadoraId());
        assertThat(cerrado.fechaInicio()).isEqualTo(anterior.fechaInicio());
        assertThat(cerrado.creadoEn()).isEqualTo(anterior.creadoEn());
        assertThat(cerrado.activo()).isFalse();
        verify(repositorio).cerrarVinculo(cerrado);
        verify(repositorio,never()).insertarVinculo(any());
        verifyNoInteractions(vehiculos);
    }
}
