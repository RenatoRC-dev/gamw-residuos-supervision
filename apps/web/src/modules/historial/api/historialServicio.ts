import { requestJson } from '../../../shared/api/http';

export interface PeriodoHistorico { id: string; fechaInicio: string; fechaFin: string }
export interface EmpresaHistorica { id: string; razonSocial: string }
export interface RutaVersionHistorica { rutaId: string; codigo: string | null; nombre: string; versionId: string; numeroVersion: number; vigenteDesde: string; vigenteHasta: string | null }
export interface ResumenJornadaHistorica {
  id: string; inicioProgramado: string; finProgramado: string | null; vigente: boolean; antecedenteId: string | null; sucesoraId: string | null;
  motivoReprogramacion: string | null; periodo: PeriodoHistorico; empresa: EmpresaHistorica; ruta: RutaVersionHistorica; cantidadAsignaciones: number;
}
export interface AsignacionHistorica {
  id: string; vigenteDesde: string; vigenteHasta: string | null; motivoCambio: string | null; vinculoVehiculoEmpresaId: string; vinculoPersonaEmpresaId: string;
  placa: string; responsable: string; empresaVehiculoId: string; empresaPersonaId: string;
}
export type EstadoEvidencia = 'CON_REGISTROS' | 'SIN_REGISTROS' | 'ACCESO_NO_PERMITIDO' | 'NO_CONSULTADO';
export interface EnlaceEvidencia { modulo: string; ruta: string; estado: EstadoEvidencia; cantidad: number | null; nota: string }
export interface DetalleHistorico {
  jornada: ResumenJornadaHistorica; cadena: ResumenJornadaHistorica[]; asignaciones: AsignacionHistorica[]; inconsistencias: string[]; enlaces: EnlaceEvidencia[];
}
export interface PaginaHistorica { jornadas: ResumenJornadaHistorica[]; siguiente: number | null }
export interface FiltrosHistorial { jornadaId: string; periodoId: string; empresaId: string; rutaId: string; desde: string; hasta: string }

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
/** Validación previa en cliente; el servidor valida de nuevo y es la autoridad. */
export function validarFiltros(f: FiltrosHistorial): string {
  if (f.jornadaId && !UUID.test(f.jornadaId.trim())) return 'El identificador de jornada no es válido.';
  if (f.periodoId && !UUID.test(f.periodoId.trim())) return 'El identificador de periodo no es válido.';
  if (f.empresaId && !UUID.test(f.empresaId.trim())) return 'El identificador de empresa no es válido.';
  if (f.rutaId && !UUID.test(f.rutaId.trim())) return 'El identificador de ruta no es válido.';
  if (f.desde && f.hasta && f.desde > f.hasta) return 'Rango de fechas contradictorio: "desde" es posterior a "hasta".';
  return '';
}
export const apiHistorialServicio = {
  buscar: (f: FiltrosHistorial, desplazamiento: number, signal?: AbortSignal) => {
    const q = new URLSearchParams();
    if (f.jornadaId.trim()) q.set('jornadaId', f.jornadaId.trim());
    if (f.periodoId.trim()) q.set('periodoId', f.periodoId.trim());
    if (f.empresaId.trim()) q.set('empresaId', f.empresaId.trim());
    if (f.rutaId.trim()) q.set('rutaId', f.rutaId.trim());
    if (f.desde) q.set('desde', f.desde);
    if (f.hasta) q.set('hasta', f.hasta);
    if (desplazamiento > 0) q.set('desplazamiento', String(desplazamiento));
    const texto = q.toString();
    return requestJson<PaginaHistorica>(`/historial-servicio/jornadas${texto ? `?${texto}` : ''}`, 'GET', undefined, signal);
  },
  detalle: (id: string, signal?: AbortSignal) => requestJson<DetalleHistorico>(`/historial-servicio/jornadas/${encodeURIComponent(id)}`, 'GET', undefined, signal),
};
