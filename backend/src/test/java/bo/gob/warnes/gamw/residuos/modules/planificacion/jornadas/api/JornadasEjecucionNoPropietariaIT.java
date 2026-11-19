package bo.gob.warnes.gamw.residuos.modules.planificacion.jornadas.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** INT-04: las mismas pruebas de {@link JornadasIT}, con la aplicación conectada como rol de ejecución NO propietario. */
@Import(EjecucionNoPropietaria.class)
class JornadasEjecucionNoPropietariaIT extends JornadasIT { }
