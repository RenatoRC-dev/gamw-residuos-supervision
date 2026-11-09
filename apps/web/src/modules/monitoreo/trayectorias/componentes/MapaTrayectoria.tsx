import { useEffect, useRef } from 'react';
import { crearMapaTrayectoria } from '../../../../shared/map/adaptadorMapaTrayectoria';
import type { DetalleTrayectoria } from '../api/trayectorias';
import 'maplibre-gl/dist/maplibre-gl.css';

export function MapaTrayectoria({ datos, unir, aviso }: { datos: DetalleTrayectoria; unir: boolean; aviso: (texto: string) => void }) {
  const contenedor = useRef<HTMLDivElement>(null);
  const mapa = useRef<ReturnType<typeof crearMapaTrayectoria> | null>(null);
  useEffect(() => {
    if (!contenedor.current) return;
    try { mapa.current = crearMapaTrayectoria(contenedor.current, aviso); }
    catch { aviso('No se pudo iniciar el mapa. Consulte los datos conservados en la tabla.'); }
    return () => { mapa.current?.destruir(); mapa.current = null; };
  }, [aviso]);
  useEffect(() => { mapa.current?.mostrar(datos.seguimiento.trayectoria, datos.rutaReferencia, unir); }, [datos, unir]);
  return <div ref={contenedor} role="region" aria-label="Mapa de trayectoria GPS y ruta histórica" style={{ height: 430, width: '100%' }} />;
}
