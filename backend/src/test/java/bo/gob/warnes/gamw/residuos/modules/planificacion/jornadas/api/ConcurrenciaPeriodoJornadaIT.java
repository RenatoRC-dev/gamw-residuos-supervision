package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.GestionarEmpresasOperadoras;
import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.GestionarPeriodosServicio;
import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion.ModificacionHistoricaIncompatible;
import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.GestionarJornadas;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.config.PostgresV8Aislado;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * H1 (auditoría F1): carrera CU-05 (corrección de periodo / estado de empresa) contra CU-11/CU-24 (programación) en PostgreSQL aislado,
 * con el código real de los servicios y transacciones intercaladas de forma determinista (una queda abierta hasta que la otra espera un
 * bloqueo). Sin el bloqueo compartido del periodo/empresa al programar, la programación validaba con fechas ya corregidas por otra
 * transacción en curso y persistía una jornada fuera del periodo.
 */
@SpringBootTest(properties={"spring.config.import=","debug=false","logging.level.root=ERROR"})
class ConcurrenciaPeriodoJornadaIT extends IntegracionPostgresV8 {
    @Autowired GestionarJornadas jornadas;
    @Autowired GestionarPeriodosServicio periodos;
    @Autowired GestionarEmpresasOperadoras empresas;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired JdbcTemplate jdbc;
    private final ExecutorService hilos = Executors.newFixedThreadPool(2);
    private static final Instant JUNIO = Instant.parse("2026-06-15T12:00:00Z");   // 15/06/2026 08:00 en Bolivia

    @AfterEach void cerrar() { hilos.shutdownNow(); }

