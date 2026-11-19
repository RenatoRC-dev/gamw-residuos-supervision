package bo.gob.warnes.gamw.residuos.modules.administracion.identidad.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** INT-04: concesiones, revocación y autorización de {@link AutorizacionOperativaV19IT} como rol NO propietario. */
@Import(EjecucionNoPropietaria.class)
class AutorizacionOperativaV19EjecucionNoPropietariaIT extends AutorizacionOperativaV19IT { }
