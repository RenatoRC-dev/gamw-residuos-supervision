package bo.gob.warnes.gamw.residuos.shared.config;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/**
 * Corte INT (V21) contra PostgreSQL/PostGIS aislado: sin DELETE de la historia de planificación, sin TRUNCATE de evidencia
 * inmutable y consistencia compuesta jornada↔asignación en eventos, GPS e incidencias. Las operaciones legítimas
 * (sustituir jornada por reprogramación, primer cierre de asignación y de versión de ruta) siguen permitidas.
 */
@SpringBootTest(properties={"spring.config.import=","debug=false","logging.level.root=ERROR"})
class IntegridadHistoricaIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    private static final List<String> TABLAS_SIN_VACIADO = List.of(
        "jornada","asignacion_jornada","ruta_version","evento_ejecucion_jornada","posicion_gps",
        "concesion_permiso","concesion_periodo_operador","facultad_delegacion","actuacion_administrativa","referencia_espacial_version",
        "inspeccion","evidencia_inspeccion","fiscalizacion","fiscalizacion_inspeccion","catalogo_resultado_fiscalizacion",
        "incidencia_operativa","accion_correctiva","respuesta_correctiva","respuesta_correctiva_evidencia","verificacion_correctiva",
        "catalogo_verificacion_correctiva","reprogramacion_autorizada","actuacion_planificacion_operador","catalogo_valoracion_reclamo",
        "reclamo_institucional","revision_reclamo","relacion_reclamo","anexo_reclamo","punto_critico","observacion_punto_critico","validacion_punto_critico");

    private record Fixture(UUID ruta,UUID version,UUID jornadaA,UUID asignacionA,UUID jornadaB,UUID asignacionB,UUID usuario) { }
    private Fixture fixture() {
        UUID empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),veh=UUID.randomUUID(),
            vv=UUID.randomUUID(),persona=UUID.randomUUID(),vp=UUID.randomUUID(),usuario=UUID.randomUUID(),ja=UUID.randomUUID(),jb=UUID.randomUUID(),
            aa=UUID.randomUUID(),ab=UUID.randomUUID();
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"INT-"+empresa);
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"INT-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",veh,"INT-"+veh.toString().substring(0,8).toUpperCase());
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,veh,empresa);
        jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",persona,"INT-"+persona,"Persona sintética");
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vp,persona,empresa);
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,'$2a$10$hashsinteticoNoUsado000000000000000000000000000000000',true,now(),now())",usuario,persona,"int-"+usuario);
        for(UUID j:List.of(ja,jb)) jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-10T12:00:00Z',NULL,NULL,NULL,true,now(),now())",j,periodo,version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,'2026-01-01T00:00:00Z',NULL,NULL,now())",aa,ja,vv,vp);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,'2026-01-01T00:00:00Z',NULL,NULL,now())",ab,jb,vv,vp);
        return new Fixture(ruta,version,ja,aa,jb,ab,usuario);
    }
    private static void falla(Runnable sql) { assertThatThrownBy(sql::run).isInstanceOf(DataAccessException.class); }

    @Test void laHistoriaDePlanificacionNoSeBorraPeroAdmiteSusOperacionesLegitimas() {
        var f=fixture();
        // Filas SIN dependientes: así el rechazo proviene del trigger V21 y no de una clave foránea.
        UUID jornadaSola=UUID.randomUUID(),rutaSola=UUID.randomUUID(),versionSola=UUID.randomUUID();
        UUID periodo=jdbc.queryForObject("SELECT periodo_servicio_id FROM jornada WHERE id=?",UUID.class,f.jornadaA());
        jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-11T12:00:00Z',NULL,NULL,NULL,true,now(),now())",jornadaSola,periodo,f.version());
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",rutaSola,"INT-"+rutaSola,"Ruta sintética sin jornadas");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-12 22,-13 23)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",versionSola,rutaSola);
        for(var intento:List.<Runnable>of(
                () -> jdbc.update("DELETE FROM asignacion_jornada WHERE id=?",f.asignacionA()),
                () -> jdbc.update("DELETE FROM jornada WHERE id=?",jornadaSola),
                () -> jdbc.update("DELETE FROM ruta_version WHERE id=?",versionSola)))
            assertThatThrownBy(intento::run).isInstanceOf(DataAccessException.class).hasMessageContaining("no se borra");
        // Operaciones legítimas previas a V21 siguen permitidas.
        assertThat(jdbc.update("UPDATE asignacion_jornada SET vigente_hasta='2026-11-10T13:00:00Z' WHERE id=?",f.asignacionA())).as("primer cierre").isEqualTo(1);
        falla(() -> jdbc.update("UPDATE asignacion_jornada SET vigente_hasta='2026-11-10T14:00:00Z' WHERE id=?",f.asignacionA()));
        assertThat(jdbc.update("UPDATE jornada SET vigente=false,actualizado_en=now() WHERE id=?",f.jornadaB())).as("sustitución por reprogramación").isEqualTo(1);
        falla(() -> jdbc.update("UPDATE jornada SET vigente=true WHERE id=?",f.jornadaB()));
        assertThat(jdbc.update("UPDATE ruta_version SET vigente_hasta='2026-06-01T00:00:00Z' WHERE id=?",f.version())).as("cierre de versión").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jornada WHERE id IN (?,?)",Integer.class,f.jornadaA(),f.jornadaB())).isEqualTo(2);
    }

    @Test void lasVigenciasDeVersionesDeUnaRutaNoSeSuperponen() {
        UUID ruta=UUID.randomUUID();
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"INT-"+ruta,"Ruta sintética de vigencias");
        String linea="ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326)";
        // Contiguas [2025-01-01, 2026-01-01) y [2026-01-01, abierta): válidas.
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,"+linea+",'2025-01-01T00:00:00Z','2026-01-01T00:00:00Z','cierre sintético',now())",UUID.randomUUID(),ruta);
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,2,"+linea+",'2026-01-01T00:00:00Z',NULL,'Cambio sintético',now())",UUID.randomUUID(),ruta);
        // Superpuesta con la versión 1 (cerrada): rechazada por la base, aunque no sea una segunda versión abierta.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ruta_version VALUES (?,?,3,"+linea+",'2025-06-01T00:00:00Z','2025-07-01T00:00:00Z','Inserción sintética superpuesta',now())",UUID.randomUUID(),ruta))
            .isInstanceOf(DataAccessException.class).hasMessageContaining("se superpone");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ruta_version WHERE ruta_id=?",Integer.class,ruta)).isEqualTo(2);
    }

    @Test void ningunaTablaHistoricaSeVaciaConTruncate() {
        for(String tabla:TABLAS_SIN_VACIADO) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgrelid=?::regclass AND NOT tgisinternal AND tgenabled='O' AND (tgtype & 32)<>0",Integer.class,tabla))
                .as("trigger BEFORE TRUNCATE en %s",tabla).isEqualTo(1);
        }
        for(String tabla:List.of("jornada","evento_ejecucion_jornada","fiscalizacion","reclamo_institucional","concesion_permiso","incidencia_operativa"))
            falla(() -> jdbc.execute("TRUNCATE "+tabla+" CASCADE"));
    }

    @Test void eventosGpsEIncidenciasSoloRefierenUnaAsignacionDeSuMismaJornada() {
        var f=fixture();
        Instant t=Instant.parse("2026-11-10T12:30:00Z");
        // Asignación de OTRA jornada: rechazado por la base (antes solo lo impedía la aplicación).
        falla(() -> jdbc.update("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'INICIO',?,?,NULL)",UUID.randomUUID(),UUID.randomUUID(),f.jornadaA(),f.asignacionB(),Timestamp.from(t),Timestamp.from(t)));
        falla(() -> jdbc.update("INSERT INTO posicion_gps VALUES (?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(-10.5,20.5),4326),5)",
            UUID.randomUUID(),UUID.randomUUID(),f.jornadaA(),f.asignacionB(),f.usuario(),Timestamp.from(t),Timestamp.from(t)));
        falla(() -> jdbc.update("INSERT INTO incidencia_operativa(id,solicitud_id,huella_solicitud,jornada_id,asignacion_jornada_id,usuario_id,ocurrida_en,descripcion,evidencia_declarada,lugar_descripcion) VALUES (?,?,'h',?,?,?,?,'x','','')",
            UUID.randomUUID(),UUID.randomUUID(),f.jornadaA(),f.asignacionB(),f.usuario(),Timestamp.from(t)));
        // Misma jornada: aceptado.
        jdbc.update("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'INICIO',?,?,NULL)",UUID.randomUUID(),UUID.randomUUID(),f.jornadaA(),f.asignacionA(),Timestamp.from(t),Timestamp.from(t.plus(1,ChronoUnit.SECONDS)));
        jdbc.update("INSERT INTO posicion_gps VALUES (?,?,?,?,?,?,?,ST_SetSRID(ST_MakePoint(-10.5,20.5),4326),5)",
            UUID.randomUUID(),UUID.randomUUID(),f.jornadaA(),f.asignacionA(),f.usuario(),Timestamp.from(t),Timestamp.from(t.plus(2,ChronoUnit.SECONDS)));
        jdbc.update("INSERT INTO incidencia_operativa(id,solicitud_id,huella_solicitud,jornada_id,asignacion_jornada_id,usuario_id,ocurrida_en,descripcion,evidencia_declarada,lugar_descripcion) VALUES (?,?,'h',?,?,?,?,'Hecho sintético','','')",
            UUID.randomUUID(),UUID.randomUUID(),f.jornadaA(),f.asignacionA(),f.usuario(),Timestamp.from(t));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname IN ('evento_ejecucion_asignacion_misma_jornada_fk','posicion_gps_asignacion_misma_jornada_fk','incidencia_asignacion_misma_jornada_fk') AND convalidated",Integer.class))
            .as("claves compuestas validadas sobre los datos existentes").isEqualTo(3);
    }
}
