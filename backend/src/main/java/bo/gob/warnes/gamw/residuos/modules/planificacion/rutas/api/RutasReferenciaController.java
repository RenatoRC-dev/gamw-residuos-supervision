package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.aplicacion.GestionarRutasReferencia;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.api.ContratosRuta.*;

@RestController
@RequestMapping("/api/rutas")
public class RutasReferenciaController {
    private final GestionarRutasReferencia casoUso;
    public RutasReferenciaController(GestionarRutasReferencia casoUso) { this.casoUso = casoUso; }

    @PostMapping
    public ResponseEntity<RutaCreadaRespuesta> crear(@Valid @RequestBody CrearRutaSolicitud request) {
        var creada = casoUso.crear(request.codigo(), request.nombre(), request.geometria().aDominio(), request.vigenteDesde(), request.motivoCambio());
        return ResponseEntity.created(URI.create("/api/rutas/" + creada.ruta().id()))
                .body(new RutaCreadaRespuesta(RutaRespuesta.desde(creada.ruta()), RutaVersionRespuesta.desde(creada.version())));
    }
    @GetMapping
    public List<RutaRespuesta> listar() { return casoUso.listar().stream().map(RutaRespuesta::desde).toList(); }
    @GetMapping("/{rutaId}")
    public RutaRespuesta obtener(@PathVariable UUID rutaId) { return RutaRespuesta.desde(casoUso.obtener(rutaId)); }
    @GetMapping("/{rutaId}/versiones")
    public List<RutaVersionRespuesta> versiones(@PathVariable UUID rutaId) {
        return casoUso.versiones(rutaId).stream().map(RutaVersionRespuesta::desde).toList();
    }
    @GetMapping("/{rutaId}/versiones/{numeroVersion}")
    public RutaVersionRespuesta obtenerVersion(@PathVariable UUID rutaId, @PathVariable int numeroVersion) {
        return RutaVersionRespuesta.desde(casoUso.obtenerVersion(rutaId, numeroVersion));
    }
    @PostMapping("/{rutaId}/versiones")
    public ResponseEntity<RutaVersionRespuesta> crearVersion(@PathVariable UUID rutaId, @Valid @RequestBody CrearVersionSolicitud request) {
        var version = casoUso.crearVersion(rutaId, request.geometria().aDominio(), request.vigenteDesde(), request.motivoCambio());
        return ResponseEntity.created(URI.create("/api/rutas/" + rutaId + "/versiones/" + version.numeroVersion()))
                .body(RutaVersionRespuesta.desde(version));
    }
}
