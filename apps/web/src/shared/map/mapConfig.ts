// Cartographic provider and viewport are configuration, never institutional route data.
import type { StyleSpecification } from 'maplibre-gl';

/** Estilo local sin fuentes externas: el mapa muestra solo capas institucionales (rutas, GPS, referencias) sin pedir teselas a terceros. */
export const ESTILO_SIN_MAPA_BASE: StyleSpecification = { version: 8, sources: {}, layers: [{ id: 'fondo', type: 'background', paint: { 'background-color': '#f3f4f6' } }] };
const DEMOSTRACION = 'https://demotiles.maplibre.org/style.json';
export const AVISO_SIN_MAPA_BASE = 'Mapa base no configurado para este despliegue: se muestran solo capas institucionales; la tabla conserva la evidencia.';

/**
 * H-06: en un build de producción NO se usa en silencio el servicio de demostración de MapLibre (dependencia y tráfico a terceros).
 * Sin VITE_MAP_STYLE_URL se usa un estilo local vacío y se avisa. El proveedor institucional aprobado es decisión de despliegue pendiente.
 * En desarrollo local se conserva el estilo de demostración como comodidad.
 */
export function resolverEstiloMapa(url: string | undefined, produccion: boolean): { style: string | StyleSpecification; sinMapaBase: boolean } {
  const configurado = url?.trim();
  if (configurado) return { style: configurado, sinMapaBase: false };
  return produccion ? { style: ESTILO_SIN_MAPA_BASE, sinMapaBase: true } : { style: DEMOSTRACION, sinMapaBase: false };
}
const estilo = resolverEstiloMapa(import.meta.env.VITE_MAP_STYLE_URL, import.meta.env.PROD);
export const mapConfig = {
  style: estilo.style,
  sinMapaBase: estilo.sinMapaBase,
  center: [Number(import.meta.env.VITE_MAP_LONGITUDE || -63.16), Number(import.meta.env.VITE_MAP_LATITUDE || -17.51)] as [number, number],
  zoom: Number(import.meta.env.VITE_MAP_ZOOM || 12),
};
