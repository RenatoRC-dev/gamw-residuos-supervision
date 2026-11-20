package bo.gob.warnes.gamw.residuos.modules.correctivas.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** CU-14 + INT-04: el registro, la idempotencia y la reversión funcionan con el rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class IncidenciasOperativasEjecucionNoPropietariaIT extends IncidenciasOperativasIT { }
