import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { PaginaJornadas } from './PaginaJornadas';
import { apiJornadas as api, type DetalleJornada, type AsignacionJornada } from '../api/jornadas';
import { apiRutas } from '../../rutas/api/rutas';
import { apiRegistroOperativo } from '../../../administracion/registro-operativo/api/registroOperativo';
import { apiVehiculos } from '../../../administracion/registro-operativo/api/vehiculos';
vi.mock('../../../ejecucion/jornadas/componentes/ResumenEjecucion', () => ({
  ResumenEjecucion: () => <section aria-label="Evidencia operativa">Consulta de evidencia</section>,
}));

const detalle: DetalleJornada = {
  jornada: { id: 'j1', periodoServicioId: 'periodo', rutaVersionId: 'rv1', inicioProgramado: '2026-05-10T12:00:00Z', finProgramado: null,
    jornadaAntecedenteId: null, motivoReprogramacion: null, vigente: true, creadoEn: '', actualizadoEn: '' },
  periodoServicio: { id: 'periodo', empresaOperadoraId: 'empresa', fechaInicio: '2026-01-01', fechaFin: '2026-12-31', activo: true },
  empresaOperadora: { id: 'empresa', razonSocial: 'Empresa sintética', activo: true },
  rutaVersion: { id: 'rv1', rutaId: 'ruta', nombreRuta: 'Ruta sintética', numeroVersion: 1, vigenteDesde: '2025-01-01T00:00:00Z', vigenteHasta: null },
  asignacionActual: null, fechaServicio: '2026-05-10',
};
const asignacion: AsignacionJornada = { id: 'a1', jornadaId: 'j1', vinculoVehiculoEmpresaId: 'vv', vinculoPersonaEmpresaId: 'vp',
  vigenteDesde: '2026-01-01T00:00:00Z', vigenteHasta: null, motivoCambio: null, creadoEn: '',
  vehiculo: { id: 'vehiculo', placa: 'SINTETICA' }, responsable: { id: 'persona', ci: 'SINTETICA', nombres: 'Responsable sintético', apellidoPaterno: null, apellidoMaterno: null } };
