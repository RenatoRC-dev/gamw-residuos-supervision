import { expect, it, vi } from 'vitest';
import { apiJornadas as api } from './jornadas';

it('programa con periodo y versión, sin empresa o recursos directos', async () => {
  const datos = { periodoServicioId: 'periodo', rutaVersionId: 'version', inicioProgramado: '2026-05-10T12:00:00Z', finProgramado: null };
  const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) });
  vi.stubGlobal('fetch', fetch);
  await api.crear(datos);
  expect(fetch.mock.calls[0][0]).toBe('/api/jornadas');
  expect(fetch.mock.calls[0][1].method).toBe('POST');
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual(datos);
});
it('reprograma y reasigna por acciones explícitas y conserva motivos', async () => {
  const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) });
  vi.stubGlobal('fetch', fetch);
  await api.reprogramar('jornada', { periodoServicioId: 'periodo', rutaVersionId: 'version', inicioProgramado: '2026-05-11T12:00:00Z', finProgramado: null }, 'Cambio');
  expect(fetch.mock.calls[0][0]).toBe('/api/jornadas/jornada/reprogramaciones');
  expect(JSON.parse(fetch.mock.calls[0][1].body).motivoReprogramacion).toBe('Cambio');
  await api.reasignar('jornada', { vinculoVehiculoEmpresaId: 'vv', vinculoPersonaEmpresaId: 'vp' }, 'Cambio de recurso');
  expect(fetch.mock.calls[1][0]).toBe('/api/jornadas/jornada/reasignaciones');
  expect(fetch.mock.calls[1][1].method).toBe('POST');
  expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ vinculoVehiculoEmpresaId: 'vv', vinculoPersonaEmpresaId: 'vp', motivoCambio: 'Cambio de recurso' });
});
