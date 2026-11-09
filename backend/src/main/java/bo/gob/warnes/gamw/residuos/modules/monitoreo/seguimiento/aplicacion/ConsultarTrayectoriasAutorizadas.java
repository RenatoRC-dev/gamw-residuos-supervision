package bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionMunicipal;
import bo.gob.warnes.gamw.residuos.modules.monitoreo.seguimiento.dominio.RepositorioContextoTrayectoria;
import bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.dominio.RepositorioRuta;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** D1-A municipal explícito; NO concede ámbito empresarial ni potestad de fiscalización. */
@Service
@Transactional(readOnly=true)
public class ConsultarTrayectoriasAutorizadas {
    private final AutorizacionMunicipal permisos;
    private final RepositorioContextoTrayectoria contextos;
    private final ConsultarSeguimientoGps seguimiento;
    private final RepositorioRuta rutas;
    public ConsultarTrayectoriasAutorizadas(AutorizacionMunicipal permisos,RepositorioContextoTrayectoria contextos,
            ConsultarSeguimientoGps seguimiento,RepositorioRuta rutas) {
        this.permisos=permisos; this.contextos=contextos; this.seguimiento=seguimiento; this.rutas=rutas;
    }
    private void validarPagina(int limite,int desplazamiento,int maximo) {
        if(limite<1 || limite>maximo || desplazamiento<0 || desplazamiento>10000000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginación fuera de límites.");
    }
    public ListaJornadas listar(UsuarioAutenticado actor,int limite,int desplazamiento) {
        permisos.exigirConsultaTrayectoria(actor);
        validarPagina(limite,desplazamiento,100);
        var filas=contextos.listar(limite+1,desplazamiento);
        return new ListaJornadas(filas.stream().limit(limite).toList(),filas.size()>limite?desplazamiento+limite:null);
    }
    public Detalle consultar(UsuarioAutenticado actor,UUID jornadaId,int limite,int desplazamiento,Instant corte) {
        permisos.exigirConsultaTrayectoria(actor); // Antes de consultar existencia o metadatos.
        validarPagina(limite,desplazamiento,1000);
        Instant ahora=Instant.now();
        if(corte!=null && corte.isAfter(ahora)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Corte de recepción futuro.");
        if(corte==null) corte=ahora.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var contexto=contextos.obtener(jornadaId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Jornada no disponible."));
        var version=rutas.versionPorId(contexto.rutaVersionId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Referencia no disponible."));
        return new Detalle(contexto,new LineaReferencia("LineString",version.geometria().coordenadas()),
                seguimiento.consultarPagina(jornadaId,limite,desplazamiento,corte));
    }
    public record ListaJornadas(List<RepositorioContextoTrayectoria.Resumen> jornadas,Integer siguiente) { }
    public record LineaReferencia(String type,List<List<Double>> coordinates) { }
    public record Detalle(RepositorioContextoTrayectoria.Resumen contexto,LineaReferencia rutaReferencia,ConsultarSeguimientoGps.Pagina seguimiento) { }
}
