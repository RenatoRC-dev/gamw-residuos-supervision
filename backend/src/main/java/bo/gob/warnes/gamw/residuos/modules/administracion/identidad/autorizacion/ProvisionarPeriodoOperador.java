package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion;

import java.io.Console;
import java.net.URI;
import java.sql.*;
import java.util.*;

/** Provisión local explícita; no carga .env, crea usuarios ni infiere funciones institucionales. */
final class ProvisionarPeriodoOperador {
    private ProvisionarPeriodoOperador() { }
    static void ejecutar(Console consola,String[] args) throws Exception {
        if(consola==null || args.length!=4) throw new IllegalArgumentException("Use consola: otorgar-periodo|revocar-periodo UUID_USUARIO UUID_PERIODO consultar|responder|reprogramar|planificacion-consultar|programar|asignar.");
        boolean otorgar=args[0].equals("otorgar-periodo");
        UUID usuario=UUID.fromString(args[1]),periodo=UUID.fromString(args[2]);
        String permiso=switch(args[3]) {
            case "consultar" -> "CORRECTIVA_CONSULTAR";
            case "responder" -> "CORRECTIVA_RESPONDER";
            case "reprogramar" -> "JORNADA_REPROGRAMAR";
            case "planificacion-consultar" -> "PLANIFICACION_CONSULTAR";
            case "programar" -> "JORNADA_PROGRAMAR";
            case "asignar" -> "JORNADA_ASIGNAR";
            default -> throw new IllegalArgumentException("Capacidad no permitida.");
        };
        String url=requerida("GAMW_D1A_DB_URL");URI uri;
        try { if(!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();uri=URI.create(url.substring(5)); }
        catch(IllegalArgumentException e) { throw new IllegalArgumentException("Destino PostgreSQL local explícito requerido."); }
        if(!Set.of("localhost","127.0.0.1").contains(uri.getHost()==null?"":uri.getHost()) || uri.getPort()<1 || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null || uri.getPath()==null || uri.getPath().length()<2)
            throw new IllegalArgumentException("Solo loopback, puerto y base explícitos; sin credenciales/parámetros en URL.");
        try(var c=DriverManager.getConnection(url,requerida("GAMW_D1A_DB_USER"),requerida("GAMW_D1A_DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try {
                try(var q=c.prepareStatement("SELECT activo FROM usuario WHERE id=?")) {
                    q.setObject(1,usuario);
                    try(var r=q.executeQuery()) { if(!r.next() || (otorgar && !r.getBoolean(1))) throw new IllegalStateException("Cuenta inexistente o inactiva."); }
                }
                try(var q=c.prepareStatement("SELECT p.fecha_inicio,p.fecha_fin,e.id,e.razon_social FROM periodo_servicio p JOIN empresa_operadora e ON e.id=p.empresa_operadora_id WHERE p.id=?")) {
                    q.setObject(1,periodo);
                    try(var r=q.executeQuery()) {
                        if(!r.next()) throw new IllegalStateException("Periodo inexistente.");
                        consola.printf("Periodo %s (%s — %s); empresa derivada %s: %s.%n",periodo,r.getObject(1),r.getObject(2),r.getObject(3),r.getString(4));
                    }
                }
                boolean historico=false;
                if(otorgar) {
                    String objeto=permiso.equals("JORNADA_REPROGRAMAR")?"jornadas con inicio programado o creación anterior":"jornadas con inicio programado anterior o acciones ya creadas";
                    String respuesta=consola.readLine("¿Autorizar EXPLÍCITAMENTE %s antes de esta concesión, solo de este periodo? SI/NO: ",objeto);
                    if(!Set.of("SI","NO").contains(respuesta==null?"":respuesta)) throw new IllegalArgumentException("Decisión histórica explícita requerida.");
                    historico=respuesta.equals("SI");
                }
                String motivo=consola.readLine("Motivo y referencia de autorización (incluya fundamento de acceso histórico si corresponde): ");
                if(motivo==null || motivo.isBlank() || motivo.length()>4000) throw new IllegalArgumentException("Referencia de autorización obligatoria, máximo 4000 caracteres.");
                String confirmar=consola.readLine("Destino %s:%d%s; %s %s a %s; histórico=%s. Escriba CONFIRMAR D1-A4: ",
                    uri.getHost(),uri.getPort(),uri.getPath(),args[0],permiso,usuario,historico);
                if(!"CONFIRMAR D1-A4".equals(confirmar)) throw new IllegalStateException("Provisión cancelada.");
                String sql=otorgar?"""
                    INSERT INTO concesion_periodo_operador(id,usuario_id,periodo_servicio_id,permiso,incluye_historico,operador_local,motivo)
                    VALUES (?,?,?,?,?,?,?)
                    """:"""
                    UPDATE concesion_periodo_operador SET revocado_en=clock_timestamp(),revocado_por_local=?,motivo_revocacion=?
                    WHERE usuario_id=? AND periodo_servicio_id=? AND permiso=? AND revocado_en IS NULL
                    """;
                try(var q=c.prepareStatement(sql)) {
                    if(otorgar) {
                        q.setObject(1,UUID.randomUUID());q.setObject(2,usuario);q.setObject(3,periodo);q.setString(4,permiso);q.setBoolean(5,historico);
                        q.setString(6,System.getProperty("user.name"));q.setString(7,motivo);
                    } else {
                        q.setString(1,System.getProperty("user.name"));q.setString(2,motivo);q.setObject(3,usuario);q.setObject(4,periodo);q.setString(5,permiso);
                    }
                    int n=q.executeUpdate();c.commit();consola.printf("Concesiones afectadas: %d. No implica aprobación institucional GAMW.%n",n);
                }
            } catch(Exception e) { c.rollback();throw new IllegalStateException("Provisión no completada; compruebe referencias, unicidad y migración. Sin cambios parciales."); }
        } catch(SQLException e) { throw new IllegalStateException("No se pudo acceder al destino local explícito."); }
    }
    private static String requerida(String nombre) {
        String valor=System.getenv(nombre);if(valor==null || valor.isBlank()) throw new IllegalStateException("Falta configurar "+nombre);return valor;
    }
}
