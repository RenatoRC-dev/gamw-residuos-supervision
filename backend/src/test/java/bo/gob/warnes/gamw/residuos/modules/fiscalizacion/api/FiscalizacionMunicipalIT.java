package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import bo.gob.warnes.gamw.residuos.modules.fiscalizacion.dominio.RepositorioFiscalizacion;
import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * CU-21 / RF-18: revisión municipal (A3) del expediente de una jornada con JWT real y PostgreSQL/PostGIS aislados: contexto histórico
 * (empresa, periodo, versión de ruta, asignaciones sucesivas, INICIO/FIN, GPS con corte de recepción, inspecciones), fuentes con permiso
 * propio, revocación, aislamiento de cuentas A4/A5 y PENDING-05: sin catálogo aprobado no se emite ni fabrica un resultado oficial. Datos y
 * geometrías sintéticos; la inspección se siembra como antecedente de CU-20 (no se ejercita su alta). La BD efímera se comparte: solo se
 * afirman IDs propios.
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class FiscalizacionMunicipalIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    @MockitoSpyBean RepositorioFiscalizacion repositorio;
    private static final String LISTA="/api/fiscalizacion/jornadas",F=LISTA+"/";
    private final UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),v1=UUID.randomUUID(),v2=UUID.randomUUID(),
        vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),personaA=UUID.randomUUID(),personaB=UUID.randomUUID(),vpA=UUID.randomUUID(),vpB=UUID.randomUUID(),
        jornada=UUID.randomUUID(),vacia=UUID.randomUUID(),a1=UUID.randomUUID(),a2=UUID.randomUUID(),inspeccion=UUID.randomUUID();
    private final String placa="FM-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
    private final Instant ahora=Instant.now().truncatedTo(ChronoUnit.MICROS),cambio=ahora.minus(2,ChronoUnit.HOURS),inicio=ahora.minus(30,ChronoUnit.MINUTES);
    private UUID conductor,inspector;

    @BeforeEach void fixtures() throws Exception {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética CU-21");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"FM-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",v1,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,placa);
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",personaA,"FM-"+personaA,"Conductor sintético A");
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",personaB,"FM-"+personaB,"Conductor sintético B");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpA,personaA,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpB,personaB,empresa);
        for(UUID vp:List.of(vpA,vpB)) jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);
        for(UUID j:List.of(jornada,vacia)) jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",j,periodo,v1);
        // Sucesión de asignaciones: B cerrada por cambio administrativo; A vigente.
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",a1,jornada,vv,vpB,Timestamp.from(cambio.minus(1,ChronoUnit.HOURS)));
        jdbc.update("UPDATE asignacion_jornada SET vigente_hasta=? WHERE id=?",Timestamp.from(cambio),a1);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,'Cambio de conductor sintético',now())",a2,jornada,vv,vpA,Timestamp.from(cambio));
        // Cambio POSTERIOR de la ruta: la jornada conserva la versión 1.
        jdbc.update("UPDATE ruta_version SET vigente_hasta='2026-11-15T00:00:00Z' WHERE id=?",v1);
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,2,ST_GeomFromText('LINESTRING(-30 40,-31 41)',4326),'2026-11-15T00:00:00Z',NULL,NULL,now())",v2,ruta);
        conductor=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",conductor,personaA,"fm-"+conductor,codificador.encode("clave-sintetica"));
        inspector=cuenta("INSPECCION_REGISTRAR");
        jdbc.update("INSERT INTO inspeccion(id,solicitud_id,huella_solicitud,jornada_id,inspector_usuario_id,realizada_en,lugar_descripcion,observaciones) VALUES (?,?,'h',?,?,?,?,?)",
            inspeccion,UUID.randomUUID(),jornada,inspector,Timestamp.from(inicio.plusSeconds(600)),"Tramo sintético","Inspección sintética CU-20 (antecedente)");
    }
    @AfterEach void restaurar() { reset(repositorio); }
    private UUID cuenta(String... permisos) {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"FM-"+persona,"Persona sintética");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"fm-"+id,codificador.encode("clave-sintetica"));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private String token(UUID u) { return emisor.emitir(u,UUID.randomUUID(),"no-confiable").token(); }
    private JsonNode llamar(UUID u,String metodo,String ruta,Object cuerpo,int estado) throws Exception { return llamar(u==null?null:token(u),metodo,ruta,cuerpo,estado); }
    private JsonNode llamar(String t,String metodo,String ruta,Object cuerpo,int estado) throws Exception {
        var b=metodo.equals("GET")?get(ruta):post(ruta);
        if(t!=null) b=b.header("Authorization","Bearer "+t);
        if(cuerpo!=null) b=b.contentType("application/json").content(cuerpo instanceof String s?s:json.writeValueAsString(cuerpo));
        var r=mvc.perform(b).andReturn().getResponse();
        assertThat(r.getStatus()).as("%s %s → %s",metodo,ruta,r.getContentAsString()).isEqualTo(estado);
        return r.getContentAsString().isBlank()?null:json.readTree(r.getContentAsString());
    }
    private Map<String,Object> evento(Instant t) { return Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",a2,"capturadoEn",t.toString()); }
    private Map<String,Object> lote(Map<String,Object>... p) { return Map.of("asignacionJornadaId",a2,"posiciones",List.of(p)); }
    private Map<String,Object> gps(int segundos,double lon,double lat) {
        return Map.of("capturaId",UUID.randomUUID(),"capturadoEn",inicio.plusSeconds(segundos).toString(),"latitud",lat,"longitud",lon,"precisionMetros",6.5);
    }
    /** A5 ejecuta por la API real: INICIO, GPS en dos lotes (el segundo recibido tras el corte), FIN. Devuelve el corte entre lotes. */
    private Instant ejecutar() throws Exception {
        llamar(conductor,"POST","/api/jornadas/"+jornada+"/ejecucion/inicio",evento(inicio),201);
        llamar(conductor,"POST","/api/jornadas/"+jornada+"/ejecucion/posiciones-gps",lote(gps(60,-10.2,20.2),gps(30,-10.1,20.1)),200);
        Instant corte=Instant.now().truncatedTo(ChronoUnit.MICROS);
        Thread.sleep(5);
        llamar(conductor,"POST","/api/jornadas/"+jornada+"/ejecucion/posiciones-gps",lote(gps(90,-10.3,20.3)),200);
        llamar(conductor,"POST","/api/jornadas/"+jornada+"/ejecucion/fin",evento(inicio.plusSeconds(1200)),201);
        return corte;
    }
    private static Map<String,Object> decision(UUID solicitud,UUID resultado,List<UUID> inspecciones) {
        var m=new LinkedHashMap<String,Object>(); m.put("solicitudId",solicitud); m.put("resultadoId",resultado); m.put("observaciones","Revisión sintética"); m.put("inspeccionesReferenciadas",inspecciones); return m;
    }
    private int fiscalizaciones() { return jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id IN (?,?)",Integer.class,jornada,vacia); }
    private Map<String,Object> evidencia() {
        return jdbc.queryForMap("""
            SELECT (SELECT string_agg(id::text||capturado_en||recibido_en||ST_AsText(geometria),',' ORDER BY id) FROM posicion_gps WHERE jornada_id=?) gps,
                   (SELECT string_agg(id::text||tipo_evento||capturado_en||recibido_en,',' ORDER BY id) FROM evento_ejecucion_jornada WHERE jornada_id=?) eventos,
                   (SELECT string_agg(id::text||realizada_en||registrada_en||observaciones,',' ORDER BY id) FROM inspeccion WHERE jornada_id=?) inspecciones
            """,jornada,jornada,jornada);
    }

    @Test void expedienteConservaContextoHistoricoEvidenciaRecibidaYDatosAusentes() throws Exception {
        Instant corte=ejecutar();
        UUID revisor=cuenta("FISCALIZACION_CONSULTAR");
        var e=llamar(revisor,"GET",F+jornada,null,200); var c=e.get("contexto");
        assertThat(c.get("jornada").get("id").asText()).isEqualTo(jornada.toString());
        assertThat(c.get("empresa").get("id").asText()).isEqualTo(empresa.toString());
        assertThat(c.get("periodo").get("id").asText()).isEqualTo(periodo.toString());
        assertThat(c.get("periodo").get("empresaOperadoraId").asText()).isEqualTo(empresa.toString());
        assertThat(c.get("rutaVersion").get("id").asText()).as("versión histórica de la jornada, no la vigente").isEqualTo(v1.toString());
        assertThat(c.get("rutaVersion").get("numeroVersion").asInt()).isEqualTo(1);
        // Asignaciones sucesivas en orden, con identidades históricas.
        var asig=c.get("asignaciones");
        assertThat(asig).hasSize(2);
        assertThat(asig.get(0).get("asignacion").get("id").asText()).isEqualTo(a1.toString());
        assertThat(asig.get(0).get("personaId").asText()).isEqualTo(personaB.toString());
        assertThat(Instant.parse(asig.get(0).get("asignacion").get("vigenteHasta").asText())).isEqualTo(cambio);
        assertThat(asig.get(1).get("asignacion").get("id").asText()).isEqualTo(a2.toString());
        assertThat(asig.get(1).get("asignacion").get("motivoCambio").asText()).isEqualTo("Cambio de conductor sintético");
        assertThat(asig.get(1).get("asignacion").get("vigenteHasta").isNull()).isTrue();
        for(JsonNode a:asig) { assertThat(a.get("placa").asText()).isEqualTo(placa); assertThat(a.get("vehiculoId").asText()).isEqualTo(vehiculo.toString()); }
        // INICIO/FIN con captura y recepción originales, autor y asignación.
        var ev=c.get("eventos");
        assertThat(ev).hasSize(2);
        assertThat(ev.get(0).get("tipoEvento").asText()).isEqualTo("INICIO");
        assertThat(Instant.parse(ev.get(0).get("capturadoEn").asText())).isEqualTo(inicio);
        assertThat(Instant.parse(ev.get(0).get("recibidoEn").asText())).isAfter(inicio);
        assertThat(ev.get(0).get("usuarioId").asText()).isEqualTo(conductor.toString());
        assertThat(ev.get(0).get("asignacionJornadaId").asText()).isEqualTo(a2.toString());
        assertThat(ev.get(1).get("tipoEvento").asText()).isEqualTo("FIN");
        // GPS: solo lo recibido; sin identificadores fuera del antecedente de emisión.
        assertThat(c.get("gps").get("cantidad").asInt()).isEqualTo(3);
        assertThat(Instant.parse(c.get("gps").get("ultimaCaptura").asText())).isEqualTo(inicio.plusSeconds(90));
        assertThat(c.get("gps").get("ids").isNull()).isTrue();
        // Inspección conservada con autor y fechas originales; sin resultado.
        assertThat(e.get("inspecciones")).hasSize(1);
        var i=e.get("inspecciones").get(0);
        assertThat(i.get("id").asText()).isEqualTo(inspeccion.toString());
        assertThat(i.get("inspectorUsuarioId").asText()).isEqualTo(inspector.toString());
        assertThat(Instant.parse(i.get("realizadaEn").asText())).isEqualTo(inicio.plusSeconds(600));
        assertThat(e.get("fiscalizacion").isNull()).isTrue();
        assertThat(e.get("puedeRegistrarInspeccion").asBoolean()).isFalse();
        assertThat(e.get("puedeEmitirResultado").asBoolean()).isFalse();
        assertThat(e.get("limitaciones").asText()).contains("no es evidencia de incumplimiento").contains("no determina cumplimiento");
        // GPS del expediente: orden por captura, [longitud, latitud], línea de la versión histórica, corte de recepción.
        var g=llamar(revisor,"GET",F+jornada+"/gps",null,200);
        var pts=g.get("seguimiento").get("trayectoria").get("features");
        assertThat(pts).hasSize(3);
        double[] lons={-10.1,-10.2,-10.3};
        for(int k=0;k<3;k++) {
            assertThat(pts.get(k).get("geometry").get("coordinates").get(0).asDouble()).isEqualTo(lons[k]);
            assertThat(Instant.parse(pts.get(k).get("properties").get("capturadoEn").asText())).isEqualTo(inicio.plusSeconds(30L*(k+1)));
        }
        assertThat(g.get("rutaReferencia").get("coordinates").get(0).get(0).asDouble()).as("geometría de la versión 1").isEqualTo(-10.0);
        var parcial=llamar(revisor,"GET",F+jornada+"/gps?corteRecepcion="+corte,null,200);
        assertThat(parcial.get("seguimiento").get("trayectoria").get("features")).as("lo recibido después del corte no se presenta").hasSize(2);
        llamar(revisor,"GET",F+jornada+"/gps?corteRecepcion="+Instant.now().plus(1,ChronoUnit.HOURS),null,400);
        llamar(revisor,"GET",F+jornada+"/gps?desplazamiento=-1",null,400);
        // Jornada sin evidencia: vacíos explícitos, sin valores inventados.
        var v=llamar(revisor,"GET",F+vacia,null,200);
        assertThat(v.get("contexto").get("eventos")).isEmpty();
        assertThat(v.get("contexto").get("asignaciones")).isEmpty();
        assertThat(v.get("contexto").get("gps").get("cantidad").asInt()).isZero();
        assertThat(v.get("contexto").get("gps").get("ultimaCaptura").isNull()).isTrue();
        assertThat(v.get("inspecciones")).isEmpty();
        assertThat(llamar(revisor,"GET",F+vacia+"/gps",null,200).get("seguimiento").get("trayectoria").get("features")).isEmpty();
        // Jornada inexistente.
        llamar(revisor,"GET",F+UUID.randomUUID(),null,404);
        llamar(revisor,"GET",F+UUID.randomUUID()+"/gps",null,404);
        assertThat(fiscalizaciones()).as("revisar no emite resultado").isZero();
    }

    @Test void fuentesConPermisoPropioRevocacionYAislamientoDeCuentasNoMunicipales() throws Exception {
        llamar(conductor,"POST","/api/jornadas/"+jornada+"/ejecucion/inicio",evento(inicio),201);
        var incidencia=Map.of("solicitudId",UUID.randomUUID(),"asignacionJornadaId",a2,"ocurridaEn",inicio.plusSeconds(300).toString(),
            "descripcion","Incidencia sintética","evidenciaDeclarada","","lugarDescripcion","Tramo sintético");
        llamar(conductor,"POST","/api/jornadas/"+jornada+"/incidencias",incidencia,200);
        llamar(conductor,"POST","/api/jornadas/"+jornada+"/ejecucion/fin",evento(inicio.plusSeconds(1200)),201);
        jdbc.update("""
            INSERT INTO reclamo_institucional(id,solicitud_id,huella_solicitud,canal_declarado,recibido_en,descripcion,lugar_descripcion,referencias_origen,jornada_id,usuario_id)
            VALUES (?,?,'h','Teléfono institucional (declarado)',?,'Reclamo sintético CU-21','','Registro sintético',?,?)""",
            UUID.randomUUID(),UUID.randomUUID(),Timestamp.from(ahora.minusSeconds(60)),jornada,cuenta());
        // Con RECLAMO_CONSULTAR pero sin CORRECTIVA_CONSULTAR: reclamos contados; incidencias/acciones desconocidas (no «sin registros»).
        UUID revisor=cuenta("FISCALIZACION_CONSULTAR","RECLAMO_CONSULTAR");
        String t=token(revisor);
        var refs=porModulo(llamar(t,"GET",F+jornada,null,200));
        assertThat(refs.get("Reclamos vinculados (directos o por relación)").get("estado").asText()).isEqualTo("CON_REGISTROS");
        assertThat(refs.get("Reclamos vinculados (directos o por relación)").get("cantidad").asInt()).isEqualTo(1);
        for(String m:List.of("Incidencias operativas","Acciones correctivas")) {
            assertThat(refs.get(m).get("estado").asText()).isEqualTo("ACCESO_NO_PERMITIDO");
            assertThat(refs.get(m).get("cantidad").isNull()).isTrue();
        }
        var amplio=porModulo(llamar(cuenta("FISCALIZACION_CONSULTAR","CORRECTIVA_CONSULTAR"),"GET",F+jornada,null,200));
        assertThat(amplio.get("Incidencias operativas").get("estado").asText()).isEqualTo("CON_REGISTROS");
        assertThat(amplio.get("Acciones correctivas").get("estado").asText()).as("ausencia, no evidencia negativa").isEqualTo("SIN_REGISTROS");
        assertThat(amplio.get("Reclamos vinculados (directos o por relación)").get("estado").asText()).isEqualTo("ACCESO_NO_PERMITIDO");
        // Revocación sobre el mismo token: la fuente pasa a desconocida; luego se pierde el expediente completo.
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='RECLAMO_CONSULTAR' AND revocado_en IS NULL",revisor);
        assertThat(porModulo(llamar(t,"GET",F+jornada,null,200)).get("Reclamos vinculados (directos o por relación)").get("estado").asText()).isEqualTo("ACCESO_NO_PERMITIDO");
        jdbc.update("UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local='prueba-sintetica',motivo_revocacion='revocación sintética' WHERE usuario_id=? AND permiso='FISCALIZACION_CONSULTAR' AND revocado_en IS NULL",revisor);
        llamar(t,"GET",F+jornada,null,403); llamar(t,"GET",F+jornada+"/gps",null,403); llamar(t,"GET",LISTA,null,403);
        // Cuentas no municipales: conductor (A5), operador A4 con concesión de su periodo, cuentas con otros permisos municipales.
        UUID operador=cuenta(); jdbc.update("INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo) VALUES (?,?,?,'CORRECTIVA_CONSULTAR',true,'prueba-sintetica','fixture sintético')",UUID.randomUUID(),operador,periodo);
        UUID trayectoria=cuenta("TRAYECTORIA_JORNADA_CONSULTAR","JORNADAS_CONSULTAR","RECLAMO_CONSULTAR");
        var antes=evidencia();
        for(UUID u:new UUID[]{conductor,operador,trayectoria,inspector}) {
            llamar(u,"GET",F+jornada,null,403); llamar(u,"GET",F+jornada+"/gps",null,403); llamar(u,"GET",LISTA,null,403);
            llamar(u,"POST",F+jornada+"/resultado",decision(UUID.randomUUID(),UUID.randomUUID(),List.of()),403);
        }
        // Escrituras no autorizadas de quien solo consulta.
        UUID soloConsulta=cuenta("FISCALIZACION_CONSULTAR");
        llamar(soloConsulta,"POST",F+jornada+"/resultado",decision(UUID.randomUUID(),UUID.randomUUID(),List.of(inspeccion)),403);
        llamar(soloConsulta,"POST",F+jornada+"/inspecciones",Map.of("solicitudId",UUID.randomUUID(),"realizadaEn",ahora.minusSeconds(60).toString(),"observaciones","x"),403);
        assertThat(mvc.perform(get(F+jornada)).andReturn().getResponse().getStatus()).isIn(401,403);
        assertThat(mvc.perform(post(F+jornada+"/resultado").contentType("application/json").content(json.writeValueAsString(decision(UUID.randomUUID(),UUID.randomUUID(),List.of()))))
            .andReturn().getResponse().getStatus()).isIn(401,403);
        assertThat(evidencia()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inspeccion WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(1);
        assertThat(fiscalizaciones()).isZero();
    }
    private static Map<String,JsonNode> porModulo(JsonNode expediente) {
        var m=new HashMap<String,JsonNode>(); for(JsonNode r:expediente.get("referencias")) m.put(r.get("modulo").asText(),r); return m;
    }

    @Test void sinCatalogoAprobadoPending05NoSeEmiteNiSeFabricaResultado() throws Exception {
        ejecutar();
        // Flyway V1–V21 no precarga categorías: toda fila existente es una fixture de prueba.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalogo_resultado_fiscalizacion WHERE operador_local<>'prueba-sintetica'",Integer.class)).isZero();
        UUID emisorMunicipal=cuenta("FISCALIZACION_CONSULTAR","FISCALIZACION_EMITIR");
        var e=llamar(emisorMunicipal,"GET",F+jornada,null,200);
        assertThat(e.get("puedeEmitirResultado").asBoolean()).isTrue();
        for(JsonNode c:e.get("resultadosHabilitados"))
            assertThat(jdbc.queryForObject("SELECT operador_local FROM catalogo_resultado_fiscalizacion WHERE id=?",String.class,UUID.fromString(c.get("id").asText())))
                .as("solo fixtures de otras pruebas, nunca un catálogo oficial").isEqualTo("prueba-sintetica");
        var antes=evidencia();
        // Categoría retirada (insertada ya retirada: no habilita nada) y categoría inexistente: 409 sin persistir.
        UUID retirada=UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo_resultado_fiscalizacion(id,codigo,nombre,referencia_aprobacion,observaciones_obligatorias,habilitado,operador_local) VALUES (?,?,'Retirada sintética','Sin aprobación (prueba)',false,false,'prueba-sintetica')",
            retirada,"RET-"+retirada);
        llamar(emisorMunicipal,"POST",F+jornada+"/resultado",decision(UUID.randomUUID(),UUID.randomUUID(),List.of(inspeccion)),409);
        llamar(emisorMunicipal,"POST",F+jornada+"/resultado",decision(UUID.randomUUID(),retirada,List.of(inspeccion)),409);
        // Entradas incompletas o con estado/autor del cliente: 400.
        llamar(emisorMunicipal,"POST",F+jornada+"/resultado",decision(UUID.randomUUID(),null,List.of()),400);
        llamar(emisorMunicipal,"POST",F+jornada+"/resultado",decision(null,retirada,List.of()),400);
        llamar(emisorMunicipal,"POST",F+jornada+"/resultado",decision(UUID.randomUUID(),retirada,null),400);
        llamar(emisorMunicipal,"POST",F+jornada+"/resultado",decision(UUID.randomUUID(),retirada,List.of(inspeccion,inspeccion)),400);
        for(String campo:List.of("estado","cumple","fiscalizadorUsuarioId","sancion")) {
            var d=decision(UUID.randomUUID(),retirada,List.of()); d.put(campo,campo.equals("fiscalizadorUsuarioId")?inspector.toString():"CUMPLE");
            llamar(emisorMunicipal,"POST",F+jornada+"/resultado",d,400);
        }
        llamar(emisorMunicipal,"POST",F+jornada+"/resultado","{malformado",400);
        llamar(emisorMunicipal,"POST",F+UUID.randomUUID()+"/resultado",decision(UUID.randomUUID(),retirada,List.of()),404);
        // Sin resultado ni rastro; la evidencia original no cambia; el expediente sigue «sin dictamen».
        assertThat(fiscalizaciones()).isZero();
        assertThat(evidencia()).isEqualTo(antes);
        assertThat(llamar(emisorMunicipal,"GET",F+jornada,null,200).get("fiscalizacion").isNull()).isTrue();
        // La fiscalización no puede fabricarse ni alterarse directamente sin una categoría existente (FK) y la evidencia es inmutable.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO fiscalizacion(id,solicitud_id,huella_solicitud,jornada_id,fiscalizador_usuario_id,resultado_id,observaciones,corte_evidencia_en,antecedentes) VALUES (?,?,'h',?,?,?,'x',now(),'{}')",
            UUID.randomUUID(),UUID.randomUUID(),jornada,emisorMunicipal,UUID.randomUUID())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE inspeccion SET observaciones='alterada' WHERE id=?",inspeccion)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM evento_ejecucion_jornada WHERE jornada_id=?",jornada)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE asignacion_jornada SET vigente_hasta=now() WHERE id=?",a1)).as("cierre ya registrado").isInstanceOf(DataAccessException.class);
        assertThat(fiscalizaciones()).isZero();
    }

    @Test void unFalloDePersistenciaResponde503YNoSeInformaComoFaltaDePermiso() throws Exception {
        UUID revisor=cuenta("FISCALIZACION_CONSULTAR","FISCALIZACION_EMITIR");
        doThrow(new DataAccessResourceFailureException("Fallo inyectado (prueba).")).when(repositorio).inspecciones(any());
        var r=mvc.perform(get(F+jornada).header("Authorization","Bearer "+token(revisor))).andReturn().getResponse();
        assertThat(r.getStatus()).as("consulta con fallo de BD, no «sin permiso»: %s",r.getContentAsString()).isEqualTo(503);
        doThrow(new DataAccessResourceFailureException("Fallo inyectado (prueba).")).when(repositorio).fiscalizacionPorSolicitud(any());
        r=mvc.perform(post(F+jornada+"/resultado").header("Authorization","Bearer "+token(revisor)).contentType("application/json")
            .content(json.writeValueAsString(decision(UUID.randomUUID(),UUID.randomUUID(),List.of())))).andReturn().getResponse();
        assertThat(r.getStatus()).as("emisión con fallo de BD, no «sin permiso»: %s",r.getContentAsString()).isEqualTo(503);
        assertThat(fiscalizaciones()).isZero();
        reset(repositorio);
        assertThat(llamar(revisor,"GET",F+jornada,null,200).get("inspecciones")).hasSize(1);
    }
}
