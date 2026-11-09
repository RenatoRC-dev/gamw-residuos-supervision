package bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.dominio.RepositorioReferenciasEspaciales;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion.AutorizacionMunicipal;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.*;

@Service
@Transactional(readOnly=true)
public class GestionarReferenciasEspaciales {
    private final RepositorioReferenciasEspaciales repo;
    private final AutorizacionMunicipal permisos;
    private final ObjectMapper json;
    public GestionarReferenciasEspaciales(RepositorioReferenciasEspaciales repo,AutorizacionMunicipal permisos,ObjectMapper json) {
        this.repo=repo; this.permisos=permisos; this.json=json;
    }
    public record Entrada(UUID rutaVersionId,String nombre,String tipo,JsonNode geometria,Double radioMetros,String motivo) { }
    public record Vista(UUID id,UUID referenciaId,int numeroVersion,UUID rutaVersionId,String nombre,String tipo,
            JsonNode geometria,Double radioMetros,Instant vigenteDesde,Instant vigenteHasta,String motivo) { }
    public record Pagina(List<Vista> referencias,Integer siguiente) { }
    private Vista vista(RepositorioReferenciasEspaciales.Version v) {
        return new Vista(v.id(),v.referenciaId(),v.numeroVersion(),v.rutaVersionId(),v.nombre(),v.tipo(),json.readTree(v.geojson()),
            v.radioMetros(),v.vigenteDesde(),v.vigenteHasta(),v.motivo());
    }
    private String texto(String s) {
        if(s==null || s.isBlank() || s.length()>1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nombre y motivo no vacíos, hasta 1000 caracteres.");
        return s.strip();
    }
    public Pagina listar(UsuarioAutenticado a,UUID ruta,int desde) {
        permisos.exigirLecturaReferencias(a);
        validarPagina(desde);
        var filas=repo.listar(ruta,101,desde);
        return new Pagina(filas.stream().limit(100).map(this::vista).toList(),filas.size()>100?desde+100:null);
    }
    public List<RepositorioReferenciasEspaciales.OpcionRuta> rutas(UsuarioAutenticado a,int desde) {
        permisos.exigirGestionReferencias(a); validarPagina(desde); return repo.rutas(100,desde);
    }
    private void validarPagina(int desde) { if(desde<0 || desde>10000000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página inválida."); }
    @Transactional public Vista guardar(UsuarioAutenticado a,UUID referencia,Entrada e) {
        permisos.exigirGestionReferencias(a);
        if(e==null || e.rutaVersionId()==null || e.geometria()==null || e.geometria().isNull()
            || e.geometria().has("crs") || json.writeValueAsString(e.geometria()).length()>1000000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Indique versión y geometría GeoJSON 2D EPSG:4326 sin CRS alternativo.");
        String nombre=texto(e.nombre()),motivo=texto(e.motivo());
        boolean punto="PUNTO".equals(e.tipo()) && "Point".equals(e.geometria().path("type").asText());
        boolean area="GEOCERCA".equals(e.tipo()) && Set.of("Polygon","MultiPolygon").contains(e.geometria().path("type").asText());
        if(!(punto||area) || (punto && (e.radioMetros()==null || !Double.isFinite(e.radioMetros()) || e.radioMetros()<=0 || e.radioMetros()>10000)) || (area && e.radioMetros()!=null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Punto requiere radio explícito (0–10000 m); geocerca Polygon/MultiPolygon sin radio.");
        var anterior=referencia==null?null:repo.actualBloqueada(referencia).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Referencia inexistente o ya cerrada."));
        Instant ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(anterior!=null && !ahora.isAfter(anterior.vigenteDesde())) ahora=anterior.vigenteDesde().plusNanos(1000);
        UUID identidad=referencia==null?UUID.randomUUID():referencia;
        try {
            if(anterior==null) repo.crearIdentidad(identidad); else repo.cerrar(anterior.id(),ahora,a.usuarioId(),motivo);
            var version=new RepositorioReferenciasEspaciales.Version(UUID.randomUUID(),identidad,anterior==null?1:anterior.numeroVersion()+1,
                e.rutaVersionId(),nombre,e.tipo(),json.writeValueAsString(e.geometria()),e.radioMetros(),ahora,null,motivo);
            repo.insertar(version,a.usuarioId()); return vista(version);
        } catch(DataIntegrityViolationException | BadSqlGrammarException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Geometría, vigencia o asociación espacial inválida; se conserva la historia anterior.");
        }
    }
    @Transactional public void cerrar(UsuarioAutenticado a,UUID referencia,String motivo) {
        permisos.exigirGestionReferencias(a); motivo=texto(motivo);
        var anterior=repo.actualBloqueada(referencia).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Referencia inexistente o cerrada."));
        var ahora=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(!ahora.isAfter(anterior.vigenteDesde())) ahora=anterior.vigenteDesde().plusNanos(1000);
        repo.cerrar(anterior.id(),ahora,a.usuarioId(),motivo);
    }
}
