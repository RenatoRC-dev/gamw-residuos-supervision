package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.aplicacion;

/** La asignación histórica no pertenece a la persona del Usuario autenticado. */
public class EjecucionNoAutorizada extends RuntimeException {
    public EjecucionNoAutorizada() {
        super("La asignación no pertenece a la persona del Usuario autenticado.");
    }
}
