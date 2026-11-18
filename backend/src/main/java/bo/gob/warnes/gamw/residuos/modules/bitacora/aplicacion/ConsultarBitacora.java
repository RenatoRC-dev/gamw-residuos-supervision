package bo.gob.warnes.gamw.residuos.modules.bitacora.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.bitacora.persistencia.RepositorioBitacora;
import bo.gob.warnes.gamw.residuos.modules.bitacora.persistencia.RepositorioBitacora.Fila;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * CU-31 (solo lectura). Separado de CU-29: aquí se auditan acciones de personas/sistemas, no la historia operativa del servicio.
 * No inventa eventos ni catálogos oficiales de acciones ni plazos de retención.
 */
@Service
@Transactional(readOnly = true)
public class ConsultarBitacora {
    public static final int PAGINA = 50;
    public static final Set<String> ENTIDADES = Set.of("USUARIO","JORNADA","PERIODO_SERVICIO");
    private static final Pattern ACCION = Pattern.compile("^[A-Z_:]{1,100}$");
    private static final Pattern EVENTO = Pattern.compile("^[A-Z]{3}:[0-9a-fA-F-]{36}$");
    private final AutorizacionBitacora permisos;
    private final RepositorioBitacora repositorio;
    private final ObjectMapper json;
    public ConsultarBitacora(AutorizacionBitacora permisos,RepositorioBitacora repositorio,ObjectMapper json) { this.permisos = permisos; this.repositorio = repositorio; this.json = json; }

    public record Evento(String id,String fuente,Instant ocurridoEn,String accion,String atribucion,UUID actorUsuarioId,String actorNombreUsuario,String entidadTipo,UUID entidadId) { }
    public record Pagina(List<Evento> eventos,Integer siguiente) { }
    public record Detalle(Evento evento,String motivo,Object anterior,Object posterior,List<String> notas) { }

    public Pagina buscar(UsuarioAutenticado actor,LocalDate desde,LocalDate hasta,UUID actorId,String accion,String entidadTipo,UUID entidadId,int desplazamiento) {
        permisos.exigir(actor);
        if (desde != null && hasta != null && desde.isAfter(hasta)) throw invalido("Rango temporal contradictorio: 'desde' es posterior a 'hasta'.");
        if (desplazamiento < 0 || desplazamiento > 10_000_000) throw invalido("Desplazamiento inválido.");
        String a = accion == null || accion.isBlank() ? null : accion.strip().toUpperCase(Locale.ROOT);
        if (a != null && !ACCION.matcher(a).matches()) throw invalido("Formato de acción inválido.");
        String tipo = entidadTipo == null || entidadTipo.isBlank() ? null : entidadTipo.strip().toUpperCase(Locale.ROOT);
        if (tipo != null && !ENTIDADES.contains(tipo)) throw invalido("Tipo de entidad no admitido.");
        if (entidadId != null && tipo == null) throw invalido("Indique el tipo de entidad junto con su identificador.");
        var filas = repositorio.buscar(desde,hasta,actorId,a,tipo,entidadId,PAGINA + 1,desplazamiento);
        return new Pagina(filas.stream().limit(PAGINA).map(this::evento).toList(),filas.size() > PAGINA ? desplazamiento + PAGINA : null);
    }

    public Detalle detalle(UsuarioAutenticado actor,String id) {
        permisos.exigir(actor);
        if (id == null || !EVENTO.matcher(id).matches()) throw invalido("Identificador de evento inválido.");
        Fila f = repositorio.evento(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Evento no encontrado."));
        var notas = new ArrayList<String>();
        if (f.actorUsuarioId() == null) notas.add("Evento no atribuible a un usuario autenticado: se originó en provisión local/técnica. No se infiere identidad.");
        if (f.anterior() == null && f.posterior() == null) notas.add("La fuente no conserva estados anterior/posterior para este tipo de evento.");
        notas.add("Los campos secretos y datos personales protegidos se ocultan; esta consulta no modifica ni elimina registros.");
        return new Detalle(evento(f),RedactorSensible.texto(f.motivo()),valor(f.anterior()),valor(f.posterior()),notas);
    }

    private Object valor(String texto) {
        if (texto == null) return null;
        try { return RedactorSensible.redactar(json.readValue(texto,Object.class)); }
        catch (RuntimeException e) { return RedactorSensible.OCULTO; } // ante un formato inesperado se prefiere no revelar
    }
    private Evento evento(Fila f) {
        String atribucion = f.actorUsuarioId() != null ? "USUARIO_AUTENTICADO" : f.origenLocal() ? "OPERADOR_LOCAL_NO_AUTENTICADO" : "NO_ATRIBUIBLE";
        return new Evento(f.eventoId(),f.fuente(),f.ocurridoEn(),f.accion(),atribucion,f.actorUsuarioId(),f.actorNombreUsuario(),f.entidadTipo(),f.entidadId());
    }
    private ResponseStatusException invalido(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,m); }
}
