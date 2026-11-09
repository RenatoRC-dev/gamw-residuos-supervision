package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.autorizacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.dominio.PermisosDelegables;
import java.io.Console;
import java.net.URI;
import java.sql.*;
import java.util.*;

/** Los límites de delegación solo se provisionan localmente, nunca desde la API administrativa. */
final class ProvisionarDelegacion {
    private ProvisionarDelegacion() { }
    static void ejecutar(Console consola,String[] args) throws Exception {
        if(consola==null || args.length!=4) throw new IllegalArgumentException("Use consola: facultad-otorgar|facultad-revocar UUID_ADMIN PERMISO MUNICIPAL|UUID_PERIODO.");
        UUID administrador=UUID.fromString(args[1]);UUID periodo=args[3].equals("MUNICIPAL")?null:UUID.fromString(args[3]);
        if(!PermisosDelegables.admite(args[2],periodo!=null)) throw new IllegalArgumentException("No se permite delegar capacidades administrativas ni permisos no implementados.");
        boolean otorgar=args[0].equals("facultad-otorgar");String url=requerida("GAMW_D1A_DB_URL");URI uri;
        try { if(!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();uri=URI.create(url.substring(5)); }
        catch(IllegalArgumentException e) { throw new IllegalArgumentException("URL PostgreSQL local explícita requerida."); }
        if(!Set.of("localhost","127.0.0.1").contains(uri.getHost()==null?"":uri.getHost()) || uri.getPort()<1 || uri.getPath()==null || uri.getPath().length()<2
            || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalArgumentException("Solo loopback con puerto/base explícitos y sin secretos en URL.");
        try(var c=DriverManager.getConnection(url,requerida("GAMW_D1A_DB_USER"),requerida("GAMW_D1A_DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try {
                try(var q=c.prepareStatement("SELECT activo FROM usuario WHERE id=? FOR UPDATE")) {
                    q.setObject(1,administrador);try(var r=q.executeQuery()) { if(!r.next() || (otorgar && !r.getBoolean(1))) throw new IllegalArgumentException("Cuenta no elegible."); }
                }
                if(periodo!=null) try(var q=c.prepareStatement("SELECT p.fecha_inicio,p.fecha_fin,e.razon_social FROM periodo_servicio p JOIN empresa_operadora e ON e.id=p.empresa_operadora_id WHERE p.id=?")) {
                    q.setObject(1,periodo);try(var r=q.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Periodo inexistente.");consola.printf("Empresa derivada: %s; periodo %s — %s.%n",r.getString(3),r.getObject(1),r.getObject(2)); }
                }
                boolean historico=false;
                if(otorgar && periodo!=null) {
                    String valor=consola.readLine("¿Permitir a este administrador delegar acceso HISTÓRICO en este periodo? SI/NO: ");
                    if(!Set.of("SI","NO").contains(valor==null?"":valor)) throw new IllegalArgumentException("Decisión explícita requerida.");historico="SI".equals(valor);
                }
                String motivo=consola.readLine("Fundamento y referencia de la política de delegación: ");
                if(motivo==null || motivo.isBlank() || motivo.length()>4000) throw new IllegalArgumentException("Fundamento obligatorio, máximo 4000 caracteres.");
                if(!"CONFIRMAR DELEGACION".equals(consola.readLine("Destino %s:%d%s, %s a %s; permiso %s; ámbito %s; histórico %s. Escriba CONFIRMAR DELEGACION: ",
                    uri.getHost(),uri.getPort(),uri.getPath(),args[0],administrador,args[2],args[3],historico))) throw new IllegalStateException("Cancelado.");
                try(var q=c.prepareStatement(otorgar?
                    "INSERT INTO facultad_delegacion(id,administrador_id,permiso,periodo_servicio_id,permite_historico,motivo,operador_local) VALUES (?,?,?,?,?,?,?)":
                    "UPDATE facultad_delegacion SET revocado_en=clock_timestamp(),revocado_por_local=?,motivo_revocacion=? WHERE administrador_id=? AND permiso=? AND periodo_servicio_id IS NOT DISTINCT FROM ?::uuid AND revocado_en IS NULL")) {
                    if(otorgar) { q.setObject(1,UUID.randomUUID());q.setObject(2,administrador);q.setString(3,args[2]);q.setObject(4,periodo);q.setBoolean(5,historico);q.setString(6,motivo);q.setString(7,System.getProperty("user.name")); }
                    else { q.setString(1,System.getProperty("user.name"));q.setString(2,motivo);q.setObject(3,administrador);q.setString(4,args[2]);q.setObject(5,periodo); }
                    int n=q.executeUpdate();c.commit();consola.printf("Facultades afectadas: %d. No crea usuarios ni concede facultades A1 automáticamente.%n",n);
                }
            } catch(Exception e) { c.rollback();throw new IllegalStateException("Provisión no completada. Revise referencias, unicidad y migración; sin cambios parciales."); }
        } catch(SQLException e) { throw new IllegalStateException("No se pudo acceder al destino local explícito."); }
    }
    private static String requerida(String nombre) { String valor=System.getenv(nombre);if(valor==null || valor.isBlank()) throw new IllegalStateException("Falta configurar "+nombre);return valor; }
}
