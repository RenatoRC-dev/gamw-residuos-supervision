import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import type { RevisionGps } from '../api/revisionGps';

// H-01: una respuesta tardía de una consulta anterior nunca reemplaza el contexto visible (jornada, tipo, umbrales, sesión).
const sesion = vi.hoisted(() => ({ valor: { sesion: { tokenAcceso: 't1', usuarioId: 'u' } as { tokenAcceso: string; usuarioId: string } | null, cerrar: vi.fn() } }));
vi.mock('../../../administracion/identidad/aplicacion/contextoAutenticacion', () => ({ useSesionActual: () => sesion.valor }));
vi.mock('../componentes/MapaRevisionGps', () => ({ MapaRevisionGps: () => <div aria-label="Mapa simulado" /> }));
type Pendiente = { jornada: string; tipo: string; signal: AbortSignal; resolver: (r: RevisionGps) => void; rechazar: (e: unknown) => void };
const pendientes = vi.hoisted(() => [] as Pendiente[]);
vi.mock('../api/revisionGps', async original => {
  const real = await original<typeof import('../api/revisionGps')>();
  return { ...real, revisarGps: (_t: string, jornada: string, tipo: string, _p: unknown, signal: AbortSignal) =>
    new Promise<RevisionGps>((resolver, rechazar) => { pendientes.push({ jornada, tipo, signal, resolver, rechazar }); }) };
});
import { PaginaRevisionGps } from './PaginaRevisionGps';
import { ErrorRevisionGps } from '../api/revisionGps';

const resultado = (jornadaId: string, estado: string): RevisionGps => ({
  jornadaId, rutaVersionId: 'rv', calculadoEn: '2026-11-19T12:00:00Z', politica: 'TECNICA_PROVISIONAL_RECALCULABLE', estado,
  parametros: { huecoSegundos: 120, precisionMetros: 50, velocidadKmh: 120, desvioMetros: 100, radioDetencionMetros: 25, detencionSegundos: 180, antiguedadSegundos: 120 },
  muestrasDisponibles: 0, muestrasAnalizadas: 0, parcial: false, ultimaCaptura: null, ultimaRecepcion: null, metadatosUbicacion: 'NO DISPONIBLES',
  rutaReferencia: { type: 'LineString', coordinates: [] }, muestras: [], hallazgos: [], referencias: [],
});
const abrir = () => render(<MemoryRouter><PaginaRevisionGps /></MemoryRouter>);
const enviar = (jornada: string) => {
  fireEvent.change(screen.getByLabelText('Jornada UUID'), { target: { value: jornada } });
  fireEvent.click(screen.getByRole('button', { name: 'Revisar muestras recibidas' }));
};
beforeEach(() => { pendientes.length = 0; sesion.valor.sesion = { tokenAcceso: 't1', usuarioId: 'u' }; sesion.valor.cerrar.mockReset(); });

it('cambiar la jornada cancela la consulta pendiente y su respuesta tardía no aparece', async () => {
  abrir();
  enviar('jornada-A');
  expect(pendientes).toHaveLength(1);
  fireEvent.change(screen.getByLabelText('Jornada UUID'), { target: { value: 'jornada-B' } });
  expect(pendientes[0].signal.aborted).toBe(true);
  await act(async () => pendientes[0].resolver(resultado('jornada-A', 'ESTADO_DE_A')));
  expect(screen.queryByText('ESTADO_DE_A')).toBeNull();
  expect(screen.queryByText(/Calculando revisión/)).toBeNull();
});

it('cambiar el tipo o un umbral invalida el resultado mostrado y cancela la consulta en curso', async () => {
  abrir();
  enviar('jornada-A');
  await act(async () => pendientes[0].resolver(resultado('jornada-A', 'RESULTADO_INTEGRIDAD')));
  expect(await screen.findByText('RESULTADO_INTEGRIDAD')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Revisión'), { target: { value: 'coincidencias' } });
  expect(screen.queryByText('RESULTADO_INTEGRIDAD')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Revisar muestras recibidas' }));
  expect(pendientes[1].tipo).toBe('coincidencias');
  fireEvent.change(screen.getByLabelText('Separación temporal (s)'), { target: { value: '60' } });
  expect(pendientes[1].signal.aborted).toBe(true);
  await act(async () => pendientes[1].resolver(resultado('jornada-A', 'RESULTADO_OBSOLETO')));
  expect(screen.queryByText('RESULTADO_OBSOLETO')).toBeNull();
});

it('entre dos envíos rápidos solo se muestra el último, aunque el primero responda después', async () => {
  abrir();
  enviar('jornada-A');
  enviar('jornada-B');
  expect(pendientes[0].signal.aborted).toBe(true);
  await act(async () => pendientes[1].resolver(resultado('jornada-B', 'ESTADO_DE_B')));
  await act(async () => pendientes[0].resolver(resultado('jornada-A', 'ESTADO_DE_A')));
  expect(screen.getByText('ESTADO_DE_B')).toBeInTheDocument();
  expect(screen.queryByText('ESTADO_DE_A')).toBeNull();
  expect(screen.getByText('Resultado de la consulta vigente: revisión integridad · jornada jornada-B.')).toBeInTheDocument();
});

it('un error tardío de una consulta obsoleta no se muestra', async () => {
  abrir();
  enviar('jornada-A');
  fireEvent.change(screen.getByLabelText('Jornada UUID'), { target: { value: 'jornada-B' } });
  await act(async () => pendientes[0].rechazar(new ErrorRevisionGps('No dispone del permiso específico para esta revisión.', 403)));
  expect(screen.queryByRole('alert')).toBeNull();
});

it('401 vigente cierra la sesión; 403 informa sin cerrarla', async () => {
  abrir();
  enviar('jornada-A');
  await act(async () => pendientes[0].rechazar(new ErrorRevisionGps('No dispone del permiso específico para esta revisión.', 403)));
  expect(await screen.findByRole('alert')).toHaveTextContent('permiso específico');
  expect(sesion.valor.cerrar).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Revisar muestras recibidas' }));
  await act(async () => pendientes[1].rechazar(new ErrorRevisionGps('Sesión vencida; vuelva a autenticarse.', 401)));
  await waitFor(() => expect(sesion.valor.cerrar).toHaveBeenCalledWith('Sesión vencida. Vuelva a autenticarse.'));
});

it('desmontar cancela la consulta pendiente', () => {
  const { unmount } = abrir();
  enviar('jornada-A');
  unmount();
  expect(pendientes[0].signal.aborted).toBe(true);
});
