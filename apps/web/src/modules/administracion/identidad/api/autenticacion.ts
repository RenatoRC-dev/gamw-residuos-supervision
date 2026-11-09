import { apiConfig } from '../../../../shared/api/config';

export interface IdentidadUsuario {
  usuarioId: string;
  personaNaturalId: string;
  nombreUsuario: string;
}
export interface SesionAutenticada extends IdentidadUsuario {
  tokenAcceso: string;
  expiraEn: string;
}
export class ErrorAutenticacion extends Error {
  constructor(public readonly estado: number) {
    super(estado === 401 ? 'Credenciales inválidas o sesión vencida.' : 'No se pudo verificar la autenticación.');
  }
}

function identidad(datos: unknown): IdentidadUsuario {
  if (!datos || typeof datos !== 'object') throw new Error('Respuesta de identidad inválida.');
  const d = datos as Record<string, unknown>;
  if (['usuarioId', 'personaNaturalId', 'nombreUsuario'].some(k => typeof d[k] !== 'string' || !d[k])) {
    throw new Error('Respuesta de identidad inválida.');
  }
  return { usuarioId: d.usuarioId as string, personaNaturalId: d.personaNaturalId as string, nombreUsuario: d.nombreUsuario as string };
}

async function solicitar(ruta: string, signal: AbortSignal, cuerpo?: unknown, token?: string): Promise<unknown> {
  const r = await fetch(`${apiConfig.baseUrl}/autenticacion/${ruta}`, {
    method: cuerpo === undefined ? 'GET' : 'POST',
    headers: { ...(cuerpo === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    signal, redirect: 'error', credentials: 'omit', cache: 'no-store',
  });
  if (!r.ok) throw new ErrorAutenticacion(r.status);
  return r.json();
}

export const autenticacionApi = {
  async iniciar(nombreUsuario: string, contrasena: string, signal: AbortSignal): Promise<SesionAutenticada> {
    const datos = await solicitar('login', signal, { nombreUsuario, contrasena });
    const usuario = identidad(datos);
    const d = datos as Record<string, unknown>;
    if (typeof d.tokenAcceso !== 'string' || !d.tokenAcceso || typeof d.expiraEn !== 'string' ||
      !Number.isFinite(Date.parse(d.expiraEn)) || Date.parse(d.expiraEn) <= Date.now()) {
      throw new Error('Respuesta de sesión inválida.');
    }
    return { ...usuario, tokenAcceso: d.tokenAcceso, expiraEn: d.expiraEn };
  },
  async actual(token: string, signal: AbortSignal): Promise<IdentidadUsuario> {
    return identidad(await solicitar('actual', signal, undefined, token));
  },
};

export type ApiAutenticacion = typeof autenticacionApi;
