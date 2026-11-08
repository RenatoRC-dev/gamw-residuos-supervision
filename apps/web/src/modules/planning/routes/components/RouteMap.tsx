import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { createRouteMap, type RouteMapAdapter } from '../../../../shared/map/routeMapAdapter';
import { parseLineString, type LineStringGeometry } from '../model/geometry';
import 'maplibre-gl/dist/maplibre-gl.css';

export interface RouteMapCommands { clearDraft(): void; editGeometry(geometry: LineStringGeometry): void }

export const RouteMap = forwardRef<RouteMapCommands, {
  persisted: LineStringGeometry | null; onDraft(geometry: LineStringGeometry | null): void; disabled: boolean;
}>(function RouteMap({ persisted, onDraft, disabled }, ref) {
  const container = useRef<HTMLDivElement>(null);
  const adapter = useRef<RouteMapAdapter | null>(null);
  const changed = useRef(onDraft);
  changed.current = onDraft;
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  useImperativeHandle(ref, () => ({
    clearDraft: () => adapter.current?.clearDraft(),
    editGeometry: geometry => adapter.current?.editLine(geometry),
  }), []);

  useEffect(() => {
    if (!container.current) return;
    try {
      const instance = createRouteMap(container.current, {
        ready: () => setReady(true),
        changed: geometry => {
          try { changed.current(geometry ? parseLineString(geometry) : null); }
          catch { changed.current(null); }
        },
        failed: message => setError(`No se pudo representar el mapa: ${message}`),
      });
      adapter.current = instance;
      return () => { instance.destroy(); adapter.current = null; };
    } catch { setError('Este navegador no pudo iniciar el mapa WebGL.'); }
  }, []);

  useEffect(() => { if (ready) adapter.current?.displayPersisted(persisted); }, [persisted, ready]);

  return <section className="route-map-section" aria-label="Digitalización de ruta">
    <div className="map-toolbar">
      <button type="button" disabled={!ready || disabled} onClick={() => adapter.current?.drawLine()}>Dibujar línea</button>
      <button type="button" disabled={!ready || disabled} onClick={() => adapter.current?.editLine()}>Editar borrador</button>
      <button type="button" disabled={!ready || disabled} onClick={() => adapter.current?.clearDraft()}>Borrar borrador</button>
    </div>
    <p id="map-help">Marque los vértices y pulse Enter para terminar. Escape cancela. En edición puede arrastrar vértices.</p>
    {error && <p role="alert">{error}</p>}
    <div ref={container} className="route-map" data-testid="route-map" aria-describedby="map-help" />
    <p role="status">{ready ? 'Mapa disponible' : 'Cargando mapa…'}</p>
    <p>Azul: versión recuperada. Naranja: borrador sin guardar.</p>
  </section>;
});
