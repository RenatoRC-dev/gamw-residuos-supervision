import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ResumenEjecucion } from './ResumenEjecucion';
import * as api from '../api/ejecucion';

const inicio: api.EventoEjecucionJornada = { id: 'evento-inicio', eventoClienteId: 'cliente-inicio', jornadaId: 'jornada', asignacionJornadaId: 'a1',
  tipoEvento: 'INICIO', capturadoEn: '2026-11-10T11:02:00Z', recibidoEn: '2026-11-10T13:47:00Z' };
it('muestra una jornada sin eventos sin acciones de ejecución', async () => {
  vi.spyOn(api, 'consultarEjecucion').mockResolvedValue({ jornadaId: 'jornada', inicio: null, fin: null });
  render(<ResumenEjecucion jornadaId="jornada" />);
  expect(await screen.findByText('Sin inicio registrado')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /Iniciar jornada|Finalizar jornada/ })).not.toBeInTheDocument();
});
it('distingue captura de recepción en una ejecución abierta', async () => {
  vi.spyOn(api, 'consultarEjecucion').mockResolvedValue({ jornadaId: 'jornada', inicio, fin: null });
  render(<ResumenEjecucion jornadaId="jornada" />);
  expect(await screen.findByText('Inicio registrado / ejecución abierta')).toBeInTheDocument();
  expect(screen.getByText(inicio.capturadoEn)).toBeInTheDocument();
  expect(screen.getByText(inicio.recibidoEn)).toBeInTheDocument();
});
it('muestra FIN con otra asignación sin inferir un resultado del servicio', async () => {
  const fin: api.EventoEjecucionJornada = { ...inicio, id: 'evento-fin', eventoClienteId: 'cliente-fin', tipoEvento: 'FIN', asignacionJornadaId: 'a2',
    capturadoEn: '2026-11-10T15:00:00Z', recibidoEn: '2026-11-10T16:00:00Z' };
  vi.spyOn(api, 'consultarEjecucion').mockResolvedValue({ jornadaId: 'jornada', inicio, fin });
  render(<ResumenEjecucion jornadaId="jornada" />);
  expect(await screen.findByText('Fin registrado')).toBeInTheDocument();
  expect(screen.getByText('Asignación histórica: a1')).toBeInTheDocument();
  expect(screen.getByText('Asignación histórica: a2')).toBeInTheDocument();
  expect(screen.queryByText(/Servicio completado|Recolección confirmada|Porcentaje de cumplimiento/)).not.toBeInTheDocument();
  expect(screen.getAllByRole('button')).toHaveLength(1);
});
