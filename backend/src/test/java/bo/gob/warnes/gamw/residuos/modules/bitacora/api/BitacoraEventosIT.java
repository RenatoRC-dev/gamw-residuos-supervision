package bo.gob.warnes.gamw.residuos.modules.bitacora.api;

import bo.gob.warnes.gamw.residuos.modules.bitacora.aplicacion.AutorizacionBitacora;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CU-31: lógica de consulta (filtros, paginación, detalle, atribución, redacción, solo lectura) por HTTP real.
 * La autorización se sustituye por un doble SOLO en esta clase, porque el permiso de bitácora no existe aún en el
 * catálogo aprobado (D1) y no puede otorgarse; la denegación real está en BitacoraAutorizacionIT.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.import=","server.address=127.0.0.1","debug=false","logging.level.root=ERROR"})
class BitacoraEventosIT extends IntegracionPostgresV8 {
    @MockitoBean AutorizacionBitacora autorizacion; // exigir() no lanza: simula un A1 futuro autorizado
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired ObjectMapper json;
    @Value("${local.server.port}") int puerto;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String clave=UUID.randomUUID().toString();
    private static final String HASH_SECRETO="$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012345";
    private static final String JWT="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaW50ZXRpY28ifQ.firmaSinteticaCU31";

    record Resp(int estado,String cuerpo) { }
    private UUID usuario() {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"BIT-"+persona,"Persona sintética bitácora");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"bit-"+id,codificador.encode(clave));
        return id;
    }
    private String token(UUID id) throws Exception {
        var r=enviar("POST","/api/autenticacion/login",null,Map.of("nombreUsuario","bit-"+id,"contrasena",clave));
        assertThat(r.estado()).isEqualTo(200);
        return json.readTree(r.cuerpo()).get("tokenAcceso").asText();
    }
    private Resp enviar(String metodo,String ruta,String jwt,Object cuerpo) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+ruta)).timeout(Duration.ofSeconds(10));
        if(jwt!=null) b.header("Authorization","Bearer "+jwt);
        if(cuerpo==null) b.method(metodo,HttpRequest.BodyPublishers.noBody());
        else b.header("Content-Type","application/json").method(metodo,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(),r.body());
    }
    private UUID actuacion(UUID actor,UUID objetivo,String tipo,String motivo,String anterior,String posterior,int segundosAtras) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO actuacion_administrativa(id,actor_usuario_id,usuario_objetivo_id,tipo,motivo,registrada_en,anterior,posterior) VALUES (?,?,?,?,?,clock_timestamp()-make_interval(secs=>?),?::jsonb,?::jsonb)",
            id,actor,objetivo,tipo,motivo,segundosAtras,anterior,posterior);
        return id;
    }
    private List<String> ids(Resp r) { var l=new ArrayList<String>(); for(JsonNode n:json.readTree(r.cuerpo()).get("eventos")) l.add(n.get("id").asText()); return l; }
    private static final String BASE="/api/bitacora/eventos";

    @Test void filtrosVacioYEntradasInvalidas() throws Exception {
        UUID a=usuario(),objetivo=usuario(),otro=usuario(); String t=token(a);
        UUID e1=actuacion(a,objetivo,"CUENTA_ACTUALIZAR","Motivo sintético 1","{}","{}",5), e2=actuacion(otro,objetivo,"PERMISO_OTORGAR","Motivo sintético 2","{}","{}",3);
        assertThat(ids(enviar("GET",BASE+"?entidadTipo=USUARIO&entidadId="+objetivo,t,null))).containsExactly("ADM:"+e2,"ADM:"+e1); // más reciente primero
        assertThat(ids(enviar("GET",BASE+"?actorId="+otro+"&entidadTipo=USUARIO&entidadId="+objetivo,t,null))).containsExactly("ADM:"+e2);
        assertThat(ids(enviar("GET",BASE+"?accion=cuenta_actualizar&entidadTipo=USUARIO&entidadId="+objetivo,t,null))).containsExactly("ADM:"+e1);
        LocalDate hoy=LocalDate.now(ZoneId.of("America/La_Paz"));
        assertThat(ids(enviar("GET",BASE+"?desde="+hoy.minusDays(1)+"&hasta="+hoy.plusDays(1)+"&entidadTipo=USUARIO&entidadId="+objetivo,t,null))).hasSize(2);
        var vacio=enviar("GET",BASE+"?actorId="+UUID.randomUUID(),t,null);
        assertThat(vacio.estado()).isEqualTo(200); assertThat(ids(vacio)).isEmpty();
        assertThat(ids(enviar("GET",BASE+"?desde="+hoy.plusDays(10),t,null))).isEmpty();
        for(String mala:List.of("?desde=2030-01-02&hasta=2030-01-01","?desde=31-12-2030","?actorId=no-uuid","?accion=accion%20mala!","?entidadTipo=OTRA","?entidadId="+UUID.randomUUID(),"?desplazamiento=-1"))
            assertThat(enviar("GET",BASE+mala,t,null).estado()).as(mala).isEqualTo(400);
        assertThat(enviar("GET",BASE+"/ADM:"+UUID.randomUUID(),t,null).estado()).isEqualTo(404);
        assertThat(enviar("GET",BASE+"/no-es-evento",t,null).estado()).isEqualTo(400);
    }

    @Test void paginacionDeCincuentaConSiguiente() throws Exception {
        UUID a=usuario(),objetivo=usuario(); String t=token(a);
        for(int i=0;i<52;i++) actuacion(a,objetivo,"CUENTA_ACTUALIZAR","Evento "+i,"{}","{}",i+1);
        var p1=json.readTree(enviar("GET",BASE+"?entidadTipo=USUARIO&entidadId="+objetivo,t,null).cuerpo());
        assertThat(p1.get("eventos").size()).isEqualTo(50);
        assertThat(p1.get("siguiente").asInt()).isEqualTo(50);
        var p2=json.readTree(enviar("GET",BASE+"?entidadTipo=USUARIO&entidadId="+objetivo+"&desplazamiento=50",t,null).cuerpo());
        assertThat(p2.get("eventos").size()).isEqualTo(2);
        assertThat(p2.get("siguiente").isNull()).isTrue();
        var todos=new HashSet<String>(); p1.get("eventos").forEach(n->todos.add(n.get("id").asText())); p2.get("eventos").forEach(n->todos.add(n.get("id").asText()));
        assertThat(todos).hasSize(52);
    }

    @Test void detalleOcultaSecretosYDatosPersonalesYDeclaraSusLimites() throws Exception {
        UUID a=usuario(),objetivo=usuario(); String t=token(a);
        // Datos que NINGÚN patrón de credenciales reconocería: clave en texto libre, documento de identidad, campos desconocidos y anidados.
        String claveLibre="Qwerty-Sint-9981",docLibre="DOC-SINT-4455667";
        String anterior=json.writeValueAsString(Map.of("cuenta",Map.of("nombreUsuario","visible-"+objetivo,"contrasenaHash",HASH_SECRETO,"nombres","Persona Real","ci","CI-SINT-7788",
            "observacion",docLibre,"detalle",Map.of("tokenAcceso",JWT,"anidado",Map.of("clave",claveLibre))),"activo",true,"comentario","la clave nueva es "+claveLibre));
        String posterior=json.writeValueAsString(Map.of("nota","Authorization: Bearer "+JWT,"otro",HASH_SECRETO,"permiso","JORNADAS_CONSULTAR","permisoRaro","jornadas consultar con espacios"));
        UUID e=actuacion(a,objetivo,"CUENTA_ACTUALIZAR","Cambio: la clave es "+claveLibre+" y el documento "+docLibre+" con "+JWT,anterior,posterior,1);
        var r=enviar("GET",BASE+"/ADM:"+e,t,null);
        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo()).doesNotContain(HASH_SECRETO,JWT,"CI-SINT-7788","Persona Real","firmaSinteticaCU31",claveLibre,docLibre,"jornadas consultar");
        var n=json.readTree(r.cuerpo());
        // Lista permitida: solo campos conocidos con forma válida; el resto se cuenta, no se muestra.
        assertThat(n.get("anterior").get("cuenta").get("nombreUsuario").asText()).isEqualTo("visible-"+objetivo);
        assertThat(n.get("anterior").get("activo").asBoolean()).isTrue();
        assertThat(n.get("anterior").get("cuenta").has("contrasenaHash")).isFalse();
        assertThat(n.get("anterior").get("cuenta").has("detalle")).isFalse();
        assertThat(n.get("anterior").get("cuenta").get("camposOmitidos").asInt()).isEqualTo(5);
        assertThat(n.get("anterior").get("camposOmitidos").asInt()).isEqualTo(1);
        assertThat(n.get("posterior").get("permiso").asText()).isEqualTo("JORNADAS_CONSULTAR");
        assertThat(n.get("posterior").has("permisoRaro")).isFalse();
        assertThat(n.get("posterior").get("camposOmitidos").asInt()).isEqualTo(3);
        assertThat(n.get("evento").get("atribucion").asText()).isEqualTo("USUARIO_AUTENTICADO");
        assertThat(n.get("evento").get("actorNombreUsuario").asText()).isEqualTo("bit-"+a);
        // El motivo es texto libre: no se devuelve su contenido, solo que existe.
        assertThat(n.has("motivo")).isFalse();
        assertThat(n.get("motivoRegistrado").asBoolean()).isTrue();
        assertThat(n.get("motivoLongitud").asInt()).isGreaterThan(10);
        // Un valor con forma inválida en un campo permitido se omite (fallo cerrado), no se limpia.
        UUID e2=actuacion(a,objetivo,"CUENTA_ACTUALIZAR","m",json.writeValueAsString(Map.of("nombreUsuario","con espacios y clave "+claveLibre,"activo","true","permiso","jornadas")),"{}",1);
        var n2=json.readTree(enviar("GET",BASE+"/ADM:"+e2,t,null).cuerpo());
        assertThat(n2.get("anterior").has("nombreUsuario")).isFalse();
        assertThat(n2.get("anterior").has("activo")).isFalse();
        assertThat(n2.get("anterior").has("permiso")).isFalse();
        assertThat(n2.get("anterior").get("camposOmitidos").asInt()).isEqualTo(3);
        // Ninguna respuesta de listado ni detalle expone hashes de las cuentas reales.
        String hashReal=jdbc.queryForObject("SELECT contrasena_hash FROM usuario WHERE id=?",String.class,a);
        assertThat(enviar("GET",BASE+"?entidadTipo=USUARIO&entidadId="+objetivo,t,null).cuerpo()).doesNotContain(hashReal);
        assertThat(r.cuerpo()).doesNotContain(hashReal);
    }

    @Test void eventoSinUsuarioSeMuestraNoAtribuibleYLaConsultaNoEscribe() throws Exception {
        UUID a=usuario(),objetivo=usuario(); String t=token(a);
        // Provisión local (CLI): no hay usuario autenticado; no se inventa uno.
        jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,'JORNADAS_CONSULTAR','MUNICIPAL_GAMW','operador-so-sintetico','fixture local')",UUID.randomUUID(),objetivo);
        var lista=enviar("GET",BASE+"?entidadTipo=USUARIO&entidadId="+objetivo,t,null);
        var ev=json.readTree(lista.cuerpo()).get("eventos");
        assertThat(ev.size()).isEqualTo(1);
        assertThat(ev.get(0).get("accion").asText()).isEqualTo("PERMISO_OTORGADO:JORNADAS_CONSULTAR");
        assertThat(ev.get(0).get("atribucion").asText()).isEqualTo("OPERADOR_LOCAL_NO_AUTENTICADO");
        assertThat(ev.get(0).get("actorUsuarioId").isNull()).isTrue();
        assertThat(lista.cuerpo()).doesNotContain("operador-so-sintetico"); // identidad del SO no es identidad institucional ni se difunde
        var d=json.readTree(enviar("GET",BASE+"/"+ev.get(0).get("id").asText(),t,null).cuerpo());
        assertThat(d.get("notas").toString()).contains("no atribuible");
        // Concesiones por HTTP ya constan como ACTUACION_ADMINISTRATIVA: no se duplican como eventos locales.
        jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,'JORNADAS_PROGRAMAR','MUNICIPAL_GAMW',?,'fixture http')",UUID.randomUUID(),objetivo,"HTTP_USUARIO:"+a);
        assertThat(json.readTree(enviar("GET",BASE+"?entidadTipo=USUARIO&entidadId="+objetivo,t,null).cuerpo()).get("eventos").size()).isEqualTo(1);
        // Solo lectura: nada cambia y los registros de actuación siguen siendo inmutables.
        UUID e=actuacion(a,objetivo,"CUENTA_ACTUALIZAR","m","{}","{}",1);
        int antes=jdbc.queryForObject("SELECT count(*) FROM actuacion_administrativa",Integer.class);
        enviar("GET",BASE+"/ADM:"+e,t,null); enviar("GET",BASE,t,null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM actuacion_administrativa",Integer.class)).isEqualTo(antes);
        for(String m:List.of("POST","PUT","PATCH","DELETE")) assertThat(enviar(m,BASE+"/ADM:"+e,t,m.equals("DELETE")?null:Map.of()).estado()).as(m).isIn(403,405);
        assertThatThrownBy(() -> jdbc.update("UPDATE actuacion_administrativa SET motivo='x' WHERE id=?",e)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM actuacion_administrativa WHERE id=?",e)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT motivo FROM actuacion_administrativa WHERE id=?",String.class,e)).isEqualTo("m");
    }
}
