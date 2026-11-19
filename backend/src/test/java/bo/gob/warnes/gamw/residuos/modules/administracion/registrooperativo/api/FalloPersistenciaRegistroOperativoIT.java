package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.api;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.RepositorioRegistroOperativo;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CA-CU-04-07 / CA-CU-05-07 (auditoría F1, H2): fallo de persistencia INYECTADO después de que la escritura ya se ejecutó en la base,
 * dentro de la misma transacción. Se verifica por HTTP real con JWT que la operación no se confirma y que el estado anterior queda
 * íntegro (sin escritura parcial). También H3: el mensaje 400 de JSON malformado corresponde al recurso real.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class FalloPersistenciaRegistroOperativoIT extends IntegracionPostgresV8 {
    @MockitoSpyBean RepositorioRegistroOperativo repositorio;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    private final String clave=UUID.randomUUID().toString(),nombre="fp-adm-"+UUID.randomUUID().toString().substring(0,8);
    private UUID empresa,periodo;
    private String token;

    @BeforeEach void preparar() throws Exception {
        UUID persona=UUID.randomUUID(),usuario=UUID.randomUUID();
        empresa=UUID.randomUUID(); periodo=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"FP-"+persona,"Cuenta fallo persistencia");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",usuario,persona,nombre,codificador.encode(clave));
        for(String p:List.of("EMPRESAS_CONSULTAR","EMPRESAS_GESTIONAR","PERIODOS_CONSULTAR","PERIODOS_GESTIONAR"))
            jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),usuario,p);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,?,?,'Comercial original',true,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')",empresa,"FP-"+empresa,"Original "+empresa);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2026-12-31',true,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')",periodo,empresa);
        var r=api("POST","/api/autenticacion/login",Map.of("nombreUsuario",nombre,"contrasena",clave),false);
        token=json.readTree(r.body()).get("tokenAcceso").asText();
    }
    @AfterEach void restaurar() { reset(repositorio); }

    private HttpResponse<String> api(String metodo,String ruta,Object cuerpo,boolean autenticado) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(10));
        if(autenticado) b.header("Authorization","Bearer "+token);
        String texto=cuerpo instanceof String s?s:cuerpo==null?null:json.writeValueAsString(cuerpo);
        if(texto==null) b.method(metodo,HttpRequest.BodyPublishers.noBody()); else b.header("Content-Type","application/json").method(metodo,HttpRequest.BodyPublishers.ofString(texto));
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private Map<String,Object> empresaFila() { return jdbc.queryForMap("SELECT nit,razon_social,nombre_comercial,activo,actualizado_en FROM empresa_operadora WHERE id=?",empresa); }
    private Map<String,Object> periodoFila() { return jdbc.queryForMap("SELECT fecha_inicio,fecha_fin,activo,actualizado_en,empresa_operadora_id FROM periodo_servicio WHERE id=?",periodo); }
    /** La escritura real se ejecuta y DESPUÉS se inyecta el fallo: solo la reversión transaccional puede dejar el estado anterior. */
    private static org.mockito.stubbing.Answer<Object> escribirYFallar() {
        return invocacion -> { invocacion.callRealMethod(); throw new DataAccessResourceFailureException("Fallo de persistencia inyectado (prueba)."); };
    }

    @Test void caCu0407FalloAlActualizarOEstadoDeEmpresaConservaElEstadoAnterior() throws Exception {
        var antes=empresaFila();
        doAnswer(escribirYFallar()).when(repositorio).actualizarEmpresa(any());
        var r=api("PUT","/api/empresas-operadoras/"+empresa,Map.of("razonSocial","Modificada "+empresa,"nit","FP-NUEVO","nombreComercial","Modificado"),true);
        assertThat(r.statusCode()).as("fallo de persistencia, no «sin permiso»").isEqualTo(503);
        assertThat(json.readTree(r.body()).get("detail").asText()).contains("se revirtió");
        assertThat(r.body()).doesNotContain("Modificada");
        assertThat(empresaFila()).as("sin escritura parcial").isEqualTo(antes);
        var e=api("PATCH","/api/empresas-operadoras/"+empresa+"/estado",Map.of("activo",false),true);
        assertThat(e.statusCode()).isEqualTo(503);
        assertThat(empresaFila()).isEqualTo(antes);
        reset(repositorio);
        assertThat(api("GET","/api/empresas-operadoras/"+empresa,null,true).statusCode()).as("sigue disponible").isEqualTo(200);
    }

    @Test void caCu0507FalloAlCorregirCrearOCambiarEstadoDePeriodoNoModificaParcialmente() throws Exception {
        var antes=periodoFila();
        int periodosAntes=jdbc.queryForObject("SELECT count(*) FROM periodo_servicio WHERE empresa_operadora_id=?",Integer.class,empresa);
        doAnswer(escribirYFallar()).when(repositorio).actualizarPeriodo(any());
        assertThat(api("PUT","/api/empresas-operadoras/"+empresa+"/periodos/"+periodo,Map.of("fechaInicio","2026-02-01","fechaFin","2027-01-31"),true).statusCode()).isEqualTo(503);
        assertThat(periodoFila()).as("fechas intactas").isEqualTo(antes);
        assertThat(api("PATCH","/api/empresas-operadoras/"+empresa+"/periodos/"+periodo+"/estado",Map.of("activo",false),true).statusCode()).isEqualTo(503);
        assertThat(periodoFila()).as("estado intacto").isEqualTo(antes);
        doAnswer(escribirYFallar()).when(repositorio).insertarPeriodo(any());
        assertThat(api("POST","/api/empresas-operadoras/"+empresa+"/periodos",Map.of("fechaInicio","2028-01-01","fechaFin","2028-12-31"),true).statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM periodo_servicio WHERE empresa_operadora_id=?",Integer.class,empresa)).as("ningún periodo a medias").isEqualTo(periodosAntes);
    }

    @Test void h3ElMensajeDeJsonMalformadoCorrespondeAlRecurso() throws Exception {
        var empresaMal=api("POST","/api/empresas-operadoras","{malformado",true);
        assertThat(empresaMal.statusCode()).isEqualTo(400);
        assertThat(json.readTree(empresaMal.body()).get("detail").asText()).contains("JSON de empresa inválido").doesNotContain("vínculo");
        var periodoMal=api("PUT","/api/empresas-operadoras/"+empresa+"/periodos/"+periodo,"{\"fechaInicio\":\"no-es-fecha\",\"fechaFin\":\"2026-12-31\"}",true);
        assertThat(periodoMal.statusCode()).isEqualTo(400);
        assertThat(json.readTree(periodoMal.body()).get("detail").asText()).contains("JSON de periodo inválido").doesNotContain("vínculo");
        assertThat(periodoFila().get("fecha_inicio").toString()).isEqualTo("2026-01-01");
    }
}
