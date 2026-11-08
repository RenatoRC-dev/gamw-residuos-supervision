import { Map as LibreMap, LngLatBounds, NavigationControl, setWorkerUrl, type GeoJSONSource } from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import { TerraDraw, TerraDrawLineStringMode, TerraDrawSelectMode } from 'terra-draw';
import { TerraDrawMapLibreGLAdapter } from 'terra-draw-maplibre-gl-adapter';
import { mapConfig } from './mapConfig';
import type { LineString, Feature } from 'geojson';

// MapLibre 6 requires the bundler-emitted worker URL (also in production builds).
setWorkerUrl(workerUrl);

// Map infrastructure works with GeoJSON only, independent of routes API/domain rules.
export interface AdaptadorMapaRuta {
  dibujarLinea(): void;
  editarLinea(geometry?: LineString): void;
  limpiarBorrador(): void;
  mostrarPersistida(geometry: LineString | null): void;
  destruir(): void;
}

export function crearMapaRuta(container: HTMLElement, callbacks: {
  ready(): void; changed(geometry: unknown): void; failed(message: string): void;
}): AdaptadorMapaRuta {
  const map = new LibreMap({ container, style: mapConfig.style, center: mapConfig.center, zoom: mapConfig.zoom });
  map.addControl(new NavigationControl(), 'top-right');
  let draw: TerraDraw | null = null;
  let destroyed = false;
  const empty = { type: 'FeatureCollection' as const, features: [] };
  const currentLine = () => draw?.getSnapshot().find(feature => feature.geometry.type === 'LineString' && feature.properties.mode === 'linestring');
  const publish = () => callbacks.changed(currentLine()?.geometry ?? null);
  map.on('error', event => { if (!destroyed) callbacks.failed(event.error.message); });
  map.on('load', () => {
    if (destroyed) return;
    map.addSource('ruta-persistida', { type: 'geojson', data: empty });
    map.addLayer({ id: 'ruta-persistida-linea', type: 'line', source: 'ruta-persistida',
      paint: { 'line-color': '#1565c0', 'line-width': 5 } });
    draw = new TerraDraw({
      adapter: new TerraDrawMapLibreGLAdapter({ map, coordinatePrecision: 9 }),
      modes: [
        new TerraDrawLineStringMode({ keyEvents: { finish: 'Enter', cancel: 'Escape' }, styles: { lineStringColor: '#b45309', lineStringWidth: 4 } }),
        new TerraDrawSelectMode({ flags: { linestring: { feature: { draggable: true,
          coordinates: { draggable: true, deletable: true, midpoints: true } } } } }),
      ],
    });
    draw.on('finish', () => { draw?.setMode('select'); publish(); });
    draw.on('change', () => { if (draw?.getMode() === 'select') publish(); });
    draw.start();
    draw.setMode('select');
    callbacks.ready();
  });
  return {
    dibujarLinea() { draw?.clear(); callbacks.changed(null); draw?.setMode('linestring'); },
    limpiarBorrador() { if (draw) { draw.setMode('select'); draw.clear(); } callbacks.changed(null); },
    editarLinea(geometry) {
      if (!draw) return;
      draw.setMode('select');
      if (geometry) {
        draw.clear();
        const feature: Feature<LineString, { mode: string }> = { id: draw.getFeatureId(), type: 'Feature', properties: { mode: 'linestring' }, geometry };
        const results = draw.addFeatures([feature]);
        if (results.some(result => !result.valid)) { callbacks.failed('No se pudo preparar la línea para edición.'); return; }
      }
      const feature = currentLine();
      if (feature?.id !== undefined) draw.selectFeature(feature.id);
      publish();
    },
    mostrarPersistida(geometry) {
      const source = map.getSource<GeoJSONSource>('ruta-persistida');
      if (!source) return;
      source.setData(geometry ? { type: 'Feature', properties: {}, geometry } : empty);
      if (geometry) {
        const bounds = new LngLatBounds();
        geometry.coordinates.forEach(position => bounds.extend([position[0], position[1]]));
        map.fitBounds(bounds, { padding: 55, maxZoom: 15, duration: 0 });
      }
    },
    destruir() { destroyed = true; draw?.stop(); map.remove(); },
  };
}
