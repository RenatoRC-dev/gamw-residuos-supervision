import { requestJson } from '../../../../shared/api/http';
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

function request<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  return requestJson<T>(`/rutas${path}`, body === undefined ? 'GET' : 'POST', body, signal);
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
