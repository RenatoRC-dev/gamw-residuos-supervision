package bo.gob.warnes.gamw.residuos.modules.monitoreo.posicion.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionMunicipal;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.posicion.dominio.RepositorioMonitoreo;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioContextoTrayectoria.Resumen;
import bo.gob.warnes.gamw.residuos.modules.ejecucion.gps.dominio.PosicionGps;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@Service
@Transactional(readOnly=true)
public class ConsultarMonitoreo {
    private final AutorizacionMunicipal permisos;
    private final RepositorioMonitoreo repositorio;
    private final ObjectMapper json;
    private final long umbral;
    public ConsultarMonitoreo(AutorizacionMunicipal permisos,RepositorioMonitoreo repositorio,ObjectMapper json,
            @Value("${gamw.monitoreo.antiguedad-segundos:120}") long umbral) {
        if(umbral<1) throw new IllegalArgumentException("Umbral técnico de visualización positivo requerido.");
        this.permisos=permisos; this.repositorio=repositorio; this.json=json; this.umbral=umbral;
    }
    public Vista consultar(UsuarioAutenticado actor,int limite,int desplazamiento) {
        permisos.exigirConsultaMonitoreo(actor);
        if(limite<1 || limite>100 || desplazamiento<0 || desplazamiento>10000000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginación fuera de límites.");
        var filas=repositorio.activas(limite+1,desplazamiento);
        return new Vista(Instant.now(),umbral,filas.stream().limit(limite).map(j->new Jornada(j.contexto(),j.vehiculoId(),j.placa(),
                j.inicioRegistrado(),j.ultimaPosicion(),json.readTree(j.referenciaGeojson()))).toList(),
                filas.size()>limite?desplazamiento+limite:null);
    }
    public record Vista(Instant consultadoEn,long umbralAntiguedadSegundos,List<Jornada> jornadas,Integer siguiente) { }
    public record Jornada(Resumen contexto,UUID vehiculoId,String placa,Instant inicioRegistrado,
            PosicionGps ultimaPosicion,JsonNode rutaReferencia) { }
}
