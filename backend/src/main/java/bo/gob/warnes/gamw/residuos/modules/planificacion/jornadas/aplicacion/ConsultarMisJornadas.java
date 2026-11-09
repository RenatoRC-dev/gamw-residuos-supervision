package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.dominio.*;
import bo.gob.warnes.gamw.residuos.shared.security.UsuarioAutenticado;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CU-13: contexto propio, incluida cada asignación histórica; no administra jornadas. */
@Service
@Transactional(readOnly = true)
public class ConsultarMisJornadas {
    private final RepositorioJornadas jornadas;
    public ConsultarMisJornadas(RepositorioJornadas jornadas) { this.jornadas = jornadas; }
    public List<JornadaAsignada> consultar(UsuarioAutenticado actor) {
        Objects.requireNonNull(actor, "Se requiere la identidad autenticada.");
        return jornadas.asignadasAPersona(actor.personaNaturalId());
    }
}
