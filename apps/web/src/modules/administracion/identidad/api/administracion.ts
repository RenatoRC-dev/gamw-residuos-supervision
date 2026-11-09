import { apiConfig } from '../../../../shared/api/config';

export interface Cuenta {
  id: string; personaNaturalId: string; nombreUsuario: string; activo: boolean; nombres: string;
  apellidoPaterno: string | null; apellidoMaterno: string | null; creadoEn: string; actualizadoEn: string; administracionProtegida: boolean;
}
export interface Persona { id: string; ci: string; nombres: string; apellidoPaterno: string | null; apellidoMaterno: string | null; activo: boolean }
export interface Concesion { id: string; permiso: string; periodoServicioId: string | null; incluyeHistorico: boolean; otorgadoEn: string; revocadoEn: string | null }
export interface Facultad { id: string; permiso: string; periodoServicioId: string | null; empresa: string | null; fechaInicio: string | null; fechaFin: string | null; permiteHistorico: boolean }
export interface Opciones { puedeGestionar: boolean; puedeAdministrarPermisos: boolean; provisionHabilitada: boolean; referenciaProvision: string; facultades: Facultad[]; pendientes: string }
export interface Actuacion { id: string; actorUsuarioId: string; tipo: string; motivo: string; registradaEn: string }
export interface Pagina<T> { registros: T[]; siguiente: number | null }
export class ErrorAdministracion extends Error {
  constructor(public readonly estado: number) {
    super(estado === 401 ? 'Sesión vencida.' : estado === 403 ? 'Sin facultad administrativa o delegación para este permiso y ámbito; auto-administración y cuentas administrativas protegidas.'
      : estado === 409 ? 'Operación bloqueada: nombre/concesión existente, referencia incompatible o política pendiente. Consulte el registro antes de reintentar.'
        : 'Operación no confirmada. Revise los campos y la conexión. No se afirma éxito sin respuesta del servidor.');
  }
}
async function solicitar<T>(token: string, ruta: string, signal: AbortSignal, metodo = 'GET', datos?: unknown): Promise<T> {
  const r = await fetch(`${apiConfig.baseUrl}/administracion/identidad${ruta}`, {
    method: metodo, headers: { Authorization: `Bearer ${token}`, ...(datos === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: datos === undefined ? undefined : JSON.stringify(datos), signal, credentials: 'omit', cache: 'no-store', redirect: 'error',
  });
  if (!r.ok) throw new ErrorAdministracion(r.status);
  return r.json() as Promise<T>;
}
export const administracionApi = {
  opciones: (t: string, s: AbortSignal) => solicitar<Opciones>(t, '/opciones', s),
  listar: (t: string, d: number, s: AbortSignal) => solicitar<Pagina<Cuenta>>(t, `/usuarios?desplazamiento=${d}`, s),
  personas: (t: string, ci: string, s: AbortSignal) => solicitar<Persona[]>(t, `/personas?ci=${encodeURIComponent(ci)}`, s),
  cuenta: (t: string, id: string, s: AbortSignal) => solicitar<Cuenta>(t, `/usuarios/${id}`, s),
  concesiones: (t: string, id: string, d: number, s: AbortSignal) => solicitar<Pagina<Concesion>>(t, `/usuarios/${id}/permisos?desplazamiento=${d}`, s),
  actuaciones: (t: string, id: string, d: number, s: AbortSignal) => solicitar<Pagina<Actuacion>>(t, `/usuarios/${id}/actuaciones?desplazamiento=${d}`, s),
  guardar: (t: string, ruta: string, metodo: string, datos: unknown, s: AbortSignal) => solicitar<unknown>(t, ruta, s, metodo, datos),
};
