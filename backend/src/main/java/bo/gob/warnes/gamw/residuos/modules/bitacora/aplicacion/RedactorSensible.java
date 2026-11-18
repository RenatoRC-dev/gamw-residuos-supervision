package bo.gob.warnes.gamw.residuos.modules.bitacora.aplicacion;

import java.util.*;
import java.util.regex.Pattern;

/** CU-31 E4/RN-CU-31-04: oculta secretos y datos personales protegidos antes de exponer cualquier detalle. */
public final class RedactorSensible {
    public static final String OCULTO = "[PROTEGIDO]";
    private static final Pattern CLAVE_SECRETA = Pattern.compile("(?i).*(contrasena|password|clave|hash|token|jwt|secret|credencial|authorization|bearer|cookie).*");
    private static final Pattern CLAVE_PERSONAL = Pattern.compile("(?i)^(ci|documento|documentoidentidad|telefono|celular|correo|email|direccion|nombres|apellidopaterno|apellidomaterno|fechanacimiento)$");
    private static final Pattern VALOR_JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern VALOR_BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final Pattern VALOR_BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{20,}");
    private RedactorSensible() { }

    public static String texto(String valor) {
        if (valor == null) return null;
        return VALOR_BCRYPT.matcher(VALOR_BEARER.matcher(VALOR_JWT.matcher(valor).replaceAll(OCULTO)).replaceAll(OCULTO)).replaceAll(OCULTO);
    }
    @SuppressWarnings("unchecked")
    public static Object redactar(Object nodo) {
        if (nodo instanceof Map<?,?> mapa) {
            var salida = new LinkedHashMap<String,Object>();
            for (var e : ((Map<String,Object>) mapa).entrySet()) {
                String clave = String.valueOf(e.getKey());
                String normal = clave.replace("_","");
                salida.put(clave,(CLAVE_SECRETA.matcher(normal).matches() || CLAVE_PERSONAL.matcher(normal).matches()) ? OCULTO : redactar(e.getValue()));
            }
            return salida;
        }
        if (nodo instanceof List<?> lista) return lista.stream().map(RedactorSensible::redactar).toList();
        if (nodo instanceof String s) return texto(s);
        return nodo;
    }
}
