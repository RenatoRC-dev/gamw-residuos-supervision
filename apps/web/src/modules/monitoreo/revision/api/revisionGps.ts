import { apiConfig } from '../../../../shared/api/config';
import type { Geometry, LineString } from 'geojson';
import type { PosicionConocida } from '../../posicion/api/monitoreo';
export type TipoRevision = 'integridad' | 'coincidencias' | 'desvios';
export interface ParametrosRevision {
  huecoSegundos: number; precisionMetros: number; velocidadKmh: number; desvioMetros: number;
  radioDetencionMetros: number; detencionSegundos: number; antiguedadSegundos: number;
}
export interface RevisionGps {
  jornadaId: string; rutaVersionId: string; calculadoEn: string; politica: string; parametros: ParametrosRevision;
  muestrasDisponibles: number; muestrasAnalizadas: number; parcial: boolean; ultimaCaptura: string | null;
  ultimaRecepcion: string | null; estado: string; metadatosUbicacion: string; rutaReferencia: LineString;
  muestras: PosicionConocida[];
  hallazgos: { tipo: string; estado: string; desde: string | null; hasta: string | null;
    capturas: string[]; valor: number; unidad: string; detalle: string; coordenadas: number[] }[];
  referencias: { versionId: string; nombre: string; numeroVersion: number; estado: string; muestras: number;
    observadas: number; primera: string | null; ultima: string | null; distanciaMinimaMetros: number | null;
    limitacion: string; geometria: Geometry }[];
}
export async function revisarGps(token: string, jornada: string, tipo: TipoRevision, parametros: ParametrosRevision, signal: AbortSignal): Promise<RevisionGps> {
  const consulta = new URLSearchParams(Object.entries(parametros).map(([k, v]) => [k, String(v)]));
  const r = await fetch(`${apiConfig.baseUrl}/revision-gps/jornadas/${encodeURIComponent(jornada)}/${tipo}?${consulta}`, {
    headers: { Authorization: `Bearer ${token}` }, signal, credentials: 'omit', redirect: 'error', cache: 'no-store',
  });
  if (!r.ok) throw new Error(r.status === 403 ? 'No dispone del permiso específico para esta revisión.' : r.status === 401
    ? 'Sesión vencida; vuelva a autenticarse.' : 'No se pudo revisar. Compruebe jornada y parámetros; no se ha alterado evidencia.');
  return r.json() as Promise<RevisionGps>;
}
