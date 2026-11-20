package bo.gob.warnes.gamw.residuos.modules.historial.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * CU-30 (corte descriptivo) con JWT real y PostgreSQL/PostGIS aislados: filtros por fecha de servicio (America/La_Paz), empresa y periodo;
 * recuentos por fuente; cero frente a acceso no permitido; validación de filtros; autorización municipal sin A4; sin exportación; solo lectura.
 * Base compartida: solo se verifican los identificadores sembrados aquí (filtro de empresa propio).
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class ReportesServicioIT extends IntegracionPostgresV8 {
    static final List<String> TODOS = List.of("JORNADAS_CONSULTAR","TRAYECTORIA_JORNADA_CONSULTAR","CORRECTIVA_CONSULTAR","FISCALIZACION_CONSULTAR","RECLAMO_CONSULTAR");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    Fixture f;

    /** Fixture reutilizable (también por la prueba de navegador). */
    record Fixture(UUID empresa, UUID periodo, UUID periodo2, UUID empresaVacia, UUID completo, UUID basico, UUID sinPermiso, UUID operador) { }
    static Fixture sembrar(JdbcTemplate jdbc, PasswordEncoder codificador, String clave) {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),periodo2=UUID.randomUUID(),empresaVacia=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),
             vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),persona=UUID.randomUUID(),vp=UUID.randomUUID();
        String s=empresa.toString().substring(0,8);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Reportes sintética "+s);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresaVacia,"Reportes vacía "+s);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2026-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2026-12-31',true,now(),now())",periodo2,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"RP-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-63.1 -17.7,-63.1 -17.6)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo(id,placa,activo,creado_en,actualizado_en) VALUES (?,?,true,now(),now())",vehiculo,("RP"+vehiculo).toUpperCase());
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"RP-"+persona,"Conductor sintético");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,persona,empresa);
        UUID conductor=usuario(jdbc,codificador,"rp-cond-"+s,clave);
        // Marzo 2026 (fecha de servicio en Bolivia). j1: con asignación, 3 GPS, 1 incidencia, 1 inspección, 1 reclamo. j2 sustituida por j3.
        // jBorde: 01/04 03:00Z = 31/03 23:00 Bolivia (dentro). jFuera: 01/03 03:00Z = 28/02 23:00 Bolivia (fuera). jAbril y jOtroPeriodo con GPS.
        UUID j1=jornada(jdbc,periodo,version,"2026-03-10T12:00:00Z",null,true),j2=jornada(jdbc,periodo,version,"2026-03-11T12:00:00Z",null,false),
             j3=jornada(jdbc,periodo,version,"2026-03-12T12:00:00Z",j2,true),jBorde=jornada(jdbc,periodo,version,"2026-04-01T03:00:00Z",null,true),
             jFuera=jornada(jdbc,periodo,version,"2026-03-01T03:00:00Z",null,true),jAbril=jornada(jdbc,periodo,version,"2026-04-20T12:00:00Z",null,true),
             jOtroPeriodo=jornada(jdbc,periodo2,version,"2026-03-15T12:00:00Z",null,true);
        UUID a1=asignar(jdbc,j1,vv,vp),aAbril=asignar(jdbc,jAbril,vv,vp),aOtro=asignar(jdbc,jOtroPeriodo,vv,vp);
        for(int i=0;i<3;i++) gps(jdbc,j1,a1,conductor,"2026-03-10T12:0"+i+":00Z");
        gps(jdbc,jAbril,aAbril,conductor,"2026-04-20T12:00:00Z");
        gps(jdbc,jOtroPeriodo,aOtro,conductor,"2026-03-15T12:00:00Z");
        jdbc.update("INSERT INTO incidencia_operativa(id,solicitud_id,huella_solicitud,jornada_id,asignacion_jornada_id,usuario_id,ocurrida_en,descripcion,evidencia_declarada,lugar_descripcion) VALUES (?,?,'h',?,?,?,'2026-03-10T12:30:00Z','Incidencia sintética','','')",
            UUID.randomUUID(),UUID.randomUUID(),j1,a1,conductor);
        UUID inspector=usuario(jdbc,codificador,"rp-insp-"+s,clave);
        jdbc.update("INSERT INTO inspeccion(id,solicitud_id,huella_solicitud,jornada_id,inspector_usuario_id,realizada_en,observaciones) VALUES (?,?,'h',?,?,'2026-03-10T13:00:00Z','Observación sintética')",
            UUID.randomUUID(),UUID.randomUUID(),j1,inspector);
        jdbc.update("INSERT INTO reclamo_institucional(id,solicitud_id,huella_solicitud,canal_declarado,recibido_en,descripcion,lugar_descripcion,referencias_origen,jornada_id,usuario_id) VALUES (?,?,'h','canal sintético','2026-03-10T15:00:00Z','Reclamo sintético','','',?,?)",
            UUID.randomUUID(),UUID.randomUUID(),j1,inspector);
        UUID completo=usuario(jdbc,codificador,"rp-adm-"+s,clave,TODOS.toArray(String[]::new));
        UUID basico=usuario(jdbc,codificador,"rp-bas-"+s,clave,"JORNADAS_CONSULTAR");
        UUID sinPermiso=usuario(jdbc,codificador,"rp-sin-"+s,clave,"TRAYECTORIA_JORNADA_CONSULTAR");
        UUID operador=usuario(jdbc,codificador,"rp-op-"+s,clave);
        for(String p:List.of("PLANIFICACION_CONSULTAR","JORNADA_ASIGNAR"))
            jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,?,true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),operador,periodo,p);
        return new Fixture(empresa,periodo,periodo2,empresaVacia,completo,basico,sinPermiso,operador);
    }
    static UUID usuario(JdbcTemplate jdbc,PasswordEncoder codificador,String nombre,String clave,String... permisos) {
        UUID id=UUID.randomUUID(),persona=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"RPU-"+persona,"Cuenta "+nombre);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,nombre,codificador.encode(clave));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    static UUID jornada(JdbcTemplate jdbc,UUID periodo,UUID version,String inicio,UUID antecedente,boolean vigente) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,?,NULL,?,?,?,now(),now())",id,periodo,version,Timestamp.from(Instant.parse(inicio)),antecedente,
            antecedente==null?null:"Reprogramación sintética",vigente);
        return id;
    }
    static UUID asignar(JdbcTemplate jdbc,UUID jornada,UUID vv,UUID vp) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,'2026-01-01T00:00:00Z',NULL,NULL,now())",id,jornada,vv,vp);
        return id;
    }
    static void gps(JdbcTemplate jdbc,UUID jornada,UUID asignacion,UUID usuario,String captura) {
        jdbc.update("INSERT INTO posicion_gps VALUES (?,?,?,?,?,?,now(),ST_SetSRID(ST_MakePoint(-63.1,-17.65),4326),5)",UUID.randomUUID(),UUID.randomUUID(),jornada,asignacion,usuario,
            Timestamp.from(Instant.parse(captura)));
    }

    @BeforeEach void preparar() { f=sembrar(jdbc,codificador,"clave-sintetica-RP"); }

    private String token(UUID usuario) { return emisor.emitir(usuario,UUID.randomUUID(),"no-confiable").token(); }
    private JsonNode reporte(UUID usuario,String consulta,int estado) throws Exception {
        var r=mvc.perform(get("/api/reportes-servicio/resumen?"+consulta).header("Authorization","Bearer "+token(usuario))).andReturn();
        assertThat(r.getResponse().getStatus()).as("%s → %s",consulta,r.getResponse().getContentAsString()).isEqualTo(estado);
        return estado==200?json.readTree(r.getResponse().getContentAsString()):null;
    }
    private static JsonNode recuento(JsonNode reporte,String clave) {
        for(JsonNode r:reporte.get("recuentos")) if(r.get("clave").asText().equals(clave)) return r;
        throw new AssertionError("Sin recuento "+clave);
    }
    private static Long cantidad(JsonNode reporte,String clave) { var c=recuento(reporte,clave).get("cantidad"); return c==null||c.isNull()?null:c.asLong(); }
    private static String estado(JsonNode reporte,String clave) { return recuento(reporte,clave).get("estado").asText(); }

    @Test void agregaPorFechaDeServicioEmpresaYPeriodoConFuenteYUltimoRegistro() throws Exception {
        var r=reporte(f.completo(),"desde=2026-03-01&hasta=2026-03-31&empresaId="+f.empresa(),200);
        assertThat(cantidad(r,"jornadas")).as("j1, j2, j3, jBorde y jOtroPeriodo; no jFuera ni jAbril").isEqualTo(5);
        assertThat(cantidad(r,"jornadasSustituidas")).isEqualTo(1);
        assertThat(cantidad(r,"jornadasConAsignacion")).isEqualTo(2);
        assertThat(cantidad(r,"posicionesGps")).as("3 de j1 + 1 de jOtroPeriodo; la de abril fuera").isEqualTo(4);
        assertThat(cantidad(r,"jornadasConGps")).isEqualTo(2);
        assertThat(cantidad(r,"incidencias")).isEqualTo(1);
        assertThat(cantidad(r,"inspecciones")).isEqualTo(1);
        assertThat(cantidad(r,"reclamosVinculados")).isEqualTo(1);
        assertThat(estado(r,"reclamosRecibidos")).as("con filtro de empresa no se atribuyen reclamos sin jornada").isEqualTo("NO_APLICA");
        assertThat(estado(r,"eventosEjecucion")).isEqualTo("NO_CONSULTADO");
        assertThat(cantidad(r,"eventosEjecucion")).isNull();
        for(String clave:List.of("jornadas","posicionesGps","incidencias","inspecciones")) {
            var c=recuento(r,clave);
            assertThat(c.get("definicion").asText()).isNotBlank();
            assertThat(c.get("fuente").asText()).isNotBlank();
            assertThat(c.get("ultimoRegistro").isNull()).as("CA-30-08 referencia temporal de %s",clave).isFalse();
        }
        assertThat(r.get("filtros").get("zonaFechaServicio").asText()).isEqualTo("America/La_Paz");
        assertThat(r.get("naturaleza").asText()).contains("No es un indicador oficial");
        // Periodo: excluye la jornada (y su GPS) del otro periodo de la misma empresa.
        var porPeriodo=reporte(f.completo(),"desde=2026-03-01&hasta=2026-03-31&empresaId="+f.empresa()+"&periodoId="+f.periodo(),200);
        assertThat(cantidad(porPeriodo,"jornadas")).isEqualTo(4);
        assertThat(cantidad(porPeriodo,"posicionesGps")).isEqualTo(3);
        // Un solo día de borde: 31/03 en Bolivia incluye jBorde (01/04 03:00Z).
        assertThat(cantidad(reporte(f.completo(),"desde=2026-03-31&hasta=2026-03-31&empresaId="+f.empresa(),200),"jornadas")).isEqualTo(1);
    }

    @Test void ceroConsultadoSeDistingueDeAccesoNoPermitido() throws Exception {
        var vacia=reporte(f.completo(),"desde=2026-03-01&hasta=2026-03-31&empresaId="+f.empresaVacia(),200);
        assertThat(cantidad(vacia,"jornadas")).isZero();
        assertThat(estado(vacia,"jornadas")).isEqualTo("SIN_REGISTROS");
        assertThat(recuento(vacia,"jornadas").get("ultimoRegistro").isNull()).isTrue();
        assertThat(cantidad(vacia,"posicionesGps")).isZero();
        var basico=reporte(f.basico(),"desde=2026-03-01&hasta=2026-03-31&empresaId="+f.empresa(),200);
        assertThat(cantidad(basico,"jornadas")).isEqualTo(5);
        for(String clave:List.of("posicionesGps","jornadasConGps","incidencias","inspecciones","reclamosVinculados")) {
            assertThat(estado(basico,clave)).as(clave).isEqualTo("ACCESO_NO_PERMITIDO");
            assertThat(cantidad(basico,clave)).as("sin cifra para %s",clave).isNull();
        }
    }

    @Test void sinFiltroDeEmpresaCuentaReclamosRecibidosPorFechaDeRecepcion() throws Exception {
        UUID registrador=usuario(jdbc,codificador,"rp-rec-"+UUID.randomUUID().toString().substring(0,8),"x");
        // Base compartida (p. ej. la variante no propietaria en la misma JVM): se verifica el incremento, no el total.
        long dia15=cantidad(reporte(f.completo(),"desde=2019-07-15&hasta=2019-07-15",200),"reclamosRecibidos");
        long dia16=cantidad(reporte(f.completo(),"desde=2019-07-16&hasta=2019-07-16",200),"reclamosRecibidos");
        jdbc.update("INSERT INTO reclamo_institucional(id,solicitud_id,huella_solicitud,canal_declarado,recibido_en,descripcion,lugar_descripcion,referencias_origen,usuario_id) VALUES (?,?,'h','canal sintético','2019-07-16T03:30:00Z','Reclamo sin jornada','','',?)",
            UUID.randomUUID(),UUID.randomUUID(),registrador);
        // 16/07/2019 03:30Z = 15/07/2019 23:30 en Bolivia.
        var r=reporte(f.completo(),"desde=2019-07-15&hasta=2019-07-15",200);
        assertThat(cantidad(r,"reclamosRecibidos")).as("cuenta el 15/07 en Bolivia").isEqualTo(dia15+1);
        assertThat(cantidad(reporte(f.completo(),"desde=2019-07-16&hasta=2019-07-16",200),"reclamosRecibidos")).as("no el 16/07 (UTC)").isEqualTo(dia16);
    }

    @Test void filtrosInvalidosOIncompatiblesSeRechazanEnLugarDeDarCero() throws Exception {
        reporte(f.completo(),"hasta=2026-03-31",400);
        reporte(f.completo(),"desde=2026-03-31&hasta=2026-03-01",400);
        reporte(f.completo(),"desde=2026-03-01&hasta=fecha",400);
        reporte(f.completo(),"desde=2026-03-01&hasta=2026-03-31&empresaId="+UUID.randomUUID(),404);
        reporte(f.completo(),"desde=2026-03-01&hasta=2026-03-31&periodoId="+UUID.randomUUID(),404);
        reporte(f.completo(),"desde=2026-03-01&hasta=2026-03-31&empresaId="+f.empresaVacia()+"&periodoId="+f.periodo(),400);
    }

    @Test void soloMunicipalConJornadasConsultarSinA4NiExportacion() throws Exception {
        reporte(f.sinPermiso(),"desde=2026-03-01&hasta=2026-03-31",403);
        reporte(f.operador(),"desde=2026-03-01&hasta=2026-03-31&periodoId="+f.periodo(),403);
        assertThat(mvc.perform(get("/api/reportes-servicio/resumen?desde=2026-03-01&hasta=2026-03-31")).andReturn().getResponse().getStatus()).isIn(401,403);
        // CA-30-09: no existe exportación; cualquier otra ruta o método queda cerrado.
        for(String ruta:List.of("/api/reportes-servicio/exportacion","/api/reportes-servicio/resumen.csv"))
            assertThat(mvc.perform(get(ruta).header("Authorization","Bearer "+token(f.completo()))).andReturn().getResponse().getStatus()).as(ruta).isEqualTo(403);
        assertThat(mvc.perform(post("/api/reportes-servicio/resumen").header("Authorization","Bearer "+token(f.completo()))).andReturn().getResponse().getStatus()).isIn(403,405);
    }

    @Test void laGeneracionEsDeSoloLecturaYNoCalculaPorcentajesNiCumplimiento() throws Exception {
        String huella="""
            SELECT (SELECT count(*) FROM jornada)||'|'||(SELECT max(actualizado_en) FROM jornada)||'|'||(SELECT count(*) FROM asignacion_jornada)||'|'||
                   (SELECT count(*) FROM posicion_gps)||'|'||(SELECT count(*) FROM incidencia_operativa)||'|'||(SELECT count(*) FROM inspeccion)||'|'||
                   (SELECT count(*) FROM reclamo_institucional)||'|'||(SELECT count(*) FROM fiscalizacion)||'|'||(SELECT count(*) FROM accion_correctiva)""";
        String antes=jdbc.queryForObject(huella,String.class);
        var r=reporte(f.completo(),"desde=2026-03-01&hasta=2026-03-31&empresaId="+f.empresa(),200);
        reporte(f.basico(),"desde=2026-01-01&hasta=2026-12-31",200);
        assertThat(jdbc.queryForObject(huella,String.class)).as("CA-30-07 sin escrituras").isEqualTo(antes);
        var campos=new TreeSet<String>();
        r.get("recuentos").get(0).properties().forEach(e -> campos.add(e.getKey()));
        assertThat(campos).containsExactlyInAnyOrder("clave","nombre","definicion","fuente","permisoRequerido","estado","cantidad","ultimoRegistro","nota");
        String texto=r.toString().toLowerCase(Locale.ROOT);
        assertThat(texto).doesNotContain("%").doesNotContain("porcentaje\":").doesNotContain("\"cumplimiento");
        for(JsonNode c:r.get("recuentos")) if(c.hasNonNull("cantidad")) assertThat(c.get("cantidad").isIntegralNumber()).as("solo recuentos enteros").isTrue();
    }
}
