package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion;

import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

/** Permiso explícito por periodo. No hereda empresas, periodos anteriores ni funciones laborales. */
@Component
public class AutorizacionOperador {
    private final JdbcTemplate jdbc;
    public AutorizacionOperador(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void exigirListado(UsuarioAutenticado actor) {
        identidad(actor);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM concesion_periodo_operador c JOIN usuario u ON u.id=c.usuario_id
              WHERE u.id=? AND u.activo AND c.permiso='CORRECTIVA_CONSULTAR' AND c.revocado_en IS NULL)
            """,Boolean.class,actor.usuarioId()))) throw new AccessDeniedException("Sin concesión de consulta empresarial.");
    }
    public UUID exigirConsulta(UsuarioAutenticado actor,UUID accion) { return exigir(actor,accion,"CORRECTIVA_CONSULTAR",false); }
    public void exigirCapacidadRespuesta(UsuarioAutenticado actor,UUID accion) { exigir(actor,accion,"CORRECTIVA_RESPONDER",false); }
    public void exigirListadoJornadas(UsuarioAutenticado actor) {
        identidad(actor);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM concesion_periodo_operador c JOIN usuario u ON u.id=c.usuario_id
             WHERE u.id=? AND u.activo AND c.permiso IN ('JORNADA_REPROGRAMAR','PLANIFICACION_CONSULTAR','JORNADA_PROGRAMAR','JORNADA_ASIGNAR') AND c.revocado_en IS NULL)
            """,Boolean.class,actor.usuarioId()))) throw new AccessDeniedException("Sin concesión de planificación por periodo.");
    }
    public UUID exigirJornada(UsuarioAutenticado actor,UUID jornada,boolean bloquear) {
        return exigirJornadaOperativa(actor,jornada,"JORNADA_REPROGRAMAR",bloquear);
    }
    public UUID exigirJornadaOperativa(UsuarioAutenticado actor,UUID jornada,String permiso,boolean bloquear) {
        if(!java.util.Set.of("JORNADA_REPROGRAMAR","JORNADA_ASIGNAR").contains(permiso)) throw new AccessDeniedException("Capacidad no admitida.");
        identidad(actor);
        var filas=jdbc.queryForList("""
            SELECT c.id FROM concesion_periodo_operador c JOIN usuario u ON u.id=c.usuario_id
             JOIN jornada j ON j.periodo_servicio_id=c.periodo_servicio_id
            WHERE u.id=? AND u.activo AND j.id=? AND c.permiso=? AND c.revocado_en IS NULL
             AND (c.incluye_historico OR (j.inicio_programado>=c.otorgado_en AND j.creado_en>=c.otorgado_en))
            """+(bloquear?" FOR SHARE OF c,u":""),UUID.class,actor.usuarioId(),jornada,permiso);
        if(filas.isEmpty()) throw new AccessDeniedException("Jornada fuera del periodo o alcance expresamente concedido.");
        return filas.getFirst();
    }
    public void exigirConsultaPlan(UsuarioAutenticado actor,UUID jornada) {
        identidad(actor);
        boolean permitido=Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM concesion_periodo_operador c JOIN usuario u ON u.id=c.usuario_id JOIN jornada j ON j.periodo_servicio_id=c.periodo_servicio_id
             WHERE u.id=? AND u.activo AND j.id=? AND c.revocado_en IS NULL
              AND c.permiso IN ('PLANIFICACION_CONSULTAR','JORNADA_PROGRAMAR','JORNADA_ASIGNAR','JORNADA_REPROGRAMAR')
              AND (c.incluye_historico OR (j.inicio_programado>=c.otorgado_en AND j.creado_en>=c.otorgado_en)))
            """,Boolean.class,actor.usuarioId(),jornada));
        if(!permitido) throw new AccessDeniedException("Jornada fuera del alcance de planificación concedido.");
    }
    public UUID exigirProgramacionPeriodo(UsuarioAutenticado actor,UUID periodo,boolean bloquear) {
        identidad(actor);
        var filas=jdbc.queryForList("""
            SELECT c.id FROM concesion_periodo_operador c JOIN usuario u ON u.id=c.usuario_id WHERE u.id=? AND u.activo
             AND c.periodo_servicio_id=? AND c.permiso='JORNADA_PROGRAMAR' AND c.revocado_en IS NULL
            """+(bloquear?" FOR SHARE OF c,u":""),UUID.class,actor.usuarioId(),periodo);
        if(filas.isEmpty()) throw new AccessDeniedException("No puede programar en este periodo.");return filas.getFirst();
    }
    public void exigirFechaSucesora(UUID concesion,java.time.Instant inicio) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT incluye_historico OR ?::timestamptz>=otorgado_en FROM concesion_periodo_operador WHERE id=? AND revocado_en IS NULL",
            Boolean.class,java.sql.Timestamp.from(inicio),concesion))) throw new AccessDeniedException("La fecha propuesta requiere autorización histórica explícita.");
    }
    public UUID exigirRespuesta(UsuarioAutenticado actor,UUID accion) {
        exigir(actor,accion,"CORRECTIVA_CONSULTAR",true);
        return exigir(actor,accion,"CORRECTIVA_RESPONDER",true);
    }
    private void identidad(UsuarioAutenticado actor) { if(actor==null) throw new InsufficientAuthenticationException("Identidad autenticada requerida."); }
    private UUID exigir(UsuarioAutenticado actor,UUID accion,String permiso,boolean bloquear) {
        identidad(actor);
        var concesiones=jdbc.queryForList("""
            SELECT c.id FROM concesion_periodo_operador c JOIN usuario u ON u.id=c.usuario_id
             JOIN jornada j ON j.periodo_servicio_id=c.periodo_servicio_id JOIN accion_correctiva a ON a.jornada_id=j.id
            WHERE u.id=? AND u.activo AND a.id=? AND c.permiso=? AND c.revocado_en IS NULL
             AND (c.incluye_historico OR (j.inicio_programado>=c.otorgado_en AND a.creada_en>=c.otorgado_en))
            """+(bloquear?" FOR SHARE OF c,u":""),UUID.class,actor.usuarioId(),accion,permiso);
        if(concesiones.isEmpty()) throw new AccessDeniedException("Acción fuera del periodo o alcance expresamente concedido.");
        return concesiones.getFirst();
    }
}
