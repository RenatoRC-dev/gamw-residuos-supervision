// Cartographic provider and viewport are configuration, never institutional route data.
export const mapConfig = {
  style: import.meta.env.VITE_MAP_STYLE_URL || 'https://demotiles.maplibre.org/style.json',
  center: [Number(import.meta.env.VITE_MAP_LONGITUDE || -63.16), Number(import.meta.env.VITE_MAP_LATITUDE || -17.51)] as [number, number],
  zoom: Number(import.meta.env.VITE_MAP_ZOOM || 12),
};
