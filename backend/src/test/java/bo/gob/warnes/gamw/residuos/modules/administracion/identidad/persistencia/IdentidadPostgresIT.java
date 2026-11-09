package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.persistencia;

import bo.gob.warnes.gamw.residuos.shared.config.PostgresV8Aislado;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** PostgreSQL/PostGIS real; solo clúster propio, fixtures sintéticos y rollback por caso. */
class IdentidadPostgresIT {
    private static final Instant CAPTURA = Instant.parse("2026-11-08T12:34:56.123456Z");
    private static final String HASH = new BCryptPasswordEncoder().encode("clave-sintetica-postgres");
    private Connection conexion;
    private JdbcTemplate jdbc;
    private Datos datos;

    @BeforeEach void preparar() throws Exception {
        conexion = PostgresV8Aislado.obtener().conexion(false);
        conexion.setAutoCommit(false);
        jdbc = jdbc(conexion);
        datos = fixture(jdbc);
    }

    @AfterEach void limpiarSoloTransaccionPropia() throws Exception {
        if (conexion != null) { try { conexion.rollback(); } finally { conexion.close(); } }
    }

    private static JdbcTemplate jdbc(Connection conexion) {
        return new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
    }

    @Test void instalaV1AV8YValidaSinCambiosAdicionales() {
        var entorno = PostgresV8Aislado.obtener();
        assertThat(entorno.instalacionLimpia.migrationsExecuted).isEqualTo(8);
        var flyway = entorno.flyway(false, null);
        assertThat(flyway.info().applied()).hasSize(8);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT postgis_lib_version()", String.class)).startsWith("3.");
        assertThat(jdbc.queryForObject("SELECT version()", String.class)).contains("PostgreSQL 16.6");
    }

