import { useEffect, useRef } from 'react';
import type { FeatureCollection, Point } from 'geojson';
import { crearMapaTrayectoria } from '../../../../shared/map/adaptadorMapaTrayectoria';
import type { JornadaMonitoreada } from '../api/monitoreo';
import 'maplibre-gl/dist/maplibre-gl.css';

export function MapaMonitoreo({ jornadas, seleccion, ahora, umbral, aviso }: {
  jornadas: JornadaMonitoreada[]; seleccion: string; ahora: number; umbral: number; aviso: (s: string) => void;
}) {
  const nodo = useRef<HTMLDivElement>(null);
  const mapa = useRef<ReturnType<typeof crearMapaTrayectoria> | null>(null);
  const previa = useRef<string | null>(null);
  useEffect(() => {
    if (!nodo.current) return;
    try { mapa.current = crearMapaTrayectoria(nodo.current, aviso); }
    catch { aviso('Mapa no disponible; consulte las posiciones conservadas en la tabla.'); }
    return () => { mapa.current?.destruir(); mapa.current = null; };
  }, [aviso]);
  useEffect(() => {
    const puntos: FeatureCollection<Point> = { type: 'FeatureCollection', features: jornadas.flatMap(j => {
      const p = j.ultimaPosicion;
      return p ? [{ type: 'Feature' as const, id: p.capturaId, geometry: { type: 'Point' as const, coordinates: [p.longitud, p.latitud] },
        properties: { ...p, placa: j.placa, sinActualizacionReciente: ahora - Date.parse(p.capturadoEn) > umbral * 1000 } }] : [];
    }) };
    mapa.current?.mostrar(puntos, jornadas.find(j => j.contexto.jornadaId === seleccion)?.rutaReferencia ?? null, false, previa.current !== seleccion);
    previa.current = seleccion;
  }, [jornadas, seleccion, ahora, umbral]);
  return <div ref={nodo} role="region" aria-label="Mapa municipal de últimas posiciones" style={{ height: 430, width: '100%' }} />;
}
