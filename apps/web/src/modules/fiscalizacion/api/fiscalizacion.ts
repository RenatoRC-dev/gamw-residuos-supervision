import { apiConfig } from '../../../shared/api/config';
import type { JornadaTrayectoria, DetalleTrayectoria } from '../../monitoreo/trayectorias/api/trayectorias';
export interface ContextoFiscal {
  jornada: { id: string; inicioProgramado: string; finProgramado: string | null; rutaVersionId: string; vigente: boolean };
  periodo: { id: string; fechaInicio: string; fechaFin: string }; empresa: { id: string; razonSocial: string };
  ruta: { id: string; codigo: string; nombre: string }; rutaVersion: { id: string; numeroVersion: number };
  asignaciones: { asignacion: { id: string; vigenteDesde: string; vigenteHasta: string | null }; vehiculoId: string; placa: string;
    personaId: string; nombres: string; apellidoPaterno: string | null; apellidoMaterno: string | null }[];
  eventos: { id: string; tipoEvento: string; usuarioId: string | null; capturadoEn: string; recibidoEn: string; asignacionJornadaId: string }[];
  gps: { cantidad: number; ultimaCaptura: string | null; ultimaRecepcion: string | null; ids: string[] | null }; corteEvidenciaEn: string;
}
export interface EvidenciaCampo {
  id: string; nombreOriginal: string; tipoMime: string; origen: string; descripcion: string; capturadaEn: string | null;
  incorporadaEn: string; autorUsuarioId: string; referenciaPolitica: string; sha256: string; tamanoBytes: number;
}
export interface InspeccionCampo {
  id: string; jornadaId: string; inspectorUsuarioId: string; realizadaEn: string; registradaEn: string;
  lugarDescripcion: string | null; latitud: number | null; longitud: number | null; observaciones: string; evidencias: EvidenciaCampo[];
}
export interface CategoriaFiscal { id: string; codigo: string; nombre: string; referenciaAprobacion: string; observacionesObligatorias: boolean }
export interface DecisionFiscal {
  id: string; fiscalizadorUsuarioId: string; registradaEn: string; corteEvidenciaEn: string; observaciones: string;
  resultado: CategoriaFiscal; antecedentes: ContextoFiscal; inspeccionesReferenciadas: string[];
  posicionesNoReferenciadas: number; inspeccionesPosteriores: number;
}
export interface ExpedienteFiscal {
  puedeRegistrarInspeccion: boolean; puedeEmitirResultado: boolean;
  contexto: ContextoFiscal; inspecciones: InspeccionCampo[]; fiscalizacion: DecisionFiscal | null;
  resultadosHabilitados: CategoriaFiscal[]; limitaciones: string;
  politicaEvidencias: { fotografiasHabilitadas: boolean; referenciaPolitica: string; maximoArchivos: number; maximoBytes: number; formatos: string[] };
}
export class ErrorFiscal extends Error {
  constructor(public readonly estado: number) {
    super(estado === 401 ? 'Sesión vencida.' : estado === 403 ? 'Permiso municipal específico requerido.'
      : estado === 409 ? 'Solicitud incompatible, determinación existente o catálogo/política no habilitados. Actualice el expediente antes de reintentar.'
      : estado === 413 ? 'Adjuntos o metadatos demasiado grandes.'
      : 'No se pudo completar la operación. Revise los datos; no se confirma un registro sin respuesta del servidor.');
  }
}
async function solicitar<T>(token: string, ruta: string, signal: AbortSignal, cuerpo?: unknown): Promise<T> {
  const multipart = cuerpo instanceof FormData;
  const r = await fetch(`${apiConfig.baseUrl}/fiscalizacion/jornadas${ruta}`, {
    method: cuerpo === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`,
      ...(cuerpo === undefined || multipart ? {} : { 'Content-Type': 'application/json' }) },
    body: cuerpo === undefined ? undefined : multipart ? cuerpo : JSON.stringify(cuerpo), signal,
    credentials: 'omit', cache: 'no-store', redirect: 'error',
  });
  if (!r.ok) throw new ErrorFiscal(r.status);
  return r.json() as Promise<T>;
}
export const fiscalizacionApi = {
  jornadas: (t: string, d: number, s: AbortSignal) => solicitar<{ jornadas: JornadaTrayectoria[]; siguiente: number | null }>(t, `?desplazamiento=${d}`, s),
  expediente: (t: string, id: string, s: AbortSignal) => solicitar<ExpedienteFiscal>(t, `/${id}`, s),
  gps: (t: string, id: string, d: number, corte: string | null, s: AbortSignal) => solicitar<DetalleTrayectoria>(t,
    `/${id}/gps?desplazamiento=${d}${corte ? `&corteRecepcion=${encodeURIComponent(corte)}` : ''}`, s),
  inspeccionar: (t: string, id: string, datos: FormData, s: AbortSignal) => solicitar<InspeccionCampo>(t, `/${id}/inspecciones`, s, datos),
  emitir: (t: string, id: string, datos: unknown, s: AbortSignal) => solicitar<DecisionFiscal>(t, `/${id}/resultado`, s, datos),
  async descargar(t: string, jornada: string, evidencia: EvidenciaCampo, signal: AbortSignal) {
    const r = await fetch(`${apiConfig.baseUrl}/fiscalizacion/jornadas/${jornada}/evidencias/${evidencia.id}`, {
      headers: { Authorization: `Bearer ${t}` }, signal, credentials: 'omit', cache: 'no-store', redirect: 'error',
    });
    if (!r.ok) throw new ErrorFiscal(r.status);
    const blob = await r.blob(); if (signal.aborted) return;
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a'); enlace.href = url; enlace.download = evidencia.nombreOriginal;
    enlace.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
