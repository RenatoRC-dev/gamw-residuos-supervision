package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** INT-04: las mismas pruebas de {@link ReclamoInstitucionalIT} (SELECT ... FOR UPDATE de reclamos) como rol NO propietario. */
@Import(EjecucionNoPropietaria.class)
class ReclamoInstitucionalEjecucionNoPropietariaIT extends ReclamoInstitucionalIT { }
