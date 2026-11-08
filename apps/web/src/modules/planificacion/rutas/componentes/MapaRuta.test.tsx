import { render, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { MapaRuta } from './MapaRuta';
import type { GeometriaLineString } from '../modelo/geometria';

const map = vi.hoisted(() => ({ mostrarPersistida: vi.fn(), dibujarLinea: vi.fn(), editarLinea: vi.fn(), limpiarBorrador: vi.fn(), destruir: vi.fn() }));
vi.mock('../../../../shared/map/adaptadorMapaRuta', () => ({
  crearMapaRuta: (_container: HTMLElement, callbacks: { ready(): void }) => { callbacks.ready(); return map; },
}));

it('redraws the persistida GeoJSON and releases the map on unmount', async () => {
  const geometria: GeometriaLineString = { type: 'LineString', coordinates: [[-10, 20], [-11, 21]] };
  const { unmount } = render(<MapaRuta persistida={geometria} alCambiarBorrador={vi.fn()} disabled={false} />);
  await waitFor(() => expect(map.mostrarPersistida).toHaveBeenCalledWith(geometria));
  unmount();
  expect(map.destruir).toHaveBeenCalledOnce();
});
