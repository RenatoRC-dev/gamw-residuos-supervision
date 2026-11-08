import { describe, expect, it, vi } from 'vitest';
import { apiRutas } from './rutas';
import type { GeometriaLineString } from '../modelo/geometria';

describe('transporte de rutas', () => {
  const geometria: GeometriaLineString = { type: 'LineString', coordinates: [[-10, 20], [-11, 21]] };
  it('envía atributos de negocio en español y conserva GeoJSON estándar', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ ruta: {}, version: {} }) });
    vi.stubGlobal('fetch', fetch);
    await apiRutas.crear({ codigo: 'SINTETICA', nombre: 'Prueba sintética', vigenteDesde: '2026-01-01T00:00:00Z', geometria });
    expect(fetch.mock.calls[0][0]).toBe('/api/rutas');
    expect(fetch.mock.calls[0][1].headers['X-GAMW-Development']).toBe('rutas-poc');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      codigo: 'SINTETICA', nombre: 'Prueba sintética', vigenteDesde: '2026-01-01T00:00:00Z', geometria,
    });
  });
  it('crea una versión mediante el contrato español', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ numeroVersion: 2, geometria }) });
    vi.stubGlobal('fetch', fetch);
    const entrada = { geometria, vigenteDesde: '2026-01-02T00:00:00Z', motivoCambio: 'Prueba sintética' };
    expect((await apiRutas.agregarVersion('test-id', entrada)).numeroVersion).toBe(2);
    expect(fetch.mock.calls[0][0]).toBe('/api/rutas/test-id/versiones');
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(entrada);
  });
  it('valida la versión recuperada antes de representarla en el mapa', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ geometria }) }));
    expect((await apiRutas.obtenerVersion('test-id', 1)).geometria).toEqual(geometria);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ geometria: { type: 'Point', coordinates: [1, 2] } }) }));
    await expect(apiRutas.obtenerVersion('test-id', 1)).rejects.toThrow();
  });
  it('conserva el mensaje de conflicto del servidor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 409, json: async () => ({ detail: 'Código existente.' }) }));
    await expect(apiRutas.listar()).rejects.toThrow('Código existente.');
  });
});
