import { apiConfig } from './config';

// Acceso temporal de desarrollo. El servidor exige perfil explícito, loopback y origen permitido.
export const developmentHeaders = { 'X-GAMW-Development': 'gamw-desarrollo' };
let acceso: { token: string | null; cerrar: (mensaje?: string) => void; controlador: AbortController } | null = null;

// Enlace en memoria con el único proveedor de sesión; nunca localStorage/cookies.
export function configurarAccesoHttp(token: string | null, cerrar: (mensaje?: string) => void) {
  acceso?.controlador.abort();
  const actual = { token, cerrar, controlador: new AbortController() }; acceso = actual;
  return () => { actual.controlador.abort(); if (acceso === actual) acceso = null; };
}

export async function requestJson<T>(path: string, method = 'GET', body?: unknown, signal?: AbortSignal): Promise<T> {
  const actual = acceso, token = actual?.token;
  const local = !token && import.meta.env.DEV && import.meta.env.VITE_GAMW_DESARROLLO_LOCAL === 'true'
    && ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname)
    && ['localhost', '127.0.0.1', '[::1]'].includes(new URL(apiConfig.baseUrl, window.location.href).hostname);
  if (!token && !local) throw new Error('Inicie sesión institucional o configure explícitamente el acceso local de desarrollo.');
  const controlador = new AbortController();
  const abortar = () => controlador.abort();
  if (signal?.aborted || actual?.controlador.signal.aborted) abortar();
  signal?.addEventListener('abort', abortar, { once: true }); actual?.controlador.signal.addEventListener('abort', abortar, { once: true });
  try {
  const response = await fetch(`${apiConfig.baseUrl}${path}`, {
    method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : developmentHeaders), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body), signal: controlador.signal, credentials: 'omit', redirect: 'error', cache: 'no-store',
  });
  if (actual !== acceso || controlador.signal.aborted) throw new Error('La sesión cambió; respuesta descartada.');
  if (response.status === 401 && token && actual?.token === token) actual.cerrar('Sesión vencida.');
  if (!response.ok) {
    const problem: { detail?: string } = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `No se pudo completar la solicitud (${response.status}).`);
  }
  const datos = await response.json() as T;
  if (actual !== acceso || controlador.signal.aborted) throw new Error('La sesión cambió; respuesta descartada.');
  return datos;
  } finally { signal?.removeEventListener('abort', abortar); actual?.controlador.signal.removeEventListener('abort', abortar); }
}
