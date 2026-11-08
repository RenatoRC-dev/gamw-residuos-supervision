import { describe, expect, it, vi } from 'vitest';
import { routesApi } from './routes';
import type { LineStringGeometry } from '../model/geometry';

describe('routes transport', () => {
  const geometry: LineStringGeometry = { type: 'LineString', coordinates: [[-10, 20], [-11, 21]] };
  it('sends a GeoJSON geometry and the explicit development header', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ route: {}, version: {} }) });
    vi.stubGlobal('fetch', fetch);
    await routesApi.create({ code: 'SYNTHETIC', name: 'Prueba sintética', validFrom: '2026-01-01T00:00:00Z', geometry });
    expect(fetch.mock.calls[0][0]).toBe('/api/routes');
    expect(fetch.mock.calls[0][1].headers['X-GAMW-Development']).toBe('route-poc');
    expect(JSON.parse(fetch.mock.calls[0][1].body).geometry).toEqual(geometry);
  });
  it('validates a retrieved version before it reaches the map', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ geometry }) }));
    expect((await routesApi.version('test-id', 1)).geometry).toEqual(geometry);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ geometry: { type: 'Point', coordinates: [1, 2] } }) }));
    await expect(routesApi.version('test-id', 1)).rejects.toThrow();
  });
  it('preserves server conflict feedback', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 409, json: async () => ({ detail: 'Código existente.' }) }));
    await expect(routesApi.list()).rejects.toThrow('Código existente.');
  });
});
