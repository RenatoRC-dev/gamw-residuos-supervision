package bo.gob.warnes.gamw.residuos.modules.bitacora.aplicacion;

import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Component;

/**
 * CU-31 / RN-CU-31-06. Frontera de autorización de la bitácora: exige una concesión municipal explícita, vigente y no
 * revocada de BITACORA_CONSULTAR. Ese permiso NO existe todavía en el catálogo aprobado (CHECK de concesion_permiso,
 * V1–V19) ni se concede por defecto, por rol, por PERMISOS_ADMINISTRAR ni por USUARIOS_*: hoy nadie puede obtenerlo y la
 * consulta permanece denegada. Habilitarla requiere decisión institucional D1 y una migración aditiva de catálogo.
 */
@Component
public class AutorizacionBitacora {
    public static final String PERMISO = "BITACORA_CONSULTAR";
    private final JdbcTemplate jdbc;
    public AutorizacionBitacora(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void exigir(UsuarioAutenticado actor) {
        if (actor == null) throw new InsufficientAuthenticationException("Identidad autenticada obligatoria.");
        boolean permitido = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM concesion_permiso c JOIN usuario u ON u.id=c.usuario_id
              WHERE c.usuario_id=? AND u.activo AND c.permiso=? AND c.alcance='MUNICIPAL_GAMW' AND c.revocado_en IS NULL)
            """,Boolean.class,actor.usuarioId(),PERMISO));
        if (!permitido) throw new AccessDeniedException("Consulta de bitácora no autorizada.");
    }
}
