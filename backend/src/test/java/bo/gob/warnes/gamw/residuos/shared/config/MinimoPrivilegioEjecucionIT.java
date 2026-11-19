package bo.gob.warnes.gamw.residuos.shared.config;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * INT-04 contra PostgreSQL/PostGIS aislado: la aplicación corre con un rol NO propietario y Flyway con el rol propietario.
 * Se verifica la identidad real, la ausencia de propiedad/DDL/DELETE/TRUNCATE/TRIGGER y que ni siquiera un intento directo
 * de desactivar triggers o quitar restricciones prospera (rechazo por PERMISO, no solo por trigger).
 */
@SpringBootTest(properties={"spring.config.import=","debug=false","logging.level.root=ERROR"})
@org.springframework.context.annotation.Import(EjecucionNoPropietaria.class)
class MinimoPrivilegioEjecucionIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datos;
    @Autowired Flyway flyway;

    private static void sinPermiso(Runnable sql) {
        assertThatThrownBy(sql::run).hasRootCauseInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) rootCause(e)).getSQLState()).as("insufficient_privilege / must be owner").isEqualTo("42501"));
    }
    private static Throwable rootCause(Throwable t) { while (t.getCause()!=null && t.getCause()!=t) t=t.getCause(); return t; }

    @Test void laAplicacionUsaElRolDeEjecucionSinPropiedadNiPrivilegiosDestructivos() {
        assertThat(jdbc.queryForObject("SELECT current_user",String.class)).isEqualTo(PostgresV8Aislado.ROL_EJECUCION);
        assertThat(jdbc.queryForObject("SELECT rolsuper OR rolcreaterole OR rolcreatedb OR rolbypassrls FROM pg_roles WHERE rolname=current_user",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND pg_has_role(current_user,c.relowner,'MEMBER')",Integer.class))
            .as("no es propietario ni miembro del propietario de ningún objeto").isZero();
        assertThat(jdbc.queryForObject("SELECT has_schema_privilege(current_user,'public','CREATE')",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT pg_has_role(current_user,(SELECT nspowner FROM pg_namespace WHERE nspname='public'),'MEMBER')",Boolean.class)).isFalse();
        List<String> conPrivilegioDestructivo=jdbc.queryForList("""
            SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname='public' AND c.relkind='r' AND (has_table_privilege(current_user,c.oid,'DELETE')
               OR has_table_privilege(current_user,c.oid,'TRUNCATE') OR has_table_privilege(current_user,c.oid,'TRIGGER')
               OR has_table_privilege(current_user,c.oid,'REFERENCES'))""",String.class);
        assertThat(conPrivilegioDestructivo).as("sin DELETE/TRUNCATE/TRIGGER/REFERENCES").isEmpty();
        // Evidencia inmutable: sin UPDATE (los triggers quedan como segunda barrera).
        for(String t:List.of("evento_ejecucion_jornada","posicion_gps","fiscalizacion","inspeccion","incidencia_operativa","accion_correctiva","reclamo_institucional","actuacion_administrativa"))
            assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,?,'UPDATE')",Boolean.class,t)).as("sin UPDATE en %s",t).isFalse();
    }

    @Test void flywayMigraYValidaConLaIdentidadDeMigracion() throws Exception {
        try(var c=flyway.getConfiguration().getDataSource().getConnection(); var r=c.createStatement().executeQuery("SELECT current_user")) {
            r.next(); assertThat(r.getString(1)).isEqualTo(PostgresV8Aislado.obtener().usuario());
        }
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(var c=PostgresV8Aislado.obtener().conexion(false); var r=c.createStatement().executeQuery("SELECT count(*) FROM flyway_schema_history WHERE installed_by<>current_user")) {
            r.next(); assertThat(r.getInt(1)).as("todas las migraciones instaladas por la identidad de migración").isZero();
        }
        sinPermiso(() -> jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history",Integer.class));
        try(var c=datos.getConnection(); var r=c.createStatement().executeQuery("SELECT current_user")) {
            r.next(); assertThat(r.getString(1)).as("la conexión de la aplicación no es la de Flyway").isEqualTo(PostgresV8Aislado.ROL_EJECUCION);
        }
    }

    @Test void ningunIntentoDirectoDeEludirV20V21Prospera() {
        sinPermiso(() -> jdbc.update("DELETE FROM jornada WHERE id=?",UUID.randomUUID()));
        sinPermiso(() -> jdbc.update("DELETE FROM evento_ejecucion_jornada WHERE id=?",UUID.randomUUID()));
        sinPermiso(() -> jdbc.execute("TRUNCATE evento_ejecucion_jornada"));
        sinPermiso(() -> jdbc.execute("TRUNCATE jornada CASCADE"));
        sinPermiso(() -> jdbc.execute("ALTER TABLE evento_ejecucion_jornada DISABLE TRIGGER ALL"));
        sinPermiso(() -> jdbc.execute("ALTER TABLE posicion_gps DISABLE TRIGGER posicion_gps_inmutable"));
        sinPermiso(() -> jdbc.execute("ALTER TABLE posicion_gps DROP CONSTRAINT posicion_gps_asignacion_misma_jornada_fk"));
        sinPermiso(() -> jdbc.execute("DROP TRIGGER jornada_sin_borrado ON jornada"));
        sinPermiso(() -> jdbc.execute("CREATE OR REPLACE FUNCTION proteger_evento_ejecucion() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$"));
        sinPermiso(() -> jdbc.execute("CREATE TABLE intrusa (id int)"));
        sinPermiso(() -> jdbc.update("UPDATE posicion_gps SET precision_metros=1 WHERE id=?",UUID.randomUUID()));
    }
}
