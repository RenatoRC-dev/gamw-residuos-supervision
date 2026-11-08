package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api.ContratosVehiculos.*;

@RestController
@RequestMapping("/api/vehiculos")
public class VehiculosController {
    private final GestionarVehiculos vehiculos;
    private final GestionarCatalogosVehiculares catalogos;
    public VehiculosController(GestionarVehiculos vehiculos,GestionarCatalogosVehiculares catalogos) { this.vehiculos = vehiculos; this.catalogos = catalogos; }
    @PostMapping
    public ResponseEntity<VehiculoRespuesta> crear(@Valid @RequestBody GuardarVehiculoSolicitud datos) {
        var vehiculo = vehiculos.crear(datos.aComando());
        return ResponseEntity.created(URI.create("/api/vehiculos/" + vehiculo.id())).body(VehiculoRespuesta.desde(vehiculo));
    }
    @GetMapping
    public List<VehiculoRespuesta> listar() { return vehiculos.listar().stream().map(VehiculoRespuesta::desde).toList(); }
    @GetMapping("/{vehiculoId}")
    public VehiculoRespuesta obtener(@PathVariable UUID vehiculoId) { return VehiculoRespuesta.desde(vehiculos.obtener(vehiculoId)); }
    @PutMapping("/{vehiculoId}")
    public VehiculoRespuesta actualizar(@PathVariable UUID vehiculoId,@Valid @RequestBody GuardarVehiculoSolicitud datos) {
        return VehiculoRespuesta.desde(vehiculos.actualizar(vehiculoId,datos.aComando()));
    }
    @PatchMapping("/{vehiculoId}/estado")
    public VehiculoRespuesta estado(@PathVariable UUID vehiculoId,@Valid @RequestBody ContratosRegistroOperativo.EstadoSolicitud datos) {
        return VehiculoRespuesta.desde(vehiculos.cambiarEstado(vehiculoId,datos.activo()));
    }
    @GetMapping("/marcas")
    public List<MarcaRespuesta> marcas() { return catalogos.marcas().stream().map(MarcaRespuesta::desde).toList(); }
    @PostMapping("/marcas")
    public ResponseEntity<MarcaRespuesta> crearMarca(@Valid @RequestBody CrearMarcaSolicitud datos) {
        return ResponseEntity.created(URI.create("/api/vehiculos/marcas")).body(MarcaRespuesta.desde(catalogos.crearMarca(datos.nombre())));
    }
    @GetMapping("/modelos")
    public List<ModeloRespuesta> modelos(@RequestParam(required=false) UUID marcaVehiculoId) {
        return catalogos.modelos(marcaVehiculoId).stream().map(ModeloRespuesta::desde).toList();
    }
    @PostMapping("/modelos")
    public ResponseEntity<ModeloRespuesta> crearModelo(@Valid @RequestBody CrearModeloSolicitud datos) {
        return ResponseEntity.created(URI.create("/api/vehiculos/modelos")).body(ModeloRespuesta.desde(catalogos.crearModelo(datos.marcaVehiculoId(),datos.nombre())));
    }
    @GetMapping("/tipos")
    public List<TipoRespuesta> tipos() { return catalogos.tipos().stream().map(TipoRespuesta::desde).toList(); }
    @PostMapping("/tipos")
    public ResponseEntity<TipoRespuesta> crearTipo(@Valid @RequestBody CrearTipoSolicitud datos) {
        return ResponseEntity.created(URI.create("/api/vehiculos/tipos")).body(TipoRespuesta.desde(catalogos.crearTipo(datos.codigo(),datos.nombre())));
    }
}
