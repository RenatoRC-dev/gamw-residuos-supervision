import { apiConfig } from './config';

// Acceso temporal de desarrollo. El servidor exige perfil explícito, loopback y origen permitido.
export const developmentHeaders = { 'X-GAMW-Development': 'gamw-desarrollo' };

export async function requestJson<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${apiConfig.baseUrl}${path}`, {
    method, headers: { ...developmentHeaders, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body), signal,
  });
  if (!response.ok) {
    const problem: { detail?: string } = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `No se pudo completar la solicitud (${response.status}).`);
  }
  return response.json() as Promise<T>;
}
