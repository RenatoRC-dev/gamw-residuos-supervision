import { requestJson } from '../../../../shared/api/http';

export interface EventoEjecucionJornada {
  id: string; eventoClienteId: string; jornadaId: string; asignacionJornadaId: string;
  tipoEvento: 'INICIO' | 'FIN'; capturadoEn: string; recibidoEn: string;
}
export interface LecturaEjecucion { jornadaId: string; inicio: EventoEjecucionJornada | null; fin: EventoEjecucionJornada | null }
export const consultarEjecucion = (jornadaId: string, signal?: AbortSignal) =>
  requestJson<LecturaEjecucion>(`/jornadas/${encodeURIComponent(jornadaId)}/ejecucion`, 'GET', undefined, signal);
