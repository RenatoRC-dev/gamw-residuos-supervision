package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioVehiculos {
    void insertarMarca(MarcaVehiculo marca);
    List<MarcaVehiculo> marcas();
    Optional<MarcaVehiculo> marca(UUID id);
    void insertarModelo(ModeloVehiculo modelo);
    List<ModeloVehiculo> modelos(UUID marcaVehiculoId);
    Optional<ModeloVehiculo> modelo(UUID id);
    void insertarTipo(TipoVehiculo tipo);
    List<TipoVehiculo> tipos();
    Optional<TipoVehiculo> tipo(UUID id);
    void insertarVehiculo(Vehiculo vehiculo);
    void actualizarVehiculo(Vehiculo vehiculo);
    Optional<Vehiculo> vehiculo(UUID id, boolean bloquear);
    List<Vehiculo> vehiculos();
    void insertarVinculo(VinculoVehiculoEmpresa vinculo);
    void cerrarVinculo(VinculoVehiculoEmpresa vinculo);
    Optional<VinculoVehiculoEmpresa> vinculo(UUID empresaId, UUID id);
    Optional<VinculoVehiculoEmpresa> vinculoPorId(UUID id);
    List<VinculoVehiculoEmpresa> vinculos(UUID empresaId);
}
