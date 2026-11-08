package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SinBorradoOperativoTests {
    @Test
    void fuentesDeProduccionIncluidasLasNoRastreadasNoBorranRegistros() throws Exception {
        var borrado = Pattern.compile("(?i)\\bDELETE\\s+FROM\\b");
        try (var archivos = Files.walk(Path.of("src/main"))) {
            for (var archivo : archivos.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".sql")).toList()) {
                assertThat(borrado.matcher(Files.readString(archivo)).find()).as(archivo.toString()).isFalse();
            }
        }
    }
}
