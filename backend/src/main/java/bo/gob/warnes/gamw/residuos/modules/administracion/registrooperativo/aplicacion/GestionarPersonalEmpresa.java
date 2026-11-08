package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.List;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-07 / RF-04 parcial. Función operativa != rol de seguridad; se conserva la identidad personal. */
@Service
@Transactional(readOnly = true)
public class GestionarPersonalEmpresa {
    private final RepositorioRegistroOperativo repositorio;
    private final GestionarEmpresasOperadoras empresas;
    private final GestionarPersonas personas;
    public GestionarPersonalEmpresa(RepositorioRegistroOperativo repositorio, GestionarEmpresasOperadoras empresas, GestionarPersonas personas) {
        this.repositorio = repositorio; this.empresas = empresas; this.personas = personas;
    }
    public List<FuncionOperativa> funciones() { return repositorio.funciones(); }
    public List<DetallePersonal> listar(UUID empresaId) {
        empresas.obtener(empresaId);
        return repositorio.personal(empresaId).stream().map(this::detalle).toList();
    }
    private DetallePersonal detalle(VinculoPersonaEmpresa vinculo) {
        return new DetallePersonal(vinculo,personas.obtener(vinculo.personaId()),repositorio.funcionesDelVinculo(vinculo.id()));
    }
    private void asignarFuncionesIniciales(UUID vinculoId, List<UUID> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("El vínculo requiere al menos una función operativa activa.");
        }
        if (new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("Las funciones operativas no pueden repetirse.");
        var disponibles = funciones().stream().filter(FuncionOperativa::activo).map(FuncionOperativa::id).toList();
        if (!disponibles.containsAll(ids)) throw new IllegalArgumentException("Función operativa inexistente o inactiva.");
        repositorio.insertarFunciones(ids.stream().map(id -> new VinculoPersonaFuncion(vinculoId,id)).toList());
    }
    @Transactional
    public DetallePersonal crear(UUID empresaId, UUID personaId, LocalDate fechaInicio, LocalDate fechaFin, boolean activo, List<UUID> funcionOperativaIds) {
        empresas.buscar(empresaId,true);
        personas.obtener(personaId);
        var ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var vinculo = new VinculoPersonaEmpresa(UUID.randomUUID(),personaId,empresaId,fechaInicio,fechaFin,activo,ahora,ahora);
        repositorio.insertarVinculo(vinculo);
        asignarFuncionesIniciales(vinculo.id(),funcionOperativaIds);
        return detalle(vinculo);
    }
    @Transactional
    public DetallePersonal actualizar(UUID empresaId, UUID id, LocalDate fechaInicio, LocalDate fechaFin, boolean activo) {
        empresas.buscar(empresaId,true);
        var anterior = repositorio.vinculo(empresaId,id).orElseThrow(() -> new RegistroNoEncontrado("Vínculo de la empresa no encontrado."));
        if (!anterior.fechaInicio().equals(fechaInicio)) {
            throw new IllegalArgumentException("La fecha de inicio identifica el segmento histórico y no puede modificarse. Cree un nuevo vínculo.");
        }
        var vinculo = new VinculoPersonaEmpresa(id,anterior.personaId(),empresaId,fechaInicio,fechaFin,activo,anterior.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.actualizarVinculo(vinculo);
        return detalle(vinculo);
    }
    public record DetallePersonal(VinculoPersonaEmpresa vinculo, PersonaNatural persona, List<FuncionOperativa> funciones) { }
}
