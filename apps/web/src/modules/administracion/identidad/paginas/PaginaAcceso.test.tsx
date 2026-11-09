import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import { PaginaAcceso } from './PaginaAcceso';
import { ProveedorAutenticacion } from '../aplicacion/contextoAutenticacion';

it('formulario seguro, identidad y cierre; nunca renderiza JWT ni conserva contraseña', async () => {
  const identidad = { usuarioId: 'A', personaNaturalId: 'PA', nombreUsuario: 'cuenta-sintetica' };
  const token = 'jwt-sintetico-no-renderizar';
  vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string) => ({ ok: true,
    json: async () => url.endsWith('login') ? { ...identidad, tokenAcceso: token, expiraEn: new Date(Date.now() + 60000).toISOString() } : identidad })));
  const { container } = render(<MemoryRouter><ProveedorAutenticacion><PaginaAcceso /></ProveedorAutenticacion></MemoryRouter>);
  expect(screen.getByLabelText('Contraseña')).toHaveAttribute('type', 'password');
  expect(screen.getByRole('form', { name: 'Inicio de sesión' })).toHaveAttribute('method', 'post');
  fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'cuenta-sintetica' } });
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'clave-sintetica' } });
  fireEvent.submit(screen.getByRole('form', { name: 'Inicio de sesión' }));
  expect(await screen.findByRole('heading', { name: 'Usuario autenticado' })).toBeInTheDocument();
  expect(container.innerHTML).not.toContain(token); expect(container.innerHTML).not.toContain('clave-sintetica');
  fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
  await waitFor(() => expect(screen.getByLabelText('Contraseña')).toHaveValue(''));
});
