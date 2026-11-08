import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { SeccionVehiculos } from './SeccionVehiculos';
import { apiVehiculos as api, type Vehiculo, type VinculoVehiculoEmpresa } from '../api/vehiculos';

const vehiculo: Vehiculo = { id: 'vehiculo', placa: '001-SINTETICA', modeloVehiculoId: null, tipoVehiculoId: null, anio: null,
  capacidadCargaKg: null, capacidadVolumenM3: null, activo: true, creadoEn: '', actualizadoEn: '' };
const vinculo: VinculoVehiculoEmpresa = { id: 'vinculo', vehiculoId: vehiculo.id, empresaOperadoraId: 'empresa', fechaInicio: '2026-01-01',
  fechaFin: null, activo: true, creadoEn: '', actualizadoEn: '' };

beforeEach(() => {
  vi.spyOn(api, 'listar').mockResolvedValue([vehiculo]);
  vi.spyOn(api, 'marcas').mockResolvedValue([]);
  vi.spyOn(api, 'modelos').mockResolvedValue([]);
  vi.spyOn(api, 'tipos').mockResolvedValue([]);
  vi.spyOn(api, 'vinculos').mockResolvedValue([]);
});
async function abrir() {
  render(<SeccionVehiculos empresaId="empresa" empresaNombre="Empresa sintética" />);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Crear vehículo' })).toBeEnabled());
}
it('registra un vehículo sin campos opcionales ni asociación directa a empresa', async () => {
  const crear = vi.spyOn(api, 'crear').mockResolvedValue(vehiculo);
  await abrir();
  fireEvent.change(screen.getByLabelText('Placa', { exact: true }), { target: { value: '001-sintetica' } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear vehículo' }));
  await waitFor(() => expect(crear).toHaveBeenCalledWith({ placa: '001-sintetica', modeloVehiculoId: null, tipoVehiculoId: null,
    anio: null, capacidadCargaKg: null, capacidadVolumenM3: null }));
});
it('muestra identidad histórica sin controles para cambiarla y cierra el vínculo', async () => {
  vi.mocked(api.vinculos).mockResolvedValue([vinculo]);
  const cerrar = vi.spyOn(api, 'cerrarVinculo').mockResolvedValue({ ...vinculo, fechaFin: '2027-12-31', activo: false });
  await abrir();
  fireEvent.click(screen.getByRole('button', { name: 'Editar cierre vehicular' }));
  expect(screen.getByText(/Identidad histórica:/)).toHaveTextContent('001-SINTETICA');
  expect(screen.getByText(/Identidad histórica:/)).toHaveTextContent('Empresa sintética');
  expect(screen.queryByLabelText('Inicio del vínculo vehicular')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('Vehículo del vínculo')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Fin del vínculo vehicular'), { target: { value: '2027-12-31' } });
  fireEvent.click(screen.getByRole('checkbox', { name: 'Vínculo vehicular activo' }));
  fireEvent.click(screen.getByRole('button', { name: 'Guardar cierre vehicular' }));
  await waitFor(() => expect(cerrar).toHaveBeenCalledWith('empresa', 'vinculo', { fechaFin: '2027-12-31', activo: false }));
});
it('rechaza capacidad cero sin enviar un registro inválido', async () => {
  const crear = vi.spyOn(api, 'crear');
  await abrir();
  fireEvent.change(screen.getByLabelText('Placa'), { target: { value: 'SINTETICA' } });
  fireEvent.change(screen.getByLabelText('Capacidad de carga (kg)'), { target: { value: '0' } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear vehículo' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('mayores que cero');
  expect(crear).not.toHaveBeenCalled();
});
