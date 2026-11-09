package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.aplicacion;

/** Las credenciales no permiten autenticar. El mensaje no distingue usuario inexistente de contraseña incorrecta. */
public class CredencialesInvalidas extends RuntimeException {
    public CredencialesInvalidas() {
        super("Credenciales inválidas.");
    }
}
