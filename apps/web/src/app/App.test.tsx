import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { appRoutes } from './router';

describe('technical application shell', () => {
  it('renders the home route and confirms backend liveness', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true, json: async () => ({ status: 'UP' }),
    }));
    render(<RouterProvider router={createMemoryRouter(appRoutes)} />);
    expect(screen.getByRole('heading', { name: 'GAMW · Base técnica' })).toBeInTheDocument();
    expect(await screen.findByText('Backend disponible')).toBeInTheDocument();
  });

  it('shows unavailability when the API request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Network unavailable')));
    render(<RouterProvider router={createMemoryRouter(appRoutes)} />);
    expect(await screen.findByText('Backend no disponible')).toBeInTheDocument();
  });
});
