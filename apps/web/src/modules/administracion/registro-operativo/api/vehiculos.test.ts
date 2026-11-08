import { describe, expect, it, vi } from 'vitest';
import { apiVehiculos as api } from './vehiculos';

describe('contratos vehiculares CU-06', () => {
  it('envía placa como texto y capacidades separadas sin empresa ni conductor', async () => {
    const datos = { placa: '001-abc', modeloVehiculoId: null, tipoVehiculoId: null, anio: null, capacidadCargaKg: 1050.125, capacidadVolumenM3: 12.345 };
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ...datos, placa: '001-ABC' }) });
    vi.stubGlobal('fetch', fetch);
    expect((await api.crear(datos)).placa).toBe('001-ABC');
    expect(fetch.mock.calls[0][0]).toBe('/api/vehiculos');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(datos);
    expect(fetch.mock.calls[0][1].headers['X-GAMW-Development']).toBe('gamw-desarrollo');
  });
  it('el cierre solo envía fechaFin y activo mediante PUT', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) });
    vi.stubGlobal('fetch', fetch);
    await api.cerrarVinculo('empresa', 'vinculo', { fechaFin: '2027-12-31', activo: false });
    expect(fetch.mock.calls[0][0]).toBe('/api/empresas-operadoras/empresa/vehiculos/vinculo');
    expect(fetch.mock.calls[0][1].method).toBe('PUT');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ fechaFin: '2027-12-31', activo: false });
  });
  it('filtra modelos por la marca y comunica conflictos de unicidad', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => [] });
    vi.stubGlobal('fetch', fetch);
    await api.modelos('marca con espacios');
    expect(fetch.mock.calls[0][0]).toBe('/api/vehiculos/modelos?marcaVehiculoId=marca%20con%20espacios');
    fetch.mockResolvedValue({ ok: false, status: 409, json: async () => ({ detail: 'La placa ya existe.' }) });
    await expect(api.crear({ placa: 'SINTETICA', modeloVehiculoId: null, tipoVehiculoId: null, anio: null, capacidadCargaKg: null, capacidadVolumenM3: null })).rejects.toThrow('La placa ya existe.');
  });
});
