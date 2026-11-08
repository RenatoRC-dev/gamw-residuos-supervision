package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;

/** CU-06 / RF-04. Registro operativo independiente de jornadas y autenticación. */
public record TipoVehiculo(UUID id, String codigo, String nombre, boolean activo) {
    public TipoVehiculo {
        codigo = ReglasRegistroOperativo.requerido(codigo); nombre = ReglasRegistroOperativo.requerido(nombre);
    }
}
