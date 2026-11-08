import { apiConfig } from '../../../../shared/api/config';
import { validarLineString, type GeometriaLineString } from '../modelo/geometria';

export interface Ruta {
  id: string; codigo: string; nombre: string; activo: boolean; creadoEn: string; actualizadoEn: string;
}
export interface RutaVersion {
  id: string; rutaId: string; numeroVersion: number; geometria: GeometriaLineString;
  vigenteDesde: string; vigenteHasta: string | null; motivoCambio: string | null; creadoEn: string;
}
export interface EntradaVersion { geometria: GeometriaLineString; vigenteDesde: string; motivoCambio?: string }
export interface EntradaRuta extends EntradaVersion { codigo: string; nombre: string }

async function request<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${apiConfig.baseUrl}/rutas${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { 'X-GAMW-Development': 'rutas-poc', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body), signal,
  });
  if (!response.ok) {
    const problem: { detail?: string } = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `No se pudo completar la solicitud (${response.status}).`);
  }
  return response.json() as Promise<T>;
}

function versionDesdeApi(version: RutaVersion): RutaVersion {
  return { ...version, geometria: validarLineString(version.geometria) };
}

export const apiRutas = {
  listar: (signal?: AbortSignal) => request<Ruta[]>('', undefined, signal),
  obtener: (id: string) => request<Ruta>(`/${encodeURIComponent(id)}`),
  versiones: async (id: string) => (await request<RutaVersion[]>(`/${encodeURIComponent(id)}/versiones`)).map(versionDesdeApi),
  obtenerVersion: async (id: string, numeroVersion: number) => versionDesdeApi(await request<RutaVersion>(`/${encodeURIComponent(id)}/versiones/${numeroVersion}`)),
  crear: async (input: EntradaRuta) => request<{ ruta: Ruta; version: RutaVersion }>('', {
    ...input, geometria: validarLineString(input.geometria),
  }),
  agregarVersion: async (id: string, input: EntradaVersion) => versionDesdeApi(await request<RutaVersion>(`/${encodeURIComponent(id)}/versiones`, {
    ...input, geometria: validarLineString(input.geometria),
  })),
};
