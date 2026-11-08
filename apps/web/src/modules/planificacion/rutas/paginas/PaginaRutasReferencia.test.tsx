import { forwardRef, useImperativeHandle } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import { PaginaRutasReferencia } from './PaginaRutasReferencia';
import { apiRutas, type Ruta, type RutaVersion } from '../api/rutas';
import type { GeometriaLineString } from '../modelo/geometria';

const fixture = vi.hoisted(() => ({
  borrador: { type: 'LineString' as const, coordinates: [[-10, 20], [-11, 21]] as [number, number][] },
  retrieved: { type: 'LineString' as const, coordinates: [[-12, 22], [-13, 23]] as [number, number][] },
}));

vi.mock('../componentes/MapaRuta', () => ({
  MapaRuta: forwardRef(function MockMap(props: { alCambiarBorrador(geometria: GeometriaLineString | null): void; persistida: GeometriaLineString | null }, ref) {
    useImperativeHandle(ref, () => ({ limpiarBorrador: () => props.alCambiarBorrador(null), editarGeometria: props.alCambiarBorrador }));
    return <><button onClick={() => props.alCambiarBorrador(fixture.borrador)}>Terminar dibujo sintético</button>
      <output data-testid="geometria-renderizada">{JSON.stringify(props.persistida)}</output></>;
  }),
}));

it('guarda el borrador y redibuja la geometría recuperada mediante GET', async () => {
  const ruta: Ruta = { id: 'test-id', codigo: 'SINTETICA', nombre: 'Prueba sintética', activo: true, creadoEn: '', actualizadoEn: '' };
  const version: RutaVersion = { id: 'version-id', rutaId: ruta.id, numeroVersion: 1, geometria: fixture.retrieved,
    vigenteDesde: '2026-01-01T00:00:00Z', vigenteHasta: null, creadoEn: '', motivoCambio: null };
  vi.spyOn(apiRutas, 'listar').mockResolvedValue([]);
  const create = vi.spyOn(apiRutas, 'crear').mockResolvedValue({ ruta, version: { ...version, geometria: fixture.borrador } });
  vi.spyOn(apiRutas, 'obtener').mockResolvedValue(ruta);
  vi.spyOn(apiRutas, 'versiones').mockResolvedValue([version]);
  const read = vi.spyOn(apiRutas, 'obtenerVersion').mockResolvedValue(version);
  render(<MemoryRouter><PaginaRutasReferencia /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText('Código'), { target: { value: ruta.codigo } });
  fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: ruta.nombre } });
  fireEvent.click(screen.getByText('Terminar dibujo sintético'));
  fireEvent.click(screen.getByRole('button', { name: 'Guardar ruta' }));
  await screen.findByText('Versión guardada y recuperada del servidor.');
  expect(create).toHaveBeenCalledWith(expect.objectContaining({ geometria: fixture.borrador }));
  expect(read).toHaveBeenCalledWith(ruta.id, 1);
  expect(screen.getByTestId('geometria-renderizada')).toHaveTextContent(JSON.stringify(fixture.retrieved));
});
