package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.aplicacion.PoliticaEvidenciasCampo;
import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.dominio.RepositorioFiscalizacion;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * CU-20 / RF-17: canal EXISTENTE de fotografías de inspección ejercitado SOLO bajo configuración sintética aislada de prueba. La referencia de
 * política es una etiqueta de prueba, NO una política institucional: en la configuración normal las fotografías siguen deshabilitadas
 * (PENDING-09; ver InspeccionCampoIT). No se inventan retención, privacidad ni formatos oficiales: se verifica el comportamiento ya implementado.
 */
@SpringBootTest(properties={"spring.config.import=","gamw.evidencias.fotografias-habilitadas=true",
        "gamw.evidencias.referencia-politica="+InspeccionEvidenciasSinteticasIT.POLITICA})
@AutoConfigureMockMvc
class InspeccionEvidenciasSinteticasIT extends IntegracionPostgresV8 {
    static final String POLITICA="PRUEBA-SINTETICA (no institucional, PENDING-09)";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    @MockitoSpyBean RepositorioFiscalizacion repositorio;
    private final UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),jornada=UUID.randomUUID(),otraJornada=UUID.randomUUID();
    private UUID inspector,lector,sinPermisos;
    private final Instant fecha=Instant.now().minus(1,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS),captura=fecha.minusSeconds(120);

    @BeforeEach void fixtures() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-20 fotos");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"IE-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        for(UUID j:List.of(jornada,otraJornada)) jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",j,periodo,version);
        inspector=cuenta("INSPECCION_REGISTRAR","FISCALIZACION_CONSULTAR");
        lector=cuenta("FISCALIZACION_CONSULTAR");
        sinPermisos=cuenta("TRAYECTORIA_JORNADA_CONSULTAR");
    }
    @AfterEach void restaurar() { reset(repositorio); }
    private UUID cuenta(String... permisos) {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"IE-"+persona,"Persona sintética");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"ie-"+id,codificador.encode("clave-sintetica"));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private String bearer(UUID u) { return "Bearer "+emisor.emitir(u,UUID.randomUUID(),"no-confiable").token(); }
    private static byte[] imagen(String formato,int color) throws Exception {
        var img=new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB); img.setRGB(0,0,color); img.setRGB(2,1,0x00FF00);
        var salida=new ByteArrayOutputStream(); assertThat(ImageIO.write(img,formato,salida)).isTrue(); return salida.toByteArray();
    }
    private static Map<String,Object> adjunto(String origen,Instant capturada) {
        var m=new LinkedHashMap<String,Object>(); m.put("origen",origen); m.put("descripcion","Fotografía sintética de prueba"); m.put("capturadaEn",capturada==null?null:capturada.toString()); return m;
    }
    private Map<String,Object> datos(UUID solicitud,List<Map<String,Object>> adjuntos) {
        var d=new LinkedHashMap<String,Object>(); d.put("solicitudId",solicitud); d.put("realizadaEn",fecha.toString()); d.put("observaciones","Observación sintética con fotografías");
        d.put("adjuntos",adjuntos); return d;
    }
    private org.springframework.mock.web.MockHttpServletResponse enviar(UUID u,UUID j,Map<String,Object> datos,MockMultipartFile... archivos) throws Exception {
        var b=multipart("/api/fiscalizacion/jornadas/"+j+"/inspecciones").file(new MockMultipartFile("datos","","application/json",json.writeValueAsBytes(datos)));
        for(var a:archivos) b.file(a);
        if(u!=null) b.header("Authorization",bearer(u));
        return mvc.perform(b).andReturn().getResponse();
    }
    private static MockMultipartFile archivo(String nombre,String tipo,byte[] bytes) { return new MockMultipartFile("archivos",nombre,tipo,bytes); }
    private int inspeccionesDe(UUID solicitud) { return jdbc.queryForObject("SELECT count(*) FROM inspeccion WHERE solicitud_id=?",Integer.class,solicitud); }
    private int evidencias(UUID j) { return jdbc.queryForObject("SELECT count(*) FROM evidencia_inspeccion e JOIN inspeccion i ON i.id=e.inspeccion_id WHERE i.jornada_id=?",Integer.class,j); }

    @Test void fotografiasPermitidasQuedanAsociadasIntegrasYRecuperablesSinResultado() throws Exception {
        byte[] png=imagen("png",0xFF0000),jpg=imagen("jpg",0x0000FF);
        UUID solicitud=UUID.randomUUID();
        var cuerpo=datos(solicitud,List.of(adjunto("Cámara municipal sintética",captura),adjunto("Teléfono del inspector (sintético)",null)));
        var r=enviar(inspector,jornada,cuerpo,archivo("frente.png","image/png",png),archivo("detalle.jpg","image/jpeg",jpg));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        var creada=json.readTree(r.getContentAsString());
        var ev=creada.get("evidencias");
        assertThat(ev).hasSize(2);
        Map<String,JsonNode> porNombre=new HashMap<>(); for(JsonNode e:ev) porNombre.put(e.get("nombreOriginal").asText(),e);
        var p=porNombre.get("frente.png"); var j=porNombre.get("detalle.jpg");
        assertThat(p.get("tipoMime").asText()).isEqualTo("image/png");
        assertThat(p.get("sha256").asText()).isEqualTo(PoliticaEvidenciasCampo.huella(png));
        assertThat(p.get("tamanoBytes").asInt()).isEqualTo(png.length);
        assertThat(p.get("origen").asText()).isEqualTo("Cámara municipal sintética");
        assertThat(Instant.parse(p.get("capturadaEn").asText())).isEqualTo(captura);
        assertThat(p.get("referenciaPolitica").asText()).isEqualTo(POLITICA);
        assertThat(p.get("autorUsuarioId").asText()).as("autor desde la sesión").isEqualTo(inspector.toString());
        assertThat(j.get("tipoMime").asText()).isEqualTo("image/jpeg");
        assertThat(j.get("capturadaEn").isNull()).as("captura desconocida, no inventada").isTrue();
        assertThat(Instant.parse(p.get("incorporadaEn").asText())).isAfterOrEqualTo(Instant.parse(creada.get("registradaEn").asText()).truncatedTo(ChronoUnit.SECONDS));
        // Recuperación íntegra (bytes originales, sin reencodear) solo con permiso de consulta y por la jornada correcta.
        UUID idPng=UUID.fromString(p.get("id").asText());
        var descarga=mvc.perform(get("/api/fiscalizacion/jornadas/"+jornada+"/evidencias/"+idPng).header("Authorization",bearer(lector))).andReturn().getResponse();
        assertThat(descarga.getStatus()).isEqualTo(200);
        assertThat(descarga.getContentAsByteArray()).isEqualTo(png);
        assertThat(descarga.getContentType()).isEqualTo("image/png");
        assertThat(descarga.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(descarga.getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(descarga.getHeader("Cache-Control")).contains("no-store");
        assertThat(mvc.perform(get("/api/fiscalizacion/jornadas/"+otraJornada+"/evidencias/"+idPng).header("Authorization",bearer(lector))).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/api/fiscalizacion/jornadas/"+jornada+"/evidencias/"+idPng).header("Authorization",bearer(sinPermisos))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/fiscalizacion/jornadas/"+jornada+"/evidencias/"+idPng)).andReturn().getResponse().getStatus()).isIn(401,403);
        // Reintento idéntico idempotente; misma solicitud con otro archivo: conflicto, sin evidencia adicional.
        var replica=enviar(inspector,jornada,cuerpo,archivo("frente.png","image/png",png),archivo("detalle.jpg","image/jpeg",jpg));
        assertThat(replica.getStatus()).isEqualTo(200);
        assertThat(json.readTree(replica.getContentAsString()).get("id")).isEqualTo(creada.get("id"));
        assertThat(enviar(inspector,jornada,cuerpo,archivo("frente.png","image/png",imagen("png",0x123456)),archivo("detalle.jpg","image/jpeg",jpg)).getStatus()).isEqualTo(409);
        assertThat(inspeccionesDe(solicitud)).isEqualTo(1);
        assertThat(evidencias(jornada)).isEqualTo(2);
        // Inmutable y sin resultado de fiscalización.
        assertThatThrownBy(() -> jdbc.update("UPDATE evidencia_inspeccion SET descripcion='alterada' WHERE id=?",idPng)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM evidencia_inspeccion WHERE id=?",idPng)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();
    }

    @Test void archivosRechazadosNoCreanEvidenciaFicticiaNiInspeccionParcial() throws Exception {
        byte[] png=imagen("png",0xFF0000),jpg=imagen("jpg",0x00FF00),gif=imagen("gif",0x0000FF);
        record Caso(String nombre,int estado,List<Map<String,Object>> adjuntos,MockMultipartFile[] archivos) { }
        var uno=List.of(adjunto("Cámara sintética",null));
        var casos=List.of(
            new Caso("bytes que no son imagen",400,uno,new MockMultipartFile[]{archivo("foto.png","image/png","no es una imagen".getBytes())}),
            new Caso("PNG con extensión .jpg",400,uno,new MockMultipartFile[]{archivo("foto.jpg","image/jpeg",png)}),
            new Caso("tipo declarado distinto del contenido",400,uno,new MockMultipartFile[]{archivo("foto.png","image/jpeg",png)}),
            new Caso("formato no admitido (GIF)",400,uno,new MockMultipartFile[]{archivo("foto.gif","image/gif",gif)}),
            new Caso("nombre con ruta",400,uno,new MockMultipartFile[]{archivo("../foto.png","image/png",png)}),
            new Caso("archivo vacío",413,uno,new MockMultipartFile[]{archivo("vacia.png","image/png",new byte[0])}),
            new Caso("más de 5 MiB",413,uno,new MockMultipartFile[]{archivo("grande.png","image/png",new byte[PoliticaEvidenciasCampo.MAX_ARCHIVO+1])}),
            new Caso("cuatro archivos",400,List.of(adjunto("a",null),adjunto("b",null),adjunto("c",null),adjunto("d",null)),
                new MockMultipartFile[]{archivo("a.png","image/png",png),archivo("b.png","image/png",png),archivo("c.png","image/png",png),archivo("d.png","image/png",png)}),
            new Caso("metadatos incompletos",400,uno,new MockMultipartFile[]{archivo("a.png","image/png",png),archivo("b.jpg","image/jpeg",jpg)}),
            new Caso("procedencia vacía",400,List.of(adjunto(" ",null)),new MockMultipartFile[]{archivo("a.png","image/png",png)}),
            new Caso("captura con nanosegundos",400,List.of(adjunto("Cámara",captura.plusNanos(1))),new MockMultipartFile[]{archivo("a.png","image/png",png)}),
            new Caso("captura futura",400,List.of(adjunto("Cámara",Instant.now().plus(1,ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))),new MockMultipartFile[]{archivo("a.png","image/png",png)}),
            new Caso("válida + inválida: nada parcial",400,List.of(adjunto("a",null),adjunto("b",null)),
                new MockMultipartFile[]{archivo("a.png","image/png",png),archivo("b.png","image/png","corrupta".getBytes())}));
        for(var c:casos) {
            UUID solicitud=UUID.randomUUID();
            var r=enviar(inspector,jornada,datos(solicitud,c.adjuntos()),c.archivos());
            assertThat(r.getStatus()).as("%s → %s",c.nombre(),r.getContentAsString()).isEqualTo(c.estado());
            assertThat(inspeccionesDe(solicitud)).as("sin inspección parcial: %s",c.nombre()).isZero();
        }
        assertThat(evidencias(jornada)).as("ningún archivo rechazado genera evidencia").isZero();
        // Actor sin permiso de registro con archivos válidos: 403 sin rastro.
        UUID s=UUID.randomUUID();
        assertThat(enviar(lector,jornada,datos(s,uno),archivo("a.png","image/png",png)).getStatus()).isEqualTo(403);
        assertThat(inspeccionesDe(s)).isZero();
        assertThat(evidencias(jornada)).isZero();
    }

    @Test void unFalloAlGuardarUnArchivoRevierteLaInspeccionCompleta() throws Exception {
        byte[] png=imagen("png",0xFF0000),jpg=imagen("jpg",0x00FF00);
        var llamadas=new AtomicInteger();
        doAnswer(i -> { i.callRealMethod(); if(llamadas.incrementAndGet()==2) throw new DataAccessResourceFailureException("Fallo inyectado (prueba)."); return null; })
            .when(repositorio).insertarArchivo(any());
        UUID solicitud=UUID.randomUUID(); var cuerpo=datos(solicitud,List.of(adjunto("a",null),adjunto("b",null)));
        var r=enviar(inspector,jornada,cuerpo,archivo("a.png","image/png",png),archivo("b.jpg","image/jpeg",jpg));
        assertThat(r.getStatus()).as("fallo de persistencia, no «sin permiso» ni éxito: %s",r.getContentAsString()).isEqualTo(503);
        assertThat(inspeccionesDe(solicitud)).as("ni la inspección ni el primer archivo quedan").isZero();
        assertThat(evidencias(jornada)).isZero();
        reset(repositorio);
        var reintento=enviar(inspector,jornada,cuerpo,archivo("a.png","image/png",png),archivo("b.jpg","image/jpeg",jpg));
        assertThat(reintento.getStatus()).isEqualTo(200);
        assertThat(inspeccionesDe(solicitud)).isEqualTo(1);
        assertThat(evidencias(jornada)).isEqualTo(2);
    }
}
