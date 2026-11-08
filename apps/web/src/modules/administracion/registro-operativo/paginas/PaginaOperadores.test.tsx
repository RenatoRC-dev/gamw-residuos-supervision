import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { PaginaOperadores } from './PaginaOperadores';
import { apiRegistroOperativo as api, type EmpresaOperadora, type PersonaNatural } from '../api/registroOperativo';

vi.mock('../componentes/SeccionVehiculos', () => ({
  SeccionVehiculos: () => <section aria-label="Sección vehicular">Registro vehicular</section>,
}));

const empresa: EmpresaOperadora = { id: 'empresa', nit: null, razonSocial: 'Empresa sintética', nombreComercial: null, activo: true, creadoEn: '', actualizadoEn: '' };
const persona: PersonaNatural = { id: 'persona', ci: '00123456-1A', nombres: 'Persona sintética', apellidoPaterno: null, apellidoMaterno: null, telefono: null,
  activo: true, creadoEn: '', actualizadoEn: '' };

beforeEach(() => {
  vi.spyOn(api, 'empresas').mockResolvedValue([empresa]);
  vi.spyOn(api, 'personas').mockResolvedValue([]);
  vi.spyOn(api, 'funciones').mockResolvedValue([{ id: 'conductor', codigo: 'CONDUCTOR', nombre: 'Conductor', activo: true }]);
  vi.spyOn(api, 'periodos').mockResolvedValue([]);
  vi.spyOn(api, 'personal').mockResolvedValue([]);
});
async function abrir() {
  render(<MemoryRouter><PaginaOperadores /></MemoryRouter>);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Crear persona' })).toBeEnabled());
}
it('crea una persona independiente y la selecciona para asociarla', async () => {
  const crear = vi.spyOn(api, 'crearPersona').mockImplementation(async () => {
    vi.mocked(api.personas).mockResolvedValue([persona]); return persona;
  });
  await abrir();
  fireEvent.change(screen.getByLabelText('CI', { exact: true }), { target: { value: persona.ci } });
  fireEvent.change(screen.getByLabelText('Nombres', { exact: true }), { target: { value: persona.nombres } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear persona' }));
  await waitFor(() => expect(crear).toHaveBeenCalledWith({ ci: persona.ci, nombres: persona.nombres, apellidoPaterno: null, apellidoMaterno: null, telefono: null }));
  await waitFor(() => expect(screen.getByLabelText('Persona existente')).toHaveValue('persona'));
});
it('muestra conflictos sin perder los datos introducidos', async () => {
  vi.spyOn(api, 'crearEmpresa').mockRejectedValue(new Error('NIT duplicado.'));
  await abrir();
  fireEvent.change(screen.getByLabelText('Razón social'), { target: { value: 'Prueba' } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear empresa' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('NIT duplicado.');
  expect(screen.getByLabelText('Razón social')).toHaveValue('Prueba');
});
it('selecciona empresa y registra un periodo sin atributos especulativos', async () => {
  const crear = vi.spyOn(api, 'crearPeriodo').mockResolvedValue({ id: 'periodo', empresaOperadoraId: empresa.id, fechaInicio: '2026-01-01', fechaFin: '2026-12-31', activo: true, creadoEn: '', actualizadoEn: '' });
  await abrir();
  fireEvent.click(screen.getByRole('button', { name: 'Seleccionar empresa' }));
  fireEvent.change(await screen.findByLabelText('Inicio del periodo'), { target: { value: '2026-01-01' } });
  fireEvent.change(screen.getByLabelText('Fin del periodo'), { target: { value: '2026-12-31' } });
  fireEvent.click(screen.getByRole('button', { name: 'Crear periodo' }));
  await waitFor(() => expect(crear).toHaveBeenCalledWith('empresa', { fechaInicio: '2026-01-01', fechaFin: '2026-12-31' }));
});

it('exige una función al crear y no envía el vínculo vacío', async () => {
  vi.mocked(api.personas).mockResolvedValue([persona]);
  const crear = vi.spyOn(api, 'crearVinculo');
  await abrir();
  fireEvent.click(screen.getByRole('button', { name: 'Seleccionar empresa' }));
  fireEvent.change(await screen.findByLabelText('Persona del vínculo'), { target: { value: persona.id } });
  fireEvent.change(screen.getByLabelText('Inicio del vínculo'), { target: { value: '2026-01-01' } });
  fireEvent.click(screen.getByRole('button', { name: 'Asociar persona' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Seleccione al menos una función operativa activa.');
  expect(crear).not.toHaveBeenCalled();
});

it('muestra funciones históricas y solo envía fechas y estado al cerrar', async () => {
  vi.mocked(api.personas).mockResolvedValue([persona]);
  const vinculo = { id: 'vinculo', personaId: persona.id, empresaOperadoraId: empresa.id, fechaInicio: '2026-01-01', fechaFin: null,
    activo: true, creadoEn: '', actualizadoEn: '', persona,
    funcionesOperativas: [{ id: 'conductor', codigo: 'CONDUCTOR', nombre: 'Conductor', activo: true }] };
  vi.mocked(api.personal).mockResolvedValue([vinculo]);
  const actualizar = vi.spyOn(api, 'actualizarVinculo').mockResolvedValue({ ...vinculo, activo: false, fechaFin: '2026-05-31' });
  await abrir();
  fireEvent.click(screen.getByRole('button', { name: 'Seleccionar empresa' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Editar vínculo' }));
  expect(screen.getByText(/Funciones históricas:/)).toHaveTextContent('Conductor');
  expect(screen.queryByRole('checkbox', { name: 'Conductor' })).not.toBeInTheDocument();
  expect(screen.getByLabelText('Inicio del vínculo')).toHaveAttribute('readonly');
  fireEvent.change(screen.getByLabelText('Fin del vínculo'), { target: { value: '2026-05-31' } });
  fireEvent.click(screen.getByRole('checkbox', { name: 'Vínculo activo' }));
  fireEvent.click(screen.getByRole('button', { name: 'Guardar vínculo' }));
  await waitFor(() => expect(actualizar).toHaveBeenCalledWith('empresa', 'vinculo', {
    fechaInicio: '2026-01-01', fechaFin: '2026-05-31', activo: false,
  }));
});
