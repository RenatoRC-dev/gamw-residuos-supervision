package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.GestionarPersonas;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api.ContratosRegistroOperativo.*;

@RestController
@RequestMapping("/api/personas")
public class PersonasController {
    private final GestionarPersonas personas;
    public PersonasController(GestionarPersonas personas) { this.personas = personas; }
    @PostMapping
    public ResponseEntity<PersonaRespuesta> crear(@Valid @RequestBody DatosPersona d) {
        var p = personas.crear(d.ci(),d.nombres(),d.apellidoPaterno(),d.apellidoMaterno(),d.telefono());
        return ResponseEntity.created(URI.create("/api/personas/" + p.id())).body(PersonaRespuesta.desde(p));
    }
    @GetMapping
    public List<PersonaRespuesta> listar() { return personas.listar().stream().map(PersonaRespuesta::desde).toList(); }
    @GetMapping("/{personaId}")
    public PersonaRespuesta obtener(@PathVariable UUID personaId) { return PersonaRespuesta.desde(personas.obtener(personaId)); }
    @PutMapping("/{personaId}")
    public PersonaRespuesta actualizar(@PathVariable UUID personaId, @Valid @RequestBody DatosPersona d) {
        return PersonaRespuesta.desde(personas.actualizar(personaId,d.ci(),d.nombres(),d.apellidoPaterno(),d.apellidoMaterno(),d.telefono()));
    }
    @PatchMapping("/{personaId}/estado")
    public PersonaRespuesta estado(@PathVariable UUID personaId, @Valid @RequestBody EstadoSolicitud d) {
        return PersonaRespuesta.desde(personas.cambiarEstado(personaId,d.activo()));
    }
}
