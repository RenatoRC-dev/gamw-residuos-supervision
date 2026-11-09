package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.ReferenciaPlanificacionNoEncontrada;
import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.RepositorioRegistroOperativo;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evidencia con actor autenticado y compatibilidad técnica local; no determina cumplimiento. */
@Service
@Transactional(readOnly=true)
public class RegistrarEventosEjecucion {
    private final RepositorioEventosEjecucion eventos;
    private final RepositorioJornadas jornadas;
    private final RepositorioRegistroOperativo registro;
    public RegistrarEventosEjecucion(RepositorioEventosEjecucion eventos,RepositorioJornadas jornadas,RepositorioRegistroOperativo registro) {
        this.eventos = eventos; this.jornadas = jornadas; this.registro = registro;
    }
    public LecturaEjecucion consultar(UUID jornadaId) {
        jornadas.buscar(jornadaId,false).orElseThrow(() -> new ReferenciaPlanificacionNoEncontrada("Jornada no encontrada."));
        var historia = eventos.porJornada(jornadaId);
        return new LecturaEjecucion(jornadaId,porTipo(historia,TipoEventoEjecucion.INICIO).orElse(null),porTipo(historia,TipoEventoEjecucion.FIN).orElse(null));
    }
    @Transactional
    public Resultado registrar(UUID jornadaId,UUID clienteId,UUID asignacionId,TipoEventoEjecucion tipo,Instant captura) {
        return registrarInterno(jornadaId,clienteId,asignacionId,tipo,captura,null);
    }
    @Transactional
    public Resultado registrarAutenticado(UUID jornadaId,UUID clienteId,UUID asignacionId,TipoEventoEjecucion tipo,Instant captura,UsuarioAutenticado actor) {
        Objects.requireNonNull(actor,"El actor autenticado es obligatorio.");
        return registrarInterno(jornadaId,clienteId,asignacionId,tipo,captura,actor);
    }
    private Resultado registrarInterno(UUID jornadaId,UUID clienteId,UUID asignacionId,TipoEventoEjecucion tipo,Instant captura,UsuarioAutenticado actor) {
        if (clienteId == null || asignacionId == null || captura == null) throw new IllegalArgumentException("Evento cliente, asignación y captura son obligatorios.");
        var existente = eventos.porCliente(clienteId);
        if (existente.isPresent()) return reintento(existente.get(),jornadaId,asignacionId,tipo,captura,actor);
        var jornada = jornadas.buscar(jornadaId,true).orElseThrow(() -> new ReferenciaPlanificacionNoEncontrada("Jornada no encontrada."));
        existente = eventos.porCliente(clienteId); // Releer después del bloqueo: reintento concurrente de la misma jornada.
        if (existente.isPresent()) return reintento(existente.get(),jornadaId,asignacionId,tipo,captura,actor);
        var asignacion = jornadas.asignacionPorId(asignacionId).orElseThrow(() -> new ReferenciaPlanificacionNoEncontrada("Asignación no encontrada."));
        if (!asignacion.jornadaId().equals(jornadaId)) throw new IllegalArgumentException("La asignación debe pertenecer a la jornada indicada.");
        if (actor != null) autorizar(asignacion,actor);
        if (captura.isBefore(asignacion.vigenteDesde()) || (asignacion.vigenteHasta() != null && captura.isAfter(asignacion.vigenteHasta()))) {
            throw new IllegalArgumentException("La captura no pertenece al intervalo administrativo de la asignación histórica.");
        }
        var historia = eventos.porJornada(jornadaId);
        if (porTipo(historia,tipo).isPresent()) throw new ConflictoEjecucion("Ya existe otro evento " + tipo + " para la jornada.");
        if (tipo == TipoEventoEjecucion.INICIO && !jornada.vigente()) throw new ConflictoEjecucion("La jornada fue reprogramada; el nuevo INICIO no se traslada a otra jornada.");
        if (tipo == TipoEventoEjecucion.FIN) {
            var inicio = porTipo(historia,TipoEventoEjecucion.INICIO).orElseThrow(() -> new ConflictoEjecucion("FIN requiere un INICIO registrado."));
            if (captura.isBefore(inicio.capturadoEn())) throw new IllegalArgumentException("La captura de FIN no puede ser anterior a INICIO.");
            // No se exige vigencia actual de planificación ni igualdad con la asignación del INICIO.
        }
        var nuevo = new EventoEjecucionJornada(UUID.randomUUID(),clienteId,jornadaId,asignacionId,tipo,captura,Instant.now().truncatedTo(ChronoUnit.MICROS),actor == null ? null : actor.usuarioId());
        var insertado = eventos.insertarSiAusente(nuevo);
        if (insertado.isPresent()) return new Resultado(insertado.get(),true);
        return eventos.porCliente(clienteId).map(e -> reintento(e,jornadaId,asignacionId,tipo,captura,actor))
                .orElseThrow(() -> new ConflictoEjecucion("Ya existe otro evento de ese tipo para la jornada."));
    }
    private Resultado reintento(EventoEjecucionJornada existente,UUID jornada,UUID asignacion,TipoEventoEjecucion tipo,Instant captura,UsuarioAutenticado actor) {
        if (existente.usuarioId() != null && !existente.usuarioId().equals(actor == null ? null : actor.usuarioId())) {
            throw new ConflictoEjecucion("eventoClienteId pertenece a otro Usuario autenticado; la atribución original no se modifica.");
        }
        if (!existente.mismoContenido(jornada,asignacion,tipo,captura)) throw new ConflictoEjecucion("eventoClienteId ya identifica evidencia con contenido diferente.");
        if (actor != null) {
            var historica = jornadas.asignacionPorId(existente.asignacionJornadaId())
                    .orElseThrow(() -> new ReferenciaPlanificacionNoEncontrada("Asignación no encontrada."));
            autorizar(historica,actor);
        }
        return new Resultado(existente,false);
    }
    private void autorizar(AsignacionJornada asignacion,UsuarioAutenticado actor) {
        var vinculo = registro.vinculoPorId(asignacion.vinculoPersonaEmpresaId()).orElseThrow(EjecucionNoAutorizada::new);
        if (!actor.personaNaturalId().equals(vinculo.personaId())) throw new EjecucionNoAutorizada();
    }
    private Optional<EventoEjecucionJornada> porTipo(List<EventoEjecucionJornada> historia,TipoEventoEjecucion tipo) {
        return historia.stream().filter(e -> e.tipoEvento() == tipo).findFirst();
    }
    public record Resultado(EventoEjecucionJornada evento,boolean creado) { }
    public record LecturaEjecucion(UUID jornadaId,EventoEjecucionJornada inicio,EventoEjecucionJornada fin) { }
}
