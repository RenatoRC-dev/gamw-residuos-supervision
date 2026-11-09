package bo.gob.warnes.gamw.residuos.shared.config;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Solo procesos propios, Vite sin .env y backend suministrado por Spring RANDOM_PORT. */
public final class NavegadorWebAislado {
    private NavegadorWebAislado() { }

    public static int puertoDisponible() throws Exception {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    public static void ejecutar(int backend, int web, String archivo) throws Exception {
        Path directorio = Path.of("..", "apps", "web").toAbsolutePath().normalize();
        var vite = new ProcessBuilder("node", "node_modules/vite/bin/vite.js", "--host", "127.0.0.1",
                "--port", Integer.toString(web), "--strictPort").directory(directorio.toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        vite.environment().put("GAMW_WEB_INTEGRACION", "true");
        vite.environment().put("VITE_API_BASE_URL", "/api");
        vite.environment().put("BACKEND_PROXY_TARGET", "http://127.0.0.1:" + backend);
        Process servidor = vite.start();
        Process navegador = null;
        try {
            var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(1)).build();
            long limite = System.nanoTime() + Duration.ofSeconds(25).toNanos();
            boolean listo = false;
            while (servidor.isAlive() && System.nanoTime() < limite) {
                try {
                    var respuesta = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + web))
                            .header("Accept", "text/html").timeout(Duration.ofSeconds(1)).GET().build(),
                            HttpResponse.BodyHandlers.discarding());
                    if (respuesta.statusCode() == 200) { listo = true; break; }
                } catch (java.io.IOException error) { /* Arranque del proceso propio. */ }
                Thread.sleep(100);
            }
            assertThat(listo).as("Vite aislado disponible").isTrue();
            var prueba = new ProcessBuilder("node", "--test", "e2e/" + archivo)
                    .directory(directorio.toFile()).redirectErrorStream(true);
            prueba.environment().put("GAMW_WEB_INTEGRACION", "true");
            prueba.environment().put("GAMW_WEB_TEST_BASE", "http://127.0.0.1:" + web);
            navegador = prueba.start();
            var salida = new java.io.ByteArrayOutputStream();
            final Process proceso = navegador;
            var lector = Thread.ofVirtual().start(() -> {
                try { proceso.getInputStream().transferTo(salida); }
                catch (java.io.IOException error) { /* Validar resultado del proceso. */ }
            });
            assertThat(navegador.waitFor(150, TimeUnit.SECONDS)).as("Plazo E2E aislado").isTrue();
            lector.join(1000);
            assertThat(navegador.exitValue()).as("%s: %s", archivo, salida.toString(StandardCharsets.UTF_8)).isZero();
            System.out.println(archivo + ": Playwright 1/1 en instancia aislada.");
        } finally {
            if (navegador != null) { navegador.descendants().forEach(ProcessHandle::destroy); navegador.destroy(); }
            servidor.descendants().forEach(ProcessHandle::destroy);
            servidor.destroy();
        }
    }
}
