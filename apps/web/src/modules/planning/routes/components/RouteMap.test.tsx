import { render, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { RouteMap } from './RouteMap';
import type { LineStringGeometry } from '../model/geometry';

const map = vi.hoisted(() => ({ displayPersisted: vi.fn(), drawLine: vi.fn(), editLine: vi.fn(), clearDraft: vi.fn(), destroy: vi.fn() }));
vi.mock('../../../../shared/map/routeMapAdapter', () => ({
  createRouteMap: (_container: HTMLElement, callbacks: { ready(): void }) => { callbacks.ready(); return map; },
}));

it('redraws the persisted GeoJSON and releases the map on unmount', async () => {
  const geometry: LineStringGeometry = { type: 'LineString', coordinates: [[-10, 20], [-11, 21]] };
  const { unmount } = render(<RouteMap persisted={geometry} onDraft={vi.fn()} disabled={false} />);
  await waitFor(() => expect(map.displayPersisted).toHaveBeenCalledWith(geometry));
  unmount();
  expect(map.destroy).toHaveBeenCalledOnce();
});
