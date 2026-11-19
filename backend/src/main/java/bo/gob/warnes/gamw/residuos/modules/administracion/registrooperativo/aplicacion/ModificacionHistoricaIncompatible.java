package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.aplicacion;

/** CU-04/CU-05 E3: la operación desnaturalizaría referencias históricas ya registradas (HTTP 409). */
public class ModificacionHistoricaIncompatible extends RuntimeException {
    public ModificacionHistoricaIncompatible(String mensaje) { super(mensaje); }
}
