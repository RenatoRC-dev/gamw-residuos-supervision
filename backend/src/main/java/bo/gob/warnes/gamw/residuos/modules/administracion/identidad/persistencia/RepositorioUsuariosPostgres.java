package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.persistencia;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.RepositorioUsuarios;
import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.dominio.Usuario;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Requiere DataSource: no se instancia en el perfil técnico smoke, que arranca sin base de datos.
@Repository
@Profile("!desarrollo-local & !smoke")
public class RepositorioUsuariosPostgres implements RepositorioUsuarios {
    private final JdbcTemplate jdbc;

    public RepositorioUsuariosPostgres(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Usuario> porNombreUsuario(String nombreUsuario) {
        return jdbc.query("SELECT * FROM usuario WHERE nombre_usuario=?", this::leer, nombreUsuario).stream().findFirst();
    }

    public Optional<Usuario> porId(UUID id) {
        return jdbc.query("SELECT * FROM usuario WHERE id=?", this::leer, id).stream().findFirst();
    }

    private Usuario leer(ResultSet r, int i) throws SQLException {
        return new Usuario(r.getObject("id", UUID.class), r.getObject("persona_natural_id", UUID.class),
                r.getString("nombre_usuario"), r.getString("contrasena_hash"), r.getBoolean("activo"),
                r.getTimestamp("creado_en").toInstant(), r.getTimestamp("actualizado_en").toInstant());
    }
}
