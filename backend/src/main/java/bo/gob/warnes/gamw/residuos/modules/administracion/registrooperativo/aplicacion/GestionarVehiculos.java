package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-06 / RF-04. La identidad vehicular no contiene empresa ni conductor. */
@Service
@Transactional(readOnly = true)
public class GestionarVehiculos {
    private final RepositorioVehiculos repositorio;
    private final GestionarCatalogosVehiculares catalogos;
    public GestionarVehiculos(RepositorioVehiculos repositorio,GestionarCatalogosVehiculares catalogos) {
        this.repositorio = repositorio; this.catalogos = catalogos;
    }
    public List<Vehiculo> listar() { return repositorio.vehiculos(); }
    public Vehiculo obtener(UUID id) { return buscar(id,false); }
    private Vehiculo buscar(UUID id,boolean bloquear) {
        return repositorio.vehiculo(id,bloquear).orElseThrow(() -> new RegistroNoEncontrado("Vehículo no encontrado."));
    }
    @Transactional
    public Vehiculo crear(DatosVehiculo datos) {
        catalogos.validarReferencias(datos.modeloVehiculoId(),datos.tipoVehiculoId());
        var ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var vehiculo = datos.registro(UUID.randomUUID(),true,ahora,ahora);
        repositorio.insertarVehiculo(vehiculo); return vehiculo;
    }
    @Transactional
    public Vehiculo actualizar(UUID id,DatosVehiculo datos) {
        var anterior = buscar(id,true);
        catalogos.validarReferencias(datos.modeloVehiculoId(),datos.tipoVehiculoId());
        var vehiculo = datos.registro(id,anterior.activo(),anterior.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.actualizarVehiculo(vehiculo); return vehiculo;
    }
    @Transactional
    public Vehiculo cambiarEstado(UUID id,boolean activo) {
        var v = buscar(id,true);
        var vehiculo = new Vehiculo(id,v.placa(),v.modeloVehiculoId(),v.tipoVehiculoId(),v.anio(),v.capacidadCargaKg(),v.capacidadVolumenM3(),
                activo,v.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.actualizarVehiculo(vehiculo); return vehiculo;
    }
    public record DatosVehiculo(String placa,UUID modeloVehiculoId,UUID tipoVehiculoId,Integer anio,BigDecimal capacidadCargaKg,BigDecimal capacidadVolumenM3) {
        Vehiculo registro(UUID id,boolean activo,Instant creadoEn,Instant actualizadoEn) {
            return new Vehiculo(id,placa,modeloVehiculoId,tipoVehiculoId,anio,capacidadCargaKg,capacidadVolumenM3,activo,creadoEn,actualizadoEn);
        }
    }
}
