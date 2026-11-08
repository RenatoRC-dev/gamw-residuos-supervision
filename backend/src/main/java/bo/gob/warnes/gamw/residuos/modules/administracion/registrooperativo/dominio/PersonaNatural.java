package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;
import java.time.Instant;

public record PersonaNatural(UUID id, String ci, String nombres, String apellidoPaterno, String apellidoMaterno, String telefono, boolean activo, Instant creadoEn, Instant actualizadoEn) {
    public PersonaNatural {
        ci = ReglasRegistroOperativo.requerido(ci); nombres = ReglasRegistroOperativo.requerido(nombres);
        apellidoPaterno = ReglasRegistroOperativo.opcional(apellidoPaterno); apellidoMaterno = ReglasRegistroOperativo.opcional(apellidoMaterno);
        telefono = ReglasRegistroOperativo.opcional(telefono);
    }

}
