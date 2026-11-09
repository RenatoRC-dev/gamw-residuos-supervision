package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion;

import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

/** D1-A provisional: solo lectura explícita municipal en esta plataforma de GAMW.
 * No deriva privilegios de PersonaNatural, funciones laborales, claims o nombres.
 */
@Component
public class AutorizacionMunicipal {
    private final JdbcTemplate jdbc;
    public AutorizacionMunicipal(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void exigirConsultaTrayectoria(UsuarioAutenticado actor) {
        exigir(actor,"TRAYECTORIA_JORNADA_CONSULTAR");
    }
    public void exigirConsultaMonitoreo(UsuarioAutenticado actor) {
        exigir(actor,"MONITOREO_JORNADA_CONSULTAR");
    }
    public void exigirLecturaReferencias(UsuarioAutenticado actor) { exigir(actor,"REFERENCIA_ESPACIAL_CONSULTAR"); }
    public void exigirGestionReferencias(UsuarioAutenticado actor) { exigir(actor,"REFERENCIA_ESPACIAL_GESTIONAR"); }
    public void exigirRegistroInspeccion(UsuarioAutenticado actor) { exigir(actor,"INSPECCION_REGISTRAR"); }
    public void exigirConsultaFiscalizacion(UsuarioAutenticado actor) { exigir(actor,"FISCALIZACION_CONSULTAR"); }
    public void exigirEmisionFiscalizacion(UsuarioAutenticado actor) { exigir(actor,"FISCALIZACION_EMITIR"); }
    public void exigirRevision(UsuarioAutenticado actor,String tipo) {
        exigir(actor,switch(tipo) {
            case "integridad" -> "GPS_INTEGRIDAD_REVISAR";
            case "coincidencias" -> "GPS_COINCIDENCIAS_REVISAR";
            case "desvios" -> "GPS_DESVIOS_REVISAR";
            default -> throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Revisión no admitida.");
        });
    }
    private void exigir(UsuarioAutenticado actor,String permiso) {
        if (actor == null) throw new InsufficientAuthenticationException("Identidad autenticada obligatoria.");
        boolean permitido = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM concesion_permiso c JOIN usuario u ON u.id=c.usuario_id
                  WHERE c.usuario_id=? AND u.activo AND c.permiso=?
                    AND c.alcance='MUNICIPAL_GAMW' AND c.revocado_en IS NULL)
                """, Boolean.class, actor.usuarioId(),permiso));
        if (!permitido) throw new AccessDeniedException("Consulta municipal no autorizada.");
    }
}
