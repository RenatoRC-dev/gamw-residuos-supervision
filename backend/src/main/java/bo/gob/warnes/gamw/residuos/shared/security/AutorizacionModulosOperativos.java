package bo.gob.warnes.gamw.residuos.shared.security;

import java.util.Set;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/** Frontera HTTP de los controladores preexistentes; no altera sus reglas de negocio ni contratos. */
@Configuration(proxyBeanMethods=false)
public class AutorizacionModulosOperativos implements WebMvcConfigurer,HandlerInterceptor {
    private final JdbcTemplate jdbc;
    private final Environment entorno;
    public AutorizacionModulosOperativos(JdbcTemplate jdbc,Environment entorno) { this.jdbc=jdbc;this.entorno=entorno; }
    @Override public void addInterceptors(InterceptorRegistry registro) { registro.addInterceptor(this).addPathPatterns("/api/**"); }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if(!(handler instanceof HandlerMethod metodo)) return true;
        String clase=metodo.getBeanType().getSimpleName(),modulo;
        String ruta=java.util.Objects.toString(request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),"");
        if(clase.equals("EmpresasOperadorasController")) modulo=ruta.contains("/periodos")?"PERIODOS":ruta.contains("/personal") || ruta.endsWith("/funciones-operativas")?"PERSONAS":"EMPRESAS";
        else if(clase.equals("PersonasController")) modulo="PERSONAS";
        else if(clase.equals("VehiculosController") || clase.equals("VinculosVehiculoEmpresaController")) modulo="VEHICULOS";
        else if(clase.equals("RutasReferenciaController")) modulo="RUTAS";
        else if(clase.equals("JornadasController")) modulo="JORNADAS";
        else if(clase.equals("PeriodosPlanificacionController")) modulo="PERIODOS";
        else return true;
        // Atributo interno emitido solo tras superar la cadena local; no se acepta una cabecera como identidad.
        if(entorno.acceptsProfiles(Profiles.of("desarrollo-local")) && Boolean.TRUE.equals(request.getAttribute("gamw.acceso-local-validado"))) return true;
        var autenticacion=SecurityContextHolder.getContext().getAuthentication();
        if(autenticacion==null || !(autenticacion.getPrincipal() instanceof UsuarioAutenticado actor)) throw new AccessDeniedException("Identidad institucional requerida.");
        boolean lectura=Set.of("GET","HEAD").contains(request.getMethod());
        if(!lectura && ruta.endsWith("/reprogramaciones")) throw new AccessDeniedException("Reprogramación institucional exclusivamente por /api/empresa/jornadas y concesión por periodo.");
        String permiso=modulo+(lectura?"_CONSULTAR":modulo.equals("JORNADAS")?(ruta.endsWith("/asignaciones") || ruta.endsWith("/reasignaciones")?"_ASIGNAR":"_PROGRAMAR"):"_GESTIONAR");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM concesion_permiso c JOIN usuario u ON u.id=c.usuario_id WHERE u.id=? AND u.activo
             AND c.permiso=? AND c.alcance='MUNICIPAL_GAMW' AND c.revocado_en IS NULL)
            """,Boolean.class,actor.usuarioId(),permiso))) throw new AccessDeniedException("Permiso municipal explícito requerido; el ámbito empresarial usa las rutas /api/empresa con periodo validado.");
        response.setHeader("Cache-Control","no-store");response.setHeader("Vary","Authorization");return true;
    }
}
