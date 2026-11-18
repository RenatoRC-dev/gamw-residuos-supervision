import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { expect, it, vi } from 'vitest';
import { PaginaHistorialServicio } from './PaginaHistorialServicio';
import { apiHistorialServicio, type DetalleHistorico, type ResumenJornadaHistorica } from '../api/historialServicio';

vi.mock('../../administracion/identidad/aplicacion/contextoAutenticacion', () => ({
  useSesionActual: () => ({ sesion: { tokenAcceso: 't', usuarioId: 'u' }, cerrar: () => undefined }),
}));

const resumen: ResumenJornadaHistorica = {
  id: 'j1', inicioProgramado: '2026-11-18T12:00:00Z', finProgramado: null, vigente: false, antecedenteId: null, sucesoraId: 'j2', motivoReprogramacion: null,
  periodo: { id: 'p1', fechaInicio: '2026-01-01', fechaFin: '2026-12-31' }, empresa: { id: 'e1', razonSocial: 'Empresa sintética' },
  ruta: { rutaId: 'r1', codigo: 'R', nombre: 'Ruta sintética', versionId: 'v1', numeroVersion: 1, vigenteDesde: '2025-01-01T00:00:00Z', vigenteHasta: null }, cantidadAsignaciones: 0,
};
const abrir = () => render(<MemoryRouter><PaginaHistorialServicio /></MemoryRouter>);

it('rechaza identificadores y rangos inválidos sin consultar', async () => {
  const buscar = vi.spyOn(apiHistorialServicio, 'buscar');
  abrir();
  fireEvent.change(screen.getByLabelText('Ruta (ID)'), { target: { value: 'no-uuid' } });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('identificador de ruta no es válido');
  fireEvent.change(screen.getByLabelText('Ruta (ID)'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-12-31' } });
  fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-01-01' } });
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Rango de fechas contradictorio');
  expect(buscar).not.toHaveBeenCalled();
});

it('lista vacía se informa sin error y sin crear registros', async () => {
  vi.spyOn(apiHistorialServicio, 'buscar').mockResolvedValue({ jornadas: [], siguiente: null });
  abrir();
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(await screen.findByText(/no encontró antecedentes/)).toBeInTheDocument();
});

it('rotula nombre actual del maestro frente a identidad histórica y distingue sin acceso de sin registros', async () => {
  vi.spyOn(apiHistorialServicio, 'buscar').mockResolvedValue({ jornadas: [resumen], siguiente: null });
  const detalle: DetalleHistorico = {
    jornada: resumen, cadena: [resumen], asignaciones: [], inconsistencias: ['La jornada no registra asignaciones de recursos.'],
    enlaces: [
      { modulo: 'Trayectoria GPS', ruta: '/trayectorias?jornada=j1', estado: 'ACCESO_NO_PERMITIDO', cantidad: null, nota: 'sin permiso' },
      { modulo: 'Inspecciones', ruta: '/fiscalizacion?jornada=j1', estado: 'SIN_REGISTROS', cantidad: 0, nota: 'vacío' },
    ],
  };
  vi.spyOn(apiHistorialServicio, 'detalle').mockResolvedValue(detalle);
  abrir();
  expect(screen.getByText(/registrados hoy en el maestro/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(await screen.findByText('Empresa sintética')).toBeInTheDocument();
  expect(screen.getByRole('columnheader', { name: /nombre actual del maestro/ })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Ver detalle' }));
  await waitFor(() => expect(screen.getByText(/identidad histórica \(ID\)/)).toBeInTheDocument());
  expect(screen.getByText('e1')).toBeInTheDocument();
  expect(screen.getByText('Acceso no permitido (se desconoce si existen)')).toBeInTheDocument();
  expect(screen.getByText(/^Sin registros/)).toBeInTheDocument();
  expect(screen.getByText(/no registra asignaciones/)).toBeInTheDocument();
  // Volver conserva los resultados y los filtros sin repetir la consulta.
  fireEvent.click(screen.getByRole('button', { name: /Volver a resultados/ }));
  expect(await screen.findByText('Empresa sintética')).toBeInTheDocument();
  expect(apiHistorialServicio.buscar).toHaveBeenCalledTimes(1);
});
