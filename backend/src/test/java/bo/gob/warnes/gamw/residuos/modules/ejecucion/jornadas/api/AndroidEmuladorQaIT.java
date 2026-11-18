package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import bo.gob.warnes.gamw.residuos.shared.config.IntegracionPostgresV8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA Android en EMULADOR bajo demanda (workflow android-emulator-qa). NO es QA física: las posiciones GPS son sintéticas (coordenadas y
 * marcas conocidas) inyectadas en el almacén local; el proveedor GPS nativo, la batería y el segundo plano siguen PENDING (dispositivo físico).
 *
 * Servidor y PostgreSQL/PostGIS propios y efímeros, datos sintéticos. Orquesta cuatro fases de {@code qa_emulador/cadena_offline_emulador_test.dart}
 * (la app conserva SQLite y sesión entre fases). La "pérdida de red" retira {@code adb reverse} (el cliente móvil solo habla HTTP por loopback:
 * ese reverse ES el único camino al backend), apaga Wi‑Fi/datos del emulador y COMPRUEBA explícitamente la indisponibilidad desde el dispositivo
 * y desde la propia app antes de continuar; el reverse nunca queda activo en la fase sin red.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.config.import=",
        "server.address=127.0.0.1","gamw.seguridad.jwt.duracion=PT1H"})
@EnabledIfSystemProperty(named="gamw.mobile.qa.emulador",matches="true")
class AndroidEmuladorQaIT extends IntegracionPostgresV8 {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder contrasenas;
    @Value("${local.server.port}") int puerto;
    private static final boolean WINDOWS=System.getProperty("os.name").startsWith("Windows");

    @Test @Timeout(value=40,unit=TimeUnit.MINUTES)
    void cadenaOfflineEnEmuladorConservaEvidenciaIdentidadYSincronizaSinDuplicar() throws Exception {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),pa=UUID.randomUUID(),pb=UUID.randomUUID(),empresa=UUID.randomUUID(),periodo=UUID.randomUUID(),
            ruta=UUID.randomUUID(),version=UUID.randomUUID(),vehiculo=UUID.randomUUID(),vv=UUID.randomUUID(),va=UUID.randomUUID(),vb=UUID.randomUUID(),
            ja=UUID.randomUUID(),jc=UUID.randomUUID(),asigA=UUID.randomUUID(),asigC=UUID.randomUUID();
        String nombreA="qa-"+a,nombreB="qa-"+b,clave=UUID.randomUUID().toString();
        Instant base=Instant.now().minus(20,ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("INSERT INTO empresa_operadora VALUES (?,NULL,?,NULL,true,now(),now())",empresa,"Empresa sintética QA emulador");
        jdbc.update("INSERT INTO periodo_servicio VALUES (?,?,DATE '2026-01-01',DATE '2035-12-31',true,now(),now())",periodo,empresa);
        jdbc.update("INSERT INTO ruta VALUES (?,?,?,true,now(),now())",ruta,"QA-"+ruta,"Ruta sintética no oficial");
        jdbc.update("INSERT INTO ruta_version VALUES (?,?,1,ST_GeomFromText('LINESTRING(-10 20,-11 21)',4326),'2025-01-01T00:00:00Z',NULL,NULL,now())",version,ruta);
        jdbc.update("INSERT INTO vehiculo VALUES (?,?,NULL,NULL,NULL,NULL,NULL,true,now(),now())",vehiculo,"QA-"+vehiculo.toString().toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO vinculo_vehiculo_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vv,vehiculo,empresa);
        for(UUID p:List.of(pa,pb)) jdbc.update("INSERT INTO persona_natural VALUES (?,?,?,NULL,NULL,NULL,true,now(),now())",p,"QA-"+p,"Persona sintética QA");
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",a,pa,nombreA,contrasenas.encode(clave));
        jdbc.update("INSERT INTO usuario VALUES (?,?,?,?,true,now(),now())",b,pb,nombreB,contrasenas.encode(clave));
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",va,pa,empresa);
        jdbc.update("INSERT INTO vinculo_persona_empresa VALUES (?,?,?,DATE '2026-01-01',NULL,true,now(),now())",vb,pb,empresa);
        for(UUID j:List.of(ja,jc)) jdbc.update("INSERT INTO jornada VALUES (?,?,?,'2026-11-08T12:00:00Z',NULL,NULL,NULL,true,now(),now())",j,periodo,version);
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asigA,ja,vv,va,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        jdbc.update("INSERT INTO asignacion_jornada VALUES (?,?,?,?,?,NULL,NULL,now())",asigC,jc,vv,va,Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));

