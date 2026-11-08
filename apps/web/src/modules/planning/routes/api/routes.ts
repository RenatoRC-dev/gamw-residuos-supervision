import { apiConfig } from '../../../../shared/api/config';
import { parseLineString, type LineStringGeometry } from '../model/geometry';

export interface ReferenceRoute {
  id: string; code: string; name: string; active: boolean; createdAt: string; updatedAt: string;
}
export interface RouteVersion {
  id: string; routeId: string; versionNumber: number; geometry: LineStringGeometry;
  validFrom: string; validTo: string | null; changeReason: string | null; createdAt: string;
}
export interface VersionInput { geometry: LineStringGeometry; validFrom: string; changeReason?: string }
export interface RouteInput extends VersionInput { code: string; name: string }

async function request<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${apiConfig.baseUrl}/routes${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { 'X-GAMW-Development': 'route-poc', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body), signal,
  });
  if (!response.ok) {
    const problem: { detail?: string } = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `No se pudo completar la solicitud (${response.status}).`);
  }
  return response.json() as Promise<T>;
}

function versionFromApi(version: RouteVersion): RouteVersion {
  return { ...version, geometry: parseLineString(version.geometry) };
}

export const routesApi = {
  list: (signal?: AbortSignal) => request<ReferenceRoute[]>('', undefined, signal),
  get: (id: string) => request<ReferenceRoute>(`/${encodeURIComponent(id)}`),
  versions: async (id: string) => (await request<RouteVersion[]>(`/${encodeURIComponent(id)}/versions`)).map(versionFromApi),
  version: async (id: string, number: number) => versionFromApi(await request<RouteVersion>(`/${encodeURIComponent(id)}/versions/${number}`)),
  create: async (input: RouteInput) => request<{ route: ReferenceRoute; version: RouteVersion }>('', {
    ...input, geometry: parseLineString(input.geometry),
  }),
  append: async (id: string, input: VersionInput) => versionFromApi(await request<RouteVersion>(`/${encodeURIComponent(id)}/versions`, {
    ...input, geometry: parseLineString(input.geometry),
  })),
};
