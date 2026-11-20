package bo.gob.warnes.gamw.residuos.modules.correctivas.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** CU-23: cadena solicitud → atención A4 → verificación, catálogo, aislamiento, revocación, concurrencia y 503 con el rol NO propietario. */
@Import(EjecucionNoPropietaria.class)
class VerificacionCorrectivaEjecucionNoPropietariaIT extends VerificacionCorrectivaIT { }
