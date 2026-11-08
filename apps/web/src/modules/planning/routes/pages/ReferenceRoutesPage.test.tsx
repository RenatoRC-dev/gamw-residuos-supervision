import { forwardRef, useImperativeHandle } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import { ReferenceRoutesPage } from './ReferenceRoutesPage';
import { routesApi, type ReferenceRoute, type RouteVersion } from '../api/routes';
import type { LineStringGeometry } from '../model/geometry';

const fixture = vi.hoisted(() => ({
  draft: { type: 'LineString' as const, coordinates: [[-10, 20], [-11, 21]] as [number, number][] },
  retrieved: { type: 'LineString' as const, coordinates: [[-12, 22], [-13, 23]] as [number, number][] },
}));

vi.mock('../components/RouteMap', () => ({
  RouteMap: forwardRef(function MockMap(props: { onDraft(geometry: LineStringGeometry | null): void; persisted: LineStringGeometry | null }, ref) {
    useImperativeHandle(ref, () => ({ clearDraft: () => props.onDraft(null), editGeometry: props.onDraft }));
    return <><button onClick={() => props.onDraft(fixture.draft)}>Terminar dibujo sintético</button>
      <output data-testid="rendered-geometry">{JSON.stringify(props.persisted)}</output></>;
  }),
}));

it('saves the draft then redraws the fresh GET response, rather than trusting the POST copy', async () => {
  const route: ReferenceRoute = { id: 'test-id', code: 'SYNTHETIC', name: 'Prueba sintética', active: true, createdAt: '', updatedAt: '' };
  const version: RouteVersion = { id: 'version-id', routeId: route.id, versionNumber: 1, geometry: fixture.retrieved,
    validFrom: '2026-01-01T00:00:00Z', validTo: null, createdAt: '', changeReason: null };
  vi.spyOn(routesApi, 'list').mockResolvedValue([]);
  const create = vi.spyOn(routesApi, 'create').mockResolvedValue({ route, version: { ...version, geometry: fixture.draft } });
  vi.spyOn(routesApi, 'get').mockResolvedValue(route);
  vi.spyOn(routesApi, 'versions').mockResolvedValue([version]);
  const read = vi.spyOn(routesApi, 'version').mockResolvedValue(version);
  render(<MemoryRouter><ReferenceRoutesPage /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('Código'), { target: { value: route.code } });
  fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: route.name } });
  fireEvent.click(screen.getByText('Terminar dibujo sintético'));
  fireEvent.click(screen.getByRole('button', { name: 'Guardar ruta' }));
  await screen.findByText('Versión guardada y recuperada del servidor.');
  expect(create).toHaveBeenCalledWith(expect.objectContaining({ geometry: fixture.draft }));
  expect(read).toHaveBeenCalledWith(route.id, 1);
  expect(screen.getByTestId('rendered-geometry')).toHaveTextContent(JSON.stringify(fixture.retrieved));
});
