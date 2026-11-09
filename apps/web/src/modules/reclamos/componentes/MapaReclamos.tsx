import { useEffect, useRef, useState } from 'react';
import type { FeatureCollection, Point } from 'geojson';
import { crearMapaReferencias } from '../../../shared/map/adaptadorReferenciasEspaciales';

export function MapaReclamos({ datos, seleccionar, tipo = 'punto' }: { datos: FeatureCollection<Point>; seleccionar?: (lon: number, lat: number) => void; tipo?: 'punto' | 'reclamo' }) {
  const nodo = useRef<HTMLDivElement | null>(null), mapa = useRef<ReturnType<typeof crearMapaReferencias> | null>(null);
  const seleccionActual = useRef(seleccionar); seleccionActual.current = seleccionar;
  const [error, setError] = useState('');
  useEffect(() => {
    if (!nodo.current) return;
    mapa.current = crearMapaReferencias(nodo.current, g => { if (g?.type === 'Point') seleccionActual.current?.(g.coordinates[0], g.coordinates[1]); }, setError);
    return () => { mapa.current?.destruir(); mapa.current = null; };
  }, []);
  useEffect(() => { mapa.current?.mostrar(datos); }, [datos]);
  return <section><div ref={nodo} style={{ minHeight: 320 }} aria-label="Ubicaciones declaradas y referencias municipales" />
    {seleccionar && <button type="button" onClick={() => mapa.current?.dibujar('point')}>Seleccionar ubicación en el mapa</button>}
    <p role="status">{error}</p><p>{tipo === 'reclamo' ? 'Verde: reclamo con pertinencia validada; no lo convierte en punto crítico. Ámbar: reclamo sin esa validación.' : 'Verde: punto con validación municipal registrada. Ámbar: candidato sin esa validación.'} El mapa base no constituye información institucional ni clasifica zonas rojas.</p></section>;
}
