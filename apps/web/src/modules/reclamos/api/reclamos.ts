import { apiConfig } from '../../../shared/api/config';

export interface Reclamo { id: string; canalDeclarado: string; recibidoEn: string; descripcion: string; lugarDescripcion: string; referenciasOrigen: string;
  longitud: number | null; latitud: number | null; jornadaId: string | null; usuarioId: string; registradoEn: string; validadoMunicipalmente: boolean }
export interface Categoria { id: string; codigo: string; nombre: string; referenciaAprobacion: string; esValidacion: boolean }
export interface Revision { id: string; usuarioId: string; resultadoId: string | null; resultado: string | null; referenciaAprobacion: string | null; esValidacion: boolean | null; observaciones: string; registradaEn: string }
export interface Relacion { id: string; tipo: 'JORNADA' | 'INCIDENCIA' | 'ACCION_CORRECTIVA' | 'INSPECCION'; referenciaId: string; fundamento: string; usuarioId: string; registradaEn: string }
export interface Anexo { id: string; usuarioId: string; origen: string; descripcion: string; capturadoEn: string | null; registradoEn: string; nombreOriginal: string; tipoMime: string; sha256: string; tamanoBytes: number; referenciaPolitica: string }
export interface Punto { id: string; denominacion: string; descripcion: string; longitud: number; latitud: number; sustentoMunicipal: string; reclamoId: string | null; inspeccionId: string | null; usuarioId: string; registradoEn: string; validadoMunicipalmente: boolean }
export interface DetalleReclamo { reclamo: Reclamo; revisiones: Revision[]; relaciones: Relacion[]; anexos: Anexo[] }
export interface DetallePunto { punto: Punto; observaciones: { id: string; usuarioId: string; observadaEn: string; descripcion: string; inspeccionId: string | null; reclamoId: string | null; registradaEn: string }[];
  validacion: { usuarioId: string; fundamento: string; referenciaPolitica: string; registradaEn: string } | null }
export interface OpcionesReclamos { puedeConsultarReclamos: boolean; puedeRegistrarReclamos: boolean; puedeRevisarReclamos: boolean;
  puedeConsultarPuntos: boolean; puedeRegistrarPuntos: boolean; puedeValidarPuntos: boolean; politicaPuntosHabilitada: boolean;
  referenciaPoliticaPuntos: string; resultados: Categoria[]; anexosHabilitados: boolean; referenciaPoliticaAnexos: string;
  politicaAnexos: { fotografiasHabilitadas: boolean; formatos: string[]; maximoBytes: number }; pendientes: string }
export interface Pagina<T> { registros: T[]; siguiente: number | null }
export class ErrorReclamo extends Error {
  constructor(public readonly estado: number) {
    super(estado === 401 ? 'Sesión vencida.' : estado === 403 ? 'Permiso municipal específico requerido. Un JWT o una función operativa no bastan.'
      : estado === 409 ? 'Referencia incompatible, registro repetido o decisión/política no habilitada. Actualice el historial antes de reintentar.'
        : estado === 413 ? 'Anexo demasiado grande; el reclamo original permanece conservado.'
          : 'Operación no confirmada. Revise datos, referencias y conexión; no se anuncia éxito sin persistencia confirmada.');
  }
}
export async function solicitarReclamos<T>(token: string, ruta: string, signal: AbortSignal, datos?: unknown): Promise<T> {
  const multipart = datos instanceof FormData;
  const r = await fetch(`${apiConfig.baseUrl}${ruta}`, { method: datos === undefined ? 'GET' : 'POST',
    headers: { Authorization: `Bearer ${token}`, ...(datos === undefined || multipart ? {} : { 'Content-Type': 'application/json' }) },
    body: datos === undefined ? undefined : multipart ? datos : JSON.stringify(datos), signal, cache: 'no-store', credentials: 'omit', redirect: 'error' });
  if (!r.ok) throw new ErrorReclamo(r.status);
  return r.json() as Promise<T>;
}
export async function descargarAnexo(token: string, reclamo: string, anexo: Anexo, signal: AbortSignal) {
  const r = await fetch(`${apiConfig.baseUrl}/reclamos-institucionales/${encodeURIComponent(reclamo)}/anexos/${encodeURIComponent(anexo.id)}`, {
    headers: { Authorization: `Bearer ${token}` }, signal, cache: 'no-store', credentials: 'omit', redirect: 'error' });
  if (!r.ok) throw new ErrorReclamo(r.status);
  const blob = await r.blob(); if (signal.aborted) return;
  const url = URL.createObjectURL(blob), a = document.createElement('a'); a.href = url; a.download = anexo.nombreOriginal;
  a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
