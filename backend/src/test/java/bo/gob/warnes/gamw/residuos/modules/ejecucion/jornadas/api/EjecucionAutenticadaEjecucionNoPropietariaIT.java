package bo.gob.warnes.gamw.residuos.modules.ejecucion.jornadas.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** INT-04: INICIO/FIN autenticados de {@link EjecucionAutenticadaIT} como rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class EjecucionAutenticadaEjecucionNoPropietariaIT extends EjecucionAutenticadaIT { }
