package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * H-05: freno técnico de fuerza bruta en el inicio de sesión, NO una política municipal de bloqueo de cuentas.
 * <ul>
 *   <li>Cuenta solo intentos FALLIDOS por (origen, nombre de usuario enviado) y por origen, en una ventana deslizante configurable.</li>
 *   <li>La clave usa el nombre ENVIADO, exista o no la cuenta: el freno se comporta igual para cuentas inexistentes (sin enumeración).</li>
 *   <li>No bloquea la cuenta: un mismo usuario puede ingresar desde otro origen; al vencer la ventana el freno desaparece.</li>
 *   <li>Memoria local acotada de un solo nodo. Con varias instancias o detrás de un proxy, el control debe complementarse en el despliegue
 *       (gateway/proxy con IP real del cliente): decisión de infraestructura pendiente, no resuelta aquí.</li>
 * </ul>
 */
@Component
public class LimitadorIntentosAcceso {
    private static final int MAXIMO_CLAVES = 20_000;
    private final int maximoPorCuentaYOrigen, maximoPorOrigen;
    private final Duration ventana;
    private final Clock reloj;
    private final Map<String,Contador> contadores = new ConcurrentHashMap<>();

    private record Contador(Instant inicio, int fallos) { }

    @Autowired
    public LimitadorIntentosAcceso(@Value("${gamw.autenticacion.fallos-maximos-por-cuenta-y-origen:10}") int maximoPorCuentaYOrigen,
            @Value("${gamw.autenticacion.fallos-maximos-por-origen:100}") int maximoPorOrigen,
            @Value("${gamw.autenticacion.ventana-segundos:300}") long ventanaSegundos) {
        this(maximoPorCuentaYOrigen, maximoPorOrigen, Duration.ofSeconds(ventanaSegundos), Clock.systemUTC());
    }
    LimitadorIntentosAcceso(int maximoPorCuentaYOrigen,int maximoPorOrigen,Duration ventana,Clock reloj) {
        if(maximoPorCuentaYOrigen<1 || maximoPorOrigen<maximoPorCuentaYOrigen || ventana.isNegative() || ventana.isZero())
            throw new IllegalArgumentException("Límites técnicos de intentos positivos y coherentes requeridos.");
        this.maximoPorCuentaYOrigen=maximoPorCuentaYOrigen; this.maximoPorOrigen=maximoPorOrigen; this.ventana=ventana; this.reloj=reloj;
    }
    private static String cuenta(String origen,String nombre) { return "C\n"+origen+"\n"+(nombre==null?"":nombre); }
    private static String origen(String origen) { return "O\n"+origen; }

    /** Lanza {@link DemasiadosIntentos} si el origen o el par (origen, nombre) agotó su cupo de fallos en la ventana vigente. */
    public void verificar(String origen,String nombre) {
        Instant ahora=reloj.instant();
        long espera=Math.max(espera(cuenta(origen,nombre),maximoPorCuentaYOrigen,ahora),espera(origen(origen),maximoPorOrigen,ahora));
        if(espera>0) throw new DemasiadosIntentos(espera);
    }
    public void registrarFallo(String origen,String nombre) {
        Instant ahora=reloj.instant();
        if(contadores.size()>=MAXIMO_CLAVES) contadores.entrySet().removeIf(e -> vencido(e.getValue(),ahora));
        for(String clave:new String[]{cuenta(origen,nombre),origen(origen)})
            contadores.compute(clave,(k,c) -> c==null || vencido(c,ahora) ? new Contador(ahora,1) : new Contador(c.inicio(),c.fallos()+1));
    }
    /** Un acceso correcto solo limpia el par (origen, nombre); el cupo del origen no se reinicia con una cuenta propia. */
    public void registrarExito(String origen,String nombre) { contadores.remove(cuenta(origen,nombre)); }

    private boolean vencido(Contador c,Instant ahora) { return !ahora.isBefore(c.inicio().plus(ventana)); }
    private long espera(String clave,int maximo,Instant ahora) {
        Contador c=contadores.get(clave);
        if(c==null || vencido(c,ahora) || c.fallos()<maximo) return 0;
        return Math.max(1,Duration.between(ahora,c.inicio().plus(ventana)).toSeconds());
    }

    /** Respuesta uniforme: no revela si la cuenta existe. */
    public static class DemasiadosIntentos extends RuntimeException {
        private final long reintentarEnSegundos;
        public DemasiadosIntentos(long reintentarEnSegundos) {
            super("Demasiados intentos de inicio de sesión desde este origen; espere antes de reintentar.");
            this.reintentarEnSegundos=reintentarEnSegundos;
        }
        public long reintentarEnSegundos() { return reintentarEnSegundos; }
    }
}
