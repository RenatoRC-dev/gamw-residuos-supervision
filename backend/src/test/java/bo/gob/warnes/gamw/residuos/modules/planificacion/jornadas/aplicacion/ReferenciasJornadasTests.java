package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReferenciasJornadasTests {
    private final RepositorioRegistroOperativo registro = mock(RepositorioRegistroOperativo.class);
    private final RepositorioVehiculos vehiculos = mock(RepositorioVehiculos.class);
    private final RepositorioRuta rutas = mock(RepositorioRuta.class);
    private final ReferenciasJornadas referencias = new ReferenciasJornadas(registro,vehiculos,rutas);
    private final UUID empresa = UUID.randomUUID(), periodo = UUID.randomUUID(), version = UUID.randomUUID(), vv = UUID.randomUUID(), vp = UUID.randomUUID(), v = UUID.randomUUID(), p = UUID.randomUUID();
    private final LocalDate fecha = LocalDate.of(2026,5,10);
    private final Instant inicio = fecha.atTime(23,30).atZone(ZoneId.of("America/La_Paz")).toInstant();
    @BeforeEach void preparar() {
        when(registro.periodoPorId(periodo)).thenReturn(Optional.of(new PeriodoServicio(periodo,empresa,fecha,fecha,true,inicio,inicio)));
        when(registro.empresa(empresa,false)).thenReturn(Optional.of(new EmpresaOperadora(empresa,null,"Empresa sintética",null,true,inicio,inicio)));
        when(rutas.versionPorId(version)).thenReturn(Optional.of(new RutaVersion(version,UUID.randomUUID(),1,null,inicio,inicio,null,inicio)));
        when(vehiculos.vinculoPorId(vv)).thenReturn(Optional.of(new VinculoVehiculoEmpresa(vv,v,empresa,fecha,null,true,inicio,inicio)));
        when(registro.vinculoPorId(vp)).thenReturn(Optional.of(new VinculoPersonaEmpresa(vp,p,empresa,fecha,null,true,inicio,inicio)));
        when(vehiculos.vehiculo(v,false)).thenReturn(Optional.of(new Vehiculo(v,"SINTETICA",null,null,null,null,null,true,inicio,inicio)));
        when(registro.persona(p,false)).thenReturn(Optional.of(new PersonaNatural(p,"SINTETICA","Prueba",null,null,null,true,inicio,inicio)));
    }
    @Test void periodoYVersionAceptanLimitesInclusivosEnZonaBoliviana() {
        referencias.validarProgramacion(periodo,version,inicio);
        assertThat(referencias.fechaServicio(inicio)).isEqualTo(fecha);
    }
    @Test void rechazaPeriodoYVersionFueraDeVigencia() {
        assertThatThrownBy(() -> referencias.validarProgramacion(periodo,version,inicio.plusSeconds(86400))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("periodo");
        assertThatThrownBy(() -> referencias.validarProgramacion(periodo,version,inicio.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("versión");
    }
    @Test void ambosRecursosDebenPertenecerAEmpresaDelPeriodo() {
        referencias.validarRecursos(periodo,inicio,vv,vp);
        when(vehiculos.vinculoPorId(vv)).thenReturn(Optional.of(new VinculoVehiculoEmpresa(vv,v,UUID.randomUUID(),fecha,null,true,inicio,inicio)));
        assertThatThrownBy(() -> referencias.validarRecursos(periodo,inicio,vv,vp)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empresa");
    }
    @Test void noSeExigeFuncionConductor() {
        referencias.validarRecursos(periodo,inicio,vv,vp);
        verify(registro,never()).funcionesDelVinculo(any());
    }
}
