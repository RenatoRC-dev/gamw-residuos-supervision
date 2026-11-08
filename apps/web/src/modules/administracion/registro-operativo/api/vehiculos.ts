import { requestJson } from '../../../../shared/api/http';

export interface MarcaVehiculo { id: string; nombre: string; activo: boolean }
export interface ModeloVehiculo { id: string; marcaVehiculoId: string; nombre: string; activo: boolean }
export interface TipoVehiculo { id: string; codigo: string; nombre: string; activo: boolean }
export interface Vehiculo {
  id: string; placa: string; modeloVehiculoId: string | null; tipoVehiculoId: string | null;
  anio: number | null; capacidadCargaKg: number | null; capacidadVolumenM3: number | null;
  activo: boolean; creadoEn: string; actualizadoEn: string;
}
export interface VinculoVehiculoEmpresa {
  id: string; vehiculoId: string; empresaOperadoraId: string; fechaInicio: string; fechaFin: string | null;
  activo: boolean; creadoEn: string; actualizadoEn: string;
}
export type DatosVehiculo = Pick<Vehiculo, 'placa' | 'modeloVehiculoId' | 'tipoVehiculoId' | 'anio' | 'capacidadCargaKg' | 'capacidadVolumenM3'>;
export interface CierreVinculoVehiculo { fechaFin: string | null; activo: boolean }
export interface NuevoVinculoVehiculo extends CierreVinculoVehiculo { vehiculoId: string; fechaInicio: string }
const vinculosPath = (empresaId: string) => `/empresas-operadoras/${encodeURIComponent(empresaId)}/vehiculos`;

export const apiVehiculos = {
  listar: (signal?: AbortSignal) => requestJson<Vehiculo[]>('/vehiculos', 'GET', undefined, signal),
  obtener: (id: string) => requestJson<Vehiculo>(`/vehiculos/${encodeURIComponent(id)}`),
  crear: (datos: DatosVehiculo) => requestJson<Vehiculo>('/vehiculos', 'POST', datos),
  actualizar: (id: string, datos: DatosVehiculo) => requestJson<Vehiculo>(`/vehiculos/${encodeURIComponent(id)}`, 'PUT', datos),
  estado: (id: string, activo: boolean) => requestJson<Vehiculo>(`/vehiculos/${encodeURIComponent(id)}/estado`, 'PATCH', { activo }),
  marcas: (signal?: AbortSignal) => requestJson<MarcaVehiculo[]>('/vehiculos/marcas', 'GET', undefined, signal),
  crearMarca: (nombre: string) => requestJson<MarcaVehiculo>('/vehiculos/marcas', 'POST', { nombre }),
  modelos: (marcaVehiculoId?: string, signal?: AbortSignal) => requestJson<ModeloVehiculo[]>(`/vehiculos/modelos${marcaVehiculoId ? `?marcaVehiculoId=${encodeURIComponent(marcaVehiculoId)}` : ''}`, 'GET', undefined, signal),
  crearModelo: (marcaVehiculoId: string, nombre: string) => requestJson<ModeloVehiculo>('/vehiculos/modelos', 'POST', { marcaVehiculoId, nombre }),
  tipos: (signal?: AbortSignal) => requestJson<TipoVehiculo[]>('/vehiculos/tipos', 'GET', undefined, signal),
  crearTipo: (codigo: string, nombre: string) => requestJson<TipoVehiculo>('/vehiculos/tipos', 'POST', { codigo, nombre }),
  vinculos: (empresaId: string, signal?: AbortSignal) => requestJson<VinculoVehiculoEmpresa[]>(vinculosPath(empresaId), 'GET', undefined, signal),
  crearVinculo: (empresaId: string, datos: NuevoVinculoVehiculo) => requestJson<VinculoVehiculoEmpresa>(vinculosPath(empresaId), 'POST', datos),
  cerrarVinculo: (empresaId: string, id: string, datos: CierreVinculoVehiculo) => requestJson<VinculoVehiculoEmpresa>(`${vinculosPath(empresaId)}/${encodeURIComponent(id)}`, 'PUT', datos),
};
