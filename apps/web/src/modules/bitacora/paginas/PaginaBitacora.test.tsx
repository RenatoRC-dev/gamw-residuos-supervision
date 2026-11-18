import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { PaginaBitacora } from './PaginaBitacora';
import { apiBitacora, type EventoBitacora } from '../api/bitacora';

const sesion = vi.hoisted(() => ({ valor: { sesion: { tokenAcceso: 't', usuarioId: 'u' } as { tokenAcceso: string; usuarioId: string } | null, cerrar: () => undefined } }));
vi.mock('../../administracion/identidad/aplicacion/contextoAutenticacion', () => ({ useSesionActual: () => sesion.valor }));

const evento = (extra: Partial<EventoBitacora> = {}): EventoBitacora => ({
  id: 'ADM:1', fuente: 'ACTUACION_ADMINISTRATIVA', ocurridoEn: '2026-11-18T12:00:00Z', accion: 'CUENTA_ACTUALIZAR', atribucion: 'USUARIO_AUTENTICADO',
  actorUsuarioId: 'u1', actorNombreUsuario: 'admin-sintetico', entidadTipo: 'USUARIO', entidadId: 'e1', ...extra,
});
const abrir = () => render(<MemoryRouter><PaginaBitacora /></MemoryRouter>);
beforeEach(() => { sesion.valor.sesion = { tokenAcceso: 't', usuarioId: 'u' }; });

it('sin sesión no consulta y pide autenticarse', () => {
  sesion.valor.sesion = null;
  const buscar = vi.spyOn(apiBitacora, 'buscar');
  abrir();
  expect(screen.getByText('Autentíquese para consultar.')).toBeInTheDocument();
  expect(buscar).not.toHaveBeenCalled();
});

it('rechaza en cliente un intervalo contradictorio sin llamar a la API', async () => {
  const buscar = vi.spyOn(apiBitacora, 'buscar');
  abrir();
  fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-12-31' } });
  fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-01-01' } });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Intervalo temporal contradictorio');
  expect(buscar).not.toHaveBeenCalled();
});

it('informa la ausencia de eventos sin error', async () => {
  vi.spyOn(apiBitacora, 'buscar').mockResolvedValue({ eventos: [], siguiente: null });
  abrir();
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(await screen.findByText(/No hay eventos para esos filtros/)).toBeInTheDocument();
  expect(screen.queryByRole('alert')).toBeNull();
});

it('una denegación del servidor se comunica como falta de autorización y no muestra datos', async () => {
  vi.spyOn(apiBitacora, 'buscar').mockRejectedValue(new Error('No se pudo completar la solicitud (403).'));
  abrir();
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('no está autorizada');
  expect(screen.queryByRole('table')).toBeNull();
});

it('un evento sin usuario se rotula no atribuible y el detalle no muestra texto libre', async () => {
  vi.spyOn(apiBitacora, 'buscar').mockResolvedValue({
    eventos: [evento({ id: 'CMO:1', atribucion: 'OPERADOR_LOCAL_NO_AUTENTICADO', actorUsuarioId: null, actorNombreUsuario: null, accion: 'PERMISO_OTORGADO:X' })], siguiente: null,
  });
  vi.spyOn(apiBitacora, 'detalle').mockResolvedValue({
    evento: evento({ id: 'CMO:1', atribucion: 'OPERADOR_LOCAL_NO_AUTENTICADO', actorUsuarioId: null, actorNombreUsuario: null }), motivoRegistrado: true, motivoLongitud: 42,
    anterior: null, posterior: { activo: true, camposOmitidos: 2 }, notas: ['Evento no atribuible a un usuario autenticado'],
  });
  abrir();
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(await screen.findByText(/No atribuible: provisión local/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Ver detalle' }));
  await waitFor(() => expect(screen.getByText('Sí (42 caracteres)')).toBeInTheDocument());
  expect(screen.getByText(/No conservado por la fuente/)).toBeInTheDocument();
  expect(screen.getByText(/"camposOmitidos": 2/)).toBeInTheDocument();
});
