import { expect, it } from 'vitest';
import { cubreFecha, versionAplicable, fechaHoraLocal } from './vigencia';
it('filtra fechas de servicio con límites inclusivos y vínculos abiertos', () => {
  expect(cubreFecha('2026-05-10','2026-05-10','2026-05-10')).toBe(true);
  expect(cubreFecha('2026-05-10','2026-05-11',null)).toBe(false);
  expect(cubreFecha('2026-05-10','2026-01-01','2026-05-09')).toBe(false);
  expect(cubreFecha('2026-05-10','2026-01-01',null)).toBe(true);
});
it('usa instantes para la versión de ruta sin cambiar coordenadas ni el modelo', () => {
  expect(versionAplicable('2026-05-10T00:00:00Z','2026-05-10T00:00:00Z','2026-05-10T00:00:00Z')).toBe(true);
  expect(versionAplicable('2026-05-10T00:00:00Z','2026-06-01T00:00:00Z',null)).toBe(false);
  expect(new Date(fechaHoraLocal('2026-05-10T12:00:00Z')).toISOString()).toBe('2026-05-10T12:00:00.000Z');
});
