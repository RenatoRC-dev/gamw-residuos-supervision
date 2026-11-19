package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** INT-04: cadena INICIO → GPS → FIN → inspección → fiscalización → correctivas de {@link CadenaOperativaFiscalizacionIT} como rol NO propietario. */
@Import(EjecucionNoPropietaria.class)
class CadenaOperativaFiscalizacionEjecucionNoPropietariaIT extends CadenaOperativaFiscalizacionIT { }
