package bo.gob.warnes.gamw.residuos.modules.planificacion.rutas.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** INT-04: las mismas pruebas de {@link RutasReferenciaIT} (incluye el trigger V21 con SELECT ... FOR UPDATE de ruta) como rol NO propietario. */
@Import(EjecucionNoPropietaria.class)
class RutasReferenciaEjecucionNoPropietariaIT extends RutasReferenciaIT { }
