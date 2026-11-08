import { requestJson } from '../../../../shared/api/http';

export interface PersonaNatural {
  id: string; ci: string; nombres: string; apellidoPaterno: string | null; apellidoMaterno: string | null;
  telefono: string | null; activo: boolean; creadoEn: string; actualizadoEn: string;
}
export interface EmpresaOperadora {
  id: string; nit: string | null; razonSocial: string; nombreComercial: string | null;
  activo: boolean; creadoEn: string; actualizadoEn: string;
}
export interface PeriodoServicio {
  id: string; empresaOperadoraId: string; fechaInicio: string; fechaFin: string;
  activo: boolean; creadoEn: string; actualizadoEn: string;
}
export interface FuncionOperativa { id: string; codigo: string; nombre: string; activo: boolean }
export interface VinculoPersonaEmpresa {
  id: string; personaId: string; empresaOperadoraId: string; fechaInicio: string; fechaFin: string | null;
  activo: boolean; creadoEn: string; actualizadoEn: string;
  persona: PersonaNatural; funcionesOperativas: FuncionOperativa[];
}
export type DatosPersona = Pick<PersonaNatural, 'ci' | 'nombres' | 'apellidoPaterno' | 'apellidoMaterno' | 'telefono'>;
export type DatosEmpresa = Pick<EmpresaOperadora, 'nit' | 'razonSocial' | 'nombreComercial'>;
export type DatosPeriodo = Pick<PeriodoServicio, 'fechaInicio' | 'fechaFin'>;
export interface DatosVinculo { fechaInicio: string; fechaFin: string | null; activo: boolean }
export interface CrearVinculo extends DatosVinculo { funcionOperativaIds: string[] }

const empresaPath = (id: string) => `/empresas-operadoras/${encodeURIComponent(id)}`;
export const apiRegistroOperativo = {
  personas: (signal?: AbortSignal) => requestJson<PersonaNatural[]>('/personas', 'GET', undefined, signal),
  crearPersona: (datos: DatosPersona) => requestJson<PersonaNatural>('/personas', 'POST', datos),
  actualizarPersona: (id: string, datos: DatosPersona) => requestJson<PersonaNatural>(`/personas/${encodeURIComponent(id)}`, 'PUT', datos),
  estadoPersona: (id: string, activo: boolean) => requestJson<PersonaNatural>(`/personas/${encodeURIComponent(id)}/estado`, 'PATCH', { activo }),
  empresas: (signal?: AbortSignal) => requestJson<EmpresaOperadora[]>('/empresas-operadoras', 'GET', undefined, signal),
  crearEmpresa: (datos: DatosEmpresa) => requestJson<EmpresaOperadora>('/empresas-operadoras', 'POST', datos),
  actualizarEmpresa: (id: string, datos: DatosEmpresa) => requestJson<EmpresaOperadora>(empresaPath(id), 'PUT', datos),
  estadoEmpresa: (id: string, activo: boolean) => requestJson<EmpresaOperadora>(`${empresaPath(id)}/estado`, 'PATCH', { activo }),
  periodos: (id: string, signal?: AbortSignal) => requestJson<PeriodoServicio[]>(`${empresaPath(id)}/periodos`, 'GET', undefined, signal),
  crearPeriodo: (id: string, datos: DatosPeriodo) => requestJson<PeriodoServicio>(`${empresaPath(id)}/periodos`, 'POST', datos),
  actualizarPeriodo: (empresaId: string, id: string, datos: DatosPeriodo) => requestJson<PeriodoServicio>(`${empresaPath(empresaId)}/periodos/${encodeURIComponent(id)}`, 'PUT', datos),
  estadoPeriodo: (empresaId: string, id: string, activo: boolean) => requestJson<PeriodoServicio>(`${empresaPath(empresaId)}/periodos/${encodeURIComponent(id)}/estado`, 'PATCH', { activo }),
  personal: (id: string, signal?: AbortSignal) => requestJson<VinculoPersonaEmpresa[]>(`${empresaPath(id)}/personal`, 'GET', undefined, signal),
  crearVinculo: (id: string, personaId: string, datos: CrearVinculo) => requestJson<VinculoPersonaEmpresa>(`${empresaPath(id)}/personal`, 'POST', { personaId, ...datos }),
  actualizarVinculo: (empresaId: string, id: string, datos: DatosVinculo) => requestJson<VinculoPersonaEmpresa>(`${empresaPath(empresaId)}/personal/${encodeURIComponent(id)}`, 'PUT', datos),
  funciones: (signal?: AbortSignal) => requestJson<FuncionOperativa[]>('/empresas-operadoras/funciones-operativas', 'GET', undefined, signal),
};
