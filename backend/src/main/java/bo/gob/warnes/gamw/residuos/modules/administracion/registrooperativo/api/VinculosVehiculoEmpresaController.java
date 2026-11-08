package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.GestionarVinculosVehiculoEmpresa;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api.ContratosVehiculos.*;

@RestController
@RequestMapping("/api/empresas-operadoras/{empresaId}/vehiculos")
public class VinculosVehiculoEmpresaController {
    private final GestionarVinculosVehiculoEmpresa vinculos;
    public VinculosVehiculoEmpresaController(GestionarVinculosVehiculoEmpresa vinculos) { this.vinculos = vinculos; }
    @PostMapping
    public ResponseEntity<VinculoVehiculoRespuesta> crear(@PathVariable UUID empresaId,@Valid @RequestBody CrearVinculoVehiculoSolicitud datos) {
        var vinculo = vinculos.crear(empresaId,datos.vehiculoId(),datos.fechaInicio(),datos.fechaFin(),datos.activo());
        return ResponseEntity.created(URI.create("/api/empresas-operadoras/" + empresaId + "/vehiculos")).body(VinculoVehiculoRespuesta.desde(vinculo));
    }
    @GetMapping
    public List<VinculoVehiculoRespuesta> listar(@PathVariable UUID empresaId) { return vinculos.listar(empresaId).stream().map(VinculoVehiculoRespuesta::desde).toList(); }
    @PutMapping("/{vinculoId}")
    public VinculoVehiculoRespuesta cerrar(@PathVariable UUID empresaId,@PathVariable UUID vinculoId,@Valid @RequestBody CerrarVinculoVehiculoSolicitud datos) {
        return VinculoVehiculoRespuesta.desde(vinculos.cerrar(empresaId,vinculoId,datos.fechaFin(),datos.activo()));
    }
}
