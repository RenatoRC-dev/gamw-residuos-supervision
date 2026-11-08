package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-06 / RF-04. Catálogos de apoyo, sin precarga de valores institucionales no confirmados. */
@Service
@Transactional(readOnly = true)
public class GestionarCatalogosVehiculares {
    private final RepositorioVehiculos repositorio;
    public GestionarCatalogosVehiculares(RepositorioVehiculos repositorio) { this.repositorio = repositorio; }
    public List<MarcaVehiculo> marcas() { return repositorio.marcas(); }
    public List<ModeloVehiculo> modelos(UUID marcaId) { return repositorio.modelos(marcaId); }
    public List<TipoVehiculo> tipos() { return repositorio.tipos(); }
    public MarcaVehiculo marcaActiva(UUID id) {
        var marca = repositorio.marca(id).orElseThrow(() -> new RegistroNoEncontrado("Marca de vehículo no encontrada."));
        if (!marca.activo()) throw new IllegalArgumentException("La marca de vehículo está inactiva.");
        return marca;
    }
    public void validarReferencias(UUID modeloId,UUID tipoId) {
        if (modeloId != null) {
            var modelo = repositorio.modelo(modeloId).orElseThrow(() -> new RegistroNoEncontrado("Modelo de vehículo no encontrado."));
            if (!modelo.activo()) throw new IllegalArgumentException("El modelo de vehículo está inactivo.");
            marcaActiva(modelo.marcaVehiculoId());
        }
        if (tipoId != null) {
            var tipo = repositorio.tipo(tipoId).orElseThrow(() -> new RegistroNoEncontrado("Tipo de vehículo no encontrado."));
            if (!tipo.activo()) throw new IllegalArgumentException("El tipo de vehículo está inactivo.");
        }
    }
    @Transactional
    public MarcaVehiculo crearMarca(String nombre) {
        var marca = new MarcaVehiculo(UUID.randomUUID(),nombre,true);
        repositorio.insertarMarca(marca); return marca;
    }
    @Transactional
    public ModeloVehiculo crearModelo(UUID marcaId,String nombre) {
        marcaActiva(marcaId);
        var modelo = new ModeloVehiculo(UUID.randomUUID(),marcaId,nombre,true);
        repositorio.insertarModelo(modelo); return modelo;
    }
    @Transactional
    public TipoVehiculo crearTipo(String codigo,String nombre) {
        var tipo = new TipoVehiculo(UUID.randomUUID(),codigo,nombre,true);
        repositorio.insertarTipo(tipo); return tipo;
    }
}
