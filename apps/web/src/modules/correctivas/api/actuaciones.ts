import { apiConfig } from '../../../shared/api/config';
import type { ContextoFiscal, InspeccionCampo, DecisionFiscal, CategoriaFiscal } from '../../fiscalizacion/api/fiscalizacion';
import type { JornadaTrayectoria } from '../../monitoreo/trayectorias/api/trayectorias';

export interface Incidencia {
  id: string; jornadaId: string; asignacionJornadaId: string; usuarioId: string;
  ocurridaEn: string; registradaEn: string; descripcion: string; evidenciaDeclarada: string; lugarDescripcion: string;
}
export interface AccionCorrectiva {
  id: string; jornadaId: string; usuarioId: string; incidenciaId: string | null; inspeccionId: string | null;
  fiscalizacionId: string | null; descripcion: string; creadaEn: string; antecedentes: string;
}
export interface RespuestaCorrectiva {
  id: string; accionId: string; usuarioId: string; comunicadaEn: string; registradaEn: string; descripcion: string; evidenciaDeclarada: string;
  evidenciasReferenciadas: string[];
}
export interface VerificacionCorrectiva {
  id: string; respuestaId: string; usuarioId: string; resultado: string; referenciaAprobacion: string;
  observaciones: string; inspeccionId: string | null; registradaEn: string;
}
export interface HistoriaCorrectiva { accion: AccionCorrectiva; respuestas: RespuestaCorrectiva[]; verificaciones: VerificacionCorrectiva[] }
export interface ExpedienteCorrectivo {
  contexto: ContextoFiscal; incidencias: Incidencia[]; inspecciones: InspeccionCampo[]; fiscalizacion: DecisionFiscal | null;
  acciones: HistoriaCorrectiva[]; resultados: CategoriaFiscal[]; puedeSolicitar: boolean; puedeVerificar: boolean; limitaciones: string;
}
export class ErrorActuacion extends Error {
  constructor(public readonly estado: number) {
    super(estado === 401 ? 'Sesión vencida.' : estado === 403 ? 'Sin permiso vigente para esta actuación o fuera del ámbito autorizado.'
      : estado === 409 ? 'Actuación incompatible, ya evaluada o catálogo no habilitado. Consulte el historial antes de reintentar.'
        : 'No se confirmó el registro. Revise datos y conexión; puede reintentar la misma solicitud.');
  }
}
async function solicitar<T>(token: string, ruta: string, signal: AbortSignal, cuerpo?: unknown): Promise<T> {
  const r = await fetch(`${apiConfig.baseUrl}/correctivas${ruta}`, {
    method: cuerpo === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`,
      ...(cuerpo === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo), signal, cache: 'no-store', credentials: 'omit', redirect: 'error',
  });
  if (!r.ok) throw new ErrorActuacion(r.status);
  return r.json() as Promise<T>;
}
export const actuacionesApi = {
  jornadas: (t: string, desde: number, s: AbortSignal) => solicitar<{ jornadas: JornadaTrayectoria[]; siguiente: number | null }>(t, `/jornadas?desplazamiento=${desde}`, s),
  expediente: (t: string, j: string, s: AbortSignal) => solicitar<ExpedienteCorrectivo>(t, `/jornadas/${encodeURIComponent(j)}`, s),
  crear: (t: string, j: string, datos: unknown, s: AbortSignal) => solicitar<HistoriaCorrectiva>(t, `/jornadas/${encodeURIComponent(j)}/acciones`, s, datos),
  verificar: (t: string, a: string, datos: unknown, s: AbortSignal) => solicitar<VerificacionCorrectiva[]>(t, `/acciones/${encodeURIComponent(a)}/verificaciones`, s, datos),
};
