import { requestJson } from '../../../shared/api/http';

export type EstadoRecuento = 'CON_REGISTROS' | 'SIN_REGISTROS' | 'ACCESO_NO_PERMITIDO' | 'NO_CONSULTADO' | 'NO_APLICA';
export interface Recuento {
  clave: string; nombre: string; definicion: string; fuente: string; permisoRequerido: string | null; estado: EstadoRecuento;
  cantidad: number | null; ultimoRegistro: string | null; nota: string | null;
}
export interface FiltrosAplicados { desde: string; hasta: string; periodoId: string | null; empresaId: string | null; zonaFechaServicio: string }
export interface ReporteServicio { filtros: FiltrosAplicados; generadoEn: string; naturaleza: string; recuentos: Recuento[]; limitaciones: string[] }
export interface FiltrosReporte { desde: string; hasta: string; periodoId: string; empresaId: string }

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
/** Validación previa en cliente; el servidor valida de nuevo y es la autoridad. */
export function validarFiltrosReporte(f: FiltrosReporte): string {
  if (!f.desde || !f.hasta) return 'Indique la fecha "desde" y la fecha "hasta".';
  if (f.desde > f.hasta) return 'Rango de fechas contradictorio: "desde" es posterior a "hasta".';
  if (f.periodoId.trim() && !UUID.test(f.periodoId.trim())) return 'El identificador de periodo no es válido.';
  if (f.empresaId.trim() && !UUID.test(f.empresaId.trim())) return 'El identificador de empresa no es válido.';
  return '';
}
export const apiReportesServicio = {
  resumen: (f: FiltrosReporte, signal?: AbortSignal) => {
    const q = new URLSearchParams({ desde: f.desde, hasta: f.hasta });
    if (f.periodoId.trim()) q.set('periodoId', f.periodoId.trim());
    if (f.empresaId.trim()) q.set('empresaId', f.empresaId.trim());
    return requestJson<ReporteServicio>(`/reportes-servicio/resumen?${q.toString()}`, 'GET', undefined, signal);
  },
};
