package bo.gob.warnes.gamw.residuos.modules.fiscalizacion.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** CU-21: revisión del expediente, permisos por fuente, revocación, PENDING-05 y 503 con el rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class FiscalizacionMunicipalEjecucionNoPropietariaIT extends FiscalizacionMunicipalIT { }
