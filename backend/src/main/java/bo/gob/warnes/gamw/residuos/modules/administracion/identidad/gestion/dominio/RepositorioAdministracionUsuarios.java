package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.gestion.dominio;

import java.time.Instant;
import java.util.*;

public interface RepositorioAdministracionUsuarios {
    record Cuenta(UUID id,UUID personaNaturalId,String nombreUsuario,boolean activo,String nombres,String apellidoPaterno,String apellidoMaterno,
        Instant creadoEn,Instant actualizadoEn,boolean administracionProtegida) { }
    record Persona(UUID id,String ci,String nombres,String apellidoPaterno,String apellidoMaterno,boolean activo) { }
    record Concesion(UUID id,String permiso,UUID periodoServicioId,boolean incluyeHistorico,Instant otorgadoEn,Instant revocadoEn) { }
    record Facultad(UUID id,String permiso,UUID periodoServicioId,String empresa,String fechaInicio,String fechaFin,boolean permiteHistorico) { }
    record Actuacion(UUID id,UUID actorUsuarioId,String tipo,String motivo,Instant registradaEn) { }
    List<Cuenta> listar(int limite,int desde);
    Optional<Cuenta> cuenta(UUID id,boolean bloquear);
    List<Persona> personas(String ci);
    boolean personaValida(UUID persona);
    void crear(UUID id,UUID persona,String nombre,String hash);
    void renombrar(UUID id,String nombre);
    void desactivar(UUID id);
    List<Concesion> concesiones(UUID usuario,int limite,int desde);
    Optional<Concesion> concesion(UUID usuario,UUID id,boolean periodo);
    List<Facultad> facultades(UUID actor,boolean bloquear);
    UUID otorgar(UUID usuario,String permiso,UUID periodo,boolean historico,String motivo,UUID actor);
    boolean revocar(Concesion concesion,UUID actor,String motivo);
    void auditar(UUID actor,UUID objetivo,String tipo,String motivo,String anterior,String posterior);
    List<Actuacion> actuaciones(UUID usuario,int limite,int desde);
}
