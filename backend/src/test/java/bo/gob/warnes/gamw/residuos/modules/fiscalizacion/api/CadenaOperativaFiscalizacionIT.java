package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import bo.gob.warnes.gamw.residuos.shared.security.EmisorTokensJwt;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadena CU-13 → CU-16 → CU-21 con JWT y PostgreSQL/PostGIS aislado (MockMvc, sin Android ni GPS físico):
 * A5 ejecuta y sincroniza GPS (fuera de orden, con reintentos), A3 consulta la trayectoria y el expediente, y solo un
 * fiscalizador autorizado emite un resultado de un catálogo habilitado. El GPS nunca crea ni infiere un resultado.
 */
@SpringBootTest(properties="spring.config.import=")
@AutoConfigureMockMvc
class CadenaOperativaFiscalizacionIT extends IntegracionPostgresV8 {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder codificador;
    @Autowired EmisorTokensJwt emisor;
    private final UUID personaA=UUID.randomUUID(),personaB=UUID.randomUUID(),conductor=UUID.randomUUID(),otroConductor=UUID.randomUUID(),
        empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),
        vpA=UUID.randomUUID(),vpB=UUID.randomUUID(),jornada=UUID.randomUUID(),jornadaVacia=UUID.randomUUID(),asignacion=UUID.randomUUID(),asignacionVacia=UUID.randomUUID();
    private final Instant captura=Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.MICROS);

    @BeforeEach void fixtures() {
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética C13-16-21");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2027-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"CF-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"CF-"+vehiculo.toString().toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        for(UUID p:List.of(personaA,personaB)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"CF-"+p,"Persona sintética CF");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpA,personaA,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vpB,personaB,empresa);
        for(UUID vp:List.of(vpA,vpB)) jdbc.update("INSERT INTO vinculo_persona_funcion VALUES (?,'00000000-0000-4000-8000-000000000002')",vp);
        for(UUID j:List.of(jornada,jornadaVacia)) jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",j,periodo,version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacion,jornada,vv,vpA,Timestamp.from(captura.minusSeconds(3600)));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asignacionVacia,jornadaVacia,vv,vpB,Timestamp.from(captura.minusSeconds(3600)));
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",conductor,personaA,"cf-"+conductor,codificador.encode("clave-sintetica"));
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",otroConductor,personaB,"cf-"+otroConductor,codificador.encode("clave-sintetica"));
    }

    // ---------- utilidades ----------
    private UUID fiscal(String... permisos) {
        UUID persona=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"CF-"+persona,"Persona sintética fiscal");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",id,persona,"cf-"+id,codificador.encode("clave-sintetica"));
        for(String p:permisos) jdbc.update("INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo) VALUES (?,?,?,'MUNICIPAL_GAMW','prueba-sintetica','fixture sintético')",UUID.randomUUID(),id,p);
        return id;
    }
    private String bearer(UUID usuario) { return "Bearer "+emisor.emitir(usuario,UUID.randomUUID(),"no-confiable").token(); } // claims falsos: manda la base
    private JsonNode obtener(UUID usuario,String ruta,int estado) throws Exception {
        var r=mvc.perform(get(ruta).header("Authorization",bearer(usuario))).andExpect(status().is(estado)).andReturn().getResponse().getContentAsString();
        return r.isBlank()?null:json.readTree(r);
    }
    private JsonNode enviar(UUID usuario,String ruta,Object cuerpo,int estado) throws Exception {
        MockHttpServletRequestBuilder b=post(ruta).contentType("application/json").content(json.writeValueAsString(cuerpo));
        if(usuario!=null) b.header("Authorization",bearer(usuario));
        var r=mvc.perform(b).andExpect(status().is(estado)).andReturn().getResponse().getContentAsString();
        return r.isBlank()?null:json.readTree(r);
    }
    /** Sin Bearer, CSRF actúa antes que la autenticación: 401 o 403, nunca éxito ni escritura. */
    private void anonimoRechazado(String ruta,Object cuerpo) throws Exception {
        int s=mvc.perform(post(ruta).contentType("application/json").content(json.writeValueAsString(cuerpo))).andReturn().getResponse().getStatus();
        assertThat(s).isIn(401,403);
    }
    private Map<String,Object> evento(Instant fecha) { return new LinkedHashMap<>(Map.of("eventoClienteId",UUID.randomUUID(),"asignacionJornadaId",asignacion,"capturadoEn",fecha.toString())); }
    private Map<String,Object> punto(UUID id,int segundos,double lat,double lon,double precision) {
        return new LinkedHashMap<>(Map.of("capturaId",id,"capturadoEn",captura.plusSeconds(segundos).toString(),"latitud",lat,"longitud",lon,"precisionMetros",precision));
    }
    private Map<String,Object> lote(Map<String,Object>... puntos) { return new LinkedHashMap<>(Map.of("asignacionJornadaId",asignacion,"posiciones",List.of(puntos))); }
    private UUID categoria(boolean habilitada) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO catalogo_resultado_fiscalizacion(id,codigo,nombre,referencia_aprobacion,observaciones_obligatorias,habilitado,operador_local) VALUES (?,?,?,'referencia sintética de prueba',false,?,'prueba-sintetica')",
            id,"CAT-SINT-"+id,"Categoría sintética de prueba",habilitada);
        return id;
    }
    private static final String EJEC="/api/jornadas/";

    /** CU-13 + CU-16: ejecución autenticada, sincronización GPS fuera de orden/idempotente, consulta A3 cronológica. */
    @SuppressWarnings("unchecked")
    @Test void ejecucionSincronizacionYTrayectoriaCronologicaConAutorizacion() throws Exception {
        // Aislamiento de cuentas y autenticación.
        anonimoRechazado(EJEC+jornada+"/ejecucion/inicio",evento(captura));
        enviar(otroConductor,EJEC+jornada+"/ejecucion/inicio",evento(captura),403); // asignación de otra persona
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",Integer.class,jornada)).isZero();
        enviar(conductor,EJEC+jornada+"/ejecucion/inicio",evento(captura),201);
        // Lote sincronizado DESPUÉS de capturar y en orden distinto al de captura (cola offline).
        UUID c1=UUID.randomUUID(),c2=UUID.randomUUID(),c3=UUID.randomUUID();
        var cuerpo=lote(punto(c3,3,20.30,-10.90,9.5),punto(c1,1,20.10,-10.70,5.5),punto(c2,2,20.20,-10.80,7.5));
        var primero=enviar(conductor,EJEC+jornada+"/ejecucion/posiciones-gps",cuerpo,200);
        assertThat(enviar(conductor,EJEC+jornada+"/ejecucion/posiciones-gps",cuerpo,200)).isEqualTo(primero); // reintento idempotente
        var conflicto=lote(punto(c1,1,25.0,-10.70,5.5)); // mismo capturaId, contenido distinto
        enviar(conductor,EJEC+jornada+"/ejecucion/posiciones-gps",conflicto,409);
        enviar(otroConductor,EJEC+jornada+"/ejecucion/posiciones-gps",cuerpo,403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=? AND recibido_en>capturado_en",Integer.class,jornada)).isEqualTo(3);
        enviar(conductor,EJEC+jornada+"/ejecucion/fin",evento(captura.plusSeconds(10)),201);
        // Consulta A3 con permiso de trayectoria: orden por captura, [longitud,latitud], precisión y recepción originales.
        UUID a3=fiscal("TRAYECTORIA_JORNADA_CONSULTAR");
        var t=obtener(a3,"/api/trayectorias/jornadas/"+jornada,200);
        var puntos=t.get("seguimiento").get("trayectoria").get("features");
        assertThat(puntos.size()).isEqualTo(3);
        double[][] esperados={{-10.70,20.10,5.5},{-10.80,20.20,7.5},{-10.90,20.30,9.5}};
        Instant previo=Instant.MIN;
        for(int i=0;i<3;i++) {
            var p=puntos.get(i);
            assertThat(p.get("geometry").get("coordinates").get(0).asDouble()).isEqualTo(esperados[i][0]); // longitud primero
            assertThat(p.get("geometry").get("coordinates").get(1).asDouble()).isEqualTo(esperados[i][1]);
            assertThat(p.get("properties").get("precisionMetros").asDouble()).isEqualTo(esperados[i][2]);
            Instant cap=Instant.parse(p.get("properties").get("capturadoEn").asText()),rec=Instant.parse(p.get("properties").get("recibidoEn").asText());
            assertThat(cap).isEqualTo(captura.plusSeconds(i+1)).isAfter(previo); previo=cap;
            assertThat(rec).isAfter(cap); // recepción distinta de captura
        }
        assertThat(t.get("rutaReferencia").get("coordinates").get(0).get(0).asDouble()).isEqualTo(-10.0); // versión histórica de la jornada
        assertThat(t.get("rutaReferencia").get("coordinates").get(1).get(1).asDouble()).isEqualTo(21.0);
        // Jornada sin GPS: se informa vacía, sin inventar posiciones ni conclusiones.
        var vacia=obtener(a3,"/api/trayectorias/jornadas/"+jornadaVacia,200);
        assertThat(vacia.get("seguimiento").get("trayectoria").get("features").size()).isZero();
        // Sin permiso de trayectoria: ni conductor, ni fiscalizador solo con FISCALIZACION_CONSULTAR.
        obtener(conductor,"/api/trayectorias/jornadas/"+jornada,403);
        obtener(fiscal("FISCALIZACION_CONSULTAR"),"/api/trayectorias/jornadas/"+jornada,403);
        // La evidencia es inmutable.
        assertThatThrownBy(() -> jdbc.update("UPDATE posicion_gps SET precision_metros=1 WHERE jornada_id=?",jornada)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM posicion_gps WHERE jornada_id=?",jornada)).isInstanceOf(DataAccessException.class);
    }

    /** CU-21: expediente con permisos por módulo y emisión humana solo con catálogo habilitado; sin dictamen automático. */
    @Test void expedienteYResultadoSoloPorFiscalizadorAutorizadoConCatalogoHabilitado() throws Exception {
        enviar(conductor,EJEC+jornada+"/ejecucion/inicio",evento(captura),201);
        UUID p1=UUID.randomUUID();
        enviar(conductor,EJEC+jornada+"/ejecucion/posiciones-gps",lote(punto(p1,1,20.10,-10.70,5.5)),200);
        enviar(conductor,EJEC+jornada+"/ejecucion/fin",evento(captura.plusSeconds(10)),201);
        // INICIO/FIN/GPS no producen ninguna fiscalización.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();

        UUID soloConsulta=fiscal("FISCALIZACION_CONSULTAR"),emisor=fiscal("FISCALIZACION_CONSULTAR","FISCALIZACION_EMITIR"),
            amplio=fiscal("FISCALIZACION_CONSULTAR","CORRECTIVA_CONSULTAR","RECLAMO_CONSULTAR");
        var e=obtener(soloConsulta,"/api/fiscalizacion/jornadas/"+jornada,200);
        List<String> tipos=new ArrayList<>(); e.get("contexto").get("eventos").forEach(n->tipos.add(n.get("tipoEvento").asText()));
        assertThat(tipos).containsExactly("INICIO","FIN");
        assertThat(e.get("contexto").get("gps").get("cantidad").asInt()).isEqualTo(1);
        assertThat(e.get("puedeEmitirResultado").asBoolean()).isFalse();
        assertThat(e.get("fiscalizacion").isNull()).isTrue();
        assertThat(e.get("limitaciones").asText()).contains("no determina cumplimiento");
        for(JsonNode r:e.get("referencias")) { // sin permiso propio no se sabe si hay registros: no es ausencia
            assertThat(r.get("estado").asText()).isEqualTo("ACCESO_NO_PERMITIDO"); assertThat(r.get("cantidad").isNull()).isTrue();
        }
        var completo=obtener(amplio,"/api/fiscalizacion/jornadas/"+jornada,200);
        assertThat(completo.get("referencias").size()).isEqualTo(3);
        for(JsonNode r:completo.get("referencias")) { assertThat(r.get("estado").asText()).isEqualTo("SIN_REGISTROS"); assertThat(r.get("cantidad").asInt()).isZero(); }
        assertThat(obtener(emisor,"/api/fiscalizacion/jornadas/"+jornada,200).get("puedeEmitirResultado").asBoolean()).isTrue();
        obtener(conductor,"/api/fiscalizacion/jornadas/"+jornada,403);
        obtener(fiscal(),"/api/fiscalizacion/jornadas/"+jornada,403);
        obtener(emisor,"/api/fiscalizacion/jornadas/"+UUID.randomUUID(),404);

        // Resultado: autorización, catálogo no aprobado/retirado, idempotencia, no sustitución.
        UUID solicitud=UUID.randomUUID(),habilitada=categoria(true),retirada=categoria(false);
        var decision=new LinkedHashMap<String,Object>(Map.of("solicitudId",solicitud,"resultadoId",habilitada,"observaciones","Observación sintética","inspeccionesReferenciadas",List.of()));
        enviar(conductor,"/api/fiscalizacion/jornadas/"+jornada+"/resultado",decision,403);        // JWT de conductor
        enviar(soloConsulta,"/api/fiscalizacion/jornadas/"+jornada+"/resultado",decision,403);     // consulta no es emisión
        anonimoRechazado("/api/fiscalizacion/jornadas/"+jornada+"/resultado",decision);
        enviar(emisor,"/api/fiscalizacion/jornadas/"+jornada+"/resultado",new LinkedHashMap<>(Map.of("solicitudId",UUID.randomUUID(),"resultadoId",UUID.randomUUID(),
            "observaciones","x","inspeccionesReferenciadas",List.of())),409); // categoría inventada
        enviar(emisor,"/api/fiscalizacion/jornadas/"+jornada+"/resultado",new LinkedHashMap<>(Map.of("solicitudId",UUID.randomUUID(),"resultadoId",retirada,
            "observaciones","x","inspeccionesReferenciadas",List.of())),409); // categoría retirada
        var invalida=new LinkedHashMap<>(decision); invalida.put("campoNoPermitido","x");
        enviar(emisor,"/api/fiscalizacion/jornadas/"+jornada+"/resultado",invalida,400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isZero();

        var primera=enviar(emisor,"/api/fiscalizacion/jornadas/"+jornada+"/resultado",decision,200);
        assertThat(primera.get("fiscalizadorUsuarioId").asText()).isEqualTo(emisor.toString());
        assertThat(primera.get("resultado").get("id").asText()).isEqualTo(habilitada.toString());
        assertThat(enviar(emisor,"/api/fiscalizacion/jornadas/"+jornada+"/resultado",decision,200)).isEqualTo(primera); // mismo solicitudId
        var otra=new LinkedHashMap<>(decision); otra.put("solicitudId",UUID.randomUUID());
        enviar(emisor,"/api/fiscalizacion/jornadas/"+jornada+"/resultado",otra,409);  // no se reemplaza ni se rectifica sin procedimiento
        enviar(fiscal("FISCALIZACION_CONSULTAR","FISCALIZACION_EMITIR"),"/api/fiscalizacion/jornadas/"+jornada+"/resultado",otra,409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fiscalizacion WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(1);
        // Evidencia original intacta tras la decisión; la decisión es inmutable.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ST_X(geometria) FROM posicion_gps WHERE captura_id=?",Double.class,p1)).isEqualTo(-10.70);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",Integer.class,jornada)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("UPDATE fiscalizacion SET observaciones='cambiada' WHERE jornada_id=?",jornada)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM fiscalizacion WHERE jornada_id=?",jornada)).isInstanceOf(DataAccessException.class);
        assertThat(obtener(soloConsulta,"/api/fiscalizacion/jornadas/"+jornada,200).get("fiscalizacion").get("fiscalizadorUsuarioId").asText()).isEqualTo(emisor.toString());
    }
}
