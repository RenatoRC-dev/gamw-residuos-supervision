import { describe, expect, it } from 'vitest';
import { validarLineString } from './geometria';

describe('CU-09 GeoJSON', () => {
  it('copies longitude/latitude positions without swapping or rounding', () => {
    const input = { type: 'LineString', coordinates: [[-10.12345678901234, 20.5], [-10.2, 20.6]] };
    const parsed = validarLineString(input);
    input.coordinates[0][0] = 100;
    expect(parsed.coordinates[0]).toEqual([-10.12345678901234, 20.5]);
  });
  it.each([
    null, { type: 'Point', coordinates: [0, 0] }, { type: 'LineString', coordinates: [[0, 0]] },
    { type: 'LineString', coordinates: [[181, 0], [1, 2]] },
    { type: 'LineString', coordinates: [[0, -91], [1, 2]] },
    { type: 'LineString', coordinates: [[NaN, 0], [1, 2]] },
    { type: 'LineString', coordinates: [[0, Infinity], [1, 2]] },
    { type: 'LineString', coordinates: [[0, 0], [0, 0]] },
  ])('rejects malformed or invalid geometria: %j', value => {
    expect(() => validarLineString(value)).toThrow();
  });
});
