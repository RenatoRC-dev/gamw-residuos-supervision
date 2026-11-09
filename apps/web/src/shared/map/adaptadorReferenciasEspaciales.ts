import { Map as LibreMap, NavigationControl, LngLatBounds, setWorkerUrl, type GeoJSONSource } from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import { TerraDraw, TerraDrawPointMode, TerraDrawPolygonMode, TerraDrawSelectMode } from 'terra-draw';
import { TerraDrawMapLibreGLAdapter } from 'terra-draw-maplibre-gl-adapter';
import type { FeatureCollection, Geometry } from 'geojson';
import { mapConfig } from './mapConfig';

setWorkerUrl(workerUrl);
export function crearMapaReferencias(nodo: HTMLElement, cambio: (g: Geometry | null) => void, error: (s: string) => void) {
  const mapa = new LibreMap({ container: nodo, style: mapConfig.style, center: mapConfig.center, zoom: mapConfig.zoom });
  mapa.addControl(new NavigationControl());
  let dibujo: TerraDraw | null = null, destruido = false;
  let referencias: FeatureCollection = { type: 'FeatureCollection', features: [] };
  const publicar = () => cambio(dibujo?.getSnapshot().find(f => ['point', 'polygon'].includes(String(f.properties.mode)))?.geometry ?? null);
  mapa.on('error', () => { if (!destruido) error('No se pudo cargar parte del mapa. La información institucional se conserva.'); });
  mapa.on('load', () => {
    if (destruido) return;
    mapa.addSource('controles', { type: 'geojson', data: referencias });
    mapa.addLayer({ id: 'areas', type: 'fill', source: 'controles', filter: ['==', ['geometry-type'], 'Polygon'], paint: { 'fill-color': '#7c3aed', 'fill-opacity': 0.15 } });
    mapa.addLayer({ id: 'bordes', type: 'line', source: 'controles', filter: ['==', ['geometry-type'], 'Polygon'], paint: { 'line-color': '#7c3aed' } });
    mapa.addLayer({ id: 'puntos', type: 'circle', source: 'controles', filter: ['==', ['geometry-type'], 'Point'], paint: { 'circle-color': '#7c3aed', 'circle-radius': 7 } });
    dibujo = new TerraDraw({ adapter: new TerraDrawMapLibreGLAdapter({ map: mapa, coordinatePrecision: 12 }), modes: [
      new TerraDrawPointMode(), new TerraDrawPolygonMode(), new TerraDrawSelectMode({ flags: {
        point: { feature: { draggable: true } }, polygon: { feature: { draggable: true, coordinates: { draggable: true, deletable: true, midpoints: true } } },
      } }),
    ] });
    dibujo.on('finish', () => { dibujo?.setMode('select'); publicar(); });
    dibujo.on('change', () => { if (dibujo?.getMode() === 'select') publicar(); });
    dibujo.start(); dibujo.setMode('select');
  });
  return {
    dibujar(tipo: 'point' | 'polygon') { if (!dibujo) { error('Espere a que el mapa esté listo.'); return; } dibujo.clear(); cambio(null); dibujo.setMode(tipo); },
    mostrar(datos: FeatureCollection) {
      referencias = datos; mapa.getSource<GeoJSONSource>('controles')?.setData(datos);
      const limites = new LngLatBounds();
      const extender = (valor: unknown): void => {
        if (!Array.isArray(valor)) return;
        if (valor.length >= 2 && typeof valor[0] === 'number' && typeof valor[1] === 'number') limites.extend([valor[0], valor[1]]);
        else valor.forEach(extender);
      };
      datos.features.forEach(f => { if ('coordinates' in f.geometry) extender(f.geometry.coordinates); });
      if (!limites.isEmpty() && mapa.isStyleLoaded()) mapa.fitBounds(limites, { padding: 40, maxZoom: 15, duration: 0 });
    },
    destruir() { destruido = true; dibujo?.stop(); mapa.remove(); },
  };
}
