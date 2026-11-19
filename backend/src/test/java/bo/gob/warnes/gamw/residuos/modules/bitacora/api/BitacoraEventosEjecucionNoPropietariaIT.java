package bo.gob.warnes.gamw.residuos.modules.bitacora.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** INT-04: las mismas pruebas de {@link BitacoraEventosIT} como rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class BitacoraEventosEjecucionNoPropietariaIT extends BitacoraEventosIT { }
