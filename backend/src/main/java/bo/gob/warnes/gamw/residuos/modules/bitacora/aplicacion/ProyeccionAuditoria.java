package bo.gob.warnes.gamw.residuos.modules.bitacora.aplicacion;

import java.util.*;
import java.util.regex.Pattern;

/**
 * CU-31 / H-05: contrato de salida por LISTA PERMITIDA y fallo cerrado. Solo salen campos conocidos cuyo valor tiene la forma
 * esperada (identificadores, códigos de permiso, booleanos, instantes). Todo lo demás —texto libre, datos personales, secretos,
 * campos nuevos o anidados desconocidos— se omite y solo se informa cuántos campos se omitieron. No se intenta "limpiar" texto libre:
 * un texto libre no puede garantizarse sin datos sensibles, por lo que nunca se devuelve.
 */
public final class ProyeccionAuditoria {
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    private static final Pattern CODIGO = Pattern.compile("^[A-Z][A-Z0-9_]{0,79}$");
    private static final Pattern USUARIO = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");
    private static final Pattern INSTANTE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?(Z|[+-]\\d{2}:?\\d{2})$");
    private enum Forma { UUID, CODIGO, USUARIO, INSTANTE, BOOLEANO, OBJETO }
    private static final Map<String,Forma> PERMITIDOS = Map.ofEntries(
        Map.entry("id",Forma.UUID),Map.entry("personaNaturalId",Forma.UUID),Map.entry("usuarioId",Forma.UUID),Map.entry("periodoServicioId",Forma.UUID),
        Map.entry("nombreUsuario",Forma.USUARIO),Map.entry("permiso",Forma.CODIGO),
        Map.entry("activo",Forma.BOOLEANO),Map.entry("incluyeHistorico",Forma.BOOLEANO),Map.entry("administracionProtegida",Forma.BOOLEANO),
        Map.entry("otorgadoEn",Forma.INSTANTE),Map.entry("revocadoEn",Forma.INSTANTE),Map.entry("creadoEn",Forma.INSTANTE),Map.entry("actualizadoEn",Forma.INSTANTE),
        Map.entry("cuenta",Forma.OBJETO));
    private ProyeccionAuditoria() { }

    @SuppressWarnings("unchecked")
    public static Object proyectar(Object nodo) {
        if (!(nodo instanceof Map<?,?> mapa)) return nodo == null ? null : Map.of("camposOmitidos",1); // listas o escalares sueltos no se exponen
        return objeto((Map<String,Object>) mapa,0);
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> objeto(Map<String,Object> entrada,int profundidad) {
        var salida = new LinkedHashMap<String,Object>();
        int omitidos = 0;
        for (var e : entrada.entrySet()) {
            Forma forma = PERMITIDOS.get(e.getKey());
            Object v = e.getValue();
            if (forma == null || !(profundidad < 2 || forma != Forma.OBJETO)) { omitidos++; continue; }
            if (v == null) { salida.put(e.getKey(),null); continue; }
            boolean ok = switch (forma) {
                case BOOLEANO -> v instanceof Boolean;
                case OBJETO -> v instanceof Map<?,?>;
                case UUID -> v instanceof String s && UUID.matcher(s).matches();
                case CODIGO -> v instanceof String s && CODIGO.matcher(s).matches();
                case USUARIO -> v instanceof String s && USUARIO.matcher(s).matches();
                case INSTANTE -> v instanceof String s && INSTANTE.matcher(s).matches();
            };
            if (!ok) { omitidos++; continue; }
            if (forma == Forma.OBJETO) salida.put(e.getKey(),objeto((Map<String,Object>) v,profundidad + 1)); else salida.put(e.getKey(),v);
        }
        if (omitidos > 0) salida.put("camposOmitidos",omitidos);
        return salida;
    }
}