    private record Fixture(UUID empresa, UUID periodo, UUID version) { }
    private Fixture fixture() {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID();
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Concurrencia "+empresa);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2026-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"CC-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.1 -17.7,-63.1 -17.6)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        return new Fixture(empresa,periodo,version);
    }
    private GestionarJornadas.Programacion programacion(Fixture f) { return new GestionarJornadas.Programacion(f.periodo(),f.version(),JUNIO,null); }
    private int jornadas(Fixture f) { return jdbc.queryForObject("SELECT count(*) FROM jornada WHERE periodo_servicio_id=?",Integer.class,f.periodo()); }
    /** Espera (por condición, con límite) a que otra sesión de la base quede bloqueada esperando un bloqueo de fila. */
    private void esperarBloqueo() throws Exception {
        long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        try(var c=PostgresV8Aislado.obtener().conexion(false); var s=c.prepareStatement(
                // pg_locks (no pg_stat_activity): visible para cualquier rol, también cuando la sesión bloqueada es la del rol de ejecución.
                // La espera por un bloqueo de fila es una espera sobre el «transactionid» del titular (sin columna database): no se filtra.
                "SELECT count(*) FROM pg_locks WHERE NOT granted")) {
            while(System.nanoTime()<limite) {
                try(var r=s.executeQuery()) { r.next(); if(r.getInt(1)>0) return; }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
            }
        }
        fail("Ninguna sesión llegó a esperar el bloqueo: el intercalado no se produjo.");
    }
    /** Ejecuta {@code accion} en una transacción que queda ABIERTA hasta liberar {@code confirmar}. */
    private Future<Object> transaccionAbierta(Callable<Object> accion, CountDownLatch hecho, CountDownLatch confirmar) {
        return hilos.submit(() -> new TransactionTemplate(transacciones).execute(estado -> {
            try { Object r=accion.call(); hecho.countDown(); assertThat(confirmar.await(30,TimeUnit.SECONDS)).isTrue(); return r; }
            catch(RuntimeException e) { hecho.countDown(); throw e; }
            catch(Exception e) { hecho.countDown(); throw new IllegalStateException(e); }
        }));
    }

    @Test @Timeout(60)
    void unaCorreccionDeFechasEnCursoHaceQueLaProgramacionValideContraLasFechasConfirmadas() throws Exception {
        var f=fixture(); var hecho=new CountDownLatch(1); var confirmar=new CountDownLatch(1);
        var correccion=transaccionAbierta(() -> periodos.actualizar(f.empresa(),f.periodo(),LocalDate.parse("2026-01-01"),LocalDate.parse("2026-05-31")),hecho,confirmar);
        assertThat(hecho.await(15,TimeUnit.SECONDS)).isTrue();          // periodo corregido (fin 31/05), sin confirmar
        var programar=hilos.submit(() -> jornadas.crear(programacion(f)));
        esperarBloqueo();                                                // la programación espera al periodo bloqueado
        confirmar.countDown(); correccion.get(15,TimeUnit.SECONDS);
        assertThatThrownBy(() -> programar.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("fuera del periodo");
        assertThat(jornadas(f)).as("ninguna jornada queda fuera del periodo corregido").isZero();
        assertThat(jdbc.queryForObject("SELECT fecha_fin::text FROM periodo_servicio WHERE id=?",String.class,f.periodo())).isEqualTo("2026-05-31");
    }

    @Test @Timeout(60)
    void unaProgramacionEnCursoHaceQueLaCorreccionIncompatibleSeRechace() throws Exception {
        var f=fixture(); var hecho=new CountDownLatch(1); var confirmar=new CountDownLatch(1);
        var programacion=transaccionAbierta(() -> jornadas.crear(programacion(f)),hecho,confirmar);
        assertThat(hecho.await(15,TimeUnit.SECONDS)).isTrue();          // jornada 15/06 insertada, sin confirmar
        var correccion=hilos.submit(() -> periodos.actualizar(f.empresa(),f.periodo(),LocalDate.parse("2026-01-01"),LocalDate.parse("2026-05-31")));
        esperarBloqueo();
        confirmar.countDown(); programacion.get(15,TimeUnit.SECONDS);
        assertThatThrownBy(() -> correccion.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(ModificacionHistoricaIncompatible.class);
        assertThat(jornadas(f)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT fecha_fin::text FROM periodo_servicio WHERE id=?",String.class,f.periodo())).as("periodo intacto").isEqualTo("2026-12-31");
    }

    @Test @Timeout(60)
    void unaDesactivacionDeEmpresaEnCursoImpideProgramarConDatosObsoletos() throws Exception {
        var f=fixture(); var hecho=new CountDownLatch(1); var confirmar=new CountDownLatch(1);
        var desactivacion=transaccionAbierta(() -> empresas.cambiarEstado(f.empresa(),false),hecho,confirmar);
        assertThat(hecho.await(15,TimeUnit.SECONDS)).isTrue();
        var programar=hilos.submit(() -> jornadas.crear(programacion(f)));
        esperarBloqueo();
        confirmar.countDown(); desactivacion.get(15,TimeUnit.SECONDS);
        assertThatThrownBy(() -> programar.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("deben estar activos");
        assertThat(jornadas(f)).isZero();
    }

    @Test @Timeout(60)
    void dosProgramacionesConcurrentesDelMismoPeriodoNoSeBloqueanEntreSi() throws Exception {
        var f=fixture(); var hecho=new CountDownLatch(1); var confirmar=new CountDownLatch(1);
        var primera=transaccionAbierta(() -> jornadas.crear(programacion(f)),hecho,confirmar);
        assertThat(hecho.await(15,TimeUnit.SECONDS)).isTrue();
        // Con la primera aún abierta, la segunda termina: el bloqueo es compartido, no exclusivo.
        var segunda=hilos.submit(() -> jornadas.crear(new GestionarJornadas.Programacion(f.periodo(),f.version(),JUNIO.plusSeconds(3600),null)));
        assertThat(segunda.get(15,TimeUnit.SECONDS)).isNotNull();
        confirmar.countDown(); primera.get(15,TimeUnit.SECONDS);
        assertThat(jornadas(f)).isEqualTo(2);
    }
}
