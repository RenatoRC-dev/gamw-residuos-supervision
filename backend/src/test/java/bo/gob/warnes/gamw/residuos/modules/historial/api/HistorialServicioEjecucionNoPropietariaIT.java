package bo.gob.warnes.gamw.residuos.modules.historial.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** INT-04: las mismas pruebas de {@link HistorialServicioIT} como rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class HistorialServicioEjecucionNoPropietariaIT extends HistorialServicioIT { }
