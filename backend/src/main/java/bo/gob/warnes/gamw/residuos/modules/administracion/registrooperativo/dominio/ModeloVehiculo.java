package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;

/** CU-06 / RF-04. Registro operativo independiente de jornadas y autenticación. */
public record ModeloVehiculo(UUID id, UUID marcaVehiculoId, String nombre, boolean activo) {
    public ModeloVehiculo {
        java.util.Objects.requireNonNull(marcaVehiculoId, "La marca es obligatoria.");
        nombre = ReglasRegistroOperativo.requerido(nombre);
    }
}
