import { requestJson } from '../../../../shared/api/http';

export interface Jornada {
  id: string; periodoServicioId: string; rutaVersionId: string; inicioProgramado: string; finProgramado: string | null;
  jornadaAntecedenteId: string | null; motivoReprogramacion: string | null; vigente: boolean; creadoEn: string; actualizadoEn: string;
}
export interface AsignacionJornada {
  id: string; jornadaId: string; vinculoVehiculoEmpresaId: string; vinculoPersonaEmpresaId: string;
  vigenteDesde: string; vigenteHasta: string | null; motivoCambio: string | null; creadoEn: string;
  vehiculo: { id: string; placa: string };
  responsable: { id: string; ci: string; nombres: string; apellidoPaterno: string | null; apellidoMaterno: string | null };
}
export interface PeriodoOpcion {
  id: string; empresaOperadoraId: string; razonSocial: string; fechaInicio: string; fechaFin: string; activo: boolean; empresaActiva: boolean;
}
export interface DetalleJornada {
  jornada: Jornada;
  periodoServicio: { id: string; empresaOperadoraId: string; fechaInicio: string; fechaFin: string; activo: boolean };
  empresaOperadora: { id: string; razonSocial: string; activo: boolean };
  rutaVersion: { id: string; rutaId: string; nombreRuta: string; numeroVersion: number; vigenteDesde: string; vigenteHasta: string | null };
  asignacionActual: AsignacionJornada | null; fechaServicio: string;
}
export interface Programacion { periodoServicioId: string; rutaVersionId: string; inicioProgramado: string; finProgramado: string | null }
export interface Recursos { vinculoVehiculoEmpresaId: string; vinculoPersonaEmpresaId: string }
const ruta = (id: string) => `/jornadas/${encodeURIComponent(id)}`;
export const apiJornadas = {
  periodos: (signal?: AbortSignal) => requestJson<PeriodoOpcion[]>('/periodos-servicio', 'GET', undefined, signal),
  listar: (signal?: AbortSignal) => requestJson<DetalleJornada[]>('/jornadas', 'GET', undefined, signal),
  obtener: (id: string) => requestJson<DetalleJornada>(ruta(id)),
  crear: (datos: Programacion) => requestJson<DetalleJornada>('/jornadas', 'POST', datos),
  reprogramar: (id: string, datos: Programacion, motivoReprogramacion: string) => requestJson<DetalleJornada>(`${ruta(id)}/reprogramaciones`, 'POST', { ...datos, motivoReprogramacion }),
  historial: (id: string) => requestJson<AsignacionJornada[]>(`${ruta(id)}/asignaciones`),
  asignar: (id: string, datos: Recursos) => requestJson<AsignacionJornada>(`${ruta(id)}/asignaciones`, 'POST', datos),
  reasignar: (id: string, datos: Recursos, motivoCambio: string) => requestJson<AsignacionJornada>(`${ruta(id)}/reasignaciones`, 'POST', { ...datos, motivoCambio }),
};
