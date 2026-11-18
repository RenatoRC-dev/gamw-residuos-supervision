package bo.gob.warnes.gamw.residuos.modules.bitacora.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CU-31 con la autorización REAL: mientras GAMW no apruebe un permiso de bitácora (D1) nadie puede consultarla.
 * Ni los permisos administrativos, de usuarios o de jornadas, ni una cuenta activa, la habilitan.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class BitacoraAutorizacionIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString();

    private UUID usuario(String... permisos) {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"BIT-"+persona,"Persona sintética bitácora");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"bit-"+id,codificador.encode(clave));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private int enviar(String metodo,String ruta,String jwt) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(10));
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        b.method(metodo,"GET".equals(metodo)||"DELETE".equals(metodo)?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString("{}"));
        if(!"GET".equals(metodo)&&!"DELETE".equals(metodo)) b.header("Content-Type","application/json");
        return http.send(b.build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    private String token(UUID id) throws Exception {
        var r=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+"/api/autenticacion/login")).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("nombreUsuario","bit-"+id,"contrasena",clave)))).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        return json.readTree(r.body()).get("tokenAcceso").asText();
    }

    @Test void nadieAccedeMientrasNoExistaUnPermisoAprobado() throws Exception {
        String evento="ADM:"+UUID.randomUUID();
        assertThat(enviar("GET","/api/bitacora/eventos",null)).isEqualTo(401);
        assertThat(enviar("GET","/api/bitacora/eventos/"+evento,null)).isEqualTo(401);
        assertThat(enviar("GET","/api/bitacora/eventos","token-invalido")).isEqualTo(401);
        // Cuenta sin permisos y cuentas con los permisos administrativos/municipales más cercanos: todas denegadas.
        var cuentas=List.of(usuario(),usuario("PERMISOS_ADMINISTRAR"),usuario("USUARIOS_CONSULTAR","USUARIOS_GESTIONAR"),usuario("JORNADAS_CONSULTAR","FISCALIZACION_CONSULTAR"));
        for(UUID c:cuentas) {
            String t=token(c);
            assertThat(enviar("GET","/api/bitacora/eventos",t)).as("listado").isEqualTo(403);
            assertThat(enviar("GET","/api/bitacora/eventos?desde=2026-01-01",t)).isEqualTo(403);
            assertThat(enviar("GET","/api/bitacora/eventos/"+evento,t)).as("detalle").isEqualTo(403);
        }
        // No hay camino de concesión: el catálogo aprobado no contiene la facultad y no se concede automáticamente.
        UUID u=usuario();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,'BITACORA_CONSULTAR','MUNICIPAL_GAMW','prueba','fixture')",UUID.randomUUID(),u))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM concesion_permiso WHERE permiso='BITACORA_CONSULTAR'",Integer.class)).isZero();
        // Sin verbos de escritura sobre la bitácora, ni siquiera con sesión válida.
        String t=token(cuentas.get(1));
        for(String m:List.of("POST","PUT","PATCH","DELETE")) assertThat(enviar(m,"/api/bitacora/eventos",t)).as(m).isIn(403,405);
        for(String m:List.of("PUT","PATCH","DELETE")) assertThat(enviar(m,"/api/bitacora/eventos/"+evento,t)).as(m).isIn(403,405);
    }
}