beforeEach(() => {
  vi.spyOn(api, 'periodos').mockResolvedValue([{ id: 'periodo', empresaOperadoraId: 'empresa', razonSocial: 'Empresa sintética', fechaInicio: '2026-01-01', fechaFin: '2026-12-31', activo: true, empresaActiva: true }]);
  vi.spyOn(api, 'listar').mockResolvedValue([detalle]);
  vi.spyOn(api, 'obtener').mockResolvedValue(detalle);
  vi.spyOn(api, 'historial').mockResolvedValue([]);
  vi.spyOn(apiRutas, 'listar').mockResolvedValue([{ id: 'ruta', codigo: 'SINTETICA', nombre: 'Ruta sintética', activo: true, creadoEn: '', actualizadoEn: '' }]);
  vi.spyOn(apiRutas, 'versiones').mockResolvedValue([{ id: 'rv1', rutaId: 'ruta', numeroVersion: 1, geometria: { type: 'LineString', coordinates: [[-10,20],[-11,21]] },
    vigenteDesde: '2025-01-01T00:00:00Z', vigenteHasta: null, motivoCambio: null, creadoEn: '' }]);
  vi.spyOn(apiVehiculos, 'vinculos').mockResolvedValue([{ id: 'vv', vehiculoId: 'vehiculo', empresaOperadoraId: 'empresa', fechaInicio: '2026-01-01', fechaFin: null, activo: true, creadoEn: '', actualizadoEn: '' }]);
  vi.spyOn(apiVehiculos, 'listar').mockResolvedValue([{ id: 'vehiculo', placa: 'SINTETICA', modeloVehiculoId: null, tipoVehiculoId: null, anio: null, capacidadCargaKg: null, capacidadVolumenM3: null, activo: true, creadoEn: '', actualizadoEn: '' }]);
  vi.spyOn(apiRegistroOperativo, 'personal').mockResolvedValue([{ id: 'vp', personaId: 'persona', empresaOperadoraId: 'empresa', fechaInicio: '2026-01-01', fechaFin: null, activo: true, creadoEn: '', actualizadoEn: '',
    persona: { id: 'persona', ci: 'SINTETICA', nombres: 'Responsable sintético', apellidoPaterno: null, apellidoMaterno: null, telefono: null, activo: true, creadoEn: '', actualizadoEn: '' },
    funcionesOperativas: [{ id: 'funcion', codigo: 'SUPERVISOR_EMPRESA', nombre: 'Supervisor de empresa', activo: true }] }]);
});
async function abrir() {
  render(<MemoryRouter><PaginaJornadas /></MemoryRouter>);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Programar jornada' })).toBeEnabled());
}
async function formulario() {
  fireEvent.change(screen.getByLabelText('Periodo de servicio'), { target: { value: 'periodo' } });
  fireEvent.change(screen.getByLabelText('Ruta de referencia'), { target: { value: 'ruta' } });
  fireEvent.change(screen.getByLabelText('Inicio programado'), { target: { value: '2026-05-10T12:00:00' } });
  await waitFor(() => expect(screen.getByLabelText('Versión de ruta')).toBeEnabled());
  fireEvent.change(screen.getByLabelText('Versión de ruta'), { target: { value: 'rv1' } });
}
it('deriva empresa y programa por periodo y versión sin edición destructiva', async () => {
  const crear = vi.spyOn(api, 'crear').mockResolvedValue(detalle);
  await abrir(); await formulario();
  expect(screen.getByLabelText('Empresa del periodo')).toHaveTextContent('Empresa sintética');
  fireEvent.click(screen.getByRole('button', { name: 'Programar jornada' }));
  await waitFor(() => expect(crear).toHaveBeenCalledWith(expect.objectContaining({ periodoServicioId: 'periodo', rutaVersionId: 'rv1', finProgramado: null })));
  expect(crear.mock.calls[0][0]).not.toHaveProperty('empresaOperadoraId');
  expect(screen.queryByRole('button', { name: 'Editar jornada' })).not.toBeInTheDocument();
});
it('ofrece supervisor y asigna vínculos históricos sin imponer CONDUCTOR', async () => {
  const asignar = vi.spyOn(api, 'asignar').mockResolvedValue(asignacion);
  await abrir(); fireEvent.click(screen.getByRole('button', { name: 'Consultar jornada' }));
  fireEvent.change(await screen.findByLabelText('Vínculo de vehículo'), { target: { value: 'vv' } });
  expect(screen.getByRole('option', { name: /Supervisor de empresa/ })).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Vínculo de responsable'), { target: { value: 'vp' } });
  fireEvent.click(screen.getByRole('button', { name: 'Asignar recursos' }));
  await waitFor(() => expect(asignar).toHaveBeenCalledWith('j1', { vinculoVehiculoEmpresaId: 'vv', vinculoPersonaEmpresaId: 'vp' }));
});
it('reprograma explícitamente creando otra jornada y muestra historial', async () => {
  const nueva = { ...detalle, jornada: { ...detalle.jornada, id: 'j2', jornadaAntecedenteId: 'j1', motivoReprogramacion: 'Cambio sintético' } };
  vi.spyOn(api, 'reprogramar').mockResolvedValue(nueva);
  vi.mocked(api.obtener).mockImplementation(async id => id === 'j2' ? nueva : detalle);
  await abrir(); fireEvent.click(screen.getByRole('button', { name: 'Consultar jornada' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Reprogramar jornada' }));
  expect(screen.getByText(/se creará otra sin copiar recursos/)).toBeInTheDocument();
  await waitFor(() => expect(screen.getByLabelText('Versión de ruta')).toBeEnabled());
  fireEvent.change(screen.getByLabelText('Motivo de reprogramación'), { target: { value: 'Cambio sintético' } });
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar reprogramación' }));
  await waitFor(() => expect(api.reprogramar).toHaveBeenCalledWith('j1', expect.anything(), 'Cambio sintético'));
  expect(await screen.findByRole('button', { name: 'Consultar antecedente' })).toBeInTheDocument();
});
it('reasigna con motivo y conserva el historial visible', async () => {
  vi.mocked(api.obtener).mockResolvedValue({ ...detalle, asignacionActual: asignacion });
  vi.mocked(api.historial).mockResolvedValue([asignacion]);
  const reasignar = vi.spyOn(api, 'reasignar').mockResolvedValue({ ...asignacion, id: 'a2', motivoCambio: 'Cambio sintético' });
  await abrir(); fireEvent.click(screen.getByRole('button', { name: 'Consultar jornada' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Reasignar recursos' }));
  fireEvent.change(screen.getByLabelText('Vínculo de vehículo'), { target: { value: 'vv' } });
  fireEvent.change(screen.getByLabelText('Vínculo de responsable'), { target: { value: 'vp' } });
  fireEvent.change(screen.getByLabelText('Motivo de reasignación'), { target: { value: 'Cambio sintético' } });
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar reasignación' }));
  await waitFor(() => expect(reasignar).toHaveBeenCalledWith('j1', { vinculoVehiculoEmpresaId: 'vv', vinculoPersonaEmpresaId: 'vp' }, 'Cambio sintético'));
  expect(within(screen.getByRole('list')).getByText(/Asignación inicial/)).toBeInTheDocument();
});
it('muestra el rechazo del servidor conservando los datos del formulario', async () => {
  vi.spyOn(api, 'crear').mockRejectedValue(new Error('La jornada está fuera del periodo de servicio.'));
  await abrir(); await formulario();
  fireEvent.click(screen.getByRole('button', { name: 'Programar jornada' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('fuera del periodo');
  expect(screen.getByLabelText('Periodo de servicio')).toHaveValue('periodo');
});
