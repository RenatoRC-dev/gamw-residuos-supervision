package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.aplicacion.ReferenciasJornadas;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import static bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api.ContratosJornadas.*;

@RestController
public class PeriodosPlanificacionController {
    private final ReferenciasJornadas referencias;
    public PeriodosPlanificacionController(ReferenciasJornadas referencias) { this.referencias = referencias; }
    @GetMapping("/api/periodos-servicio")
    public List<PeriodoOpcion> listar() {
        return referencias.periodos().stream().map(p -> {
            var e = referencias.empresa(p.empresaOperadoraId());
            return new PeriodoOpcion(p.id(),e.id(),e.razonSocial(),p.fechaInicio(),p.fechaFin(),p.activo(),e.activo());
        }).toList();
    }
}
