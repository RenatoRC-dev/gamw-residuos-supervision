package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.time.LocalDate;

public final class ReglasRegistroOperativo {
    private ReglasRegistroOperativo() { }
    public static String requerido(String valor) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("El dato requerido no puede estar vacío.");
        return valor.strip();
    }
    public static String opcional(String valor) { return valor == null || valor.isBlank() ? null : valor.strip(); }
    public static void fechas(LocalDate inicio, LocalDate fin, boolean finObligatorio) {
        if (inicio == null || (finObligatorio && fin == null) || (fin != null && fin.isBefore(inicio))) {
            throw new IllegalArgumentException("La fecha de fin debe ser igual o posterior a la fecha de inicio.");
        }
    }
}
