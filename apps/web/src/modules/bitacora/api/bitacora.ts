import { requestJson } from '../../../shared/api/http';

export type Atribucion = 'USUARIO_AUTENTICADO' | 'OPERADOR_LOCAL_NO_AUTENTICADO' | 'NO_ATRIBUIBLE';
export interface EventoBitacora {
  id: string; fuente: string; ocurridoEn: string; accion: string; atribucion: Atribucion;
  actorUsuarioId: string | null; actorNombreUsuario: string | null; entidadTipo: string; entidadId: string;
}
export interface PaginaBitacora { eventos: EventoBitacora[]; siguiente: number | null }
export interface DetalleEvento { evento: EventoBitacora; motivoRegistrado: boolean; motivoLongitud: number; anterior: unknown; posterior: unknown; notas: string[] }
export interface FiltrosBitacora { desde: string; hasta: string; actorId: string; accion: string; entidadTipo: string; entidadId: string }
export const ENTIDADES = ['USUARIO', 'JORNADA', 'PERIODO_SERVICIO'] as const;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
/** Validación previa en cliente; el servidor valida de nuevo y es la autoridad. */
export function validarFiltrosBitacora(f: FiltrosBitacora): string {
  if (f.desde && f.hasta && f.desde > f.hasta) return 'Intervalo temporal contradictorio: "desde" es posterior a "hasta".';
  if (f.actorId && !UUID.test(f.actorId.trim())) return 'El identificador de actor no es válido.';
  if (f.entidadId && !UUID.test(f.entidadId.trim())) return 'El identificador de entidad no es válido.';
  if (f.entidadId && !f.entidadTipo) return 'Seleccione el tipo de entidad para filtrar por su identificador.';
  if (f.accion && !/^[A-Za-z_:]{1,100}$/.test(f.accion.trim())) return 'La acción solo admite letras, guion bajo y dos puntos.';
  return '';
}
export const apiBitacora = {
  buscar: (f: FiltrosBitacora, desplazamiento: number, signal?: AbortSignal) => {
    const q = new URLSearchParams();
    (['desde', 'hasta', 'actorId', 'accion', 'entidadTipo', 'entidadId'] as const).forEach(k => { const v = f[k].trim(); if (v) q.set(k, v); });
    if (desplazamiento > 0) q.set('desplazamiento', String(desplazamiento));
    const texto = q.toString();
    return requestJson<PaginaBitacora>(`/bitacora/eventos${texto ? `?${texto}` : ''}`, 'GET', undefined, signal);
  },
  detalle: (id: string, signal?: AbortSignal) => requestJson<DetalleEvento>(`/bitacora/eventos/${encodeURIComponent(id)}`, 'GET', undefined, signal),
};
