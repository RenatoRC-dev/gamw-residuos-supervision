package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api.ContratosRegistroOperativo.*;

@RestController
@RequestMapping("/api/empresas-operadoras")
public class EmpresasOperadorasController {
    private final GestionarEmpresasOperadoras empresas;
    private final GestionarPeriodosServicio periodos;
    private final GestionarPersonalEmpresa personal;
    public EmpresasOperadorasController(GestionarEmpresasOperadoras empresas, GestionarPeriodosServicio periodos, GestionarPersonalEmpresa personal) {
        this.empresas = empresas; this.periodos = periodos; this.personal = personal;
    }
    @PostMapping
    public ResponseEntity<EmpresaRespuesta> crear(@Valid @RequestBody DatosEmpresa d) {
        var e = empresas.crear(d.nit(),d.razonSocial(),d.nombreComercial());
        return ResponseEntity.created(URI.create("/api/empresas-operadoras/" + e.id())).body(EmpresaRespuesta.desde(e));
    }
    @GetMapping
    public List<EmpresaRespuesta> listar() { return empresas.listar().stream().map(EmpresaRespuesta::desde).toList(); }
    @GetMapping("/{empresaId}")
    public EmpresaRespuesta obtener(@PathVariable UUID empresaId) { return EmpresaRespuesta.desde(empresas.obtener(empresaId)); }
    @PutMapping("/{empresaId}")
    public EmpresaRespuesta actualizar(@PathVariable UUID empresaId, @Valid @RequestBody DatosEmpresa d) {
        return EmpresaRespuesta.desde(empresas.actualizar(empresaId,d.nit(),d.razonSocial(),d.nombreComercial()));
    }
    @PatchMapping("/{empresaId}/estado")
    public EmpresaRespuesta estado(@PathVariable UUID empresaId, @Valid @RequestBody EstadoSolicitud d) {
        return EmpresaRespuesta.desde(empresas.cambiarEstado(empresaId,d.activo()));
    }
    @GetMapping("/funciones-operativas")
    public List<FuncionRespuesta> funciones() { return personal.funciones().stream().map(FuncionRespuesta::desde).toList(); }
    @PostMapping("/{empresaId}/periodos")
    public ResponseEntity<PeriodoRespuesta> crearPeriodo(@PathVariable UUID empresaId, @Valid @RequestBody DatosPeriodo d) {
        var p = periodos.crear(empresaId,d.fechaInicio(),d.fechaFin());
        return ResponseEntity.created(URI.create("/api/empresas-operadoras/" + empresaId + "/periodos")).body(PeriodoRespuesta.desde(p));
    }
    @GetMapping("/{empresaId}/periodos")
    public List<PeriodoRespuesta> periodos(@PathVariable UUID empresaId) { return periodos.listar(empresaId).stream().map(PeriodoRespuesta::desde).toList(); }
    @PutMapping("/{empresaId}/periodos/{periodoId}")
    public PeriodoRespuesta actualizarPeriodo(@PathVariable UUID empresaId, @PathVariable UUID periodoId, @Valid @RequestBody DatosPeriodo d) {
        return PeriodoRespuesta.desde(periodos.actualizar(empresaId,periodoId,d.fechaInicio(),d.fechaFin()));
    }
    @PatchMapping("/{empresaId}/periodos/{periodoId}/estado")
    public PeriodoRespuesta estadoPeriodo(@PathVariable UUID empresaId, @PathVariable UUID periodoId, @Valid @RequestBody EstadoSolicitud d) {
        return PeriodoRespuesta.desde(periodos.cambiarEstado(empresaId,periodoId,d.activo()));
    }
    @PostMapping("/{empresaId}/personal")
    public ResponseEntity<PersonalRespuesta> crearVinculo(@PathVariable UUID empresaId, @Valid @RequestBody CrearVinculoSolicitud d) {
        var v = personal.crear(empresaId,d.personaId(),d.fechaInicio(),d.fechaFin(),d.activo(),d.funcionOperativaIds());
        return ResponseEntity.created(URI.create("/api/empresas-operadoras/" + empresaId + "/personal")).body(PersonalRespuesta.desde(v));
    }
    @GetMapping("/{empresaId}/personal")
    public List<PersonalRespuesta> personal(@PathVariable UUID empresaId) { return personal.listar(empresaId).stream().map(PersonalRespuesta::desde).toList(); }
    @PutMapping("/{empresaId}/personal/{vinculoId}")
    public PersonalRespuesta actualizarVinculo(@PathVariable UUID empresaId, @PathVariable UUID vinculoId, @Valid @RequestBody ActualizarVinculoSolicitud d) {
        return PersonalRespuesta.desde(personal.actualizar(empresaId,vinculoId,d.fechaInicio(),d.fechaFin(),d.activo()));
    }
}
