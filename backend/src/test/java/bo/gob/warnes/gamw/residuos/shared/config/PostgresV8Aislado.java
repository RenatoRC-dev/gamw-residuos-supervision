package bo.gob.warnes.gamw.residuos.shared.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/** Solo tests: cada JVM crea su propio clúster. Nunca toma DB_*, .env o bases existentes. */
public final class PostgresV8Aislado implements AutoCloseable {
    private final Path bin = Path.of(System.getenv().getOrDefault("GAMW_PG_BIN", "C:/Program Files/PostgreSQL/16/bin"));
    private final Path raiz;
    private final Path datos;
    private final int puerto;
    private final String clave = UUID.randomUUID().toString() + UUID.randomUUID();
    private final String marca = UUID.randomUUID().toString();
    private int secuenciaProceso;
    private boolean iniciado;
    public final MigrateResult instalacionLimpia;
    public final MigrateResult instalacionV7;

    private static final class Instancia {
        private static final PostgresV8Aislado VALOR = crear();
        private static PostgresV8Aislado crear() {
            try { return new PostgresV8Aislado(); }
            catch (Exception error) { throw new IllegalStateException("No se pudo crear PostgreSQL aislado; no hay fallback.", error); }
        }
    }

    public static PostgresV8Aislado obtener() { return Instancia.VALOR; }

