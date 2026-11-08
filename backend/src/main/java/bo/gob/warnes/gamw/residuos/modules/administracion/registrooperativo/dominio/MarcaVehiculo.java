package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;

/** CU-06 / RF-04. Registro operativo independiente de jornadas y autenticación. */
public record MarcaVehiculo(UUID id, String nombre, boolean activo) {
    public MarcaVehiculo {
        nombre = ReglasRegistroOperativo.requerido(nombre);
    }
}
