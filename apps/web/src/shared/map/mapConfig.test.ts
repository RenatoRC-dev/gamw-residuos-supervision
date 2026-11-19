import { expect, it } from 'vitest';
import { ESTILO_SIN_MAPA_BASE, resolverEstiloMapa } from './mapConfig';

// H-06: producción nunca depende en silencio del servicio de demostración.
it('producción sin configuración usa un estilo local sin fuentes externas y lo declara', () => {
  const r = resolverEstiloMapa(undefined, true);
  expect(r).toEqual({ style: ESTILO_SIN_MAPA_BASE, sinMapaBase: true });
  expect(JSON.stringify(r.style)).not.toMatch(/https?:/);
  expect(resolverEstiloMapa('   ', true).sinMapaBase).toBe(true);
});

it('un estilo configurado explícitamente se usa tal cual en cualquier entorno', () => {
  expect(resolverEstiloMapa('https://mapas.ejemplo.invalid/estilo.json', true)).toEqual({ style: 'https://mapas.ejemplo.invalid/estilo.json', sinMapaBase: false });
  expect(resolverEstiloMapa('data:application/json,{}', false).style).toBe('data:application/json,{}');
});

it('desarrollo local conserva el estilo de demostración como comodidad', () => {
  expect(resolverEstiloMapa(undefined, false)).toEqual({ style: 'https://demotiles.maplibre.org/style.json', sinMapaBase: false });
});
