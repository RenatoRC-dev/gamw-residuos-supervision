package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VehiculoTests {
    private Vehiculo vehiculo(String placa,Integer anio,BigDecimal carga,BigDecimal volumen) {
        return new Vehiculo(UUID.randomUUID(),placa,null,null,anio,carga,volumen,true,Instant.now(),Instant.now());
    }
    @Test
    void placaEsTextoNormalizadoSinConversionNumerica() {
        assertThat(vehiculo("  001-abc  ",null,null,null).placa()).isEqualTo("001-ABC").isInstanceOf(String.class);
    }
    @Test
    void admiteDatosOpcionalesNulos() {
        var v = vehiculo("SINTETICA",null,null,null);
        assertThat(v.modeloVehiculoId()).isNull(); assertThat(v.tipoVehiculoId()).isNull();
        assertThat(v.anio()).isNull(); assertThat(v.capacidadCargaKg()).isNull(); assertThat(v.capacidadVolumenM3()).isNull();
    }
    @Test
    void rechazaPlacaVacia() {
        for (String placa : new String[]{null,""," "}) assertThatThrownBy(() -> vehiculo(placa,null,null,null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void validaAnioSinLimiteDinamico() {
        assertThatThrownBy(() -> vehiculo("SINTETICA",1899,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(vehiculo("SINTETICA",1900,null,null).anio()).isEqualTo(1900);
        assertThat(vehiculo("SINTETICA",2500,null,null).anio()).isEqualTo(2500);
    }
    @Test
    void capacidadesSonDecimalesPositivasConUnidadesDistintas() {
        var v = vehiculo("SINTETICA",2026,new BigDecimal("1050.125"),new BigDecimal("12.345"));
        assertThat(v.capacidadCargaKg()).isEqualByComparingTo("1050.125");
        assertThat(v.capacidadVolumenM3()).isEqualByComparingTo("12.345");
        for (var invalida : new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ONE.negate()}) {
            assertThatThrownBy(() -> vehiculo("SINTETICA",null,invalida,null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> vehiculo("SINTETICA",null,null,invalida)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test
    void catalogosValidanYRecortanNombres() {
        var marca = new MarcaVehiculo(UUID.randomUUID(),"  Marca sintética  ",true);
        var modelo = new ModeloVehiculo(UUID.randomUUID(),marca.id(),"  Modelo sintético  ",true);
        var tipo = new TipoVehiculo(UUID.randomUUID(),"  SINTETICO  ","  Tipo sintético  ",true);
        assertThat(marca.nombre()).isEqualTo("Marca sintética");
        assertThat(modelo.marcaVehiculoId()).isEqualTo(marca.id());
        assertThat(modelo.nombre()).isEqualTo("Modelo sintético");
        assertThat(tipo.codigo()).isEqualTo("SINTETICO");
        assertThatThrownBy(() -> new MarcaVehiculo(UUID.randomUUID()," ",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModeloVehiculo(UUID.randomUUID(),marca.id()," ",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TipoVehiculo(UUID.randomUUID()," ","Prueba",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TipoVehiculo(UUID.randomUUID(),"SINTETICO"," ",true)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void vinculoAdmiteSegmentoAbiertoOFinIgualAlInicio() {
        var inicio = LocalDate.of(2026,1,1);
        var abierto = new VinculoVehiculoEmpresa(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),inicio,null,true,Instant.now(),Instant.now());
        assertThat(abierto.fechaFin()).isNull();
        new VinculoVehiculoEmpresa(abierto.id(),abierto.vehiculoId(),abierto.empresaOperadoraId(),inicio,inicio,false,abierto.creadoEn(),Instant.now());
        assertThatThrownBy(() -> new VinculoVehiculoEmpresa(abierto.id(),abierto.vehiculoId(),abierto.empresaOperadoraId(),inicio,inicio.minusDays(1),true,abierto.creadoEn(),Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