    @Test void actualizaV7AV8SinReescribirEvidenciaNiHistoriaFlyway() throws Exception {
        var entorno = PostgresV8Aislado.obtener();
        assertThat(entorno.instalacionV7.migrationsExecuted).isEqualTo(7);
        try (var actualizacion = entorno.conexion(true)) {
            var sql = jdbc(actualizacion);
            Datos anteriores = fixture(sql);
            insertarEvento(sql, anteriores, "INICIO", null, false);
            insertarEvento(sql, anteriores, "FIN", null, false);
            String columnas = "id,evento_cliente_id,jornada_id,asignacion_jornada_id,tipo_evento,capturado_en,recibido_en";
            var evidencia = sql.queryForList("SELECT " + columnas + " FROM evento_ejecucion_jornada ORDER BY tipo_evento");
            var historia = sql.queryForList("SELECT installed_rank,version,checksum,success,installed_on FROM flyway_schema_history ORDER BY installed_rank");
            var flyway = entorno.flyway(true, null);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
            assertThat(sql.queryForList("SELECT " + columnas + " FROM evento_ejecucion_jornada ORDER BY tipo_evento")).isEqualTo(evidencia);
            assertThat(sql.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE usuario_id IS NULL", Integer.class)).isEqualTo(2);
            assertThat(sql.queryForList("SELECT installed_rank,version,checksum,success,installed_on FROM flyway_schema_history WHERE version <> '8' ORDER BY installed_rank")).isEqualTo(historia);
            assertThat(sql.queryForList("SELECT * FROM usuario")).isEmpty();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            var repositorio = new bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.persistencia.RepositorioEventosEjecucionPostgres(sql);
            assertThat(repositorio.porJornada(anteriores.jornada())).hasSize(2);
        }
    }

    @Test void repositorioRealRecuperaTodosLosCamposYRespetaBusquedaExacta() {
        UUID usuario = insertarUsuario("Usuario.Prueba", false);
        var repositorio = new RepositorioUsuariosPostgres(jdbc);
        var recuperado = repositorio.porId(usuario).orElseThrow();
        assertThat(recuperado.id()).isEqualTo(usuario);
        assertThat(recuperado.personaNaturalId()).isEqualTo(datos.persona());
        assertThat(recuperado.nombreUsuario()).isEqualTo("Usuario.Prueba");
        assertThat(recuperado.contrasenaHash()).isEqualTo(HASH);
        assertThat(recuperado.activo()).isFalse();
        assertThat(recuperado.creadoEn()).isEqualTo(CAPTURA);
        assertThat(recuperado.actualizadoEn()).isEqualTo(CAPTURA.plusSeconds(1));
        assertThat(repositorio.porNombreUsuario("Usuario.Prueba")).contains(recuperado);
        assertThat(repositorio.porNombreUsuario("usuario.prueba")).isEmpty();
        assertThat(repositorio.porId(UUID.randomUUID())).isEmpty();
        assertThat(repositorio.porNombreUsuario("inexistente")).isEmpty();
    }

    @Test void restriccionesUsuarioRechazanDuplicadosNulosVaciosYPersonaInexistente() throws Exception {
        UUID id = insertarUsuario("usuario.unico", true);
        falla("INSERT INTO usuario SELECT ?,persona_natural_id,nombre_usuario,contrasena_hash,activo,creado_en,actualizado_en FROM usuario WHERE id=?", UUID.randomUUID(), id);
        falla("INSERT INTO usuario SELECT id,persona_natural_id,'otro',contrasena_hash,activo,creado_en,actualizado_en FROM usuario WHERE id=?", id);
        for (String columna : List.of("id", "persona_natural_id", "nombre_usuario", "contrasena_hash", "activo", "creado_en", "actualizado_en")) {
            falla("UPDATE usuario SET " + columna + "=NULL WHERE id=?", id);
        }
        for (String nombre : List.of("", " extremo", "extremo ", " ")) falla("UPDATE usuario SET nombre_usuario=? WHERE id=?", nombre, id);
        for (String hash : List.of("", "   ")) falla("UPDATE usuario SET contrasena_hash=? WHERE id=?", hash, id);
        falla("UPDATE usuario SET persona_natural_id=? WHERE id=?", UUID.randomUUID(), id);
    }

    @Test void personaPuedeExistirSinCuentaOConDosUsuariosDiferentes() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE persona_natural_id=?", Integer.class, datos.persona())).isZero();
        insertarUsuario("cuenta.uno", true);
        insertarUsuario("cuenta.dos", true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE persona_natural_id=?", Integer.class, datos.persona())).isEqualTo(2);
    }

    @Test void eventoAdmiteUsuarioNuloOValidoYRechazaReferenciaInexistente() throws Exception {
        UUID usuario = insertarUsuario("usuario.evento", true);
        insertarEvento(jdbc, datos, "INICIO", null, true);
        falla("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'FIN',?,?,?)", UUID.randomUUID(), UUID.randomUUID(), datos.jornada(), datos.asignacion(), Timestamp.from(CAPTURA), Timestamp.from(CAPTURA.plusSeconds(2)), UUID.randomUUID());
        insertarEvento(jdbc, datos, "FIN", usuario, true);
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM evento_ejecucion_jornada WHERE tipo_evento='INICIO' AND jornada_id=?", UUID.class, datos.jornada())).isNull();
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM evento_ejecucion_jornada WHERE tipo_evento='FIN' AND jornada_id=?", UUID.class, datos.jornada())).isEqualTo(usuario);
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='evento_ejecucion_jornada' AND column_name='usuario_id'", String.class)).isEqualTo("YES");
    }

    @Test void clavesIndicesYBorradosSinCascadaProtegenIdentidadHistorica() throws Exception {
        UUID usuario = insertarUsuario("usuario.referenciado", true);
        insertarEvento(jdbc, datos, "INICIO", usuario, true);
        assertThat(jdbc.queryForList("""
                SELECT conname FROM pg_constraint WHERE contype='f' AND confdeltype='a' AND
                ((conrelid='usuario'::regclass AND confrelid='persona_natural'::regclass) OR
                 (conrelid='evento_ejecucion_jornada'::regclass AND confrelid='usuario'::regclass)) ORDER BY conname
                """, String.class)).containsExactly("evento_ejecucion_usuario_fk", "usuario_persona_natural_fk");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE contype='f' AND conrelid IN ('usuario'::regclass,'evento_ejecucion_jornada'::regclass) AND confdeltype='c'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid WHERE c.relname='evento_ejecucion_usuario_idx' AND i.indrelid='evento_ejecucion_jornada'::regclass AND i.indisvalid AND pg_get_indexdef(i.indexrelid) LIKE '%(usuario_id)%'", Integer.class)).isEqualTo(1);
        falla("DELETE FROM usuario WHERE id=?", usuario);
        falla("DELETE FROM persona_natural WHERE id=?", datos.persona());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id=?", Integer.class, usuario)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persona_natural WHERE id=?", Integer.class, datos.persona())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?", Integer.class, datos.jornada())).isEqualTo(1);
    }

