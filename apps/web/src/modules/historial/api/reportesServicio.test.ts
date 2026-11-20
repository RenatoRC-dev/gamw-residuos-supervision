import { describe, expect, it } from 'vitest';
import { validarFiltrosReporte } from './reportesServicio';

const UUID = '11111111-1111-4111-8111-111111111111';

describe('validarFiltrosReporte (CU-30)', () => {
  it('exige ambas fechas y un rango no contradictorio', () => {
    expect(validarFiltrosReporte({ desde: '', hasta: '2026-03-31', periodoId: '', empresaId: '' })).toMatch(/Indique/);
    expect(validarFiltrosReporte({ desde: '2026-03-31', hasta: '2026-03-01', periodoId: '', empresaId: '' })).toMatch(/contradictorio/);
    expect(validarFiltrosReporte({ desde: '2026-03-01', hasta: '2026-03-01', periodoId: '', empresaId: '' })).toBe('');
  });
  it('rechaza identificadores de empresa o periodo mal formados', () => {
    expect(validarFiltrosReporte({ desde: '2026-03-01', hasta: '2026-03-31', periodoId: 'x', empresaId: '' })).toMatch(/periodo/);
    expect(validarFiltrosReporte({ desde: '2026-03-01', hasta: '2026-03-31', periodoId: '', empresaId: 'x' })).toMatch(/empresa/);
    expect(validarFiltrosReporte({ desde: '2026-03-01', hasta: '2026-03-31', periodoId: UUID, empresaId: ` ${UUID} ` })).toBe('');
  });
});
