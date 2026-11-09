import { apiConfig } from '../../../../shared/api/config';
import type { FeatureCollection, Point, LineString } from 'geojson';

export interface JornadaTrayectoria {
  jornadaId: string; inicioProgramado: string; finProgramado: string | null;
  periodoServicioId: string; empresaOperadora: string; rutaId: string;
  codigoRuta: string; nombreRuta: string; rutaVersionId: string; numeroVersion: number;
}
export interface MetadatosGps {
  id: string; capturaId: string; jornadaId: string; asignacionJornadaId: string;
  usuarioId: string; capturadoEn: string; recibidoEn: string; precisionMetros: number;
}
export interface DetalleTrayectoria {
  contexto: JornadaTrayectoria; rutaReferencia: LineString;
  seguimiento: { trayectoria: FeatureCollection<Point, MetadatosGps>; desplazamiento: number;
    siguiente: number | null; corteRecepcion: string };
}
export class ErrorTrayectoria extends Error {
  constructor(public readonly estado: number) {
    super(estado === 403 ? 'No dispone de autorización municipal para consultar trayectorias.'
      : estado === 401 ? 'Sesión vencida. Vuelva a autenticarse.'
      : estado === 404 ? 'Jornada o referencia no disponible.' : 'No se pudo consultar la trayectoria.');
  }
}
async function consultar<T>(ruta: string, token: string, signal: AbortSignal): Promise<T> {
  const r = await fetch(`${apiConfig.baseUrl}/trayectorias/jornadas${ruta}`, {
    headers: { Authorization: `Bearer ${token}` }, signal,
    redirect: 'error', credentials: 'omit', cache: 'no-store',
  });
  if (!r.ok) throw new ErrorTrayectoria(r.status);
  return r.json() as Promise<T>;
}
export const trayectoriasApi = {
  jornadas: (token: string, desplazamiento: number, signal: AbortSignal) =>
    consultar<{ jornadas: JornadaTrayectoria[]; siguiente: number | null }>(`?limite=50&desplazamiento=${desplazamiento}`, token, signal),
  obtener: (token: string, id: string, desplazamiento: number, corte: string | null, signal: AbortSignal) =>
    consultar<DetalleTrayectoria>(`/${encodeURIComponent(id)}?limite=500&desplazamiento=${desplazamiento}`
      + (corte ? `&corteRecepcion=${encodeURIComponent(corte)}` : ''), token, signal),
};
