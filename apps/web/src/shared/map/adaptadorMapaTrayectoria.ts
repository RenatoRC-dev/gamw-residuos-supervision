import { Map as LibreMap, LngLatBounds, NavigationControl, Popup, setWorkerUrl, type GeoJSONSource } from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import type { FeatureCollection, Point, LineString } from 'geojson';
import { mapConfig } from './mapConfig';

setWorkerUrl(workerUrl);
export function crearMapaTrayectoria(elemento: HTMLElement, aviso: (texto: string) => void) {
  const vacio: FeatureCollection = { type: 'FeatureCollection', features: [] };
  const mapa = new LibreMap({ container: elemento, style: mapConfig.style, center: mapConfig.center, zoom: mapConfig.zoom });
  mapa.addControl(new NavigationControl(), 'top-right');
  let listo = false;
  let destruido = false;
  let ajustado = false;
  let referencias: FeatureCollection = vacio;
  let datos: { puntos: FeatureCollection<Point>; ruta: LineString | null; unir: boolean; ajustar: boolean } | null = null;
  const pintar = () => {
    if (!listo || !datos || destruido) return;
    mapa.getSource<GeoJSONSource>('muestras')?.setData(datos.puntos);
    mapa.getSource<GeoJSONSource>('controles')?.setData(referencias);
    mapa.getSource<GeoJSONSource>('referencia')?.setData(datos.ruta ? { type: 'Feature', properties: {}, geometry: datos.ruta } : vacio);
    const coordenadas = datos.puntos.features.map(p => p.geometry.coordinates);
    mapa.getSource<GeoJSONSource>('inferencias')?.setData(datos.unir && coordenadas.length > 1
      ? { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: coordenadas } } : vacio);
    const limites = new LngLatBounds();
    [...(datos.ruta?.coordinates ?? []), ...coordenadas].forEach(p => limites.extend([p[0], p[1]]));
    if ((!ajustado || datos.ajustar) && !limites.isEmpty()) {
      mapa.fitBounds(limites, { padding: 35, maxZoom: 15, duration: 0 }); ajustado = true;
    }
  };
  mapa.on('error', () => { if (!destruido) aviso('El mapa base no está disponible o presenta errores. Los datos siguen consultables en la tabla.'); });
  mapa.on('load', () => {
    if (destruido) return;
    for (const id of ['referencia', 'inferencias', 'muestras', 'controles']) mapa.addSource(id, { type: 'geojson', data: vacio });
    mapa.addLayer({ id: 'geocercas-area', type: 'fill', source: 'controles', filter: ['==', ['geometry-type'], 'Polygon'], paint: { 'fill-color': '#7c3aed', 'fill-opacity': 0.15 } });
    mapa.addLayer({ id: 'geocercas-borde', type: 'line', source: 'controles', filter: ['==', ['geometry-type'], 'Polygon'], paint: { 'line-color': '#7c3aed', 'line-width': 2 } });
    mapa.addLayer({ id: 'puntos-control', type: 'circle', source: 'controles', filter: ['==', ['geometry-type'], 'Point'], paint: { 'circle-color': '#7c3aed', 'circle-radius': 8, 'circle-opacity': 0.65 } });
    mapa.addLayer({ id: 'referencia-linea', type: 'line', source: 'referencia', paint: { 'line-color': '#1565c0', 'line-width': 4 } });
    mapa.addLayer({ id: 'tramos-inferidos', type: 'line', source: 'inferencias',
      paint: { 'line-color': '#b45309', 'line-width': 2, 'line-dasharray': [2, 3] } });
    mapa.addLayer({ id: 'gps-muestras', type: 'circle', source: 'muestras',
      paint: { 'circle-radius': 5, 'circle-color': ['case', ['boolean', ['get', 'sinActualizacionReciente'], false], '#6b7280', '#b45309'],
        'circle-stroke-color': '#fff', 'circle-stroke-width': 1 } });
    mapa.on('click', 'gps-muestras', evento => {
      const p = evento.features?.[0];
      if (p?.geometry.type !== 'Point') return;
      new Popup().setLngLat([p.geometry.coordinates[0], p.geometry.coordinates[1]])
        .setText(`${p.properties?.placa ? `Vehículo asignado: ${p.properties.placa} · ` : ''}Captura: ${p.properties?.capturadoEn} · Recepción: ${p.properties?.recibidoEn} · Precisión: ${p.properties?.precisionMetros} m`)
        .addTo(mapa);
    });
    listo = true; pintar();
  });
  return {
    mostrar(puntos: FeatureCollection<Point>, ruta: LineString | null, unir: boolean, ajustar = true) { datos = { puntos, ruta, unir, ajustar }; pintar(); },
    mostrarReferencias(coleccion: FeatureCollection) { referencias = coleccion; if (listo) mapa.getSource<GeoJSONSource>('controles')?.setData(referencias); },
    destruir() { destruido = true; mapa.remove(); },
  };
}
