package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.regex.Pattern;
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
        boolean gpsReal=Boolean.getBoolean("gamw.mobile.gps.real");
        if(gpsReal && !Boolean.getBoolean("gamw.mobile.gps.install-approved"))
            throw new IllegalStateException("Instalar el APK .it requiere aprobación explícita. La app normal no se reinstala ni limpia.");
        int esperaGps=Integer.parseInt(System.getProperty("gamw.mobile.gps.wait-seconds","600"));
        if(gpsReal && (esperaGps<30 || esperaGps>600))
            throw new IllegalArgumentException("La espera técnica GPS de prueba debe estar entre 30 y 600 segundos.");
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
        if(gpsReal && dispositivo.startsWith("emulator-"))
            throw new IllegalStateException("GPS real requiere un dispositivo físico explícito; no se simularán posiciones.");
        Path adb=Path.of(System.getenv("LOCALAPPDATA"),"Android","Sdk","platform-tools","adb.exe");
        Path movil=Path.of("..","apps","mobile").toAbsolutePath().normalize();
        ejecutar(List.of(adb.toString(),"-s",dispositivo,"reverse","tcp:"+puerto,"tcp:"+puerto),movil,1,false);
        try {
            for(String fase:List.of("1","2")) {
                List<String> definiciones=List.of("--dart-define=GAMW_IT_FASE="+fase,
                        "--dart-define=GAMW_IT_API_URL=http://127.0.0.1:"+puerto+"/api",
                        "--dart-define=GAMW_IT_USUARIO_A="+nombreA,"--dart-define=GAMW_IT_USUARIO_B="+nombreB,
                        "--dart-define=GAMW_IT_CLAVE="+clave,"--dart-define=GAMW_IT_JORNADA_A="+ja,"--dart-define=GAMW_IT_JORNADA_B="+jb,
                        "--dart-define=GAMW_IT_GPS_ESPERA_SEGUNDOS="+esperaGps);
                String objetivo="integration_test/"+(gpsReal?"slice4a_real_test.dart":"slice3c_real_test.dart");
                List<String> cmd=new ArrayList<>(List.of("cmd.exe","/d","/c","flutter","drive","--no-pub",
                        "--driver=test_driver/integration_driver.dart","--target="+objetivo,"-d",dispositivo,"--keep-app-running"));
                if(gpsReal) {
                    var construir=new ArrayList<>(List.of("cmd.exe","/d","/c","flutter","build","apk","--debug","--no-pub",
                            "--target-platform","android-arm64","--target="+objetivo));
                    construir.addAll(definiciones);
                    ejecutar(construir,movil,15,true);
                    Path apk=movil.resolve("build/app/outputs/flutter-apk/app-debug.apk");
                    comprobarApkIntegracion(inspeccionarApk(apk)); // ANTES de cualquier instalación.
                    cmd.add("--use-application-binary="+apk);
                } else cmd.addAll(definiciones);
                ejecutar(cmd,movil,15,gpsReal);
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=? AND usuario_id=?",Integer.class,ja,a)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=?",Integer.class,jb)).isZero();
            assertThat(jdbc.queryForObject("SELECT usuario_id FROM evento_ejecucion_jornada WHERE id=?",UUID.class,eventoHistorico)).isNull();
            assertThat(jdbc.queryForObject("SELECT capturado_en FROM evento_ejecucion_jornada WHERE id=?",Timestamp.class,eventoHistorico).toInstant()).isEqualTo(captura);
            assertThat(jdbc.queryForObject("SELECT recibido_en FROM evento_ejecucion_jornada WHERE id=?",Timestamp.class,eventoHistorico).toInstant()).isEqualTo(captura.plusSeconds(60));
            if(gpsReal) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",Integer.class,ja)).isPositive();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM posicion_gps WHERE jornada_id=? AND (usuario_id<>? OR ST_SRID(geometria)<>4326 OR GeometryType(geometria)<>'POINT')",Integer.class,ja,a)).isZero();
            }
        } finally {
            ejecutar(List.of(adb.toString(),"-s",dispositivo,"reverse","--remove","tcp:"+puerto),movil,1,false);
        }
    }

    static void comprobarApkIntegracion(String manifiesto) {
        var paquete=Pattern.compile("(?m)^package: name='([^']+)'").matcher(manifiesto);
        if(!paquete.find() || !paquete.group(1).equals("bo.gob.warnes.gamw.gamw_mobile.it"))
            throw new IllegalStateException("Instalación bloqueada: el APK GPS no es el paquete aislado .it.");
    }

    private String inspeccionarApk(Path apk) throws Exception {
        Path herramientas=Path.of(System.getenv("LOCALAPPDATA"),"Android","Sdk","build-tools");
        Path aapt;
        try(var versiones=Files.list(herramientas)) {
            aapt=versiones.map(v -> v.resolve("aapt.exe")).filter(Files::isRegularFile)
                    .max(Comparator.comparing(Path::toString)).orElseThrow(() -> new IllegalStateException("Falta aapt; no se instalará el APK sin verificarlo."));
        }
        var proceso=new ProcessBuilder(aapt.toString(),"dump","badging",apk.toString()).redirectErrorStream(true).start();
        var contenido=new java.io.ByteArrayOutputStream();
        var lector=Thread.ofVirtual().start(() -> {
            try { proceso.getInputStream().transferTo(contenido); } catch(java.io.IOException error) { /* Fallar cerrado al validar salida. */ }
        });
        if(!proceso.waitFor(1,TimeUnit.MINUTES)) {
            proceso.destroy(); throw new IllegalStateException("Tiempo agotado verificando el paquete APK.");
        }
        lector.join(1000);
        if(proceso.exitValue()!=0 || lector.isAlive()) throw new IllegalStateException("No se pudo verificar el paquete APK.");
        return contenido.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void ejecutar(List<String> comando,Path directorio,int minutos,boolean aislado) throws Exception {
        var builder=new ProcessBuilder(comando).directory(directorio.toFile()).redirectErrorStream(true);
        builder.environment().remove("DEBUG");
        if(aislado) builder.environment().put("GAMW_ANDROID_INTEGRACION","true");
        else builder.environment().remove("GAMW_ANDROID_INTEGRACION");
        var proceso=builder.start();
        var lector=Thread.ofVirtual().start(() -> {
            try(var lineas=new java.io.BufferedReader(new java.io.InputStreamReader(proceso.getInputStream()))) {
                for(String linea;(linea=lineas.readLine())!=null;) {
                    // Gradle Windows puede imprimir dart-defines en base64: contienen claves sintéticas.
                    if(linea.contains("dart-defines=") || linea.contains("GAMW_IT_CLAVE="))
                        System.out.println("[Argumentos de integración con credenciales omitidos]");
                    else System.out.println(linea);
                }
            } catch(java.io.IOException error) { /* El resultado se valida por exit code. */ }
        });
        if(!proceso.waitFor(minutos,TimeUnit.MINUTES)) {
            proceso.destroy(); throw new IllegalStateException("Tiempo agotado en integración Android; no se usará otro backend.");
        }
        lector.join(1000);
        assertThat(proceso.exitValue()).as("Proceso de integración Flutter/ADB").isZero();
    }
}
