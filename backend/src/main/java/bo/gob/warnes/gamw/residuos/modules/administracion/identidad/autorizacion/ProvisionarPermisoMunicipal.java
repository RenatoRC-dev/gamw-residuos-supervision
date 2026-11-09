package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion;

import java.net.URI;
import java.sql.*;
import java.util.UUID;

/** Herramienta CLI local, sin Spring/HTTP/.env ni credenciales predeterminadas.
 * Ejecutar con classpath de runtime: otorgar|revocar UUID_USUARIO.
 * Variables explícitas GAMW_D1A_DB_URL, GAMW_D1A_DB_USER, GAMW_D1A_DB_PASSWORD.
 * Registra al operador del SO, no lo presenta como autoridad municipal autenticada.
 */
public final class ProvisionarPermisoMunicipal {
    private ProvisionarPermisoMunicipal() { }
    public static void main(String[] args) throws Exception {
        var consola = System.console();
        if(consola!=null && args.length==2 && (args[0].equals("catalogo-registrar") || args[0].equals("catalogo-retirar"))) {
            catalogo(consola,args); return;
        }
        if (consola == null || (args.length != 2 && args.length != 3) || !(args[0].equals("otorgar") || args[0].equals("revocar")))
            throw new IllegalArgumentException("Use consola local: otorgar|revocar UUID_USUARIO [trayectoria|monitoreo].");
        String capacidad=args.length==3?args[2]:"trayectoria";
        String permiso=switch(capacidad) {
            case "trayectoria" -> "TRAYECTORIA_JORNADA_CONSULTAR";
            case "monitoreo" -> "MONITOREO_JORNADA_CONSULTAR";
            case "referencias-consultar" -> "REFERENCIA_ESPACIAL_CONSULTAR";
            case "referencias-gestionar" -> "REFERENCIA_ESPACIAL_GESTIONAR";
            case "integridad" -> "GPS_INTEGRIDAD_REVISAR";
            case "coincidencias" -> "GPS_COINCIDENCIAS_REVISAR";
            case "desvios" -> "GPS_DESVIOS_REVISAR";
            case "inspecciones-registrar" -> "INSPECCION_REGISTRAR";
            case "fiscalizacion-consultar" -> "FISCALIZACION_CONSULTAR";
            case "fiscalizacion-emitir" -> "FISCALIZACION_EMITIR";
            default -> throw new IllegalArgumentException("Capacidad no permitida.");
        };
        UUID usuario = UUID.fromString(args[1]);
        String url = requerida("GAMW_D1A_DB_URL");
        if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException("URL PostgreSQL explícita requerida.");
        URI uri;
        try { uri=URI.create(url.substring(5)); }
        catch(IllegalArgumentException error) { throw new IllegalArgumentException("URL local inválida."); }
        if (!("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || uri.getPort()<1 || uri.getPath()==null || uri.getPath().length()<2)
            throw new IllegalArgumentException("Solo conexión local con puerto y base explícitos, sin parámetros.");
        String motivo = consola.readLine("Motivo y referencia de autorización provisional: ");
        if (motivo==null || motivo.isBlank()) throw new IllegalArgumentException("Motivo obligatorio.");
        String confirmacion = consola.readLine("Destino %s:%d%s; %s %s a %s. Escriba CONFIRMAR D1-A: ",
                uri.getHost(),uri.getPort(),uri.getPath(),args[0],capacidad,usuario);
        if (!"CONFIRMAR D1-A".equals(confirmacion)) throw new IllegalStateException("Operación cancelada.");
        try (Connection c=DriverManager.getConnection(url,requerida("GAMW_D1A_DB_USER"),requerida("GAMW_D1A_DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try {
                try(var q=c.prepareStatement("SELECT activo FROM usuario WHERE id=? FOR UPDATE")) {
                    q.setObject(1,usuario);
                    try(var r=q.executeQuery()) {
                        if(!r.next() || (args[0].equals("otorgar") && !r.getBoolean(1)))
                            throw new IllegalStateException("Cuenta inexistente o no habilitada para recibir permisos.");
                    }
                }
                String sql=args[0].equals("otorgar") ? """
                    INSERT INTO concesion_permiso(id,usuario_id,permiso,alcance,operador_local,motivo)
                    VALUES (?,?,?,'MUNICIPAL_GAMW',?,?)
                    """ : """
                    UPDATE concesion_permiso SET revocado_en=clock_timestamp(),revocado_por_local=?,motivo_revocacion=?
                    WHERE usuario_id=? AND permiso=?
                      AND alcance='MUNICIPAL_GAMW' AND revocado_en IS NULL
                    """;
                try(var q=c.prepareStatement(sql)) {
                    if(args[0].equals("otorgar")) {
                        q.setObject(1,UUID.randomUUID()); q.setObject(2,usuario);
                        q.setString(3,permiso); q.setString(4,System.getProperty("user.name")); q.setString(5,motivo.strip());
                    } else {
                        q.setString(1,System.getProperty("user.name")); q.setString(2,motivo.strip()); q.setObject(3,usuario); q.setString(4,permiso);
                    }
                    int cambios=q.executeUpdate(); c.commit();
                    consola.printf("Operación completada: %d concesión(es). No implica aprobación GAMW.%n",cambios);
                }
            } catch(Exception error) {
                c.rollback(); throw new IllegalStateException("No se modificó la autorización. Revise destino, migración y concesiones existentes.");
            }
        } catch(SQLException error) {
            throw new IllegalStateException("No se pudo acceder al destino local configurado.");
        }
    }
    private static String requerida(String nombre) {
        String valor=System.getenv(nombre);
        if(valor==null || valor.isBlank()) throw new IllegalStateException("Falta configurar "+nombre);
        return valor;
    }
    /** Provisión sin categorías predeterminadas: registrar CODIGO o retirar UUID. */
    private static void catalogo(java.io.Console consola,String[] args) throws Exception {
        String url=requerida("GAMW_D1A_DB_URL"); URI uri;
        try { if(!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException(); uri=URI.create(url.substring(5)); }
        catch(IllegalArgumentException error) { throw new IllegalArgumentException("URL local explícita requerida."); }
        if(!java.util.Set.of("localhost","127.0.0.1").contains(uri.getHost()==null?"":uri.getHost()) || uri.getPort()<1
            || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || uri.getPath()==null || uri.getPath().length()<2)
            throw new IllegalArgumentException("Solo destino local sin credenciales en URL.");
        boolean registrar=args[0].equals("catalogo-registrar");
        String nombre="",referencia=""; boolean obligatorias=false;
        if(registrar) {
            if(args[1].isBlank() || args[1].length()>80) throw new IllegalArgumentException("Código explícito de hasta 80 caracteres.");
            nombre=consola.readLine("Nombre institucional de la categoría: ");
            referencia=consola.readLine("Referencia de aprobación institucional (no se verifica automáticamente): ");
            String respuesta=consola.readLine("¿Fundamentación obligatoria según esa política? SI/NO: ");
            if(nombre==null || nombre.isBlank() || nombre.length()>200 || referencia==null || referencia.isBlank() || referencia.length()>2000
                || !("SI".equals(respuesta)||"NO".equals(respuesta))) throw new IllegalArgumentException("Complete la definición explícita, sin valores por defecto.");
            obligatorias="SI".equals(respuesta);
        }
        String confirmacion=consola.readLine("Destino %s:%d%s; %s %s. Escriba CONFIRMAR CATALOGO: ",uri.getHost(),uri.getPort(),uri.getPath(),args[0],args[1]);
        if(!"CONFIRMAR CATALOGO".equals(confirmacion)) throw new IllegalStateException("Operación cancelada.");
        try(var c=DriverManager.getConnection(url,requerida("GAMW_D1A_DB_USER"),requerida("GAMW_D1A_DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try(var q=c.prepareStatement(registrar?
                "INSERT INTO catalogo_resultado_fiscalizacion(id,codigo,nombre,referencia_aprobacion,observaciones_obligatorias,habilitado,operador_local) VALUES (?,?,?,?,?,true,?)":
                "UPDATE catalogo_resultado_fiscalizacion SET habilitado=false WHERE id=? AND habilitado")) {
                if(registrar) {
                    q.setObject(1,UUID.randomUUID()); q.setString(2,args[1]); q.setString(3,nombre); q.setString(4,referencia);
                    q.setBoolean(5,obligatorias); q.setString(6,System.getProperty("user.name"));
                } else q.setObject(1,UUID.fromString(args[1]));
                int n=q.executeUpdate(); c.commit(); consola.printf("Categorías afectadas: %d. Sin categorías inventadas ni permisos automáticos.%n",n);
            } catch(Exception error) { c.rollback(); throw new IllegalStateException("Catálogo no modificado; compruebe migración, unicidad y operación."); }
        } catch(SQLException error) { throw new IllegalStateException("No se pudo acceder al catálogo local."); }
    }
}
