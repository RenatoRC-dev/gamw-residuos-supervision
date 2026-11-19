package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** H1 + INT-04: el mismo patrón de bloqueo (FOR SHARE / FOR UPDATE) funciona con el rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class ConcurrenciaPeriodoJornadaEjecucionNoPropietariaIT extends ConcurrenciaPeriodoJornadaIT { }
