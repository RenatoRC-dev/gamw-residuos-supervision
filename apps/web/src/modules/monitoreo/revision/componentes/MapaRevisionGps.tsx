import { useEffect, useRef } from 'react';
import type { FeatureCollection, Point } from 'geojson';
import { crearMapaTrayectoria } from '../../../../shared/map/adaptadorMapaTrayectoria';
import type { RevisionGps } from '../api/revisionGps';
import 'maplibre-gl/dist/maplibre-gl.css';

export function MapaRevisionGps({ datos, aviso }: { datos: RevisionGps; aviso: (s: string) => void }) {
  const nodo = useRef<HTMLDivElement>(null), mapa = useRef<ReturnType<typeof crearMapaTrayectoria> | null>(null);
  useEffect(() => {
    if (!nodo.current) return;
    try { mapa.current = crearMapaTrayectoria(nodo.current, aviso); }
    catch { aviso('Mapa no disponible; los hallazgos y datos siguen consultables.'); }
    return () => { mapa.current?.destruir(); mapa.current = null; };
  }, [aviso]);
  useEffect(() => {
    const puntos: FeatureCollection<Point> = { type: 'FeatureCollection', features: datos.muestras.map(p => ({
      type: 'Feature', id: p.capturaId, geometry: { type: 'Point', coordinates: [p.longitud, p.latitud] }, properties: { ...p },
    })) };
    const referencias: FeatureCollection = { type: 'FeatureCollection', features: datos.referencias.map(r => ({
      type: 'Feature', id: r.versionId, geometry: r.geometria, properties: { nombre: r.nombre, estado: r.estado },
    })) };
    mapa.current?.mostrarReferencias(referencias);
    mapa.current?.mostrar(puntos, datos.rutaReferencia, false);
  }, [datos]);
  return <div ref={nodo} aria-label="Muestras originales, ruta histórica y referencias de revisión" style={{ height: 430 }} />;
}
