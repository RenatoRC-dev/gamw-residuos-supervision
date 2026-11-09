package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** Opt-in Android real: dos arranques de Flutter, KeyStore y SQLite nativos; servidor/BD propios. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.config.import=",
        "server.address=127.0.0.1","gamw.seguridad.jwt.duracion=PT1H"})
@EnabledIfSystemProperty(named="gamw.mobile.e2e",matches="true")
class FlujoMovilIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder contrasenas;
    @Value("${local.server.port}") int puerto;

    @Test @Timeout(value=35,unit=TimeUnit.MINUTES)
    void androidRealRestauraSesionYEvidenciaYOperaContraPostgresAislado() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),pa=UUID.randomUUID(),pb=UUID.randomUUID(),empresa=UUID.randomUUID(),
                periodo=UUID.randomUUID(),ruta=UUID.randomUUID(),version=UUID.randomUUID(),vehiculo=UUID.randomUUID(),
                vv=UUID.randomUUID(),va=UUID.randomUUID(),vb=UUID.randomUUID(),ja=UUID.randomUUID(),jb=UUID.randomUUID(),jh=UUID.randomUUID();
        String nombreA="movil-"+a,nombreB="movil-"+b,clave=UUID.randomUUID().toString();
        Instant captura=Instant.parse("2026-11-08T12:00:00.123456Z");
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética E2E");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2027-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"IT-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"IT-"+vehiculo.toString().toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        for(UUID p:List.of(pa,pb)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"IT-"+p,"Persona sintética móvil");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",a,pa,nombreA,contrasenas.encode(clave));
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",b,pb,nombreB,contrasenas.encode(clave));
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",va,pa,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vb,pb,empresa);
        UUID historica=null;
        for(UUID j:List.of(ja,jb,jh)) {
            jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",j,periodo,version);
            UUID asig=UUID.randomUUID();
            jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,? ,NULL,NULL,now())",asig,j,vv,j.equals(jb)?vb:va,
                    Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
            if(j.equals(jh)) historica=asig;
        }
        UUID eventoHistorico=UUID.randomUUID();
        jdbc.update("INSERT INTO evento_ejecucion_jornada VALUES (?,?,?,?,'INICIO',?,?,NULL)",eventoHistorico,UUID.randomUUID(),jh,historica,
                Timestamp.from(captura),Timestamp.from(captura.plusSeconds(60)));
        String dispositivo=System.getProperty("gamw.mobile.device","emulator-5554");
        Path adb=Path.of(System.getenv("LOCALAPPDATA"),"Android","Sdk","platform-tools","adb.exe");
        Path movil=Path.of("..","apps","mobile").toAbsolutePath().normalize();
        ejecutar(List.of(adb.toString(),"-s",dispositivo,"reverse","tcp:"+puerto,"tcp:"+puerto),movil,1);
        try {
            for(String fase:List.of("1","2")) {
                List<String> cmd=new ArrayList<>(List.of("cmd.exe","/d","/c","flutter","drive",
                        "--driver=test_driver/integration_driver.dart","--target=integration_test/slice3c_real_test.dart",
                        "-d",dispositivo,"--keep-app-running","--dart-define=GAMW_IT_FASE="+fase,
                        "--dart-define=GAMW_IT_API_URL=http://127.0.0.1:"+puerto+"/api",
                        "--dart-define=GAMW_IT_USUARIO_A="+nombreA,"--dart-define=GAMW_IT_USUARIO_B="+nombreB,
                        "--dart-define=GAMW_IT_CLAVE="+clave,"--dart-define=GAMW_IT_JORNADA_A="+ja,"--dart-define=GAMW_IT_JORNADA_B="+jb));
                ejecutar(cmd,movil,15);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=? AND usuario_id=?",Integer.class,ja,a)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",Integer.class,jb)).isZero();
            assertThat(jdbc.queryForObject("SELECT usuario_id FROM evento_ejecucion_jornada WHERE id=?",UUID.class,eventoHistorico)).isNull();
            assertThat(jdbc.queryForObject("SELECT capturado_en FROM evento_ejecucion_jornada WHERE id=?",Timestamp.class,eventoHistorico).toInstant()).isEqualTo(captura);
            assertThat(jdbc.queryForObject("SELECT recibido_en FROM evento_ejecucion_jornada WHERE id=?",Timestamp.class,eventoHistorico).toInstant()).isEqualTo(captura.plusSeconds(60));
        } finally {
            ejecutar(List.of(adb.toString(),"-s",dispositivo,"reverse","--remove","tcp:"+puerto),movil,1);
        }
    }

    private void ejecutar(List<String> comando,Path directorio,int minutos) throws Exception {
        var builder=new ProcessBuilder(comando).directory(directorio.toFile()).redirectErrorStream(true);
        builder.environment().remove("DEBUG");
        var proceso=builder.start();
        var lector=Thread.ofVirtual().start(() -> {
            try { proceso.getInputStream().transferTo(System.out); } catch(java.io.IOException error) { /* El resultado se valida por exit code. */ }
        });
        if(!proceso.waitFor(minutos,TimeUnit.MINUTES)) {
            proceso.destroy(); throw new IllegalStateException("Tiempo agotado en integración Android; no se usará otro backend.");
        }
        lector.join(1000);
        assertThat(proceso.exitValue()).as("Proceso de integración Flutter/ADB").isZero();
    }
}
