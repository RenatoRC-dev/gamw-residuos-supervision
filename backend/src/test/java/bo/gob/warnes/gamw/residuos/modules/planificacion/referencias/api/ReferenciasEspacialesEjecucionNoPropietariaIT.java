package bo.gob.warnes.gamw.residuos.modules.planificacion.referencias.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** CU-10 + INT-04: registro, versionado, cierre, reversión y CU-18 con el rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class ReferenciasEspacialesEjecucionNoPropietariaIT extends ReferenciasEspacialesIT { }
