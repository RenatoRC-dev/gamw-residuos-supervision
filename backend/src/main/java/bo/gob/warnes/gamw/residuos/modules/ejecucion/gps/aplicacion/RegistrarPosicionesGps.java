package bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion.*;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.RepositorioJornadas;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lotes atómicos de evidencia. No calcula cumplimiento ni impone precisión institucional. */
@Service
public class RegistrarPosicionesGps {
    private final RegistrarEventosEjecucion autorizacion;
    private final RepositorioEventosEjecucion eventos;
    private final RepositorioJornadas jornadas;
    private final RepositorioPosicionesGps repositorio;
    public RegistrarPosicionesGps(RegistrarEventosEjecucion autorizacion,RepositorioEventosEjecucion eventos,
            RepositorioJornadas jornadas,RepositorioPosicionesGps repositorio) {
        this.autorizacion=autorizacion; this.eventos=eventos; this.jornadas=jornadas; this.repositorio=repositorio;
    }
    public record Lectura(UUID capturaId,Instant capturadoEn,Double latitud,Double longitud,Double precisionMetros) {
        public void validar() {
            if(capturaId==null || capturadoEn==null || !capturadoEn.equals(capturadoEn.truncatedTo(java.time.temporal.ChronoUnit.MICROS)) || latitud==null || longitud==null || precisionMetros==null ||
                    !Double.isFinite(latitud) || !Double.isFinite(longitud) || !Double.isFinite(precisionMetros) ||
                    latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180 || precisionMetros < 0)
                throw new IllegalArgumentException("Captura GPS inválida: UUID, instante, coordenadas y precisión finita no negativa son obligatorios.");
        }
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rechazarCampo(String campo,Object valor) { throw new IllegalArgumentException("Campo GPS no permitido."); }
    }
    public record Confirmacion(UUID capturaId,UUID jornadaId,UUID asignacionJornadaId,UUID usuarioId,
            Instant capturadoEn,Instant recibidoEn,double latitud,double longitud,double precisionMetros) { }

    @Transactional
    public List<Confirmacion> registrar(UUID jornada,UUID asignacion,List<Lectura> posiciones,UsuarioAutenticado actor) {
        if(asignacion==null || posiciones==null || posiciones.isEmpty() || posiciones.size()>200)
            throw new IllegalArgumentException("Indique asignación y entre 1 y 200 posiciones por lote.");
        for(var p:posiciones) { if(p==null) throw new IllegalArgumentException("Posición obligatoria."); p.validar(); }
        // Mismo bloqueo que INICIO/FIN: serializa recepción de evidencia con el cierre de jornada.
        jornadas.buscar(jornada,true).orElseThrow(() -> new IllegalArgumentException("Jornada no encontrada."));
        autorizacion.autorizarAsignacionHistorica(jornada,asignacion,actor);
        var historia=eventos.porJornada(jornada);
        var inicio=historia.stream().filter(e -> e.tipoEvento()==TipoEventoEjecucion.INICIO).findFirst()
                .orElseThrow(() -> new ConflictoEjecucion("GPS requiere INICIO aceptado."));
        var fin=historia.stream().filter(e -> e.tipoEvento()==TipoEventoEjecucion.FIN).findFirst();
        var resultado=new ArrayList<Confirmacion>();
        for(var p:posiciones) {
            var anteriores=repositorio.buscar(jornada,p.capturaId());
            if(anteriores.isPresent()) {
                var a=anteriores.get();
                if(!a.usuarioId().equals(actor.usuarioId()) || !a.asignacionJornadaId().equals(asignacion) ||
                        !a.capturadoEn().equals(p.capturadoEn()) || a.latitud()!=p.latitud() ||
                        a.longitud()!=p.longitud() || a.precisionMetros()!=p.precisionMetros())
                    throw new ConflictoEjecucion("capturaId ya pertenece a otra evidencia o Usuario; no se sobrescribe.");
                resultado.add(confirmacion(a)); continue;
            }
            autorizacion.autorizarCaptura(jornada,asignacion,p.capturadoEn(),actor);
            if(p.capturadoEn().isBefore(inicio.capturadoEn()) ||
                    (fin.isPresent() && p.capturadoEn().isAfter(fin.get().capturadoEn())))
                throw new ConflictoEjecucion("La posición debe corresponder al intervalo INICIO/FIN de ejecución.");
            resultado.add(confirmacion(repositorio.insertar(new PosicionGps(UUID.randomUUID(),p.capturaId(),jornada,asignacion,
                actor.usuarioId(),p.capturadoEn(),Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),p.latitud(),p.longitud(),p.precisionMetros()))));
        }
        return resultado;
    }
    private Confirmacion confirmacion(PosicionGps p) {
        return new Confirmacion(p.capturaId(),p.jornadaId(),p.asignacionJornadaId(),p.usuarioId(),p.capturadoEn(),p.recibidoEn(),
            p.latitud(),p.longitud(),p.precisionMetros());
    }
}
