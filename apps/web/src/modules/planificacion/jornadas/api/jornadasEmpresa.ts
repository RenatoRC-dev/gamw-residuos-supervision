import { apiConfig } from '../../../../shared/api/config';

export interface PlanEmpresa {
  id: string; periodoServicioId: string; empresa: string; rutaVersionId: string; ruta: string; numeroVersion: number;
  inicioProgramado: string; finProgramado: string | null; antecedenteId: string | null; sucesoraId: string | null;
  motivo: string | null; vigente: boolean; puedeReprogramar: boolean; estadoAsignacion: 'SIN_ASIGNACIONES' | 'CON_ASIGNACION'; puedeAsignar: boolean; limitacion: string;
  asignaciones: { id: string; vinculoVehiculoEmpresaId: string; vinculoPersonaEmpresaId: string; placa: string;
    responsable: string; vigenteDesde: string; vigenteHasta: string | null }[];
}
export class ErrorReprogramacion extends Error {
  constructor(public readonly estado: number) {
    super(estado === 401 ? 'Sesión vencida.' : estado === 403 ? 'Sin concesión vigente para esta operación de planificación y su periodo.'
      : estado === 409 ? 'Antecedente no elegible, ya sustituido o solicitud incompatible. Consulte el historial; la evidencia no se transfiere.'
        : 'Operación no confirmada. Revise fechas, vigencia de la versión de ruta y motivo; no genere otra solicitud ante una respuesta incierta.');
  }
}
async function solicitar<T>(t: string, ruta: string, s: AbortSignal, datos?: unknown, base='/empresa/jornadas'): Promise<T> {
  const r = await fetch(`${apiConfig.baseUrl}${base}${ruta}`, {
    method: datos === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${t}`,
      ...(datos === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: datos === undefined ? undefined : JSON.stringify(datos), signal: s, credentials: 'omit', cache: 'no-store', redirect: 'error',
  });
  if (!r.ok) throw new ErrorReprogramacion(r.status);
  return r.json() as Promise<T>;
}
export const jornadasEmpresaApi = {
  periodos: (t: string, s: AbortSignal) => solicitar<PeriodoPlan[]>(t, '/periodos-servicio', s, undefined, '/empresa'),
  referencias: (t: string, periodo: string, s: AbortSignal) => solicitar<VersionPlan[]>(t, `/periodos/${encodeURIComponent(periodo)}/referencias`, s, undefined, '/empresa'),
  programar: (t: string, periodo: string, datos: unknown, s: AbortSignal) => solicitar<PlanEmpresa>(t, `/periodos/${encodeURIComponent(periodo)}/jornadas`, s, datos, '/empresa'),
  recursos: (t: string, id: string, s: AbortSignal) => solicitar<RecursosPlan>(t, `/${encodeURIComponent(id)}/recursos`, s),
  asignar: (t: string, id: string, datos: unknown, reasignar: boolean, s: AbortSignal) => solicitar<PlanEmpresa>(t, `/${encodeURIComponent(id)}/${reasignar?'reasignaciones':'asignaciones'}`, s, datos),
  listar: (t: string, d: number, s: AbortSignal) => solicitar<{ jornadas: PlanEmpresa[]; siguiente: number | null }>(t, `?desplazamiento=${d}`, s),
  obtener: (t: string, id: string, s: AbortSignal) => solicitar<PlanEmpresa>(t, `/${encodeURIComponent(id)}`, s),
  reprogramar: (t: string, id: string, datos: unknown, s: AbortSignal) => solicitar<PlanEmpresa>(t, `/${encodeURIComponent(id)}/reprogramaciones`, s, datos),
};
export interface PeriodoPlan { id: string; empresaId: string; empresa: string; fechaInicio: string; fechaFin: string; puedeProgramar: boolean }
export interface VersionPlan { id: string; rutaId: string; ruta: string; numeroVersion: number; vigenteDesde: string; vigenteHasta: string | null }
export interface RecursosPlan { vehiculos: { vinculoId: string; recursoId: string; nombre: string }[]; personas: { vinculoId: string; recursoId: string; nombre: string }[] }
