import { apiConfig } from './config';

export async function readTechnicalHealth(signal?: AbortSignal): Promise<void> {
  const response = await fetch(`${apiConfig.baseUrl}/technical/health`, { signal });
  if (!response.ok) throw new Error('No se pudo consultar el estado técnico.');
  const body: unknown = await response.json();
  if (!body || typeof body !== 'object' || !('status' in body) || body.status !== 'UP') {
    throw new Error('Respuesta de estado técnico no válida.');
  }
}
