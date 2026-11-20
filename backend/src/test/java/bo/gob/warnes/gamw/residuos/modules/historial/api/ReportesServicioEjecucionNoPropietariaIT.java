package bo.gob.warnes.gamw.residuos.modules.historial.api;

import bo.gob.warnes.gamw.residuos.shared.config.EjecucionNoPropietaria;
import org.springframework.context.annotation.Import;

/** CU-30 + INT-04: el reporte descriptivo funciona con el rol de ejecución NO propietario (solo lectura). */
@Import(EjecucionNoPropietaria.class)
class ReportesServicioEjecucionNoPropietariaIT extends ReportesServicioIT { }
