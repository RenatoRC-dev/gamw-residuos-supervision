package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** INT-04: las mismas pruebas de {@link InspeccionCampoIT} como rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class InspeccionCampoEjecucionNoPropietariaIT extends InspeccionCampoIT { }
