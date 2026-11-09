package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.dominio.*;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.dominio.RepositorioAdministracionUsuarios.*;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import com.fasterxml.jackson.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly=true)
public class AdministrarIdentidad {
    private static final String CONSULTAR="USUARIOS_CONSULTAR",GESTIONAR="USUARIOS_GESTIONAR",PERMISOS="PERMISOS_ADMINISTRAR";
    private final RepositorioAdministracionUsuarios repo;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final ObjectMapper json;
    private final boolean provision;
    private final String referenciaProvision;
    public AdministrarIdentidad(RepositorioAdministracionUsuarios repo,JdbcTemplate jdbc,PasswordEncoder encoder,ObjectMapper json,
            @Value("${gamw.administracion.provision-manual-habilitada:false}") boolean provision,
            @Value("${gamw.administracion.referencia-provision:}") String referenciaProvision) {
        if(provision && referenciaProvision.isBlank()) throw new IllegalStateException("Provisión manual requiere referencia explícita del procedimiento aprobado.");
        this.repo=repo;this.jdbc=jdbc;this.encoder=encoder;this.json=json;this.provision=provision;this.referenciaProvision=referenciaProvision;
    }
    public record NuevaCuenta(UUID personaNaturalId,String nombreUsuario,@JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String contrasena,String motivo) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Campo de cuenta no autorizado."); }
        @Override public String toString() { return "NuevaCuenta[contrasena=[REDACTADA]]"; }
    }
    public record NombreCuenta(String nombreUsuario,String motivo) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Solo nombre de usuario y motivo."); }
    }
    public record EstadoCuenta(Boolean activo,String motivo) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Solo estado y motivo."); }
    }
    public record Otorgar(String permiso,UUID periodoServicioId,boolean incluyeHistorico,String motivo) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Campo no autorizado; el servidor valida facultad y ámbito."); }
    }
    public record Revocar(boolean porPeriodo,String motivo) {
        @JsonAnySetter public void desconocido(String k,Object v) { throw new IllegalArgumentException("Campo de revocación no autorizado."); }
    }
    public record Pagina<T>(List<T> registros,Integer siguiente) { }
    public record Opciones(boolean puedeGestionar,boolean puedeAdministrarPermisos,boolean provisionHabilitada,String referenciaProvision,List<Facultad> facultades,String pendientes) { }
    private ResponseStatusException error(HttpStatus estado,String mensaje) { return new ResponseStatusException(estado,mensaje); }
    private void exigir(UsuarioAutenticado actor,String permiso,boolean bloquear) {
        if(actor==null) throw new InsufficientAuthenticationException("Identidad institucional requerida.");
        for(String p:permiso.equals(CONSULTAR)?List.of(CONSULTAR):List.of(CONSULTAR,permiso)) {
            var filas=jdbc.queryForList("""
                SELECT c.id FROM concesion_permiso c JOIN usuario u ON u.id=c.usuario_id WHERE u.id=? AND u.activo
                 AND c.permiso=? AND c.alcance='MUNICIPAL_GAMW' AND c.revocado_en IS NULL
                """+(bloquear?" FOR SHARE OF c,u":""),UUID.class,actor.usuarioId(),p);
            if(filas.isEmpty()) throw new AccessDeniedException("Facultad administrativa municipal específica requerida.");
        }
    }
    private boolean puede(UsuarioAutenticado actor,String permiso) { try { exigir(actor,permiso,false);return true; } catch(AccessDeniedException e) { return false; } }
    private int pagina(int desde) { if(desde<0 || desde>10000000) throw error(HttpStatus.BAD_REQUEST,"Página inválida.");return desde; }
    private <T> Pagina<T> pagina(List<T> registros,int desde) { return new Pagina<>(registros.stream().limit(50).toList(),registros.size()>50?desde+50:null); }
    private String motivo(String s) { if(s==null || s.isBlank() || s.length()>4000) throw error(HttpStatus.BAD_REQUEST,"Motivo obligatorio, máximo 4000 caracteres.");return s; }
    private String nombre(String s) { if(s==null || s.isBlank() || s.length()>100 || !s.equals(s.strip()) || s.codePoints().anyMatch(Character::isISOControl)) throw error(HttpStatus.BAD_REQUEST,"Nombre no vacío, hasta 100 caracteres y sin espacios extremos ni controles.");return s; }
    private Cuenta cuenta(UUID id,boolean bloqueo) { return repo.cuenta(id,bloqueo).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Cuenta no encontrada.")); }
    private Cuenta objetivo(UsuarioAutenticado actor,UUID id) {
        if(id.equals(actor.usuarioId())) throw new AccessDeniedException("La auto-administración no está habilitada por este procedimiento.");
        var c=cuenta(id,true);
        if(c.administracionProtegida()) throw new AccessDeniedException("Gestión de administradores reservada a provisión controlada; política institucional pendiente.");
        return c;
    }
    public Pagina<Cuenta> listar(UsuarioAutenticado actor,int desde) { exigir(actor,CONSULTAR,false);pagina(desde);return pagina(repo.listar(51,desde),desde); }
    public Cuenta consultar(UsuarioAutenticado actor,UUID id) { exigir(actor,CONSULTAR,false);return cuenta(id,false); }
    public List<Persona> personas(UsuarioAutenticado actor,String ci) {
        exigir(actor,CONSULTAR,false);if(ci==null || ci.isBlank() || ci.length()>200) throw error(HttpStatus.BAD_REQUEST,"CI textual exacto requerido.");return repo.personas(ci);
    }
    public Opciones opciones(UsuarioAutenticado actor) {
        exigir(actor,CONSULTAR,false);boolean permisos=puede(actor,PERMISOS);
        return new Opciones(puede(actor,GESTIONAR),permisos,provision,referenciaProvision,permisos?repo.facultades(actor.usuarioId(),false):List.of(),
            "Roles, auto-administración, gestión de administradores, recuperación/cambio de contraseña, reactivación y cambio de PersonaNatural bloqueados hasta política aprobada. Reactivar podría rehabilitar JWT anteriores; no se habilita implícitamente.");
    }
    @Transactional public Cuenta crear(UsuarioAutenticado actor,NuevaCuenta e) {
        exigir(actor,GESTIONAR,true);
        if(!provision) throw error(HttpStatus.CONFLICT,"Procedimiento de provisión/entrega de credenciales pendiente de habilitación explícita.");
        if(e==null || e.personaNaturalId()==null || e.contrasena()==null || e.contrasena().isBlank() || e.contrasena().length()<12 || e.contrasena().length()>72
            || e.contrasena().getBytes(StandardCharsets.UTF_8).length>72) throw error(HttpStatus.BAD_REQUEST,"Persona y contraseña de 12 caracteres mínimos, máximo 72 bytes UTF-8 BCrypt, requeridas.");
        String motivo=motivo(e.motivo()),nombre=nombre(e.nombreUsuario());
        if(!repo.personaValida(e.personaNaturalId())) throw error(HttpStatus.BAD_REQUEST,"PersonaNatural existente y activa requerida; no se crea ni duplica identidad.");
        UUID id=UUID.randomUUID();repo.crear(id,e.personaNaturalId(),nombre,encoder.encode(e.contrasena()));
        var nueva=cuenta(id,false);repo.auditar(actor.usuarioId(),id,"CUENTA_CREAR",motivo,"{}",json.writeValueAsString(Map.of("cuenta",nueva,"referenciaProvision",referenciaProvision)));return nueva;
    }
    @Transactional public Cuenta actualizar(UsuarioAutenticado actor,UUID id,NombreCuenta e) {
        exigir(actor,GESTIONAR,true);if(e==null) throw error(HttpStatus.BAD_REQUEST,"Contrato requerido.");
        String motivo=motivo(e.motivo()),nombre=nombre(e.nombreUsuario());var anterior=objetivo(actor,id);
        repo.renombrar(id,nombre);var nueva=cuenta(id,false);repo.auditar(actor.usuarioId(),id,"CUENTA_ACTUALIZAR",motivo,json.writeValueAsString(anterior),json.writeValueAsString(nueva));return nueva;
    }
    @Transactional public Cuenta estado(UsuarioAutenticado actor,UUID id,EstadoCuenta e) {
        exigir(actor,GESTIONAR,true);if(e==null || e.activo()==null) throw error(HttpStatus.BAD_REQUEST,"Estado explícito requerido.");
        String motivo=motivo(e.motivo());var anterior=objetivo(actor,id);
        if(e.activo() && !anterior.activo()) throw error(HttpStatus.CONFLICT,"Reactivación bloqueada: política de credenciales y sesiones anteriores pendiente.");
        if(!e.activo() && anterior.activo()) { repo.desactivar(id);repo.auditar(actor.usuarioId(),id,"CUENTA_DESACTIVAR",motivo,json.writeValueAsString(anterior),json.writeValueAsString(cuenta(id,false))); }
        return cuenta(id,false);
    }
    public Pagina<Concesion> concesiones(UsuarioAutenticado actor,UUID id,int desde) { exigir(actor,PERMISOS,false);cuenta(id,false);pagina(desde);return pagina(repo.concesiones(id,51,desde),desde); }
    public Pagina<Actuacion> actuaciones(UsuarioAutenticado actor,UUID id,int desde) { exigir(actor,CONSULTAR,false);cuenta(id,false);pagina(desde);return pagina(repo.actuaciones(id,51,desde),desde); }
    private void delegacion(UsuarioAutenticado actor,String permiso,UUID periodo,boolean historico) {
        if(!PermisosDelegables.admite(permiso,periodo!=null) || (periodo==null && historico)) throw new AccessDeniedException("No se delegan capacidades administrativas ni ámbitos implícitos.");
        boolean permitido=repo.facultades(actor.usuarioId(),true).stream().anyMatch(f->f.permiso().equals(permiso) && Objects.equals(f.periodoServicioId(),periodo) && (!historico || f.permiteHistorico()));
        if(!permitido) throw new AccessDeniedException("Falta facultad local explícita para delegar este permiso, periodo o alcance histórico.");
    }
    @Transactional public Concesion otorgar(UsuarioAutenticado actor,UUID id,Otorgar e) {
        exigir(actor,PERMISOS,true);if(e==null) throw error(HttpStatus.BAD_REQUEST,"Contrato requerido.");String motivo=motivo(e.motivo());
        var destino=objetivo(actor,id);if(!destino.activo()) throw error(HttpStatus.CONFLICT,"No se otorgan permisos a una cuenta inactiva.");
        delegacion(actor,e.permiso(),e.periodoServicioId(),e.incluyeHistorico());
        UUID nueva=repo.otorgar(id,e.permiso(),e.periodoServicioId(),e.incluyeHistorico(),motivo,actor.usuarioId());
        var c=repo.concesion(id,nueva,e.periodoServicioId()!=null).orElseThrow();repo.auditar(actor.usuarioId(),id,"PERMISO_OTORGAR",motivo,"{}",json.writeValueAsString(c));return c;
    }
    @Transactional public Concesion revocar(UsuarioAutenticado actor,UUID usuario,UUID id,Revocar e) {
        exigir(actor,PERMISOS,true);if(e==null) throw error(HttpStatus.BAD_REQUEST,"Contrato requerido.");String motivo=motivo(e.motivo());objetivo(actor,usuario);
        var c=repo.concesion(usuario,id,e.porPeriodo()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Concesión no encontrada para esta cuenta y ámbito."));
        delegacion(actor,c.permiso(),c.periodoServicioId(),c.incluyeHistorico());
        if(c.revocadoEn()==null && repo.revocar(c,actor.usuarioId(),motivo)) { repo.auditar(actor.usuarioId(),usuario,"PERMISO_REVOCAR",motivo,json.writeValueAsString(c),json.writeValueAsString(repo.concesion(usuario,id,e.porPeriodo()).orElseThrow())); }
        return repo.concesion(usuario,id,e.porPeriodo()).orElseThrow();
    }
}
