package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;
import java.time.Instant;

public record EmpresaOperadora(UUID id, String nit, String razonSocial, String nombreComercial, boolean activo, Instant creadoEn, Instant actualizadoEn) {
    public EmpresaOperadora {
        nit = nit == null ? null : ReglasRegistroOperativo.requerido(nit);
        razonSocial = ReglasRegistroOperativo.requerido(razonSocial); nombreComercial = ReglasRegistroOperativo.opcional(nombreComercial);
    }

}
