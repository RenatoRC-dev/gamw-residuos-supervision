package bo.gob.warnes.gamw.residuos.modules.administracion.registrooperativo.dominio;

import java.util.UUID;

public record FuncionOperativa(UUID id, String codigo, String nombre, boolean activo) {
}