        String dispositivo=System.getProperty("gamw.mobile.device","emulator-5554");
        if(!dispositivo.startsWith("emulator-")) throw new IllegalStateException("Esta QA es solo para emulador; la QA física es otro procedimiento explícito.");
        Path movil=Path.of("..","apps","mobile").toAbsolutePath().normalize();
        String adb=resolverAdb().toString();
        String tcp="tcp:"+puerto;
        // Dos únicas rutas de red: adb reverse (solo cuando se permite) y la red virtual del emulador.
        ejecutar(List.of(adb,"-s",dispositivo,"reverse","--remove-all"),movil,1,false,false);
        try {
            ejecutar(List.of(adb,"-s",dispositivo,"reverse",tcp,tcp),movil,1,false,false);
            asegurarAlcanzable(adb,dispositivo,tcp,movil,"antes de la fase 1");
            fase(1,base,puerto,nombreA,nombreB,clave,ja,asigA,jc,asigC,movil,dispositivo);

            // ---- PÉRDIDA DE RED explícita ----
            ejecutar(List.of(adb,"-s",dispositivo,"reverse","--remove",tcp),movil,1,false,false);
            ejecutar(List.of(adb,"-s",dispositivo,"shell","svc","wifi","disable"),movil,1,false,true);
            ejecutar(List.of(adb,"-s",dispositivo,"shell","svc","data","disable"),movil,1,false,true);
            asegurarInalcanzable(adb,dispositivo,tcp,movil);
            fase(2,base,puerto,nombreA,nombreB,clave,ja,asigA,jc,asigC,movil,dispositivo);
            assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id IN (?,?)",ja,jc)).as("Nada llegó al servidor sin red").isZero();
            assertThat(contar("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",ja)).isZero();

            // ---- RECUPERACIÓN ----
            ejecutar(List.of(adb,"-s",dispositivo,"shell","svc","wifi","enable"),movil,1,false,true);
            ejecutar(List.of(adb,"-s",dispositivo,"shell","svc","data","enable"),movil,1,false,true);
            ejecutar(List.of(adb,"-s",dispositivo,"reverse",tcp,tcp),movil,1,false,false);
            asegurarAlcanzable(adb,dispositivo,tcp,movil,"tras la recuperación");
            fase(3,base,puerto,nombreA,nombreB,clave,ja,asigA,jc,asigC,movil,dispositivo); // cuenta B: no transmite evidencia de A
            assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id IN (?,?)",ja,jc)).as("B no envía evidencia de A").isZero();
            assertThat(contar("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",ja)).isZero();
            fase(4,base,puerto,nombreA,nombreB,clave,ja,asigA,jc,asigC,movil,dispositivo); // cuenta A: sincroniza dos veces

            // ---- Evidencia en el servidor ----
            assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=? AND usuario_id=?",ja,a)).isEqualTo(2);
            assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id=? AND usuario_id=?",jc,a)).isEqualTo(1);
            assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE usuario_id=?",b)).isZero();
            assertThat(jdbc.queryForObject("SELECT capturado_en FROM evento_ejecucion_jornada WHERE jornada_id=? AND tipo_evento='INICIO'",Timestamp.class,ja).toInstant()).isEqualTo(base);
            assertThat(jdbc.queryForObject("SELECT capturado_en FROM evento_ejecucion_jornada WHERE jornada_id=? AND tipo_evento='FIN'",Timestamp.class,ja).toInstant()).isEqualTo(base.plusSeconds(10));
            assertThat(contar("SELECT count(*) FROM evento_ejecucion_jornada WHERE jornada_id IN (?,?) AND recibido_en<=capturado_en",ja,jc)).as("recepción posterior a captura").isZero();
            assertThat(contar("SELECT count(*) FROM posicion_gps WHERE jornada_id=?",ja)).as("sin duplicados tras dos sincronizaciones").isEqualTo(3);
            assertThat(contar("SELECT count(*) FROM posicion_gps WHERE jornada_id=? AND usuario_id=? AND ST_SRID(geometria)=4326 AND GeometryType(geometria)='POINT' AND recibido_en>capturado_en",ja,a)).isEqualTo(3);
            var filas=jdbc.queryForList("SELECT capturado_en,ST_Y(geometria) AS lat,ST_X(geometria) AS lon,precision_metros FROM posicion_gps WHERE jornada_id=? ORDER BY capturado_en",ja);
            double[][] esperado={{20.10,-10.70,5.5},{20.20,-10.80,7.5},{20.30,-10.90,9.5}};
            for(int i=0;i<3;i++) {
                assertThat(((Timestamp)filas.get(i).get("capturado_en")).toInstant()).isEqualTo(base.plusSeconds(i+1)); // marca original conocida
                assertThat(((Number)filas.get(i).get("lat")).doubleValue()).isEqualTo(esperado[i][0]);
                assertThat(((Number)filas.get(i).get("lon")).doubleValue()).isEqualTo(esperado[i][1]); // GeoJSON/PostGIS: [longitud, latitud]
                assertThat(((Number)filas.get(i).get("precision_metros")).doubleValue()).isEqualTo(esperado[i][2]);
            }
            assertThat(contar("SELECT count(*) FROM fiscalizacion WHERE jornada_id IN (?,?)",ja,jc)).as("ningún dictamen automático").isZero();
        } finally {
            try { ejecutar(List.of(adb,"-s",dispositivo,"shell","svc","wifi","enable"),movil,1,false,true); } catch(Exception ignorada) { /* limpieza */ }
            try { ejecutar(List.of(adb,"-s",dispositivo,"shell","svc","data","enable"),movil,1,false,true); } catch(Exception ignorada) { /* limpieza */ }
            try { ejecutar(List.of(adb,"-s",dispositivo,"reverse","--remove-all"),movil,1,false,true); } catch(Exception ignorada) { /* limpieza */ }
        }
    }

    private int contar(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }

    private void fase(int fase,Instant base,int puerto,String nombreA,String nombreB,String clave,UUID ja,UUID asigA,UUID jc,UUID asigC,Path movil,String dispositivo) throws Exception {
        List<String> cmd=new ArrayList<>(comandoFlutter());
        cmd.addAll(List.of("drive","--no-pub","--driver=test_driver/integration_driver.dart","--target=qa_emulador/cadena_offline_emulador_test.dart","-d",dispositivo,"--keep-app-running",
            "--dart-define=GAMW_QA_FASE="+fase,"--dart-define=GAMW_QA_API_URL=http://127.0.0.1:"+puerto+"/api",
            "--dart-define=GAMW_QA_USUARIO_A="+nombreA,"--dart-define=GAMW_QA_USUARIO_B="+nombreB,"--dart-define=GAMW_QA_CLAVE="+clave,
            "--dart-define=GAMW_QA_JORNADA_A="+ja,"--dart-define=GAMW_QA_ASIGNACION_A="+asigA,"--dart-define=GAMW_QA_JORNADA_C="+jc,
            "--dart-define=GAMW_QA_ASIGNACION_C="+asigC,"--dart-define=GAMW_QA_BASE_ISO="+base));
        System.out.println("[QA emulador] fase "+fase);
        ejecutar(cmd,movil,20,false,true);
    }

    /** Comprobación explícita desde el dispositivo: el puerto del backend debe responder (reverse activo). */
    private void asegurarAlcanzable(String adb,String dispositivo,String tcp,Path movil,String momento) throws Exception {
        assertThat(salida(List.of(adb,"-s",dispositivo,"reverse","--list"),movil)).as("reverse activo "+momento).contains(tcp);
        assertThat(codigo(sondaPuerto(adb,dispositivo,tcp),movil)).as("backend alcanzable "+momento).isZero();
    }

    /** Sin red: ningún reverse y el puerto NO responde desde el dispositivo; si respondiera, la prueba falla (no se continúa). */
    private void asegurarInalcanzable(String adb,String dispositivo,String tcp,Path movil) throws Exception {
        assertThat(salida(List.of(adb,"-s",dispositivo,"reverse","--list"),movil)).as("adb reverse NO debe seguir activo en la fase sin red").doesNotContain(tcp);
        assertThat(codigo(sondaPuerto(adb,dispositivo,tcp),movil)).as("backend inalcanzable desde el dispositivo").isNotZero();
    }

    /** toybox nc no ofrece -z: se abre la conexión con entrada vacía; código 0 solo si el puerto acepta, distinto de 0 si se rechaza. */
    private List<String> sondaPuerto(String adb,String dispositivo,String tcp) {
        return List.of(adb,"-s",dispositivo,"shell","echo | nc -w 3 127.0.0.1 "+tcp.substring(4));
    }

    private List<String> comandoFlutter() { return WINDOWS ? List.of("cmd.exe","/d","/c","flutter") : List.of("flutter"); }

    private Path resolverAdb() {
        for(String variable:List.of("ANDROID_HOME","ANDROID_SDK_ROOT")) {
            String raiz=System.getenv(variable);
            if(raiz!=null && !raiz.isBlank()) { Path p=Path.of(raiz,"platform-tools",WINDOWS?"adb.exe":"adb"); if(Files.isRegularFile(p)) return p; }
        }
        String local=System.getenv("LOCALAPPDATA");
        if(local!=null) { Path p=Path.of(local,"Android","Sdk","platform-tools","adb.exe"); if(Files.isRegularFile(p)) return p; }
        throw new IllegalStateException("No se encontró adb (ANDROID_HOME/ANDROID_SDK_ROOT).");
    }

    private String salida(List<String> comando,Path directorio) throws Exception {
        var proceso=new ProcessBuilder(comando).directory(directorio.toFile()).redirectErrorStream(true).start();
        String texto=new String(proceso.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(!proceso.waitFor(1,TimeUnit.MINUTES)) { proceso.destroyForcibly(); throw new IllegalStateException("Tiempo agotado: "+comando); }
        return texto;
    }

    private int codigo(List<String> comando,Path directorio) throws Exception {
        var proceso=new ProcessBuilder(comando).directory(directorio.toFile()).redirectErrorStream(true).start();
        proceso.getInputStream().readAllBytes();
        if(!proceso.waitFor(1,TimeUnit.MINUTES)) { proceso.destroyForcibly(); throw new IllegalStateException("Tiempo agotado: "+comando); }
        return proceso.exitValue();
    }

    private void ejecutar(List<String> comando,Path directorio,int minutos,boolean aislado,boolean tolerarFallo) throws Exception {
        var builder=new ProcessBuilder(comando).directory(directorio.toFile()).redirectErrorStream(true);
        builder.environment().remove("DEBUG");
        builder.environment().put("GAMW_ANDROID_INTEGRACION","true"); // applicationId .it: UID, SQLite y KeyStore separados de la app normal
        var proceso=builder.start();
        var lector=Thread.ofVirtual().start(() -> {
            try(var lineas=new java.io.BufferedReader(new java.io.InputStreamReader(proceso.getInputStream()))) {
                for(String linea;(linea=lineas.readLine())!=null;) {
                    if(linea.contains("dart-defines=") || linea.contains("GAMW_QA_CLAVE=")) System.out.println("[Argumentos con credenciales omitidos]");
                    else System.out.println(linea);
                }
            } catch(java.io.IOException error) { /* El resultado se valida por exit code. */ }
        });
        if(!proceso.waitFor(minutos,TimeUnit.MINUTES)) { proceso.destroyForcibly(); throw new IllegalStateException("Tiempo agotado en QA emulador: "+comando.get(0)); }
        lector.join(1000);
        if(!tolerarFallo || comando.contains("drive")) assertThat(proceso.exitValue()).as("Proceso de QA: "+comando.subList(0,Math.min(4,comando.size()))).isZero();
    }
}
