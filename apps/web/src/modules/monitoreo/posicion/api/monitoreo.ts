import { apiConfig } from '../../../../shared/api/config';
import type { LineString } from 'geojson';
import type { JornadaTrayectoria } from '../../trayectorias/api/trayectorias';

export interface PosicionConocida {
  id: string; capturaId: string; jornadaId: string; asignacionJornadaId: string; usuarioId: string;
  capturadoEn: string; recibidoEn: string; latitud: number; longitud: number; precisionMetros: number;
}
export interface JornadaMonitoreada {
  contexto: JornadaTrayectoria; vehiculoId: string; placa: string; inicioRegistrado: string;
  ultimaPosicion: PosicionConocida | null; rutaReferencia: LineString;
}
export interface VistaMonitoreo {
  consultadoEn: string; umbralAntiguedadSegundos: number; jornadas: JornadaMonitoreada[]; siguiente: number | null;
}
export class ErrorMonitoreo extends Error {
  constructor(public readonly estado: number) {
    super(estado === 401 ? 'Sesión vencida.' : estado === 403
      ? 'No dispone del permiso municipal de monitoreo.' : 'No se pudo actualizar el monitoreo.');
  }
}
export async function consultarMonitoreo(token: string, desplazamiento: number, signal: AbortSignal): Promise<VistaMonitoreo> {
  const respuesta = await fetch(`${apiConfig.baseUrl}/monitoreo/jornadas?limite=50&desplazamiento=${desplazamiento}`, {
    headers: { Authorization: `Bearer ${token}` }, signal, redirect: 'error', credentials: 'omit', cache: 'no-store',
  });
  if (!respuesta.ok) throw new ErrorMonitoreo(respuesta.status);
  return respuesta.json() as Promise<VistaMonitoreo>;
}
