package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** CU-27 + INT-04: revisión, vínculos, idempotencia, revocación y reversión con el rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class RevisionReclamosEjecucionNoPropietariaIT extends RevisionReclamosIT { }