    @Test void evidenciaSigueInmutableIncluidaAtribucionPosteriorDeUsuario() throws Exception {
        UUID usuario = insertarUsuario("usuario.inmutable", true);
        insertarEvento(jdbc, datos, "INICIO", null, true);
        var antes = jdbc.queryForList("SELECT * FROM evento_ejecucion_jornada WHERE jornada_id=?", datos.jornada());
        for (String columna : List.of("id", "evento_cliente_id", "jornada_id", "asignacion_jornada_id", "usuario_id")) {
            falla("UPDATE evento_ejecucion_jornada SET " + columna + "=? WHERE jornada_id=?", usuario, datos.jornada());
        }
        falla("UPDATE evento_ejecucion_jornada SET tipo_evento='FIN' WHERE jornada_id=?", datos.jornada());
        falla("UPDATE evento_ejecucion_jornada SET capturado_en=capturado_en+interval '1 second' WHERE jornada_id=?", datos.jornada());
        falla("UPDATE evento_ejecucion_jornada SET recibido_en=recibido_en+interval '1 second' WHERE jornada_id=?", datos.jornada());
        assertThat(jdbc.queryForList("SELECT * FROM evento_ejecucion_jornada WHERE jornada_id=?", datos.jornada())).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgrelid='evento_ejecucion_jornada'::regclass AND tgname='evento_ejecucion_inmutable' AND tgenabled='O'", Integer.class)).isEqualTo(1);
    }

    private UUID insertarUsuario(String nombre, boolean activo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,?,?,?)", id, datos.persona(), nombre, HASH, activo, Timestamp.from(CAPTURA), Timestamp.from(CAPTURA.plusSeconds(1)));
        return id;
    }

    private void falla(String sql, Object... parametros) throws Exception {
        var punto = conexion.setSavepoint();
        try { assertThatThrownBy(() -> jdbc.update(sql, parametros)).isInstanceOf(DataIntegrityViolationException.class); }
        finally { conexion.rollback(punto); conexion.releaseSavepoint(punto); }
    }

    private static void insertarEvento(JdbcTemplate jdbc, Datos datos, String tipo, UUID usuario, boolean v8) {
        jdbc.update("INSERT INTO evento_ejecucion_jornada(id,evento_cliente_id,jornada_id,asignacion_jornada_id,tipo_evento,capturado_en,recibido_en" + (v8 ? ",usuario_id" : "") + ") VALUES (?,?,?,?,?,?,?" + (v8 ? ",?" : "") + ")",
                v8 ? new Object[]{UUID.randomUUID(), UUID.randomUUID(), datos.jornada(), datos.asignacion(), tipo, Timestamp.from(CAPTURA), Timestamp.from(CAPTURA.plusSeconds(2)), usuario}
                   : new Object[]{UUID.randomUUID(), UUID.randomUUID(), datos.jornada(), datos.asignacion(), tipo, Timestamp.from(CAPTURA), Timestamp.from(CAPTURA.plusSeconds(2))});
    }

    private record Datos(UUID persona, UUID jornada, UUID asignacion) { }

    private static Datos fixture(JdbcTemplate jdbc) {
        UUID persona=UUID.randomUUID(), empresa=UUID.randomUUID(), periodo=UUID.randomUUID(), ruta=UUID.randomUUID(),
                version=UUID.randomUUID(), vehiculo=UUID.randomUUID(), vp=UUID.randomUUID(), vv=UUID.randomUUID(), jornada=UUID.randomUUID(), asignacion=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())", persona, "IT-"+persona, "Persona sintética");
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())", empresa, "Empresa sintética");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2027-12-31',true,now(),now())", periodo, empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())", ruta, "IT-"+ruta, "Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())", version, ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())", vehiculo, "IT-"+vehiculo.toString().toUpperCase());
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())", vv, vehiculo, empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())", vp, persona, empresa);
        jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')", vp);
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())", jornada, periodo, version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())", asignacion, jornada, vv, vp, Timestamp.from(CAPTURA.minusSeconds(3600)));
        return new Datos(persona,jornada,asignacion);
    }
}
