package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-07 / RF-04 parcial. Identidad independiente de la empresa y de los roles del sistema. */
@Service
@Transactional(readOnly = true)
public class GestionarPersonas {
    private final RepositorioRegistroOperativo repositorio;
    public GestionarPersonas(RepositorioRegistroOperativo repositorio) { this.repositorio = repositorio; }
    public List<PersonaNatural> listar() { return repositorio.personas(); }
    public PersonaNatural obtener(UUID id) { return buscar(id, false); }
    private PersonaNatural buscar(UUID id, boolean bloquear) {
        return repositorio.persona(id,bloquear).orElseThrow(() -> new RegistroNoEncontrado("Persona no encontrada."));
    }
    @Transactional
    public PersonaNatural crear(String ci, String nombres, String apellidoPaterno, String apellidoMaterno, String telefono) {
        var ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var persona = new PersonaNatural(UUID.randomUUID(),ci,nombres,apellidoPaterno,apellidoMaterno,telefono,true,ahora,ahora);
        repositorio.insertarPersona(persona);
        return persona;
    }
    @Transactional
    public PersonaNatural actualizar(UUID id, String ci, String nombres, String apellidoPaterno, String apellidoMaterno, String telefono) {
        var anterior = buscar(id,true);
        var persona = new PersonaNatural(id,ci,nombres,apellidoPaterno,apellidoMaterno,telefono,anterior.activo(),anterior.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.actualizarPersona(persona);
        return persona;
    }
    @Transactional
    public PersonaNatural cambiarEstado(UUID id, boolean activo) {
        var p = buscar(id,true);
        var persona = new PersonaNatural(id,p.ci(),p.nombres(),p.apellidoPaterno(),p.apellidoMaterno(),p.telefono(),activo,p.creadoEn(),Instant.now().truncatedTo(ChronoUnit.MICROS));
        repositorio.actualizarPersona(persona);
        return persona;
    }
}
