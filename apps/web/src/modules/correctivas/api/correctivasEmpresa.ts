import { apiConfig } from '../../../shared/api/config';
import { ErrorActuacion, type RespuestaCorrectiva, type VerificacionCorrectiva } from './actuaciones';
import type { EvidenciaCampo } from '../../fiscalizacion/api/fiscalizacion';

export type SoporteEmpresa = Pick<EvidenciaCampo, 'id' | 'nombreOriginal' | 'tipoMime' | 'origen' | 'descripcion'
  | 'capturadaEn' | 'incorporadaEn' | 'sha256' | 'tamanoBytes'> & { inspeccionId: string };

export interface ResumenEmpresa {
  id: string; jornadaId: string; periodoServicioId: string; empresaOperadoraId: string; razonSocial: string;
  inicioProgramado: string; rutaVersionId: string; descripcion: string; creadaEn: string;
}
export interface DetalleEmpresa {
  solicitud: { id: string; jornadaId: string; usuarioId: string; descripcion: string; creadaEn: string };
  contexto: {
    jornada: { id: string; inicioProgramado: string; finProgramado: string | null; rutaVersionId: string };
    periodo: { id: string; fechaInicio: string; fechaFin: string; empresaOperadoraId: string };
    empresa: { id: string; razonSocial: string }; ruta: { codigo: string; nombre: string };
    rutaVersion: { id: string; numeroVersion: number };
  };
  antecedente: { tipo: string; id: string; descripcion: string; fecha: string };
  respuestas: RespuestaCorrectiva[]; verificaciones: VerificacionCorrectiva[];
  evidenciasDisponibles: SoporteEmpresa[]; puedeResponder: boolean;
}
async function solicitar<T>(token: string, ruta: string, signal: AbortSignal, datos?: unknown): Promise<T> {
  const respuesta = await fetch(`${apiConfig.baseUrl}/empresa/correctivas${ruta}`, {
    method: datos === undefined ? 'GET' : 'POST', headers: { Authorization: `Bearer ${token}`,
      ...(datos === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: datos === undefined ? undefined : JSON.stringify(datos), signal, cache: 'no-store', credentials: 'omit', redirect: 'error',
  });
  if (!respuesta.ok) throw new ErrorActuacion(respuesta.status);
  return respuesta.json() as Promise<T>;
}
export const correctivasEmpresaApi = {
  listar: (t: string, d: number, s: AbortSignal) => solicitar<{ acciones: ResumenEmpresa[]; siguiente: number | null }>(t, `?desplazamiento=${d}`, s),
  detalle: (t: string, a: string, s: AbortSignal) => solicitar<DetalleEmpresa>(t, `/${encodeURIComponent(a)}`, s),
  responder: (t: string, a: string, datos: unknown, s: AbortSignal) => solicitar<RespuestaCorrectiva>(t, `/${encodeURIComponent(a)}/respuestas`, s, datos),
  async descargar(t: string, a: string, e: SoporteEmpresa, signal: AbortSignal) {
    const respuesta = await fetch(`${apiConfig.baseUrl}/empresa/correctivas/${encodeURIComponent(a)}/evidencias/${encodeURIComponent(e.id)}`, {
      headers: { Authorization: `Bearer ${t}` }, signal, cache: 'no-store', credentials: 'omit', redirect: 'error',
    });
    if (!respuesta.ok) throw new ErrorActuacion(respuesta.status);
    const blob = await respuesta.blob(); if (signal.aborted) return;
    const url = URL.createObjectURL(blob), enlace = document.createElement('a');
    enlace.href = url; enlace.download = e.nombreOriginal; enlace.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
