import { apiConfig } from '../../../../shared/api/config';
import type { Point, Polygon, MultiPolygon } from 'geojson';
export type GeometriaControl = Point | Polygon | MultiPolygon;
export interface ReferenciaEspacial {
  id: string; referenciaId: string; numeroVersion: number; rutaVersionId: string; nombre: string;
  tipo: 'PUNTO' | 'GEOCERCA'; geometria: GeometriaControl; radioMetros: number | null;
  vigenteDesde: string; vigenteHasta: string | null; motivo: string;
}
export interface VersionRutaControl { rutaVersionId: string; codigo: string; nombre: string; numeroVersion: number }
/** Error HTTP con su código, para distinguir «sin facultad de gestión» (403) de otros fallos sin depender del texto. */
export class ErrorEspacial extends Error {
  constructor(message: string, readonly estado: number) { super(message); }
}
async function solicitar<T>(token: string, ruta: string, signal: AbortSignal, cuerpo?: unknown): Promise<T> {
  const r = await fetch(`${apiConfig.baseUrl}/referencias-espaciales${ruta}`, {
    method: cuerpo === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`, ...(cuerpo === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo), signal, credentials: 'omit', cache: 'no-store', redirect: 'error',
  });
  if (!r.ok) throw new ErrorEspacial(r.status === 403 ? 'Permiso explícito de consulta/gestión espacial requerido.' : r.status === 401
    ? 'Sesión vencida; vuelva a autenticarse.' : r.status === 503 ? 'No se pudo guardar; la operación se revirtió y la historia anterior se conserva.'
    : 'No se pudo completar la operación espacial. Revise geometría, asociación y vigencia.', r.status);
  return r.status === 204 ? undefined as T : r.json() as Promise<T>;
}
export const referenciasApi = {
  listar: (t: string, d: number, s: AbortSignal) => solicitar<{ referencias: ReferenciaEspacial[]; siguiente: number | null }>(t, `?desplazamiento=${d}`, s),
  rutas: (t: string, d: number, s: AbortSignal) => solicitar<VersionRutaControl[]>(t, `/versiones-ruta?desplazamiento=${d}`, s),
  guardar: (t: string, id: string | null, datos: unknown, s: AbortSignal) => solicitar<ReferenciaEspacial>(t, id ? `/${id}/versiones` : '', s, datos),
  cerrar: (t: string, id: string, motivo: string, s: AbortSignal) => solicitar<void>(t, `/${id}/cierre`, s, { motivo }),
};
