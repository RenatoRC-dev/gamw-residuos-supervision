import { useEffect, useState } from 'react';
import { consultarEjecucion, type LecturaEjecucion } from '../api/ejecucion';

/** Visibilidad de evidencia; la web no emite acciones operativas ni deriva resultados del servicio. */
export function ResumenEjecucion({ jornadaId }: { jornadaId: string }) {
  const [lectura, setLectura] = useState<LecturaEjecucion | null>(null);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const abort = new AbortController();
    setLectura(null); setError('');
    consultarEjecucion(jornadaId, abort.signal).then(l => { if (!abort.signal.aborted) setLectura(l); })
      .catch(e => { if (!abort.signal.aborted) setError(e instanceof Error ? e.message : 'No se pudo consultar la evidencia.'); });
    return () => abort.abort();
  }, [jornadaId, revision]);
  return <section aria-label="Evidencia operativa">
    <h3>Evidencia operativa registrada</h3>
    <p>Registrar INICIO o FIN no acredita recolección. El fiscalizador determina el resultado institucional.</p>
    {error && <p role="alert">{error}</p>}
    {!lectura && !error && <p>Consultando evidencia operativa…</p>}
    {lectura && <>
      <p>{lectura.fin ? 'Fin registrado' : lectura.inicio ? 'Inicio registrado / ejecución abierta' : 'Sin inicio registrado'}</p>
      {([lectura.inicio, lectura.fin]).filter(e => e !== null).map(e => <article key={e.id}>
        <h4>{e.tipoEvento === 'INICIO' ? 'INICIO registrado' : 'FIN registrado'}</h4>
        <p>Capturado por el cliente: <time dateTime={e.capturadoEn}>{e.capturadoEn}</time></p>
        <p>Recibido por el servidor: <time dateTime={e.recibidoEn}>{e.recibidoEn}</time></p>
        <p>Asignación histórica: {e.asignacionJornadaId}</p>
      </article>)}
    </>}
    <button type="button" onClick={() => setRevision(r => r + 1)}>Actualizar evidencia</button>
  </section>;
}
