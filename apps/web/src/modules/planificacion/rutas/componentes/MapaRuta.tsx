import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import { crearMapaRuta, type AdaptadorMapaRuta } from '../../../../shared/map/adaptadorMapaRuta';
import { validarLineString, type GeometriaLineString } from '../modelo/geometria';
import 'maplibre-gl/dist/maplibre-gl.css';

export interface ComandosMapaRuta { limpiarBorrador(): void; editarGeometria(geometria: GeometriaLineString): void }

export const MapaRuta = forwardRef<ComandosMapaRuta, {
  persistida: GeometriaLineString | null; alCambiarBorrador(geometria: GeometriaLineString | null): void; disabled: boolean;
}>(function MapaRuta({ persistida, alCambiarBorrador, disabled }, ref) {
  const container = useRef<HTMLDivElement>(null);
  const adapter = useRef<AdaptadorMapaRuta | null>(null);
  const changed = useRef(alCambiarBorrador);
  changed.current = alCambiarBorrador;
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  useImperativeHandle(ref, () => ({
    limpiarBorrador: () => adapter.current?.limpiarBorrador(),
    editarGeometria: geometria => adapter.current?.editarLinea(geometria),
  }), []);

  useEffect(() => {
    if (!container.current) return;
    try {
      const instance = crearMapaRuta(container.current, {
        ready: () => setReady(true),
        changed: geometria => {
          try { changed.current(geometria ? validarLineString(geometria) : null); }
          catch { changed.current(null); }
        },
        failed: message => setError(`No se pudo representar el mapa: ${message}`),
      });
      adapter.current = instance;
      return () => { instance.destruir(); adapter.current = null; };
    } catch { setError('Este navegador no pudo iniciar el mapa WebGL.'); }
  }, []);

  useEffect(() => { if (ready) adapter.current?.mostrarPersistida(persistida); }, [persistida, ready]);

  return <section className="seccion-mapa-ruta" aria-label="Digitalización de ruta">
    <div className="barra-mapa">
      <button type="button" disabled={!ready || disabled} onClick={() => adapter.current?.dibujarLinea()}>Dibujar línea</button>
      <button type="button" disabled={!ready || disabled} onClick={() => adapter.current?.editarLinea()}>Editar borrador</button>
      <button type="button" disabled={!ready || disabled} onClick={() => adapter.current?.limpiarBorrador()}>Borrar borrador</button>
    </div>
    <p id="map-help">Marque los vértices y pulse Enter para terminar. Escape cancela. En edición puede arrastrar vértices.</p>
    {error && <p role="alert">{error}</p>}
    <div ref={container} className="mapa-ruta" data-testid="mapa-ruta" aria-describedby="map-help" />
    <p role="status">{ready ? 'Mapa disponible' : 'Cargando mapa…'}</p>
    <p>Azul: versión recuperada. Naranja: borrador sin guardar.</p>
  </section>;
});
