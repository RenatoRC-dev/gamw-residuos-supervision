package bo.gob.warnes.gamw.residuos.modules.reclamos.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** CU-28 con el rol de aplicación no propietario (privilegios mínimos de afterMigrate), mismos escenarios que {@link PuntosCriticosIT}. */
@Import(EjecucionNoPropietaria.class)
class PuntosCriticosEjecucionNoPropietariaIT extends PuntosCriticosIT { }
