export type Position = [longitude: number, latitude: number];
export interface GeometriaLineString { type: 'LineString'; coordinates: Position[] }

/** CU-09: validate the transport shape without repairing or swapping coordinates. */
export function validarLineString(value: unknown): GeometriaLineString {
  if (!value || typeof value !== 'object' || !('type' in value) || value.type !== 'LineString'
      || !('coordinates' in value) || !Array.isArray(value.coordinates)
      || value.coordinates.length < 2 || value.coordinates.length > 10000) {
    throw new Error('Dibuje una LineString con al menos dos posiciones.');
  }
  const coordinates = value.coordinates.map((position: unknown): Position => {
    if (!Array.isArray(position) || position.length !== 2
        || typeof position[0] !== 'number' || typeof position[1] !== 'number'
        || !Number.isFinite(position[0]) || !Number.isFinite(position[1])
        || position[0] < -180 || position[0] > 180 || position[1] < -90 || position[1] > 90) {
      throw new Error('Las posiciones deben ser [longitud, latitud], finitas y dentro de rango.');
    }
    return [position[0], position[1]];
  });
  if (coordinates.every(([x, y]) => x === coordinates[0][0] && y === coordinates[0][1])) {
    throw new Error('La línea necesita dos posiciones distintas.');
  }
  return { type: 'LineString', coordinates };
}