    private PostgresV8Aislado() throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")) {
            throw new IllegalStateException("Este aprovisionamiento requiere los binarios PostgreSQL 16.6 para Windows.");
        }
        raiz = Files.createTempDirectory("gamw-v8-it-").toRealPath();
        datos = raiz.resolve("data");
        puerto = puertoDisponible();
        Files.writeString(raiz.resolve("instance.marker"), marca, StandardCharsets.UTF_8);
        try {
            if (!ejecutar("postgres", "--version").contains("16.6")) {
                throw new IllegalStateException("Se requiere PostgreSQL 16.6; no se utilizará otro servidor.");
            }
            ejecutar("initdb", "-D", datos.toString(), "-U", "gamw_bootstrap_it", "--auth-local=trust",
                    "--auth-host=trust", "--encoding=UTF8", "--locale=C");
            Files.writeString(datos.resolve("postgresql.conf"), "\nlisten_addresses='127.0.0.1'\nport=" + puerto + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
            // Un start fallido puede haber creado un proceso: close verifica PID y propiedad.
            iniciado = true;
            ejecutar("pg_ctl", "-D", datos.toString(), "-l", raiz.resolve("postgres.log").toString(), "-w", "start");
            verificarProcesoPropio();
            ejecutar("pg_isready", "-h", "127.0.0.1", "-p", Integer.toString(puerto), "-U", "gamw_bootstrap_it", "-d", "postgres");
            try (var admin = DriverManager.getConnection(url("postgres"), "gamw_bootstrap_it", ""); var sql = admin.createStatement()) {
                verificarServidor(admin, "postgres");
                try (var r = sql.executeQuery("SELECT current_setting('data_directory'), current_setting('listen_addresses'), current_setting('server_version')")) {
                    if (!r.next() || !datos.toRealPath().equals(Path.of(r.getString(1)).toRealPath())
                            || !"127.0.0.1".equals(r.getString(2)) || !r.getString(3).startsWith("16.6")) {
                        throw new IllegalStateException("El servidor nuevo no coincide con el clúster propio.");
                    }
                }
                sql.execute("ALTER ROLE gamw_bootstrap_it PASSWORD '" + clave + "'");
                sql.execute("CREATE ROLE gamw_app_dev LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '" + clave + "'");
                for (String base : List.of("gamw_v8_clean", "gamw_v8_upgrade")) {
                    sql.execute("CREATE DATABASE " + base + " OWNER gamw_app_dev TEMPLATE template0");
                }
            }
            Files.writeString(datos.resolve("pg_hba.conf"), "host all all 127.0.0.1/32 scram-sha-256\n", StandardCharsets.UTF_8);
            ejecutar("pg_ctl", "-D", datos.toString(), "reload");
            // Esperar al cambio de autenticación antes de aprovisionar con SCRAM.
            try (var prueba = conexionAdminLista()) { verificarServidor(prueba, "postgres"); }
            for (String base : List.of("gamw_v8_clean", "gamw_v8_upgrade")) {
                try (var admin = DriverManager.getConnection(url(base), "gamw_bootstrap_it", clave); var sql = admin.createStatement()) {
                    verificarServidor(admin, base);
                    sql.execute("CREATE EXTENSION postgis");
                    sql.execute("ALTER SCHEMA public OWNER TO gamw_app_dev");
                    try (var version = sql.executeQuery("SELECT postgis_lib_version()")) {
                        if (!version.next() || !version.getString(1).startsWith("3.")) throw new IllegalStateException("PostGIS no está disponible.");
                        System.out.println("PostGIS " + version.getString(1) + " verificado en " + base);
                    }
                }
            }
            instalacionLimpia = flyway(false, null).migrate();
            instalacionV7 = flyway(true, "7").migrate();
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, "cerrar-postgres-v8-aislado"));
            System.out.println("PostgreSQL 16.6 aislado listo: puerto=" + puerto + "; directorio=" + raiz);
        } catch (Exception error) {
            close();
            // No imprimir SQL, contraseñas ni mensajes JDBC que pudieran incluirlos.
            throw new IllegalStateException("Falló el aprovisionamiento o migración del clúster nuevo: "
                    + error.getClass().getSimpleName() + ". " + String.valueOf(error.getMessage()).replace(clave, "[oculto]")
                    + ". Sin fallback; diagnóstico: " + raiz);
        }
    }

    private int puertoDisponible() throws IOException {
        try (var socket = new ServerSocket(55432, 1, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); }
        catch (IOException ocupado) {
            try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); }
        }
    }

    private String url(String base) { return "jdbc:postgresql://127.0.0.1:" + puerto + "/" + base; }
    public String urlLimpia() { return url("gamw_v8_clean"); }
    public String usuario() { return "gamw_app_dev"; }
    public String clave() { return clave; }

    public Connection conexion(boolean actualizacion) throws Exception {
        verificarProcesoPropio();
        String base = actualizacion ? "gamw_v8_upgrade" : "gamw_v8_clean";
        Connection conexion = DriverManager.getConnection(url(base), usuario(), clave);
        try { verificarServidor(conexion, base); return conexion; }
        catch (Exception error) { conexion.close(); throw error; }
    }

    private void verificarServidor(Connection conexion, String base) throws Exception {
        verificarProcesoPropio();
        try (var sql = conexion.createStatement(); var r = sql.executeQuery(
                "SELECT current_database(), inet_server_port(), host(inet_server_addr())")) {
            if (!r.next() || !base.equals(r.getString(1)) || puerto != r.getInt(2)
                    || !"127.0.0.1".equals(r.getString(3))) {
                throw new IllegalStateException("Destino inesperado; operación bloqueada.");
            }
        }
    }

    public Flyway flyway(boolean actualizacion, String objetivo) {
        try { verificarProcesoPropio(); }
        catch (Exception error) { throw new IllegalStateException("No se verifica la propiedad del clúster; Flyway bloqueado."); }
        var configuracion = Flyway.configure().dataSource(url(actualizacion ? "gamw_v8_upgrade" : "gamw_v8_clean"), usuario(), clave)
                .locations("classpath:db/migration").cleanDisabled(true).validateOnMigrate(true);
        if (objetivo != null) configuracion.target(objetivo);
        return configuracion.load();
    }

    private String ejecutar(String herramienta, String... argumentos) throws Exception {
        var comando = new ArrayList<String>();
        comando.add(bin.resolve(herramienta + ".exe").toString());
        comando.addAll(List.of(argumentos));
        var builder = new ProcessBuilder(comando).redirectErrorStream(true);
        for (String variable : List.of("PGHOST", "PGPORT", "PGUSER", "PGDATABASE", "PGPASSWORD", "PGPASSFILE", "PGSERVICE", "PGSERVICEFILE", "PGOPTIONS", "PGDATA")) {
            builder.environment().remove(variable);
        }
        Path salida = raiz.resolve(String.format("%02d-%s.log", ++secuenciaProceso, herramienta));
            var proceso = builder.redirectOutput(salida.toFile()).start();
            if (!proceso.waitFor(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS)) {
                proceso.destroyForcibly();
                throw new IllegalStateException(herramienta + ": tiempo de espera agotado");
            }
            if (proceso.exitValue() != 0) throw new IllegalStateException(herramienta + ": exit=" + proceso.exitValue() + "; salida: " + salida);
            var codificacionSalida = Charset.forName(
                    System.getProperty("native.encoding", "windows-1252"));
            return Files.readString(salida, codificacionSalida);
    }

    private void verificarProcesoPropio() throws Exception {
        if (!marca.equals(Files.readString(raiz.resolve("instance.marker"), StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Marcador de instancia propio inválido.");
        }
        var pid = Files.readAllLines(datos.resolve("postmaster.pid"));
        if (pid.size() < 4 || !datos.toRealPath().equals(Path.of(pid.get(1)).toRealPath())
                || Integer.parseInt(pid.get(3)) != puerto
                || !ProcessHandle.of(Long.parseLong(pid.getFirst())).map(ProcessHandle::isAlive).orElse(false)) {
            throw new IllegalStateException("PID, directorio o puerto no pertenecen al clúster temporal.");
        }
    }

    private Connection conexionAdminLista() throws Exception {
        Exception ultimo = null;
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < limite) {
            verificarProcesoPropio();
            try { return DriverManager.getConnection(url("postgres"), "gamw_bootstrap_it", clave); }
            catch (java.sql.SQLException error) { ultimo = error; Thread.sleep(100); }
        }
        throw new IllegalStateException("No se pudo verificar conectividad del bootstrap aislado: "
                + (ultimo == null ? "sin respuesta" : ultimo.getClass().getSimpleName()));
    }

    @Override public synchronized void close() {
        if (!iniciado) return;
        try {
            if (!datos.getParent().equals(raiz) || !Files.isRegularFile(datos.resolve("postmaster.pid"))) return;
            verificarProcesoPropio();
            ejecutar("pg_ctl", "-D", datos.toString(), "-m", "fast", "-w", "stop");
            if (Files.exists(datos.resolve("postmaster.pid"))) throw new IllegalStateException("El PID del clúster sigue presente después de stop.");
            iniciado = false;
            System.out.println("Clúster propio detenido; directorio preservado: " + raiz);
        } catch (Exception error) { System.err.println("No se pudo detener el clúster temporal propio: " + raiz); }
        // Nunca borrar bases, directorios de datos ni volúmenes. El clúster queda detenido.
    }
}
